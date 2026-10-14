package com.bankrecon.repository;

import com.bankrecon.entity.Account;
import com.bankrecon.entity.enums.AccountStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    Page<Account> findByStatus(AccountStatus status, Pageable pageable);

    /**
     * Acquires a pessimistic write lock (SELECT ... FOR UPDATE) on the account row.
     * This prevents concurrent transactions from reading or modifying the balance
     * until the current transaction commits. Essential for safe withdrawals.
     *
     * Trade-off: Pessimistic locking blocks other threads, reducing throughput under
     * high contention. However, for financial operations, correctness is more important
     * than throughput. Optimistic locking (version column + retry) could offer better
     * throughput but risks more complex retry logic and potential starvation under
     * heavy contention.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);
}
