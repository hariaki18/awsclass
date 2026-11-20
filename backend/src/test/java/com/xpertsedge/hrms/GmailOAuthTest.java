package com.xpertsedge.hrms;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
/** Offline tests: no Google, database or network access is used. */
class GmailOAuthTest {
  static final String CLIENT="123456-abc.apps.googleusercontent.com",SECRET="test-secret-value";
  static GmailApi api(boolean enabled,String secret,String redirect){
    return new GmailApi(enabled,CLIENT,secret,redirect,"http://localhost:5173,http://127.0.0.1:5173",new ObjectMapper(),new GmailTokenCrypto(new byte[32]),null);
  }
  @Test void tokenEncryptionRoundTripsAndIsBoundToTheMailbox(){
    GmailTokenCrypto crypto=new GmailTokenCrypto(new byte[32]);
    String token="1//refresh-token-value";String cipher=crypto.encrypt(token,"7");
    assertFalse(cipher.contains(token));assertEquals(token,crypto.decrypt(cipher,"7"));
    assertNotEquals(cipher,crypto.encrypt(token,"7"),"each encryption must use a fresh nonce");
    assertThrows(IllegalStateException.class,()->crypto.decrypt(cipher,"8"));
    byte[] otherKey=new byte[32];otherKey[0]=1;
    assertThrows(IllegalStateException.class,()->new GmailTokenCrypto(otherKey).decrypt(cipher,"7"));
    String tampered=cipher.substring(0,cipher.length()-4)+(cipher.endsWith("AAAA")?"BBBB":"AAAA");
    assertThrows(IllegalStateException.class,()->crypto.decrypt(tampered,"7"));
    assertThrows(IllegalStateException.class,()->new GmailTokenCrypto(new byte[16]));
  }
  @Test void disabledByDefaultAndEnabledConfigurationFailsClosed(){
    GmailApi off=api(false,"","http://localhost:8080/api/webmail/gmail/callback");
    assertFalse(off.enabled());
    assertEquals(503,assertThrows(ResponseStatusException.class,()->off.authorizationUrl(1L)).getStatusCode().value());
    assertThrows(IllegalStateException.class,()->api(true,"","http://localhost:8080/cb"));
    assertThrows(IllegalStateException.class,()->api(true,SECRET,"http://example.com/cb"),"plain HTTP is allowed only for localhost");
    assertThrows(IllegalStateException.class,()->new GmailApi(true,"not-a-google-id",SECRET,"http://localhost:8080/cb","http://localhost:5173",new ObjectMapper(),new GmailTokenCrypto(new byte[32]),null));
    assertDoesNotThrow(()->api(true,SECRET,"https://mail.example.com/api/webmail/gmail/callback"));
  }
  @Test void authorizationUrlUsesPkceOfflineAccessAndNeverContainsTheClientSecret(){
    GmailApi api=api(true,SECRET,"http://localhost:8080/api/webmail/gmail/callback");
    String url=api.authorizationUrl(5L),other=api.authorizationUrl(5L);
    assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"));
    assertFalse(url.contains(SECRET));
    for(String part:List.of("client_id="+CLIENT,"response_type=code","access_type=offline","prompt=consent","code_challenge_method=S256",
      "gmail.send","gmail.readonly","redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Fapi%2Fwebmail%2Fgmail%2Fcallback"))assertTrue(url.contains(part),part);
    assertFalse(url.contains("mail.google.com%2F&"),"full-access scope must not be requested");
    assertNotEquals(param(url,"state"),param(other,"state"));
    assertTrue(param(url,"state").length()>=40);
  }
  @Test void unknownReusedOrMissingStateIsRejectedBeforeAnyNetworkCall(){
    GmailApi api=api(true,SECRET,"http://localhost:8080/api/webmail/gmail/callback");
    for(String state:new String[]{null,"","never-issued"})assertEquals(400,assertThrows(ResponseStatusException.class,()->api.complete(state,"code")).getStatusCode().value());
    String state=param(api.authorizationUrl(5L),"state");api.discard(state);
    assertEquals(400,assertThrows(ResponseStatusException.class,()->api.complete(state,"code")).getStatusCode().value(),"a discarded state cannot be replayed");
  }
  @Test void rawMessageIsUrlSafeBase64WithoutPaddingAndKeepsHeadersAndAttachments()throws Exception{
    WebmailAttachment file=new WebmailAttachment();file.filename="note.txt";file.contentType="text/plain";file.data="hello".getBytes();file.byteSize=5;
    MimeMessage message=MailMime.create(new JavaMailSenderImpl(),"me@gmail.com","you@example.com, two@example.com","cc@example.com","Héllo ✓","Body text",List.of(file));
    String raw=GmailApi.raw(message);
    assertTrue(raw.matches("[A-Za-z0-9_-]+"),"must be base64url without padding or line breaks");
    MimeMessage parsed=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
    assertEquals("me@gmail.com",parsed.getFrom()[0].toString());assertEquals(2,parsed.getRecipients(jakarta.mail.Message.RecipientType.TO).length);
    assertEquals("Héllo ✓",parsed.getSubject());assertTrue(parsed.isMimeType("multipart/*"));
    assertEquals("Body text",GmailSync.bodyText(parsed));
  }
  @Test void receivedBodyPrefersPlainTextAndFallsBackToReadableHtml()throws Exception{
    MimeMessage both=new MimeMessage(Session.getInstance(new Properties()));
    MimeMultipart alt=new MimeMultipart("alternative");
    MimeBodyPart html=new MimeBodyPart();html.setContent("<p>HTML version</p>","text/html; charset=UTF-8");alt.addBodyPart(html);
    MimeBodyPart plain=new MimeBodyPart();plain.setText("Plain version","UTF-8");alt.addBodyPart(plain);
    both.setContent(alt);
    assertEquals("Plain version",GmailSync.bodyText(reparse(both)));
    MimeMessage onlyHtml=new MimeMessage(Session.getInstance(new Properties()));
    onlyHtml.setContent("<style>p{color:red}</style><div>Hello&nbsp;<b>team</b> &amp; friends</div><script>alert(1)</script><p>Line two</p>","text/html; charset=UTF-8");
    String text=GmailSync.bodyText(reparse(onlyHtml));
    assertTrue(text.contains("Hello team & friends"),text);assertTrue(text.contains("Line two"));
    assertFalse(text.contains("alert")||text.contains("color:red")||text.contains("<"));
  }
  @Test void importedFieldsAreBoundedAndSafeEvenForUnreadableMessages(){
    WebmailMessage broken=new WebmailMessage();GmailSync.fill(broken,null,"me@gmail.com");
    assertEquals("unknown",broken.fromAddress);assertEquals("me@gmail.com",broken.toAddress);assertEquals("",broken.subject);assertEquals("[No text content]",broken.body);
  }
  /** Serialises and parses again, exactly like a message downloaded from Gmail. */
  static MimeMessage reparse(MimeMessage message)throws Exception{
    java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();message.writeTo(out);
    return new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(out.toByteArray()));
  }
  static String param(String url,String name){
    for(String pair:URI.create(url).getRawQuery().split("&"))if(pair.startsWith(name+"="))return java.net.URLDecoder.decode(pair.substring(name.length()+1),java.nio.charset.StandardCharsets.UTF_8);
    throw new AssertionError("missing "+name);
  }
}
