package com.xpertsedge.hrms;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
public class WebmailSession {
  @Id public String id;
  @Column(nullable=false) public Long ownerId;
  @Column(nullable=false) public String credentialFingerprint;
  @Column(nullable=false) public Instant expiresAt;
}
