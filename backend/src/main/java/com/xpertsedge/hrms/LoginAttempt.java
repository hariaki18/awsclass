package com.xpertsedge.hrms;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
public class LoginAttempt {
  @Id public String email;
  public int failures;
  public Instant windowStart;
}
