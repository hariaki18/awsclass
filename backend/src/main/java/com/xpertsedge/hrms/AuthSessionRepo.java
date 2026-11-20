package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
public interface AuthSessionRepo extends JpaRepository<AuthSession,String> {
  void deleteByExpiresAtBefore(Instant now);
  void deleteByEmail(String email);
}
