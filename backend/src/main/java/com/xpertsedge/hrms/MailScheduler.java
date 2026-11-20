package com.xpertsedge.hrms;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import java.time.Instant;
@Component public class MailScheduler {
 private final WebmailMessageRepo messages;private final WebmailController controller;private final boolean enabled;
 public MailScheduler(WebmailMessageRepo messages,WebmailController controller,@Value("${hrms.local-mail.enabled:false}")boolean enabled){this.messages=messages;this.controller=controller;this.enabled=enabled;}
 @Scheduled(fixedDelay=5000) public void run(){if(!enabled)return;var due=messages.findAll((root,q,cb)->cb.and(cb.equal(root.get("folder"),"Scheduled"),cb.lessThanOrEqualTo(root.get("scheduledAt"),Instant.now())),org.springframework.data.domain.PageRequest.of(0,20));for(var m:due)try{controller.processScheduled(m.id);}catch(Exception e){org.slf4j.LoggerFactory.getLogger(getClass()).warn("Scheduled mail processing failed for {}: {}",m.id,e.getClass().getSimpleName());}}
}
