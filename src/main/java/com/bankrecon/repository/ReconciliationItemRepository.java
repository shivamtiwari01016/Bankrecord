package com.bankrecon.repository;

import com.bankrecon.entity.ReconciliationItem;
import com.bankrecon.entity.enums.MatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReconciliationItemRepository extends JpaRepository<ReconciliationItem, Long> {

    List<ReconciliationItem> findByReconciliationRunId(Long runId);

    Page<ReconciliationItem> findByReconciliationRunIdAndMatchStatusNot(
            Long runId, MatchStatus excludeStatus, Pageable pageable);

    Page<ReconciliationItem> findByMatchStatusNot(MatchStatus excludeStatus, Pageable pageable);

    List<ReconciliationItem> findByReconciliationRunIdAndMatchStatus(Long runId, MatchStatus status);
}
