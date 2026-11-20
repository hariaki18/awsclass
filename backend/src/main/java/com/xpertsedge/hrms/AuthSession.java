package com.xpertsedge.hrms;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
public class AuthSession {
  @Id public String id;
  @Column(nullable=false) public String email;
  @Column(nullable=false) public Instant expiresAt;
}
