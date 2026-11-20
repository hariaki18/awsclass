package com.xpertsedge.hrms;

import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Public, signed MailerSend inbound endpoint. It never uses a browser session or logs mail data. */
@RestController @RequestMapping("/api/mail")
public class MailerSendInboundController {
  private static final String TEST_SECRET="test_Am3L1GuOIc4blLUuHqAPxxwkZaJyEk8G";
  private final String secret,eventsSecret; private final ObjectMapper json; private final MailboxRepo boxes; private final AliasRepo aliases;
  private final WebmailMessageRepo messages; private final WebmailAttachmentRepo attachments; private final WebmailDeletedCaptureRepo deleted;
  public MailerSendInboundController(@Value("${MAILERSEND_INBOUND_SIGNING_SECRET:}") String secret,@Value("${MAILERSEND_EVENTS_SIGNING_SECRET:}") String eventsSecret,ObjectMapper json,MailboxRepo boxes,AliasRepo aliases,WebmailMessageRepo messages,WebmailAttachmentRepo attachments,WebmailDeletedCaptureRepo deleted){
    this.secret=secret.trim();this.eventsSecret=eventsSecret.trim();this.json=json;this.boxes=boxes;this.aliases=aliases;this.messages=messages;this.attachments=attachments;this.deleted=deleted;
  }
  boolean valid(byte[] raw,String signature,String key){try{if(key.isBlank()||signature==null)return false;Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));return MessageDigest.isEqual(mac.doFinal(raw),hex(signature));}catch(Exception e){return false;}}
  static byte[] hex(String value){if(value==null||value.length()%2!=0)return new byte[0];try{byte[] out=new byte[value.length()/2];for(int i=0;i<out.length;i++)out[i]=(byte)Integer.parseInt(value.substring(i*2,i*2+2),16);return out;}catch(Exception e){return new byte[0];}}
  Mailbox recipientMailbox(String email){
    String local=email.contains("@")?email.substring(0,email.indexOf('@')).toLowerCase(Locale.ROOT):"";
    String target=aliases.findAll().stream().filter(a->a.alias.equalsIgnoreCase(local)).map(a->a.target).findFirst().orElse(local);
    return boxes.findAll().stream().filter(b->b.active&&b.address.equalsIgnoreCase(target)).findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE,"Unknown or inactive mailbox."));
  }
  String cleanText(JsonNode data){String body=data.path("text").asText("");if(body.isBlank())body=data.path("html").asText("").replaceAll("(?s)<[^>]*>"," ").replaceAll("\\s+"," ").trim();return body.length()>20000?body.substring(0,20000):body;}
  void copyAttachments(JsonNode data,String messageId){
    JsonNode list=data.path("attachments");if(list.has("data"))list=list.path("data");if(!list.isArray())return;long total=0;int count=0;
    for(JsonNode part:list){if(count>=5)break;String encoded=part.path("content").asText("");if(encoded.isBlank())continue;try{byte[] bytes=Base64.getDecoder().decode(encoded);if(bytes.length>1048576||total+bytes.length>1048576)continue;WebmailAttachment file=new WebmailAttachment();file.id=UUID.randomUUID().toString();file.messageId=messageId;file.filename=part.path("filename").asText("attachment");file.contentType=part.path("content_type").asText("application/octet-stream");file.byteSize=bytes.length;file.data=bytes;attachments.save(file);total+=bytes.length;count++;}catch(IllegalArgumentException ignored){/* malformed provider attachment is ignored */}}
  }
  @PostMapping(value="/inbound",consumes=MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> inbound(@RequestBody byte[] raw,@RequestHeader(value="Signature",required=false) String signature,HttpServletRequest ignored){
    try{
      JsonNode root=json.readTree(raw);String type=root.path("type").asText();String key="webhook.test".equals(type)?TEST_SECRET:secret;
      if(!valid(raw,signature,key))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid MailerSend signature.");
      if("webhook.test".equals(type)||!"inbound.message".equals(type))return ResponseEntity.ok().build();
      JsonNode data=root.path("data"),to=data.path("recipients").path("to").path("data");String recipient=to.isArray()&&to.size()>0?to.get(0).path("email").asText(""):"";
      Mailbox box=recipientMailbox(recipient);String providerId=data.path("id").asText();if(providerId.isBlank())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Missing provider message id.");String source="mailersend:"+providerId;
      if(messages.existsBySourceKey(source)||deleted.existsById(source))return ResponseEntity.ok().build();
      WebmailMessage message=new WebmailMessage();message.id=UUID.randomUUID().toString();message.ownerId=box.id;message.sourceKey=source;message.providerMessageId=providerId;message.folder=data.path("spam").path("verdict").asText("").equalsIgnoreCase("spam")?"Spam":"Inbox";message.originalFolder=message.folder;
      message.fromAddress=data.path("from").path("email").asText("");message.toAddress=recipient;message.replyToAddress=data.path("reply_to").path("email").asText("");message.subject=data.path("subject").asText("");message.body=cleanText(data);message.deliveryStatus="RECEIVED_PROVIDER";message.seen=false;
      messages.save(message);copyAttachments(data,message.id);return ResponseEntity.ok().build();
    }catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid MailerSend payload.");}
  }
  @PostMapping(value="/events",consumes=MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> events(@RequestBody byte[] raw,@RequestHeader(value="Signature",required=false) String signature){
    try{
      JsonNode root=json.readTree(raw);String type=root.path("type").asText();String key="webhook.test".equals(type)?TEST_SECRET:eventsSecret;
      if(!valid(raw,signature,key))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid MailerSend signature.");
      if("webhook.test".equals(type))return ResponseEntity.ok().build();
      JsonNode data=root.path("data");String providerId=data.path("message_id").asText();if(providerId.isBlank())return ResponseEntity.ok().build();
      WebmailMessage message=messages.findFirstByProviderMessageIdOrderByCreatedAtDesc(providerId).orElse(null);if(message==null)return ResponseEntity.ok().build();
      if("activity.delivered".equals(type)){message.deliveryStatus="DELIVERED";message.deliveryError="";}
      else if("activity.sent".equals(type)){message.deliveryStatus="SENT";}
      else if(type.equals("activity.hard_bounced")||type.equals("activity.soft_bounced")||type.equals("activity.spam_complaint")||type.equals("activity.suppressed")){
        String reason=data.path("meta").path("bounce_reason").asText("");if(reason.isBlank())reason=data.path("meta").path("suppression_reason").asText("");if(reason.isBlank())reason="Delivery failed.";
        message.deliveryStatus="FAILED";message.deliveryError=reason.length()>500?reason.substring(0,500):reason;
      }else return ResponseEntity.ok().build();
      messages.save(message);return ResponseEntity.ok().build();
    }catch(ResponseStatusException e){throw e;}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid MailerSend event payload.");}
  }
}
