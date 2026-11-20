package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NotificationRepo extends JpaRepository<AppNotification, Long> {
  long countByRecipientAndReadFalse(String recipient);
  @org.springframework.data.jpa.repository.Modifying
  @org.springframework.data.jpa.repository.Query("update AppNotification n set n.read=true where n.recipient=:email and n.read=false")
  int markAllRead(@org.springframework.data.repository.query.Param("email") String email);
  List<AppNotification> findTop100ByRecipientOrderByCreatedAtDescIdDesc(String recipient);
  Optional<AppNotification> findByIdAndRecipient(Long id, String recipient);
}
