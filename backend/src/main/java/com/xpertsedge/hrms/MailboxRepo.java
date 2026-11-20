package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface MailboxRepo extends JpaRepository<Mailbox, Long> {
  boolean existsByAddress(String address);
  Optional<Mailbox> findByAddress(String address);
}
