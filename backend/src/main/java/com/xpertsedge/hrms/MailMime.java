package com.xpertsedge.hrms;
import java.util.*;
import jakarta.mail.internet.*;
import org.springframework.mail.javamail.*;
import org.springframework.core.io.ByteArrayResource;
final class MailMime {
  static MimeMessage create(JavaMailSenderImpl sender,String from,String to,String cc,String subject,String body,List<WebmailAttachment> attachments)throws Exception{
    MimeMessage message=sender.createMimeMessage();MimeMessageHelper helper=new MimeMessageHelper(message,!attachments.isEmpty(),"UTF-8");
    helper.setFrom(from);helper.setTo(InternetAddress.parse(to,true));if(!cc.isBlank())helper.setCc(InternetAddress.parse(cc,true));helper.setSubject(subject);helper.setText(body,false);helper.setSentDate(new Date());
    for(WebmailAttachment file:attachments)helper.addAttachment(file.filename,new ByteArrayResource(file.data),file.contentType);
    return message;
  }
}
