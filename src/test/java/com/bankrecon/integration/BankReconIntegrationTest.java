package com.bankrecon.integration;

import com.bankrecon.dto.request.CreateAccountRequest;
import com.bankrecon.dto.request.CreateTransactionRequest;
import com.bankrecon.dto.response.AccountResponse;
import com.bankrecon.dto.response.ImportSummaryResponse;
import com.bankrecon.dto.response.ReconciliationRunResponse;
import com.bankrecon.dto.response.TransactionResponse;
import com.bankrecon.entity.Account;
import com.bankrecon.entity.enums.TransactionType;
import com.bankrecon.exception.BusinessRuleException;
import com.bankrecon.exception.IdempotencyConflictException;
import com.bankrecon.repository.AccountRepository;
import com.bankrecon.repository.TransactionRepository;
import com.bankrecon.service.AccountService;
import com.bankrecon.service.ReconciliationService;
import com.bankrecon.service.ReportService;
import com.bankrecon.service.StatementImportService;
import com.bankrecon.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Pageable;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration tests running against an in-memory H2 database
 * (MySQL compatibility mode).
 * <p>
 * Docker is not available in the build environment, so Testcontainers-based MySQL
 * integration tests cannot run here. This suite provides the practical local
 * alternative recommended by the project spec: the full Spring context with the
 * real JPA/persistence stack, exercising the same entities, repositories and services.
 * See {@code docs/INTERVIEW_PREPARATION.md} for how to run the Testcontainers variant.
 */
@SpringBootTest
@ActiveProfiles("test")
class BankReconIntegrationTest {

    @Autowired private AccountService accountService;
    @Autowired private TransactionService transactionService;
    @Autowired private StatementImportService importService;
    @Autowired private ReconciliationService reconciliationService;
    @Autowired private ReportService reportService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private TransactionRepository transactionRepository;

    private AccountResponse createAccount(String holder) {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setHolderName(holder);
        request.setAccountType("SAVINGS");
        request.setCurrency("INR");
        return accountService.createAccount(request);
    }

