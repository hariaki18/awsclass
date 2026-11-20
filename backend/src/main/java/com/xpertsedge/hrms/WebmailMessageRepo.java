package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface WebmailMessageRepo extends JpaRepository<WebmailMessage,String>,org.springframework.data.jpa.repository.JpaSpecificationExecutor<WebmailMessage> {
  List<WebmailMessage> findTop200ByOwnerIdOrderByCreatedAtDesc(Long ownerId);
  org.springframework.data.domain.Page<WebmailMessage> findByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId,org.springframework.data.domain.Pageable page);
  Optional<WebmailMessage> findByIdAndOwnerId(String id,Long ownerId);
  Optional<WebmailMessage> findFirstByGmailThreadIdOrderByCreatedAtDesc(String gmailThreadId);
  Optional<WebmailMessage> findFirstByProviderMessageIdOrderByCreatedAtDesc(String providerMessageId);
  boolean existsBySourceKey(String sourceKey);
}
