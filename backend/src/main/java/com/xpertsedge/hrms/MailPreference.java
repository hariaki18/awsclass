package com.xpertsedge.hrms;
import jakarta.persistence.*;
@Entity public class MailPreference {
@Id public Long ownerId;
@Column(nullable=false,length=2000) public String signature="";
@Column(nullable=false,length=15) public String theme="light";
@Column(nullable=false,length=15) public String density="compact";
@Column(nullable=false,length=60) public String timeZone="Asia/Kolkata";
@Column(nullable=false) public boolean shortcuts=true;
}
