package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
public interface WebmailSessionRepo extends JpaRepository<WebmailSession,String> {
  @org.springframework.transaction.annotation.Transactional void deleteByExpiresAtBefore(Instant now);
  @org.springframework.transaction.annotation.Transactional void deleteByOwnerId(Long ownerId);
}
