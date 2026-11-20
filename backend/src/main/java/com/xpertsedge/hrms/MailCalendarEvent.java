package com.xpertsedge.hrms;
import jakarta.persistence.*;
@Entity public class MailCalendarEvent {
@Id public String id;
@Column(nullable=false) public Long ownerId;
@Column(nullable=false,length=150) public String title;
@Column(nullable=false) public java.time.Instant startsAt;
@Column(nullable=false) public java.time.Instant endsAt;
@Column(nullable=false,length=200) public String location="";
@Column(nullable=false,length=2000) public String notes="";
}
