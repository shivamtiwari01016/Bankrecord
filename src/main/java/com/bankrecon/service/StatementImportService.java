package com.bankrecon.service;

import com.bankrecon.dto.response.ImportSummaryResponse;
import com.bankrecon.entity.ExternalStatementEntry;
import com.bankrecon.entity.StatementImport;
import com.bankrecon.entity.enums.EntryStatus;
import com.bankrecon.entity.enums.ImportStatus;
import com.bankrecon.mapper.EntityMapper;
import com.bankrecon.repository.ExternalStatementEntryRepository;
import com.bankrecon.repository.StatementImportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Handles CSV statement file import with streaming parsing, validation, and duplicate detection.
 * <p>
 * The CSV is read line-by-line using a BufferedReader to avoid loading the entire file into memory.
 * Each row is validated for required columns, data types, and duplicates before being persisted.
 * <p>
 * The import process is separate from transaction posting — importing a statement does NOT
 * create or modify any account balances. It only stores the external records for later reconciliation.
 */
@Service
public class StatementImportService {

    private static final Logger log = LoggerFactory.getLogger(StatementImportService.class);

    private static final String[] EXPECTED_HEADERS = {
        "external_ref", "account_number", "transaction_type", "amount", "transaction_timestamp"
    };

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StatementImportRepository importRepository;
    private final ExternalStatementEntryRepository entryRepository;
    private final EntityMapper mapper;

    public StatementImportService(StatementImportRepository importRepository,
                                   ExternalStatementEntryRepository entryRepository,
                                   EntityMapper mapper) {
        this.importRepository = importRepository;
        this.entryRepository = entryRepository;
        this.mapper = mapper;
    }

    /**
     * Imports a CSV statement file. Reads line-by-line for memory efficiency.
     */
    @Transactional
    public ImportSummaryResponse importStatement(MultipartFile file) {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.csv";

        StatementImport importRecord = new StatementImport();
        importRecord.setFileName(fileName);
        importRecord.setStatus(ImportStatus.PROCESSING);
        importRecord = importRepository.save(importRecord);

        int totalRows = 0;
        int accepted = 0;
        int rejected = 0;
        int duplicates = 0;

        Set<String> seenRefs = new HashSet<>(); // Track refs within this file for in-file duplicates
        List<ExternalStatementEntry> batch = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {

            // Step 1: Validate header row
            String headerLine = reader.readLine();
            if (headerLine == null || headerLine.isBlank()) {
                importRecord.setStatus(ImportStatus.FAILED);
                importRecord.setCompletedAt(LocalDateTime.now());
                importRepository.save(importRecord);
                throw new IllegalArgumentException("CSV file is empty or has no header row");
            }

            String[] headers = headerLine.toLowerCase().split(",");
            validateHeaders(headers);

            // Step 2: Parse data rows line by line
            String line;
            while ((line = reader.readLine()) != null) {
                totalRows++;

                if (line.isBlank()) {
                    continue; // Skip blank lines
                }

                ExternalStatementEntry entry = new ExternalStatementEntry();
                entry.setStatementImport(importRecord);
                entry.setRowNumber(totalRows + 1); // +1 because header is row 1

                String[] columns = line.split(",", -1); // -1 preserves trailing empty strings

                if (columns.length < 5) {
                    entry.setStatus(EntryStatus.REJECTED);
                    entry.setRejectionReason("Row has " + columns.length + " columns, expected 5");
                    rejected++;
                    batch.add(entry);
                    continue;
                }

                // Parse and validate each field
                List<String> errors = new ArrayList<>();

                String externalRef = columns[0].trim();
                String accountNumber = columns[1].trim();
                String txnType = columns[2].trim().toUpperCase();
                String amountStr = columns[3].trim();
                String timestampStr = columns[4].trim();

                if (externalRef.isBlank()) {
                    errors.add("external_ref is empty");
                }
                if (accountNumber.isBlank()) {
                    errors.add("account_number is empty");
                }
                if (!txnType.equals("DEPOSIT") && !txnType.equals("WITHDRAWAL")) {
                    errors.add("transaction_type must be DEPOSIT or WITHDRAWAL, got: " + txnType);
                }

                BigDecimal amount = null;
                try {
                    amount = new BigDecimal(amountStr);
                    if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.add("amount must be positive, got: " + amountStr);
                    }
                } catch (NumberFormatException e) {
                    errors.add("Invalid amount format: " + amountStr);
                }

                LocalDateTime timestamp = null;
                try {
                    timestamp = LocalDateTime.parse(timestampStr, TIMESTAMP_FORMAT);
                } catch (DateTimeParseException e) {
                    errors.add("Invalid timestamp format: " + timestampStr + " (expected yyyy-MM-dd HH:mm:ss)");
                }

                entry.setExternalRef(externalRef);
                entry.setAccountNumber(accountNumber);
                entry.setTransactionType(txnType);
                entry.setAmount(amount);
                entry.setTransactionTimestamp(timestamp);

                if (!errors.isEmpty()) {
                    entry.setStatus(EntryStatus.REJECTED);
                    entry.setRejectionReason(String.join("; ", errors));
                    rejected++;
                } else if (!seenRefs.add(externalRef)) {
                    // Duplicate within the same file
                    entry.setStatus(EntryStatus.DUPLICATE);
                    entry.setRejectionReason("Duplicate external_ref within the same file: " + externalRef);
                    duplicates++;
                } else {
                    entry.setStatus(EntryStatus.ACCEPTED);
                    accepted++;
                }

                batch.add(entry);

                // Flush in batches of 100 to limit memory usage
                if (batch.size() >= 100) {
                    entryRepository.saveAll(batch);
                    batch.clear();
                }
            }

            // Save remaining batch
            if (!batch.isEmpty()) {
                entryRepository.saveAll(batch);
            }

            importRecord.setTotalRows(totalRows);
            importRecord.setAcceptedRows(accepted);
            importRecord.setRejectedRows(rejected);
            importRecord.setDuplicateRows(duplicates);
            importRecord.setStatus(ImportStatus.COMPLETED);
            importRecord.setCompletedAt(LocalDateTime.now());
            importRepository.save(importRecord);

            log.info("Statement import completed: file={}, total={}, accepted={}, rejected={}, duplicates={}",
                    fileName, totalRows, accepted, rejected, duplicates);

        } catch (IllegalArgumentException e) {
            throw e; // Re-throw validation errors
        } catch (Exception e) {
            importRecord.setStatus(ImportStatus.FAILED);
            importRecord.setCompletedAt(LocalDateTime.now());
            importRepository.save(importRecord);
            log.error("Statement import failed for file: {}", fileName, e);
            throw new RuntimeException("Failed to import statement file: " + e.getMessage(), e);
        }

        return mapper.toImportSummaryResponse(importRecord);
    }

    private void validateHeaders(String[] headers) {
        for (int i = 0; i < EXPECTED_HEADERS.length; i++) {
            if (i >= headers.length || !headers[i].trim().equals(EXPECTED_HEADERS[i])) {
                throw new IllegalArgumentException(
                        "Invalid CSV header. Expected columns: " + String.join(", ", EXPECTED_HEADERS)
                        + ". Got: " + String.join(", ", headers));
            }
        }
    }
}
