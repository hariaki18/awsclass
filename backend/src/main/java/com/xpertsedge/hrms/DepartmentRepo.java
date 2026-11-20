package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DepartmentRepo extends JpaRepository<Department,Long> { boolean existsByNameKey(String key); }
