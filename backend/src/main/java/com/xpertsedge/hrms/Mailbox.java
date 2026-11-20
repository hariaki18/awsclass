package com.xpertsedge.hrms;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
@Entity
public class Mailbox { @Column(nullable=false) public java.time.Instant createdAt=java.time.Instant.now(); @Id @GeneratedValue public Long id; @Column(unique = true) public String address; public String displayName;
  public int quotaGb = 2; public double usedGb = 0; public boolean active = true; @JsonIgnore public String passwordHash; }
