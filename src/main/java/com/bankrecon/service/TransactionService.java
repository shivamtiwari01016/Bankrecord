package com.bankrecon.service;

import com.bankrecon.dto.request.CreateTransactionRequest;
import com.bankrecon.dto.response.TransactionResponse;
import com.bankrecon.entity.Account;
import com.bankrecon.entity.IdempotencyRecord;
import com.bankrecon.entity.Transaction;
import com.bankrecon.entity.enums.AccountStatus;
import com.bankrecon.entity.enums.TransactionStatus;
import com.bankrecon.entity.enums.TransactionType;
import com.bankrecon.exception.*;
import com.bankrecon.mapper.EntityMapper;
import com.bankrecon.repository.AccountRepository;
import com.bankrecon.repository.IdempotencyRecordRepository;
import com.bankrecon.repository.TransactionRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Core transaction service handling deposits, withdrawals, idempotency, and concurrency.
 *
 * <h3>Concurrency Strategy: Pessimistic Write Locking</h3>
 * <p>
 * We use {@code SELECT ... FOR UPDATE} (pessimistic write lock) when reading the account
 * row before modifying its balance. This ensures:
 * <ul>
 *   <li>Only one transaction at a time can modify a given account's balance.</li>
 *   <li>Concurrent withdrawals cannot race past the balance check.</li>
 *   <li>The balance can never go negative due to concurrent operations.</li>
 * </ul>
 *
 * <h4>Trade-offs vs. Optimistic Locking</h4>
 * <ul>
 *   <li><b>Pessimistic (chosen):</b> Simpler code, guaranteed correctness, but reduces
 *       throughput under high contention because threads block waiting for the lock.</li>
 *   <li><b>Optimistic (alternative):</b> Higher throughput when contention is low. Uses a
 *       version column and retries on {@code OptimisticLockException}. However, it requires
 *       retry logic, and under heavy contention, retries can cascade and starve threads.</li>
 * </ul>
 * For a banking system where correctness is paramount and per-account contention is
 * typically low (one user acting at a time), pessimistic locking is the safer default.
 *
 * <h3>Idempotency</h3>
 * Clients may include an {@code Idempotency-Key} header. The system:
 * <ol>
 *   <li>Hashes the request body (SHA-256).</li>
 *   <li>Checks if the key already exists in {@code idempotency_records}.</li>
 *   <li>If it exists and the hash matches → returns the cached response (safe retry).</li>
 *   <li>If it exists and the hash differs → returns 409 Conflict (conflicting reuse).</li>
 *   <li>If it does not exist → processes normally and stores key + hash + response.</li>
 * </ol>
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final IdempotencyRecordRepository idempotencyRecordRepository;
    private final EntityMapper mapper;
    private final ObjectMapper objectMapper;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              IdempotencyRecordRepository idempotencyRecordRepository,
                              EntityMapper mapper,
                              ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.idempotencyRecordRepository = idempotencyRecordRepository;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a deposit transaction with atomic balance update.
     */
    @Transactional
    public TransactionResponse createDeposit(Long accountId, CreateTransactionRequest request, String idempotencyKey) {
        return processTransaction(accountId, request, TransactionType.DEPOSIT, idempotencyKey);
    }

    /**
     * Creates a withdrawal transaction with atomic balance update and overdraft protection.
     */
    @Transactional
    public TransactionResponse createWithdrawal(Long accountId, CreateTransactionRequest request, String idempotencyKey) {
        return processTransaction(accountId, request, TransactionType.WITHDRAWAL, idempotencyKey);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(Long id) {
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with id: " + id));
        return mapper.toTransactionResponse(txn);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionByRef(String ref) {
        Transaction txn = transactionRepository.findByTransactionRef(ref)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with reference: " + ref));
        return mapper.toTransactionResponse(txn);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listTransactions(Pageable pageable) {
        return transactionRepository.findAll(pageable).map(mapper::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> listAccountTransactions(Long accountId, Pageable pageable) {
        // Verify account exists
        if (!accountRepository.existsById(accountId)) {
            throw new ResourceNotFoundException("Account not found with id: " + accountId);
        }
        return transactionRepository.findByAccountId(accountId, pageable).map(mapper::toTransactionResponse);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> filterTransactions(Long accountId, TransactionType type,
                                                         TransactionStatus status,
                                                         LocalDateTime from, LocalDateTime to,
                                                         Pageable pageable) {
        if (accountId != null) {
            return transactionRepository.findWithFilters(accountId, type, status, from, to, pageable)
                    .map(mapper::toTransactionResponse);
        }
        return transactionRepository.findAllWithFilters(type, status, from, to, pageable)
                .map(mapper::toTransactionResponse);
    }

    // ============================
    // Private transaction engine
    // ============================

    private TransactionResponse processTransaction(Long accountId, CreateTransactionRequest request,
                                                    TransactionType type, String idempotencyKey) {
        // Step 1: Check idempotency
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String requestHash = hashRequest(accountId, request, type);
            Optional<IdempotencyRecord> existing = idempotencyRecordRepository.findByIdempotencyKey(idempotencyKey);

            if (existing.isPresent()) {
                IdempotencyRecord record = existing.get();
                if (record.getRequestHash().equals(requestHash)) {
                    // Safe retry — return cached response
                    log.info("Idempotent retry detected for key: {}", idempotencyKey);
                    try {
                        return objectMapper.readValue(record.getResponseBody(), TransactionResponse.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to deserialize cached response for idempotency key: {}", idempotencyKey);
                        throw new RuntimeException("Failed to retrieve cached response", e);
                    }
                } else {
                    // Conflicting reuse — different request body with same key
                    throw new IdempotencyConflictException(
                            "Idempotency key '" + idempotencyKey + "' was already used with a different request. " +
                            "Each unique operation must use a unique idempotency key.");
                }
            }
        }

        // Step 2: Lock account row (SELECT ... FOR UPDATE)
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + accountId));

        // Step 3: Validate business rules
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("Account is not active. Current status: " + account.getStatus());
        }

        BigDecimal amount = request.getAmount();
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessRuleException("Transaction amount must be greater than zero");
        }

        // Step 4: Check transaction ref uniqueness
        String txnRef = request.getTransactionRef();
        if (txnRef != null && !txnRef.isBlank()) {
            if (transactionRepository.existsByTransactionRef(txnRef)) {
                throw new DuplicateResourceException("Transaction reference already exists: " + txnRef);
            }
        } else {
            txnRef = "TXN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        }

        // Step 5: Calculate new balance
        BigDecimal newBalance;
        if (type == TransactionType.DEPOSIT) {
            newBalance = account.getBalance().add(amount);
        } else {
            if (account.getBalance().compareTo(amount) < 0) {
                throw new BusinessRuleException(
                        "Insufficient funds. Available balance: " + account.getBalance()
                        + ", requested withdrawal: " + amount);
            }
            newBalance = account.getBalance().subtract(amount);
        }

        // Step 6: Update balance and create transaction atomically
        account.setBalance(newBalance);
        accountRepository.save(account);

        Transaction txn = new Transaction();
        txn.setTransactionRef(txnRef);
        txn.setAccount(account);
        txn.setTransactionType(type);
        txn.setAmount(amount);
        txn.setStatus(TransactionStatus.COMPLETED);
        txn.setDescription(request.getDescription());
        txn.setBalanceAfter(newBalance);

        Transaction saved = transactionRepository.save(txn);

        TransactionResponse response = mapper.toTransactionResponse(saved);

        // Step 7: Store idempotency record
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            try {
                IdempotencyRecord record = new IdempotencyRecord();
                record.setIdempotencyKey(idempotencyKey);
                record.setTransactionId(saved.getId());
                record.setRequestHash(hashRequest(accountId, request, type));
                record.setResponseStatus(201);
                record.setResponseBody(objectMapper.writeValueAsString(response));
                idempotencyRecordRepository.save(record);
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize response for idempotency record", e);
                // Transaction still succeeds — idempotency caching is best-effort
            }
        }

        log.info("Transaction completed: ref={}, type={}, amount={}, accountId={}",
                txnRef, type, amount, accountId);

        return response;
    }

    /**
     * Creates a SHA-256 hash of the request parameters to detect conflicting idempotency key reuse.
     */
    private String hashRequest(Long accountId, CreateTransactionRequest request, TransactionType type) {
        try {
            String payload = accountId + "|" + type + "|" + request.getAmount().toPlainString()
                           + "|" + (request.getDescription() != null ? request.getDescription() : "");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
