package com.xpertsedge.hrms;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;
/** The single company Gmail account. mailboxId identifies the Webmail administrator/company Inbox owner. */
@Entity
public class GmailAccount {
  @Id @Column(name="mailbox_id") public Long mailboxId;
  @Column(nullable=false,length=254) public String gmailAddress;
  @JsonIgnore @Column(nullable=false,length=1024) public String refreshTokenCipher;
  @Column(length=40) public String historyId;
  public Instant lastSyncAt;
  @Column(nullable=false) public Instant connectedAt=Instant.now();
  /** "", "RECONNECT" or "SYNC_FAILED". Never contains provider error text. */
  @Column(nullable=false,length=200) public String lastError="";
}
