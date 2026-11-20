package com.xpertsedge.hrms;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** MailerSend HTTP API boundary. The API token stays in deployment environment variables. */
@Service
public class MailerSendTransport implements MailTransport {
  private final String token,domain,inboundDomain;
  private final ObjectMapper json;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  public MailerSendTransport(@Value("${MAILERSEND_API_TOKEN:}") String token,@Value("${hrms.mail.domain:xpertsedgetech.com}") String domain,@Value("${MAILERSEND_INBOUND_DOMAIN:inbound.xpertsedgetech.com}") String inboundDomain,ObjectMapper json){this.token=token.trim();this.domain=domain.trim();this.inboundDomain=inboundDomain.trim();this.json=json;}
  public boolean configured(){return !token.isBlank()&&!domain.isBlank();}
  String replyTo(String from){int at=from.indexOf('@');return at>0?from.substring(0,at)+"@"+inboundDomain:from;}
  static ArrayNode addresses(ObjectNode root,String field,String raw){ArrayNode values=root.putArray(field);for(String address:raw.split(",")){String email=address.trim();if(!email.isEmpty())values.addObject().put("email",email);}return values;}
  public SendResult send(OutgoingMail mail){
    if(!configured())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"MailerSend is not configured. Set MAILERSEND_API_TOKEN in the backend environment.");
    try{
      ObjectNode payload=json.createObjectNode();payload.putObject("from").put("email",mail.from());addresses(payload,"to",mail.to());if(!mail.cc().isBlank())addresses(payload,"cc",mail.cc());
      payload.putObject("reply_to").put("email",replyTo(mail.from()));payload.put("subject",mail.subject());payload.put("text",mail.body());
      ArrayNode uploads=payload.putArray("attachments");for(WebmailAttachment file:mail.attachments()){uploads.addObject().put("filename",file.filename).put("content",Base64.getEncoder().encodeToString(file.data)).put("disposition","attachment");}
      HttpRequest request=HttpRequest.newBuilder(URI.create("https://api.mailersend.com/v1/email")).timeout(Duration.ofSeconds(30)).header("Authorization","Bearer "+token).header("Content-Type","application/json").header("X-Idempotency-Key",mail.idempotencyKey()).POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload))).build();
      HttpResponse<String> response=http.send(request,HttpResponse.BodyHandlers.ofString());
      if(response.statusCode()/100!=2)throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"MailerSend rejected the message (HTTP "+response.statusCode()+").");
      return new SendResult(response.headers().firstValue("x-message-id").orElse(""),"ACCEPTED");
    }catch(ResponseStatusException error){throw error;}catch(Exception error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"MailerSend could not be reached.");}
  }
}
