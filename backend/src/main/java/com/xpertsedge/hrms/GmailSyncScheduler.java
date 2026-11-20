package com.xpertsedge.hrms;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** Polls the one company Gmail account. Does nothing unless the gmail-api profile is active. */
@Component
public class GmailSyncScheduler {
  private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(GmailSyncScheduler.class);
  private final GmailApi api;private final GmailAccountRepo accounts;private final GmailSync sync;
  public GmailSyncScheduler(GmailApi api,GmailAccountRepo accounts,GmailSync sync){this.api=api;this.accounts=accounts;this.sync=sync;}
  @Scheduled(initialDelay=15000,fixedDelayString="${hrms.gmail.sync-ms:45000}")
  public void run(){
    if(!api.enabled())return;
    accounts.findFirstByOrderByConnectedAtAsc().ifPresent(account->{
      try{sync.sync(account.mailboxId);}
      catch(RuntimeException error){log.warn("Company Gmail sync failed: {}",error.getClass().getSimpleName());}
    });
  }
}
