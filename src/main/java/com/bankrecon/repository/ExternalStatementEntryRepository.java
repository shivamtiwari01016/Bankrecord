package com.bankrecon.repository;

import com.bankrecon.entity.ExternalStatementEntry;
import com.bankrecon.entity.enums.EntryStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExternalStatementEntryRepository extends JpaRepository<ExternalStatementEntry, Long> {

    List<ExternalStatementEntry> findByStatementImportId(Long importId);

    List<ExternalStatementEntry> findByStatementImportIdAndStatus(Long importId, EntryStatus status);

    @Query("SELECT e.externalRef FROM ExternalStatementEntry e " +
           "WHERE e.statementImport.id = :importId AND e.status = 'ACCEPTED' " +
           "GROUP BY e.externalRef HAVING COUNT(e) > 1")
    List<String> findDuplicateExternalRefs(@Param("importId") Long importId);

    boolean existsByExternalRefAndStatementImportId(String externalRef, Long importId);
}
