package com.xpertsedge.hrms;
import jakarta.persistence.*;
@Entity public class MailContact {
@Id public String id;
@Column(nullable=false) public Long ownerId;
@Column(nullable=false,length=100) public String name;
@Column(nullable=false,length=254) public String email;
@Column(nullable=false,length=32) public String phone="";
@Column(nullable=false,length=100) public String company="";
@Column(nullable=false,length=1000) public String notes="";
}
