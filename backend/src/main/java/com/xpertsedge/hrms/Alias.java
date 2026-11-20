package com.xpertsedge.hrms;
import jakarta.persistence.*;
@Entity @Table(name = "mail_alias")
public class Alias { @Column(nullable=false) public java.time.Instant createdAt=java.time.Instant.now(); @Id @GeneratedValue public Long id; @Column(unique = true, name = "alias_name") public String alias; public String target; }
