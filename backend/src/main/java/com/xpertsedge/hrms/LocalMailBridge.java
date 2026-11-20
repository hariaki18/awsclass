package com.xpertsedge.hrms;
import com.fasterxml.jackson.databind.*;
import jakarta.mail.internet.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class LocalMailBridge {
  private final ObjectMapper json; private final String domain; private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
  private final JavaMailSenderImpl smtp=new JavaMailSenderImpl();
  @org.springframework.beans.factory.annotation.Autowired private WebmailAttachmentRepo attachments;
  @org.springframework.beans.factory.annotation.Autowired private WebmailDeletedCaptureRepo deleted;
  @org.springframework.beans.factory.annotation.Autowired private AliasRepo aliases;
  public LocalMailBridge(ObjectMapper json){this(json,"xpertsedgetech.com");}
  @org.springframework.beans.factory.annotation.Autowired public LocalMailBridge(ObjectMapper json,@org.springframework.beans.factory.annotation.Value("${hrms.mail.domain:xpertsedgetech.com}") String domain){this.json=json;this.domain=domain;smtp.setHost("127.0.0.1");smtp.setPort(1025);smtp.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout","3000");smtp.getJavaMailProperties().setProperty("mail.smtp.timeout","5000");smtp.getJavaMailProperties().setProperty("mail.smtp.writetimeout","5000");}
  JsonNode get(String path){try{HttpResponse<String> response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8025"+path)).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException();return json.readTree(response.body());}catch(Exception error){if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Local SMTP capture server is unavailable. Run start-local-mail.ps1.");}}
  public boolean ready(){try{get("/api/v1/info");return true;}catch(ResponseStatusException error){return false;}}
  public void send(String from,String to,String subject,String body){try{var message=smtp.createMimeMessage();message.setFrom(new InternetAddress(from));message.setRecipient(jakarta.mail.Message.RecipientType.TO,new InternetAddress(to));message.setSubject(subject,"UTF-8");message.setText(body,"UTF-8");message.setSentDate(new Date());smtp.send(message);}catch(Exception error){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Local SMTP send failed. Check the local mail server; retry only after checking Sent to avoid duplicate messages.");}}
  public void send(String from,String to,String cc,String subject,String body,List<WebmailAttachment> files){try{smtp.send(MailMime.create(smtp,from,to,cc,subject,body,files));}catch(Exception error){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Local SMTP did not confirm sending. Check server/capture before retrying.");}}
  // The server-side filter verifies exact addresses; no Mailpit-wide data is exposed to clients.
  public void sync(Mailbox box,String email,WebmailMessageRepo messages){
    Map<String,Instant> routes=new LinkedHashMap<>();routes.put(email,box.createdAt);
    for(Alias alias:aliases.findAll())if(alias.target.equals(box.address))routes.put(alias.alias+"@"+domain,alias.createdAt.isAfter(box.createdAt)?alias.createdAt:box.createdAt);
    for(var route:routes.entrySet()){
    int offset=0;while(true){
    JsonNode listing=get("/api/v1/search?query="+URLEncoder.encode("addressed:\""+route.getKey()+"\"",java.nio.charset.StandardCharsets.UTF_8)+"&limit=200&start="+offset);
    for(JsonNode summary:listing.path("messages")){
      String source=summary.path("ID").asText();if(!source.matches("[a-zA-Z0-9-]{1,100}"))continue;
      String key=source+":"+box.id;if(messages.existsBySourceKey(key)||deleted.existsById(key))continue;
      Instant receivedAt;try{receivedAt=OffsetDateTime.parse(summary.path("Created").asText()).toInstant();}catch(Exception error){continue;}
      if(receivedAt.isBefore(route.getValue()))continue;
      boolean recipient=false;for(String field:List.of("To","Cc","Bcc"))for(JsonNode address:summary.path(field))if(route.getKey().equalsIgnoreCase(address.path("Address").asText()))recipient=true;
      if(!recipient)continue;JsonNode detail=get("/api/v1/message/"+source);
      WebmailMessage message=new WebmailMessage();message.id=UUID.randomUUID().toString();message.ownerId=box.id;message.sourceKey=key;
      message.fromAddress=detail.path("From").path("Address").asText("");message.toAddress=addressList(detail.path("To"));if(message.toAddress.isBlank())message.toAddress=email;message.ccAddress=addressList(detail.path("Cc"));message.replyToAddress=addressList(detail.path("ReplyTo"));message.subject=cut(detail.path("Subject").asText(),255);
      message.body=cut(detail.path("Text").asText().isBlank()?summary.path("Snippet").asText("[No plain-text content]"):detail.path("Text").asText(),20000);message.folder="Inbox";
      try{message.createdAt=OffsetDateTime.parse(summary.path("Created").asText()).toInstant();}catch(Exception ignored){}
      message.originalFolder="Inbox";message.deliveryStatus="RECEIVED_LOCAL";messages.saveAndFlush(message);
      if(detail.path("Attachments").size()>0||detail.path("Inline").size()>0)importAttachments(source,message,messages);
    }
    if(listing.path("messages").size()<200)break;offset+=200;
    }
    }
  }
  static String addressList(JsonNode addresses){List<String> values=new ArrayList<>();for(JsonNode address:addresses)if(values.size()<20)values.add(address.path("Address").asText());return cut(String.join(", ",values),2000);}
  void importAttachments(String source,WebmailMessage message,WebmailMessageRepo messages){
    try{
      HttpResponse<byte[]> response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:8025/api/v1/message/"+source+"/raw")).timeout(Duration.ofSeconds(5)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
      if(response.statusCode()!=200||response.body().length>3*1024*1024)throw new IllegalStateException();
      var mime=new MimeMessage(jakarta.mail.Session.getInstance(new Properties()),new java.io.ByteArrayInputStream(response.body()));
      List<WebmailAttachment> found=new ArrayList<>();extract(mime,message.id,found,0,new int[]{0});attachments.saveAll(found);
    }catch(Exception error){message.body=cut(cut(message.body,19500)+"\n[Some attachments could not be imported; local mail supports 5 files / 1 MB total.]",20000);messages.save(message);}
  }
  void extract(jakarta.mail.Part part,String messageId,List<WebmailAttachment> found,int depth,int[] parts)throws Exception{
    if(depth>12||++parts[0]>100)throw new IllegalStateException();
    if(part.isMimeType("multipart/*")){jakarta.mail.Multipart multi=(jakarta.mail.Multipart)part.getContent();for(int index=0;index<multi.getCount();index++)extract(multi.getBodyPart(index),messageId,found,depth+1,parts);return;}
    if(part.getFileName()==null&&!jakarta.mail.Part.ATTACHMENT.equalsIgnoreCase(part.getDisposition()))return;
    if(found.size()>=WebmailFiles.MAX_FILES)throw new IllegalStateException();byte[] data;try(var input=part.getInputStream()){data=input.readNBytes(WebmailFiles.MAX_BYTES+1);}
    if(data.length+found.stream().mapToLong(file->file.byteSize).sum()>WebmailFiles.MAX_BYTES)throw new IllegalStateException();
    WebmailAttachment file=new WebmailAttachment();file.id=UUID.randomUUID().toString();file.messageId=messageId;try{file.filename=WebmailFiles.filename(part.getFileName());}catch(Exception error){file.filename="attachment";}file.contentType=WebmailFiles.contentType(part.getContentType().split(";")[0].trim());file.data=data;file.byteSize=data.length;found.add(file);
  }
  private static String cut(String text,int max){return text.length()>max?text.substring(0,max):text;}
}
