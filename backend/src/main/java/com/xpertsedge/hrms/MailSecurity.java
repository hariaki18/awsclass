package com.xpertsedge.hrms;
import jakarta.persistence.*;
@Entity public class MailSecurity {
@Id public Long ownerId;
@Column(nullable=false) public boolean enabled=false;
@Column(length=255) public String secretCipher;
@Column(length=255) public String pendingCipher;
public java.time.Instant pendingExpires;
@Column(nullable=false) public long lastCounter=-1;
@Lob public String recoveryHashes="";
}
