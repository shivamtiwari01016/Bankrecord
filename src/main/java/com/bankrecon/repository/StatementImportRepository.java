package com.bankrecon.repository;

import com.bankrecon.entity.StatementImport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StatementImportRepository extends JpaRepository<StatementImport, Long> {
}
