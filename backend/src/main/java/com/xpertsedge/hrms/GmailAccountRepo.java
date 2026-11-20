package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface GmailAccountRepo extends JpaRepository<GmailAccount,Long> {
  Optional<GmailAccount> findByGmailAddress(String gmailAddress);
  Optional<GmailAccount> findFirstByOrderByConnectedAtAsc();
}
