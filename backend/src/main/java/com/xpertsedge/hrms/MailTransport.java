package com.xpertsedge.hrms;
import java.util.List;

/** Provider boundary: Webmail never talks to Mailpit/local SMTP in production. */
public interface MailTransport {
  record OutgoingMail(String idempotencyKey,String from,String to,String cc,String subject,String body,List<WebmailAttachment> attachments){}
  record SendResult(String providerMessageId,String status){}
  SendResult send(OutgoingMail mail);
  boolean configured();
}
