package com.xpertsedge.hrms;
public interface MailSecurityRepo extends org.springframework.data.jpa.repository.JpaRepository<MailSecurity,Long>{@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) java.util.Optional<MailSecurity> findByOwnerId(Long ownerId);}
