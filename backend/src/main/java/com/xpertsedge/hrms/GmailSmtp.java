package com.xpertsedge.hrms;
import jakarta.mail.internet.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
@Service
public class GmailSmtp {
  private final boolean enabled;private final String senderEmail;private final Set<String> allowed;
  final JavaMailSenderImpl sender=new JavaMailSenderImpl();
  public GmailSmtp(@Value("${hrms.gmail.enabled:false}") boolean enabled,
    @Value("${GMAIL_SMTP_USERNAME:}") String email,@Value("${GMAIL_SMTP_APP_PASSWORD:}") String password,
    @Value("${GMAIL_ALLOWED_MAILBOXES:}") String mailboxes){
    this.enabled=enabled;senderEmail=email.trim().toLowerCase(Locale.ROOT);
    allowed=new HashSet<>();for(String mailbox:mailboxes.split(","))if(!mailbox.isBlank())allowed.add(mailbox.trim().toLowerCase(Locale.ROOT));
    if(enabled){
      if(!senderEmail.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9.-]+\\.[a-z]{2,}"))throw new IllegalStateException("A valid GMAIL_SMTP_USERNAME is required.");
      if(password.replace(" ","").length()!=16)throw new IllegalStateException("GMAIL_SMTP_APP_PASSWORD must be a Google 16-character App Password, not your normal password.");
      if(allowed.isEmpty()||allowed.stream().anyMatch(value->!value.matches("[a-z0-9_-]+(?:[.][a-z0-9_-]+)*@xpertsedgetech[.]com")))throw new IllegalStateException("GMAIL_ALLOWED_MAILBOXES must explicitly name local mailboxes allowed to use this sender.");
    }
    sender.setHost("smtp.gmail.com");sender.setPort(587);sender.setUsername(senderEmail);sender.setPassword(password.replace(" ",""));
    var properties=sender.getJavaMailProperties();properties.setProperty("mail.smtp.auth","true");properties.setProperty("mail.smtp.starttls.enable","true");properties.setProperty("mail.smtp.starttls.required","true");properties.setProperty("mail.smtp.ssl.checkserveridentity","true");properties.setProperty("mail.smtp.connectiontimeout","10000");properties.setProperty("mail.smtp.timeout","15000");properties.setProperty("mail.smtp.writetimeout","15000");
  }
  public boolean enabled(){return enabled;}
  public boolean allowed(String mailbox){return enabled&&allowed.contains(mailbox);}
  public String senderEmail(){return senderEmail;}
  private void authorize(String mailbox){if(!enabled)throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Gmail SMTP is not configured.");if(!allowed(mailbox))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"This mailbox is not authorized to use the connected Gmail sender.");}
  MimeMessage message(String recipient,String subject,String body)throws Exception{
    MimeMessage message=sender.createMimeMessage();message.setFrom(new InternetAddress(senderEmail));message.setRecipient(jakarta.mail.Message.RecipientType.TO,new InternetAddress(recipient,true));message.setSubject(subject,"UTF-8");message.setText(body,"UTF-8");message.setSentDate(new Date());return message;
  }
  public void verify(String mailbox){authorize(mailbox);try{sender.testConnection();}catch(Exception error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Cannot authenticate to Gmail SMTP. Check internet access, Google App Password and account policy.");}}
  public String send(String mailbox,String recipient,String subject,String body){authorize(mailbox);try{sender.send(message(recipient,subject,body));return senderEmail;}catch(Exception error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Gmail SMTP did not confirm sending. Check account settings and Sent before retrying to avoid duplicates.");}}
  public String send(String mailbox,String to,String cc,String subject,String body,List<WebmailAttachment> files){authorize(mailbox);try{sender.send(MailMime.create(sender,senderEmail,to,cc,subject,body,files));return senderEmail;}catch(Exception error){throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,"Gmail SMTP did not confirm sending. Check account settings and Sent before retrying to avoid duplicates.");}}
}