    private TransactionResponse deposit(Long accountId, String amount, String ref) {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal(amount));
        request.setTransactionRef(ref);
        request.setDescription("Integration test deposit");
        return transactionService.createDeposit(accountId, request, null);
    }

    @Test
    @DisplayName("Deposit then withdrawal preserve balance consistency")
    void depositAndWithdrawal_preserveBalanceConsistency() {
        AccountResponse account = createAccount("Integration Consistency");
        deposit(account.getId(), "1000.0000", null);

        CreateTransactionRequest withdrawal = new CreateTransactionRequest();
        withdrawal.setAmount(new BigDecimal("300.0000"));
        transactionService.createWithdrawal(account.getId(), withdrawal, null);

        Account updated = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("700.0000").compareTo(updated.getBalance()));
        assertEquals(2, transactionRepository.findByAccountId(account.getId(), Pageable.unpaged())
                .getTotalElements());
    }

    @Test
    @DisplayName("Insufficient funds reject the withdrawal and roll back the balance")
    void insufficientFunds_leavesBalanceUnchanged() {
        AccountResponse account = createAccount("Integration Rollback");
        deposit(account.getId(), "500.0000", null);

        CreateTransactionRequest withdrawal = new CreateTransactionRequest();
        withdrawal.setAmount(new BigDecimal("900.0000"));

        assertThrows(BusinessRuleException.class,
                () -> transactionService.createWithdrawal(account.getId(), withdrawal, null));

        Account updated = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("500.0000").compareTo(updated.getBalance()));
        assertEquals(1, transactionRepository.findByAccountId(account.getId(), Pageable.unpaged())
                .getTotalElements());
    }

    @Test
    @DisplayName("Repeated request with the same idempotency key returns the original transaction")
    void idempotentRetry_returnsSameTransaction() {
        AccountResponse account = createAccount("Integration Idempotent");

        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("100.0000"));
        request.setDescription("Idempotent deposit");

        TransactionResponse first = transactionService.createDeposit(account.getId(), request, "idem-it-1");
        TransactionResponse second = transactionService.createDeposit(account.getId(), request, "idem-it-1");

        assertEquals(first.getId(), second.getId());
        assertEquals(1, transactionRepository.findByAccountId(account.getId(), Pageable.unpaged())
                .getTotalElements());

        Account updated = accountRepository.findById(account.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("100.0000").compareTo(updated.getBalance()));
    }

    @Test
    @DisplayName("Conflicting idempotency key reuse is rejected with a conflict")
    void conflictingIdempotencyKey_throwsConflict() {
        AccountResponse account = createAccount("Integration Conflict");

        CreateTransactionRequest first = new CreateTransactionRequest();
        first.setAmount(new BigDecimal("100.0000"));
        transactionService.createDeposit(account.getId(), first, "idem-conf-1");

        CreateTransactionRequest second = new CreateTransactionRequest();
        second.setAmount(new BigDecimal("200.0000")); // different payload -> different hash
        assertThrows(IdempotencyConflictException.class,
                () -> transactionService.createDeposit(account.getId(), second, "idem-conf-1"));
    }

    @Test
    @DisplayName("Concurrent withdrawals never overdraw the account")
    void concurrentWithdrawals_neverOverdraw() throws Exception {
        AccountResponse account = createAccount("Integration Concurrent");
        deposit(account.getId(), "1000.0000", null);

        int threads = 8;
        BigDecimal withdrawalAmount = new BigDecimal("200.0000");
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                try {
                    startGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                CreateTransactionRequest withdrawal = new CreateTransactionRequest();
                withdrawal.setAmount(withdrawalAmount);
                try {
                    transactionService.createWithdrawal(account.getId(), withdrawal, null);
                    successes.incrementAndGet();
                } catch (Exception expected) {
                    // Insufficient funds (or transient lock contention) are expected for some threads.
                }
            }));
        }

        startGate.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        Account updated = accountRepository.findById(account.getId()).orElseThrow();
        int successCount = successes.get();
        BigDecimal expectedBalance =
                new BigDecimal("1000.0000").subtract(withdrawalAmount.multiply(new BigDecimal(successCount)));

        assertEquals(0, expectedBalance.compareTo(updated.getBalance()),
                "Balance must equal initial minus successful withdrawals");
        assertTrue(updated.getBalance().compareTo(BigDecimal.ZERO) >= 0,
                "Balance must never become negative");
        assertTrue(successCount <= 5, "At most 5 withdrawals of 200 can succeed from a 1000 balance");

        long persistedWithdrawals = transactionRepository.findByAccountId(account.getId(), Pageable.unpaged())
                .stream()
                .filter(t -> t.getTransactionType() == TransactionType.WITHDRAWAL)
                .count();
        assertEquals(successCount, persistedWithdrawals,
                "One persisted withdrawal transaction per successful request");
    }

    @Test
    @DisplayName("CSV import and reconciliation detect every discrepancy type")
    void csvImportAndReconciliation_detectDiscrepancies() {
        AccountResponse account = createAccount("Integration Reco");
        String accountNumber = account.getAccountNumber();

        deposit(account.getId(), "1000.0000", "REF-MATCH");
        deposit(account.getId(), "400.0000", "REF-AMT");
        deposit(account.getId(), "300.0000", "REF-TYPE");
        deposit(account.getId(), "999.0000", "REF-ONLY");

        String csv = "external_ref,account_number,transaction_type,amount,transaction_timestamp\n"
                + "REF-MATCH," + accountNumber + ",DEPOSIT,1000.0000,2026-10-01 09:00:00\n"
                + "REF-AMT," + accountNumber + ",DEPOSIT,500.0000,2026-10-01 10:00:00\n"
                + "REF-TYPE," + accountNumber + ",WITHDRAWAL,300.0000,2026-10-01 11:00:00\n"
                + "REF-MISS," + accountNumber + ",DEPOSIT,200.0000,2026-10-01 12:00:00\n"
                + "REF-DUP," + accountNumber + ",DEPOSIT,111.0000,2026-10-01 13:00:00\n"
                + "REF-DUP," + accountNumber + ",DEPOSIT,111.0000,2026-10-01 13:00:00\n"
                + "," + accountNumber + ",DEPOSIT,50.0000,2026-10-01 14:00:00\n";

        MockMultipartFile file = new MockMultipartFile("file", "statement.csv", "text/csv",
                csv.getBytes(StandardCharsets.UTF_8));

        ImportSummaryResponse summary = importService.importStatement(file);
        assertEquals(7, summary.getTotalRows());
        assertEquals(5, summary.getAcceptedRows());
        assertEquals(1, summary.getRejectedRows());
        assertEquals(1, summary.getDuplicateRows());
        assertEquals("COMPLETED", summary.getStatus());

        ReconciliationRunResponse run = reconciliationService.runReconciliation(summary.getImportId());
        assertEquals("COMPLETED", run.getStatus());
        assertEquals(1, run.getMatched());
        assertEquals(1, run.getAmountMismatch());
        assertEquals(1, run.getTypeMismatch());
        assertEquals(1, run.getDuplicateExternal());
        assertEquals(1, run.getInvalidExternal());
        assertEquals(1, run.getMissingExternal());
        assertEquals(2, run.getMissingInternal()); // REF-MISS and the accepted first REF-DUP
    }

    @Test
    @DisplayName("Report queries return correct results for persisted data")
    void reportQueries_returnResults() {
        AccountResponse account = createAccount("Integration Report");
        deposit(account.getId(), "1234.0000", null);

        Map<String, Long> statusCounts = reportService.getTransactionCountsByStatus();
        assertTrue(statusCounts.containsKey("COMPLETED"));
        assertTrue(statusCounts.get("COMPLETED") >= 1);

        assertFalse(reportService.getTopAccounts(5).isEmpty());
        assertNotNull(reportService.getDuplicateRefs());
        assertNotNull(reportService.getFailedTransactions());

        Map<String, Object> summary = reportService.getDepositWithdrawalSummary(
                LocalDateTime.now().minusYears(5), LocalDateTime.now().plusDays(1));
        assertTrue(((BigDecimal) summary.get("totalDeposits")).compareTo(BigDecimal.ZERO) > 0);
    }
}
