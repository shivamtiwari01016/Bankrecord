package com.bankrecon.service;

import com.bankrecon.dto.request.CreateTransactionRequest;
import com.bankrecon.dto.response.TransactionResponse;
import com.bankrecon.entity.Account;
import com.bankrecon.entity.Transaction;
import com.bankrecon.entity.enums.AccountStatus;
import com.bankrecon.entity.enums.AccountType;
import com.bankrecon.entity.enums.TransactionStatus;
import com.bankrecon.entity.enums.TransactionType;
import com.bankrecon.exception.BusinessRuleException;
import com.bankrecon.exception.DuplicateResourceException;
import com.bankrecon.exception.IdempotencyConflictException;
import com.bankrecon.exception.ResourceNotFoundException;
import com.bankrecon.mapper.EntityMapper;
import com.bankrecon.repository.AccountRepository;
import com.bankrecon.repository.IdempotencyRecordRepository;
import com.bankrecon.repository.TransactionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock private EntityMapper mapper;

    private TransactionService transactionService;
    private Account activeAccount;
    private TransactionResponse depositResponse;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules(); // Register JavaTimeModule
        transactionService = new TransactionService(
                transactionRepository, accountRepository, idempotencyRecordRepository, mapper, objectMapper);

        activeAccount = new Account();
        activeAccount.setId(1L);
        activeAccount.setAccountNumber("ACC-1001");
        activeAccount.setHolderName("Test User");
        activeAccount.setAccountType(AccountType.SAVINGS);
        activeAccount.setStatus(AccountStatus.ACTIVE);
        activeAccount.setBalance(new BigDecimal("50000.0000"));
        activeAccount.setCreatedAt(LocalDateTime.now());
        activeAccount.setUpdatedAt(LocalDateTime.now());

        depositResponse = new TransactionResponse();
        depositResponse.setId(1L);
        depositResponse.setTransactionRef("TXN-TEST");
        depositResponse.setAccountId(1L);
        depositResponse.setTransactionType("DEPOSIT");
        depositResponse.setAmount(new BigDecimal("10000"));
        depositResponse.setStatus("COMPLETED");
        depositResponse.setBalanceAfter(new BigDecimal("60000"));
    }

    @Test
    @DisplayName("Should create deposit successfully")
    void createDeposit_success() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("10000"));
        request.setDescription("Test deposit");

        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(1L);
            return t;
        });
        when(mapper.toTransactionResponse(any(Transaction.class))).thenReturn(depositResponse);

        TransactionResponse response = transactionService.createDeposit(1L, request, null);

        assertNotNull(response);
        assertEquals("DEPOSIT", response.getTransactionType());
        assertEquals(new BigDecimal("60000.0000"), activeAccount.getBalance());
        verify(accountRepository).save(activeAccount);
    }

    @Test
    @DisplayName("Should create withdrawal successfully")
    void createWithdrawal_success() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("5000"));
        request.setDescription("Test withdrawal");

        TransactionResponse withdrawalResponse = new TransactionResponse();
        withdrawalResponse.setTransactionType("WITHDRAWAL");
        withdrawalResponse.setAmount(new BigDecimal("5000"));
        withdrawalResponse.setBalanceAfter(new BigDecimal("45000"));

        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(inv -> {
            Transaction t = inv.getArgument(0);
            t.setId(2L);
            return t;
        });
        when(mapper.toTransactionResponse(any(Transaction.class))).thenReturn(withdrawalResponse);

        TransactionResponse response = transactionService.createWithdrawal(1L, request, null);

        assertNotNull(response);
        assertEquals(new BigDecimal("45000.0000"), activeAccount.getBalance());
    }

    @Test
    @DisplayName("Should reject withdrawal exceeding balance")
    void createWithdrawal_insufficientFunds() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("100000")); // More than 50000 balance

        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));

        assertThrows(BusinessRuleException.class,
                () -> transactionService.createWithdrawal(1L, request, null));

        // Balance should not change
        assertEquals(new BigDecimal("50000.0000"), activeAccount.getBalance());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject zero amount")
    void createDeposit_zeroAmount() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(BigDecimal.ZERO);

        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));

        assertThrows(BusinessRuleException.class,
                () -> transactionService.createDeposit(1L, request, null));
    }

    @Test
    @DisplayName("Should reject negative amount")
    void createDeposit_negativeAmount() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("-1000"));

        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));

        assertThrows(BusinessRuleException.class,
                () -> transactionService.createDeposit(1L, request, null));
    }

    @Test
    @DisplayName("Should throw not found for non-existent account")
    void createDeposit_accountNotFound() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("1000"));

        when(accountRepository.findByIdWithLock(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> transactionService.createDeposit(99L, request, null));
    }

    @Test
    @DisplayName("Should reject transaction on inactive account")
    void createDeposit_inactiveAccount() {
        activeAccount.setStatus(AccountStatus.INACTIVE);
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("1000"));

        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));

        assertThrows(BusinessRuleException.class,
                () -> transactionService.createDeposit(1L, request, null));
    }

    @Test
    @DisplayName("Should reject duplicate transaction reference")
    void createDeposit_duplicateRef() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("1000"));
        request.setTransactionRef("TXN-EXISTING");

        when(accountRepository.findByIdWithLock(1L)).thenReturn(Optional.of(activeAccount));
        when(transactionRepository.existsByTransactionRef("TXN-EXISTING")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> transactionService.createDeposit(1L, request, null));
    }

    @Test
    @DisplayName("Should handle idempotent retry correctly")
    void createDeposit_idempotentRetry() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("10000"));
        request.setDescription("Test deposit");

        com.bankrecon.entity.IdempotencyRecord existingRecord = new com.bankrecon.entity.IdempotencyRecord();
        existingRecord.setIdempotencyKey("idem-key-1");
        // The hash needs to match what TransactionService produces
        existingRecord.setResponseStatus(201);
        existingRecord.setResponseBody("{\"id\":1,\"transactionRef\":\"TXN-TEST\",\"accountId\":1,\"transactionType\":\"DEPOSIT\",\"amount\":10000,\"status\":\"COMPLETED\",\"balanceAfter\":60000}");

        // We need the hash to match. Let's compute it
        String expectedPayload = "1|DEPOSIT|10000|Test deposit";
        String hash = sha256(expectedPayload);
        existingRecord.setRequestHash(hash);

        when(idempotencyRecordRepository.findByIdempotencyKey("idem-key-1")).thenReturn(Optional.of(existingRecord));

        TransactionResponse response = transactionService.createDeposit(1L, request, "idem-key-1");

        assertNotNull(response);
        // Should NOT call accountRepository or transactionRepository — it's a cached response
        verify(accountRepository, never()).findByIdWithLock(anyLong());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject conflicting idempotency key reuse")
    void createDeposit_conflictingIdempotencyKey() {
        CreateTransactionRequest request = new CreateTransactionRequest();
        request.setAmount(new BigDecimal("10000"));
        request.setDescription("Test deposit");

        com.bankrecon.entity.IdempotencyRecord existingRecord = new com.bankrecon.entity.IdempotencyRecord();
        existingRecord.setIdempotencyKey("idem-key-2");
        existingRecord.setRequestHash("different-hash-value"); // Different from what will be computed
        existingRecord.setResponseStatus(201);

        when(idempotencyRecordRepository.findByIdempotencyKey("idem-key-2")).thenReturn(Optional.of(existingRecord));

        assertThrows(IdempotencyConflictException.class,
                () -> transactionService.createDeposit(1L, request, "idem-key-2"));
    }

    @Test
    @DisplayName("Should throw not found for non-existent transaction")
    void getTransactionById_notFound() {
        when(transactionRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
                () -> transactionService.getTransactionById(99L));
    }

    /**
     * Helper to compute SHA-256 hash matching TransactionService's implementation.
     */
    private String sha256(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
