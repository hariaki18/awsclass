package com.xpertsedge.hrms;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class GmailSmtpTest {
  @Test void disabledByDefaultAndRequiresExplicitMailboxAuthorization(){
    GmailSmtp disabled=new GmailSmtp(false,"","","");assertFalse(disabled.enabled());assertFalse(disabled.allowed("alice@xpertsedgetech.com"));
    GmailSmtp configured=new GmailSmtp(true,"sender@gmail.com","abcdefghijklmnop","alice@xpertsedgetech.com");
    assertTrue(configured.allowed("alice@xpertsedgetech.com"));assertFalse(configured.allowed("bob@xpertsedgetech.com"));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->configured.verify("bob@xpertsedgetech.com"));
  }
  @Test void missingCredentialsAndWildcardAuthorizationFailClosed(){
    assertThrows(IllegalStateException.class,()->new GmailSmtp(true,"sender@gmail.com","", "alice@xpertsedgetech.com"));
    assertThrows(IllegalStateException.class,()->new GmailSmtp(true,"sender@gmail.com","abcdefghijklmnop","*"));
    assertThrows(IllegalStateException.class,()->new GmailSmtp(true,"not-an-email","abcdefghijklmnop","alice@xpertsedgetech.com"));
  }
  @Test void requiresVerifiedTlsAndAlwaysUsesConfiguredSender()throws Exception{
    GmailSmtp configured=new GmailSmtp(true,"sender@gmail.com","abcd efgh ijkl mnop","alice@xpertsedgetech.com");
    assertEquals("smtp.gmail.com",configured.sender.getHost());assertEquals(587,configured.sender.getPort());
    assertEquals("true",configured.sender.getJavaMailProperties().getProperty("mail.smtp.starttls.required"));
    assertEquals("true",configured.sender.getJavaMailProperties().getProperty("mail.smtp.ssl.checkserveridentity"));
    var message=configured.message("recipient@example.com","Test","Body");
    assertEquals("sender@gmail.com",message.getFrom()[0].toString());
    assertNull(message.getHeader("Reply-To"));
  }
}
