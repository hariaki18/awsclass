package com.xpertsedge.hrms;
public interface MailContactRepo extends org.springframework.data.jpa.repository.JpaRepository<MailContact,String>{java.util.List<MailContact> findByOwnerIdOrderByNameAsc(Long ownerId); java.util.Optional<MailContact> findByIdAndOwnerId(String id,Long ownerId);}
