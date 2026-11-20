package com.xpertsedge.hrms;
import jakarta.persistence.*;
@Entity @Table(name = "app_user")
public class AppUser {
  @Id @GeneratedValue public Long id;
  @Column(unique=true) public String email;
  public String name;
  @com.fasterxml.jackson.annotation.JsonIgnore public String passwordHash;
  @Column(name="role_name",nullable=false,length=20) public String role="ADMIN";
  @Column(nullable=false) public boolean active=true;
  @Column(unique=true) public Long employeeId;
}
