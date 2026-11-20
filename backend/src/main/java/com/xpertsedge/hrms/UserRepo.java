package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface UserRepo extends JpaRepository<AppUser, Long> {
  Optional<AppUser> findByEmail(String email);
  boolean existsByEmployeeId(Long id);
  long countByRoleAndActiveTrue(String role);
  @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
  @org.springframework.data.jpa.repository.Query("select u from AppUser u where u.role='ADMIN' and u.active=true order by u.id")
  java.util.List<AppUser> lockActiveAdmins();
}
