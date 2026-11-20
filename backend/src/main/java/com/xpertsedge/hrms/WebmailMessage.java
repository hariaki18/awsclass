package com.xpertsedge.hrms;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
@Entity
public class WebmailMessage {
  @Id public String id;
  @JsonIgnore @Column(nullable=false) public Long ownerId;
  @JsonIgnore @Column(unique=true) public String sourceKey;
  @Column(nullable=false) public String fromAddress;
  @Column(nullable=false,length=2000) public String toAddress;
  @Column(nullable=false,length=2000) public String ccAddress="";
  @Column(nullable=false,length=2000) public String replyToAddress="";
  @Column(nullable=false) public String subject="";
  @Lob public String body="";
  @Column(nullable=false,length=20) public String folder="Drafts";
  @Column(nullable=false,length=20) public String originalFolder="Drafts";
  @Column(nullable=false,length=30) public String deliveryStatus="DRAFT";
  @Column(nullable=false,length=500) public String deliveryError="";
  @Column(nullable=false,length=20) public String tag="";
  @Column(nullable=false) public boolean seen=false;
  @Column(nullable=false) public boolean starred=false;
  @Column(nullable=false) public Instant createdAt=Instant.now();
  @Column(nullable=false) public Instant updatedAt=Instant.now();
  public Instant scheduledAt;
  @JsonIgnore @Column(length=64) public String gmailMessageId;
  @JsonIgnore @Column(length=64) public String gmailThreadId;
  @JsonIgnore @Column(length=128) public String providerMessageId;
  @Transient public java.util.List<java.util.Map<String,Object>> attachments=java.util.List.of();
}
