package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EmployeeRepo extends JpaRepository<Employee,Long> {
  boolean existsByEmployeeCode(String code);
  boolean existsByEmail(String email);
  boolean existsByDepartmentId(Long id);
}
