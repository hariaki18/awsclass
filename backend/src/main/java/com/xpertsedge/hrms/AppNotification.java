package com.xpertsedge.hrms;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;

@Entity
public class AppNotification {
  @Id @GeneratedValue public Long id;
  @JsonIgnore @Column(nullable=false) public String recipient;
  public String title;
  public String body;
  public boolean read = false;
  public Instant createdAt = Instant.now();
}
