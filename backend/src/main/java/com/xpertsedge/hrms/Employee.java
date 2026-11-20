package com.xpertsedge.hrms;
import jakarta.persistence.*;
import java.time.LocalDate;
@Entity
public class Employee {
  @Id @GeneratedValue public Long id;
  @Column(nullable=false,unique=true,length=32) public String employeeCode;
  @Column(nullable=false,length=100) public String firstName;
  @Column(length=100) public String lastName;
  @Column(nullable=false,unique=true,length=254) public String email;
  @Column(length=32) public String phone;
  @Column(length=100) public String jobTitle;
  @Column(nullable=false) public LocalDate joiningDate;
  @Column(nullable=false,length=20) public String employmentType;
  @Column(nullable=false,length=20) public String status;
  public Long departmentId;
}
