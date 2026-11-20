package com.xpertsedge.hrms;
import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
@Entity
public class Department {
  @Id @GeneratedValue public Long id;
  @Column(nullable=false,length=100) public String name;
  @JsonIgnore @Column(nullable=false,unique=true,length=100) public String nameKey;
  @Column(length=500) public String description;
  @Column(nullable=false) public boolean active=true;
}
