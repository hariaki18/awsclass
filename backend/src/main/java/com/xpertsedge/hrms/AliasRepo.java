package com.xpertsedge.hrms;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AliasRepo extends JpaRepository<Alias, Long> { boolean existsByAlias(String alias); }
