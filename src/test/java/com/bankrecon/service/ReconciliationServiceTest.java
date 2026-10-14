package com.bankrecon.service;

import com.bankrecon.dto.response.ReconciliationRunResponse;
import com.bankrecon.entity.Account;
import com.bankrecon.entity.ExternalStatementEntry;
import com.bankrecon.entity.ReconciliationRun;
import com.bankrecon.entity.StatementImport;
import com.bankrecon.entity.Transaction;
import com.bankrecon.entity.enums.AccountStatus;
import com.bankrecon.entity.enums.AccountType;
import com.bankrecon.entity.enums.EntryStatus;
import com.bankrecon.entity.enums.ImportStatus;
import com.bankrecon.entity.enums.TransactionStatus;
import com.bankrecon.entity.enums.TransactionType;
import com.bankrecon.mapper.EntityMapper;
import com.bankrecon.repository.ExternalStatementEntryRepository;
import com.bankrecon.repository.ReconciliationItemRepository;
import com.bankrecon.repository.ReconciliationRunRepository;
import com.bankrecon.repository.StatementImportRepository;
import com.bankrecon.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReconciliationService} verifying that every discrepancy
 * type is detected and classified according to the documented precedence.
 */
@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock private ReconciliationRunRepository runRepository;
    @Mock private ReconciliationItemRepository itemRepository;
    @Mock private ExternalStatementEntryRepository entryRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private StatementImportRepository importRepository;

    private ReconciliationService service;
    private Account account;

    @BeforeEach
    void setUp() {
        service = new ReconciliationService(runRepository, itemRepository, entryRepository,
                transactionRepository, importRepository, new EntityMapper());

        account = new Account();
        account.setId(1L);
        account.setAccountNumber("ACC-1001");
        account.setHolderName("Test Holder");
        account.setAccountType(AccountType.SAVINGS);
        account.setStatus(AccountStatus.ACTIVE);
    }

    private ExternalStatementEntry external(String ref, String accountNumber, String type,
                                            String amount, EntryStatus status) {
        ExternalStatementEntry entry = new ExternalStatementEntry();
        entry.setExternalRef(ref);
        entry.setAccountNumber(accountNumber);
        entry.setTransactionType(type);
        entry.setAmount(new BigDecimal(amount));
        entry.setTransactionTimestamp(LocalDateTime.now());
        entry.setStatus(status);
        return entry;
    }

    private Transaction internal(long id, String ref, TransactionType type, String amount) {
        Transaction txn = new Transaction();
        txn.setId(id);
        txn.setTransactionRef(ref);
        txn.setAccount(account);
        txn.setTransactionType(type);
        txn.setAmount(new BigDecimal(amount));
        txn.setStatus(TransactionStatus.COMPLETED);
        txn.setBalanceAfter(new BigDecimal(amount));
        return txn;
    }

    @Test
    @DisplayName("Classifies MATCHED, AMOUNT, TYPE, MISSING and DUPLICATE discrepancies")
    void runReconciliation_classifiesAllDiscrepancyTypes() {
        StatementImport statementImport = new StatementImport();
        statementImport.setId(1L);
        statementImport.setStatus(ImportStatus.COMPLETED);
        when(importRepository.findById(1L)).thenReturn(Optional.of(statementImport));

        when(runRepository.save(any(ReconciliationRun.class))).thenAnswer(invocation -> {
            ReconciliationRun run = invocation.getArgument(0);
            if (run.getId() == null) {
                run.setId(10L);
            }
            return run;
        });

        List<ExternalStatementEntry> entries = List.of(
                external("REF-MATCH", "ACC-1001", "DEPOSIT", "1000.0000", EntryStatus.ACCEPTED),
                external("REF-AMT", "ACC-1001", "DEPOSIT", "500.0000", EntryStatus.ACCEPTED),
                external("REF-TYPE", "ACC-1001", "WITHDRAWAL", "300.0000", EntryStatus.ACCEPTED),
                external("REF-MISS", "ACC-1001", "DEPOSIT", "200.0000", EntryStatus.ACCEPTED),
                external("REF-DUP", "ACC-1001", "DEPOSIT", "111.0000", EntryStatus.DUPLICATE),
                external("REF-INV", "ACC-1001", "DEPOSIT", "50.0000", EntryStatus.REJECTED)
        );
        when(entryRepository.findByStatementImportId(1L)).thenReturn(entries);
        when(entryRepository.findDuplicateExternalRefs(1L)).thenReturn(List.of());

        when(transactionRepository.findByTransactionRefIn(anyList())).thenReturn(List.of(
                internal(101L, "REF-MATCH", TransactionType.DEPOSIT, "1000.0000"),
                internal(102L, "REF-AMT", TransactionType.DEPOSIT, "400.0000"),
                internal(103L, "REF-TYPE", TransactionType.DEPOSIT, "300.0000")
        ));

        when(transactionRepository.findCompletedByAccountAndDateRange(eq("ACC-1001"), isNull(), isNull()))
                .thenReturn(List.of(
                        internal(101L, "REF-MATCH", TransactionType.DEPOSIT, "1000.0000"),
                        internal(102L, "REF-AMT", TransactionType.DEPOSIT, "400.0000"),
                        internal(103L, "REF-TYPE", TransactionType.DEPOSIT, "300.0000"),
                        internal(104L, "REF-ONLY", TransactionType.DEPOSIT, "999.0000")
                ));

        ReconciliationRunResponse result = service.runReconciliation(1L);

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(1, result.getMatched());
        assertEquals(1, result.getAmountMismatch());
        assertEquals(1, result.getTypeMismatch());
        assertEquals(1, result.getMissingInternal());
        assertEquals(1, result.getDuplicateExternal());
        assertEquals(1, result.getInvalidExternal());
        assertEquals(1, result.getMissingExternal());
        assertEquals(7, result.getTotalItems());
        verify(itemRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("Refuses to reconcile an import that is not COMPLETED")
    void runReconciliation_incompleteImport_throws() {
        StatementImport statementImport = new StatementImport();
        statementImport.setId(2L);
        statementImport.setStatus(ImportStatus.PROCESSING);
        when(importRepository.findById(2L)).thenReturn(Optional.of(statementImport));

        assertThrows(IllegalArgumentException.class, () -> service.runReconciliation(2L));
    }
}
