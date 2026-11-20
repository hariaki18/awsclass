package com.xpertsedge.hrms;
import jakarta.persistence.*;
@Entity
public class WebmailAttachment {
  @Id public String id;
  @Column(nullable=false) public String messageId;
  @Column(nullable=false) public String filename;
  @Column(nullable=false,length=120) public String contentType;
  @Column(nullable=false) public long byteSize;
  @Lob @Column(nullable=false,columnDefinition="LONGBLOB",length=1048576) public byte[] data;
}
