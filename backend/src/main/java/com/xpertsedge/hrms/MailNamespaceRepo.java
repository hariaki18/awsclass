package com.xpertsedge.hrms;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
public interface MailNamespaceRepo extends JpaRepository<MailNamespace,Long> {
  @Override @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<MailNamespace> findById(Long id);
}
