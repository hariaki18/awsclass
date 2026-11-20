package com.xpertsedge.hrms;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.http.*;
import org.springframework.web.server.ResponseStatusException;
import com.fasterxml.jackson.databind.*;
import jakarta.servlet.http.Cookie;
import java.util.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:webmail-features;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.flyway.locations=classpath:db/migration","hrms.local-mail.enabled=true","hrms.jwt.secret=isolated-mail-features-signing-secret-32-bytes","hrms.admin.email=features-admin@example.invalid","hrms.admin.password=IsolatedTestOnly@2026"})
@AutoConfigureMockMvc
@Import(WebmailFeaturesTest.TestConfig.class)
class WebmailFeaturesTest {
  @Autowired MockMvc mvc;@Autowired ObjectMapper json;@Autowired MailboxRepo boxes;@Autowired WebmailMessageRepo messages;@Autowired WebmailAttachmentRepo attachments;@Autowired BCryptPasswordEncoder encoder;
  @Autowired StubBridge bridge;
  @TestConfiguration static class TestConfig {@Bean @Primary StubBridge stubBridge(ObjectMapper json){return new StubBridge(json);}}
  static class StubBridge extends LocalMailBridge {boolean fail;StubBridge(ObjectMapper json){super(json);}@Override public boolean ready(){return true;}@Override public void sync(Mailbox box,String email,WebmailMessageRepo messages){}@Override public void send(String from,String to,String cc,String subject,String body,List<WebmailAttachment> files){if(fail)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Test SMTP offline");}}
  Cookie alice,bob;Mailbox a,b;String prefix;
  @BeforeEach void setup()throws Exception{
    prefix="junit"+UUID.randomUUID().toString().replace("-","").substring(0,8);a=box(prefix+"a");b=box(prefix+"b");alice=login(a);bob=login(b);bridge.fail=false;
  }
  Mailbox box(String address){Mailbox box=new Mailbox();box.address=address;box.displayName=address;box.passwordHash=encoder.encode("LocalJUnitOnly@2026");return boxes.save(box);}
  Cookie login(Mailbox box)throws Exception{var response=mvc.perform(post("/api/webmail/login").header("Origin","http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("email",box.address+"@xpertsedgetech.com","password","LocalJUnitOnly@2026")))).andReturn().getResponse();assertEquals(200,response.getStatus());String token=response.getHeader("Set-Cookie").split(";",2)[0].split("=",2)[1];return new Cookie("xe_mail_session",token);}
  JsonNode call(String method,String path,Cookie cookie,Object body,int status)throws Exception{
    var request=request(org.springframework.http.HttpMethod.valueOf(method),"/api/webmail"+path).cookie(cookie).header("Origin","http://localhost:5173");if(body!=null)request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));var response=mvc.perform(request).andReturn().getResponse();assertEquals(status,response.getStatus(),response.getContentAsString());return response.getContentAsByteArray().length==0?json.nullNode():json.readTree(response.getContentAsByteArray());
  }
  Map<String,Object> draftBody(){return new HashMap<>(Map.of("to",b.address+"@xpertsedgetech.com","cc","","subject","Report","body","Body","tag","Green"));}
  @Test void attachmentOwnershipDownloadAndForwardCopiesAreEnforced()throws Exception{
    var body=draftBody();body.put("attachments",List.of(Map.of("name","../../report.txt","contentType","text/plain","data",Base64.getEncoder().encodeToString("private bytes".getBytes()))));
    JsonNode saved=call("POST","/drafts",alice,body,200);String id=saved.path("id").asText(),fileId=saved.path("attachments").get(0).path("id").asText();assertEquals("report.txt",saved.path("attachments").get(0).path("name").asText());
    var download=mvc.perform(get("/api/webmail/messages/"+id+"/attachments/"+fileId).cookie(alice)).andReturn().getResponse();assertEquals(200,download.getStatus());assertEquals("application/octet-stream",download.getContentType());assertEquals("nosniff",download.getHeader("X-Content-Type-Options"));assertArrayEquals("private bytes".getBytes(),download.getContentAsByteArray());
    call("GET","/messages/"+id+"/attachments/"+fileId,bob,null,404);
    var forward=draftBody();forward.put("forwardFromId",id);JsonNode copied=call("POST","/drafts",alice,forward,200);String copiedId=copied.path("attachments").get(0).path("id").asText();assertNotEquals(fileId,copiedId);assertArrayEquals(attachments.findById(fileId).orElseThrow().data,attachments.findById(copiedId).orElseThrow().data);
    call("POST","/drafts",bob,forward,404);
    var bad=draftBody();bad.put("attachmentIds",List.of(fileId));call("POST","/drafts",alice,bad,400);
  }
  @Test void smtpFailureIsDurableAndRetryCannotDoubleSend()throws Exception{
    var body=draftBody();String id=call("POST","/drafts",alice,body,200).path("id").asText();body.put("id",id);
    bridge.fail=true;
    call("POST","/send",alice,body,503);assertEquals("FAILED",messages.findById(id).orElseThrow().deliveryStatus);assertEquals("Drafts",messages.findById(id).orElseThrow().folder);
    bridge.fail=false;
    assertEquals("SMTP_ACCEPTED",call("POST","/send",alice,body,200).path("deliveryStatus").asText());call("POST","/send",alice,body,400);
  }
  @Test void trashRestorePermanentDeleteAndSearchWorkForSentAndDrafts()throws Exception{
    var body=draftBody();String id=call("POST","/drafts",alice,body,200).path("id").asText();
    call("PATCH","/messages/"+id,alice,Map.of("folder","Trash"),200);assertEquals("Drafts",call("PATCH","/messages/"+id,alice,Map.of("folder","Restore"),200).path("folder").asText());
    assertEquals(1,call("GET","/messages?folder=Drafts&q=report",alice,null,200).path("total").asInt());assertEquals(0,call("GET","/messages?q=report",bob,null,200).path("total").asInt());
    call("DELETE","/messages/"+id,alice,null,400);call("PATCH","/messages/"+id,alice,Map.of("folder","Trash"),200);call("DELETE","/messages/"+id,alice,null,204);assertFalse(messages.existsById(id));
  }
  @Test void inputAndUploadLimitsRejectBadRequests()throws Exception{
    call("POST","/drafts",alice,Map.of("to","","subject","","body","","attachments",List.of()),400);
    var body=draftBody();body.put("to","one@xpertsedgetech.com\r\nBcc: other@xpertsedgetech.com");call("POST","/drafts",alice,body,400);
    body=draftBody();body.put("attachments",List.of(Map.of("name","oversized.txt","data",Base64.getEncoder().encodeToString(new byte[1048577]))));call("POST","/drafts",alice,body,400);
    var response=mvc.perform(post("/api/webmail/drafts").cookie(alice).header("Origin","http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(new byte[2*1024*1024+1])).andReturn().getResponse();assertEquals(413,response.getStatus());
  }

  @Autowired WebmailController controller;@Autowired MailMfa mfa;@Autowired MailSecurityRepo security;
  @Test void personalModulesValidateAndIsolateOwners()throws Exception{
    var contact=new HashMap<String,Object>(Map.of("name","Alice","email","alice@example.com","phone","123","company","XpertsEdge","notes","Private"));
    String id=call("POST","/contacts",alice,contact,200).path("id").asText();assertEquals(1,call("GET","/contacts",alice,null,200).size());assertEquals(0,call("GET","/contacts",bob,null,200).size());call("PUT","/contacts/"+id,bob,contact,404);contact.put("email","bad");call("PUT","/contacts/"+id,alice,contact,400);call("DELETE","/contacts/"+id,alice,null,204);
    var event=new HashMap<String,Object>(Map.of("title","Review","startsAt","2027-01-01T10:00:00Z","endsAt","2027-01-01T11:00:00Z","location","Office","notes","Private"));
    id=call("POST","/calendar",alice,event,200).path("id").asText();call("DELETE","/calendar/"+id,bob,null,404);assertEquals(0,call("GET","/calendar",bob,null,200).size());event.put("endsAt","2026-01-01T11:00:00Z");call("PUT","/calendar/"+id,alice,event,400);call("DELETE","/calendar/"+id,alice,null,204);
    var pref=new HashMap<String,Object>(Map.of("signature","Regards","theme","dark","density","comfortable","timeZone","Asia/Kolkata","shortcuts",false));call("PUT","/preferences",alice,pref,200);assertEquals("dark",call("GET","/preferences",alice,null,200).path("theme").asText());assertEquals("light",call("GET","/preferences",bob,null,200).path("theme").asText());pref.put("timeZone","invalid");call("PUT","/preferences",alice,pref,400);
  }
  @Test void scheduleCancelAndDueDeliveryArePersistent()throws Exception{
    String id=call("POST","/drafts",alice,draftBody(),200).path("id").asText();call("POST","/schedule",alice,Map.of("id",id,"scheduledAt","2000-01-01T00:00:00Z"),400);
    String at=java.time.Instant.now().plusSeconds(3600).toString();assertEquals("Scheduled",call("POST","/schedule",alice,Map.of("id",id,"scheduledAt",at),200).path("folder").asText());call("POST","/schedule/"+id+"/cancel",bob,Map.of(),404);assertEquals("Drafts",call("POST","/schedule/"+id+"/cancel",alice,Map.of(),200).path("folder").asText());
    call("POST","/schedule",alice,Map.of("id",id,"scheduledAt",at),200);var record=messages.findById(id).orElseThrow();record.scheduledAt=java.time.Instant.now().minusSeconds(10);messages.save(record);controller.processScheduled(id);assertEquals("SMTP_ACCEPTED",messages.findById(id).orElseThrow().deliveryStatus);controller.processScheduled(id);assertEquals("Sent",messages.findById(id).orElseThrow().folder);
  }
  @Test void bulkIsAtomicAndFiltersAndStorageUseOwner()throws Exception{
    String one=call("POST","/drafts",alice,draftBody(),200).path("id").asText(),two=call("POST","/drafts",alice,draftBody(),200).path("id").asText(),foreign=call("POST","/drafts",bob,draftBody(),200).path("id").asText();
    call("POST","/bulk",alice,Map.of("ids",List.of(one,foreign),"action","trash"),404);assertEquals("Drafts",messages.findById(one).orElseThrow().folder);
    call("POST","/bulk",alice,Map.of("ids",List.of(one,two),"action","read"),200);assertEquals(2,call("GET","/messages?read=read",alice,null,200).path("total").asInt());assertEquals(0,call("GET","/messages?read=unread",alice,null,200).path("total").asInt());assertEquals(0,call("GET","/messages?hasAttachment=true",alice,null,200).path("total").asInt());call("GET","/messages?after=not-a-date",alice,null,400);assertEquals(8,call("GET","/storage",alice,null,200).path("bodyBytes").asInt());
    call("POST","/bulk",alice,Map.of("ids",List.of(one,two),"action","trash"),200);call("POST","/bulk",alice,Map.of("ids",List.of(one,two),"action","delete"),200);assertFalse(messages.existsById(one));assertTrue(messages.existsById(foreign));
  }
  @Test void authenticatorEncryptionReplayRecoveryAndRememberCookie()throws Exception{
    assertEquals("287082",MailMfa.code(MailMfa.base32("12345678901234567890".getBytes()),1));
    String secret=call("POST","/security/setup",alice,Map.of("password","LocalJUnitOnly@2026"),200).path("secret").asText();assertNotEquals(secret,security.findById(a.id).orElseThrow().pendingCipher);
    String otp=MailMfa.code(secret,java.time.Instant.now().getEpochSecond()/30);var codes=call("POST","/security/confirm",alice,Map.of("password","LocalJUnitOnly@2026","code",otp),200).path("recoveryCodes");assertEquals(8,codes.size());call("GET","/me",alice,null,401);
    var body=new HashMap<String,Object>(Map.of("email",a.address+"@xpertsedgetech.com","password","LocalJUnitOnly@2026","code",otp));call("POST","/login",alice,body,401);body.put("code",codes.get(0).asText());body.put("rememberMe",true);
    var response=mvc.perform(post("/api/webmail/login").header("Origin","http://localhost:5173").contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body))).andReturn().getResponse();assertEquals(200,response.getStatus());assertTrue(response.getHeader("Set-Cookie").contains("Max-Age=2592000"));call("POST","/login",alice,body,401);
    assertFalse(security.findById(a.id).orElseThrow().recoveryHashes.contains(codes.get(0).asText()));assertFalse(mfa.enabled(b.id));
  }
  @Test void securityChangesAreRateLimited()throws Exception{for(int i=0;i<5;i++)call("POST","/security/setup",alice,Map.of("password","wrong"),401);call("POST","/security/setup",alice,Map.of("password","LocalJUnitOnly@2026"),429);assertFalse(mfa.enabled(a.id));}
}
