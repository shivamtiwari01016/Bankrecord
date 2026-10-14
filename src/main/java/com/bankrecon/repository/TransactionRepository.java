package com.bankrecon.repository;

import com.bankrecon.entity.Transaction;
import com.bankrecon.entity.enums.TransactionStatus;
import com.bankrecon.entity.enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByTransactionRef(String transactionRef);

    boolean existsByTransactionRef(String transactionRef);

    Page<Transaction> findByAccountId(Long accountId, Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE t.account.id = :accountId " +
           "AND (:type IS NULL OR t.transactionType = :type) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:from IS NULL OR t.createdAt >= :from) " +
           "AND (:to IS NULL OR t.createdAt <= :to)")
    Page<Transaction> findWithFilters(
            @Param("accountId") Long accountId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    @Query("SELECT t FROM Transaction t WHERE " +
           "(:type IS NULL OR t.transactionType = :type) " +
           "AND (:status IS NULL OR t.status = :status) " +
           "AND (:from IS NULL OR t.createdAt >= :from) " +
           "AND (:to IS NULL OR t.createdAt <= :to)")
    Page<Transaction> findAllWithFilters(
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            Pageable pageable);

    /**
     * Find internal transactions matching a set of external references — used by reconciliation.
     */
    @Query("SELECT t FROM Transaction t WHERE t.transactionRef IN :refs")
    List<Transaction> findByTransactionRefIn(@Param("refs") List<String> refs);

    /**
     * Find all completed transactions within a date range for reconciliation scope.
     */
    @Query("SELECT t FROM Transaction t WHERE t.account.accountNumber = :accountNumber " +
           "AND t.status = 'COMPLETED' " +
           "AND (:from IS NULL OR t.createdAt >= :from) " +
           "AND (:to IS NULL OR t.createdAt <= :to)")
    List<Transaction> findCompletedByAccountAndDateRange(
            @Param("accountNumber") String accountNumber,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to);

    // --- Reporting queries ---

    @Query(value = "SELECT t.transaction_type, COUNT(*) as cnt, SUM(t.amount) as total " +
                   "FROM transactions t WHERE t.created_at BETWEEN :from AND :to " +
                   "AND t.status = 'COMPLETED' GROUP BY t.transaction_type",
           nativeQuery = true)
    List<Object[]> sumByTypeAndDateRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT DATE(t.created_at) as txn_date, COUNT(*) as cnt, SUM(t.amount) as total " +
                   "FROM transactions t WHERE t.created_at BETWEEN :from AND :to " +
                   "GROUP BY DATE(t.created_at) ORDER BY txn_date",
           nativeQuery = true)
    List<Object[]> dailySummary(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query(value = "SELECT t.status, COUNT(*) as cnt FROM transactions t GROUP BY t.status",
           nativeQuery = true)
    List<Object[]> countByStatus();

    @Query(value = "SELECT a.account_number, a.holder_name, COUNT(t.id) as txn_count, " +
                   "SUM(t.amount) as total_amount " +
                   "FROM transactions t JOIN accounts a ON t.account_id = a.id " +
                   "GROUP BY a.id, a.account_number, a.holder_name " +
                   "ORDER BY txn_count DESC LIMIT :limit",
           nativeQuery = true)
    List<Object[]> topAccountsByVolume(@Param("limit") int limit);

    @Query(value = "SELECT t.id, t.transaction_ref, t.account_id, t.transaction_type, " +
                   "t.amount, t.status, t.description, t.created_at " +
                   "FROM transactions t WHERE t.status IN ('FAILED', 'REJECTED') " +
                   "ORDER BY t.created_at DESC",
           nativeQuery = true)
    List<Object[]> findFailedOrRejected();

    @Query(value = "SELECT t.transaction_ref, COUNT(*) as cnt " +
                   "FROM transactions t GROUP BY t.transaction_ref HAVING cnt > 1",
           nativeQuery = true)
    List<Object[]> findDuplicateRefs();
}
