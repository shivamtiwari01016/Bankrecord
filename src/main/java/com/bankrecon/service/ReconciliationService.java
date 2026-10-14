package com.bankrecon.service;

import com.bankrecon.dto.response.ReconciliationItemResponse;
import com.bankrecon.dto.response.ReconciliationRunResponse;
import com.bankrecon.entity.*;
import com.bankrecon.entity.enums.*;
import com.bankrecon.exception.ResourceNotFoundException;
import com.bankrecon.mapper.EntityMapper;
import com.bankrecon.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reconciliation engine comparing internal transactions against external statement entries.
 *
 * <h3>Matching Rules (in precedence order)</h3>
 * <ol>
 *   <li><b>INVALID_EXTERNAL:</b> External entries with status REJECTED are flagged first.</li>
 *   <li><b>DUPLICATE_EXTERNAL:</b> External entries with duplicate refs (within the same import).</li>
 *   <li><b>MISSING_INTERNAL:</b> External ref has no corresponding internal transaction ref.</li>
 *   <li><b>TYPE_MISMATCH:</b> Refs match but transaction types differ.</li>
 *   <li><b>AMOUNT_MISMATCH:</b> Refs match but amounts differ.</li>
 *   <li><b>MATCHED:</b> Refs match and type + amount are identical.</li>
 *   <li><b>MISSING_EXTERNAL:</b> Internal transactions with no matching external entry.</li>
 * </ol>
 *
 * <p><b>IMPORTANT:</b> Reconciliation NEVER modifies account balances. It is a read-only
 * comparison that produces a report of discrepancies.</p>
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationRunRepository runRepository;
    private final ReconciliationItemRepository itemRepository;
    private final ExternalStatementEntryRepository entryRepository;
    private final TransactionRepository transactionRepository;
    private final StatementImportRepository importRepository;
    private final EntityMapper mapper;

    public ReconciliationService(ReconciliationRunRepository runRepository,
                                  ReconciliationItemRepository itemRepository,
                                  ExternalStatementEntryRepository entryRepository,
                                  TransactionRepository transactionRepository,
                                  StatementImportRepository importRepository,
                                  EntityMapper mapper) {
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.entryRepository = entryRepository;
        this.transactionRepository = transactionRepository;
        this.importRepository = importRepository;
        this.mapper = mapper;
    }

    /**
     * Runs reconciliation for a given statement import.
     */
    @Transactional
    public ReconciliationRunResponse runReconciliation(Long importId) {
        StatementImport statementImport = importRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("Statement import not found with id: " + importId));

        if (statementImport.getStatus() != ImportStatus.COMPLETED) {
            throw new IllegalArgumentException("Cannot reconcile an import that is not COMPLETED. Current status: " + statementImport.getStatus());
        }

        // Create reconciliation run
        ReconciliationRun run = new ReconciliationRun();
        run.setStatementImport(statementImport);
        run.setStatus(ReconciliationStatus.IN_PROGRESS);
        run = runRepository.save(run);

        // Load all external entries for this import
        List<ExternalStatementEntry> allEntries = entryRepository.findByStatementImportId(importId);

        // Counters
        int matched = 0, missingInternal = 0, missingExternal = 0;
        int amountMismatch = 0, typeMismatch = 0, duplicateExternal = 0, invalidExternal = 0;

        List<ReconciliationItem> items = new ArrayList<>();

        // Step 1: Find duplicate external refs
        Set<String> duplicateRefs = new HashSet<>(entryRepository.findDuplicateExternalRefs(importId));

        // Step 2: Collect all accepted entries and build lookup maps
        List<ExternalStatementEntry> acceptedEntries = allEntries.stream()
                .filter(e -> e.getStatus() == EntryStatus.ACCEPTED)
                .collect(Collectors.toList());

        // Collect unique external refs for bulk lookup
        List<String> externalRefs = acceptedEntries.stream()
                .map(ExternalStatementEntry::getExternalRef)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // Bulk load matching internal transactions
        Map<String, Transaction> internalByRef = new HashMap<>();
        if (!externalRefs.isEmpty()) {
            List<Transaction> internalTxns = transactionRepository.findByTransactionRefIn(externalRefs);
            for (Transaction txn : internalTxns) {
                internalByRef.put(txn.getTransactionRef(), txn);
            }
        }

        // Track which internal transactions were matched
        Set<String> matchedInternalRefs = new HashSet<>();

        // Step 3: Process each external entry
        for (ExternalStatementEntry entry : allEntries) {
            ReconciliationItem item = new ReconciliationItem();
            item.setReconciliationRun(run);
            item.setExternalEntryId(entry.getId());
            item.setExternalRef(entry.getExternalRef());
            item.setExternalAmount(entry.getAmount());
            item.setExternalType(entry.getTransactionType());

            // Check invalid entries first (rejected during import)
            if (entry.getStatus() == EntryStatus.REJECTED) {
                item.setMatchStatus(MatchStatus.INVALID_EXTERNAL);
                item.setDiscrepancyDetail("Entry was rejected during import: " + entry.getRejectionReason());
                invalidExternal++;
                items.add(item);
                continue;
            }

            // Check duplicates
            if (entry.getStatus() == EntryStatus.DUPLICATE || duplicateRefs.contains(entry.getExternalRef())) {
                item.setMatchStatus(MatchStatus.DUPLICATE_EXTERNAL);
                item.setDiscrepancyDetail("Duplicate external reference: " + entry.getExternalRef());
                duplicateExternal++;
                items.add(item);
                continue;
            }

            // Look up internal transaction by ref
            Transaction internal = internalByRef.get(entry.getExternalRef());

            if (internal == null) {
                item.setMatchStatus(MatchStatus.MISSING_INTERNAL);
                item.setDiscrepancyDetail("No internal transaction found for external ref: " + entry.getExternalRef());
                missingInternal++;
            } else {
                matchedInternalRefs.add(internal.getTransactionRef());
                item.setInternalTransactionId(internal.getId());
                item.setInternalAmount(internal.getAmount());
                item.setInternalType(internal.getTransactionType().name());

                // Check type first, then amount
                boolean typeMatches = internal.getTransactionType().name().equalsIgnoreCase(entry.getTransactionType());
                boolean amountMatches = internal.getAmount().compareTo(entry.getAmount()) == 0;

                if (!typeMatches) {
                    item.setMatchStatus(MatchStatus.TYPE_MISMATCH);
                    item.setDiscrepancyDetail("Type mismatch: external=" + entry.getTransactionType()
                            + ", internal=" + internal.getTransactionType().name());
                    typeMismatch++;
                } else if (!amountMatches) {
                    item.setMatchStatus(MatchStatus.AMOUNT_MISMATCH);
                    item.setDiscrepancyDetail("Amount mismatch: external=" + entry.getAmount()
                            + ", internal=" + internal.getAmount());
                    amountMismatch++;
                } else {
                    item.setMatchStatus(MatchStatus.MATCHED);
                    item.setDiscrepancyDetail(null);
                    matched++;
                }
            }

            items.add(item);
        }

        // Step 4: Find MISSING_EXTERNAL — internal transactions not in any external entry
        // Scope: all unique account numbers in the import's accepted entries
        Set<String> importAccountNumbers = acceptedEntries.stream()
                .map(ExternalStatementEntry::getAccountNumber)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (String accNum : importAccountNumbers) {
            List<Transaction> accountTxns = transactionRepository.findCompletedByAccountAndDateRange(accNum, null, null);
            for (Transaction txn : accountTxns) {
                if (!matchedInternalRefs.contains(txn.getTransactionRef())
                        && !externalRefs.contains(txn.getTransactionRef())) {
                    ReconciliationItem item = new ReconciliationItem();
                    item.setReconciliationRun(run);
                    item.setInternalTransactionId(txn.getId());
                    item.setExternalRef(txn.getTransactionRef());
                    item.setInternalAmount(txn.getAmount());
                    item.setInternalType(txn.getTransactionType().name());
                    item.setMatchStatus(MatchStatus.MISSING_EXTERNAL);
                    item.setDiscrepancyDetail("Internal transaction has no corresponding external entry");
                    items.add(item);
                    missingExternal++;
                }
            }
        }

        // Save all items
        itemRepository.saveAll(items);

        // Update run summary
        int total = matched + missingInternal + missingExternal + amountMismatch
                  + typeMismatch + duplicateExternal + invalidExternal;
        run.setTotalItems(total);
        run.setMatched(matched);
        run.setMissingInternal(missingInternal);
        run.setMissingExternal(missingExternal);
        run.setAmountMismatch(amountMismatch);
        run.setTypeMismatch(typeMismatch);
        run.setDuplicateExternal(duplicateExternal);
        run.setInvalidExternal(invalidExternal);
        run.setStatus(ReconciliationStatus.COMPLETED);
        run.setCompletedAt(java.time.LocalDateTime.now());
        runRepository.save(run);

        log.info("Reconciliation completed: runId={}, total={}, matched={}, discrepancies={}",
                run.getId(), total, matched, total - matched);

        return mapper.toReconciliationRunResponse(run);
    }

    @Transactional(readOnly = true)
    public ReconciliationRunResponse getRunById(Long runId) {
        ReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Reconciliation run not found with id: " + runId));
        return mapper.toReconciliationRunResponse(run);
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationItemResponse> getDiscrepancies(Long runId, Pageable pageable) {
        if (runId != null) {
            return itemRepository.findByReconciliationRunIdAndMatchStatusNot(runId, MatchStatus.MATCHED, pageable)
                    .map(mapper::toReconciliationItemResponse);
        }
        return itemRepository.findByMatchStatusNot(MatchStatus.MATCHED, pageable)
                .map(mapper::toReconciliationItemResponse);
    }
}
