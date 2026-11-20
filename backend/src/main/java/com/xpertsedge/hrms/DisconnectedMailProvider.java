package com.xpertsedge.hrms;
import org.springframework.stereotype.Component;
import java.util.Map;
@Component
public class DisconnectedMailProvider implements MailProvider {
  @org.springframework.beans.factory.annotation.Value("${hrms.local-mail.enabled:false}") private boolean local;
  @org.springframework.beans.factory.annotation.Autowired private LocalMailBridge bridge;
  @Override public Map<String,Object> status() {
    if(local)return Map.of("ok",bridge.ready(),"status","LOCAL_ONLY","provider","mailpit","message","Local mailbox login and SMTP capture are enabled. Internet delivery is unavailable.");
    return Map.of("ok",false,"status","NOT_CONFIGURED","provider","none","message","Mail provider is not connected. Mailboxes are local records only; sending and receiving email are unavailable.");
  }
}
