package com.bankrecon.service;

import com.bankrecon.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * SQL reporting service for analytics endpoints.
 */
@Service
public class ReportService {

    private final TransactionRepository transactionRepository;

    public ReportService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    /**
     * Total deposits and withdrawals for a date range.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getDepositWithdrawalSummary(LocalDateTime from, LocalDateTime to) {
        List<Object[]> results = transactionRepository.sumByTypeAndDateRange(from, to);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("dateRange", Map.of("from", from.toString(), "to", to.toString()));

        BigDecimal totalDeposits = BigDecimal.ZERO;
        BigDecimal totalWithdrawals = BigDecimal.ZERO;

        for (Object[] row : results) {
            String type = (String) row[0];
            BigDecimal total = (BigDecimal) row[2];
            if ("DEPOSIT".equals(type)) {
                totalDeposits = total;
            } else if ("WITHDRAWAL".equals(type)) {
                totalWithdrawals = total;
            }
        }

        summary.put("totalDeposits", totalDeposits);
        summary.put("totalWithdrawals", totalWithdrawals);
        summary.put("netFlow", totalDeposits.subtract(totalWithdrawals));
        return summary;
    }

    /**
     * Daily transaction counts and amounts.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDailySummary(LocalDateTime from, LocalDateTime to) {
        List<Object[]> results = transactionRepository.dailySummary(from, to);
        List<Map<String, Object>> summaries = new ArrayList<>();

        for (Object[] row : results) {
            Map<String, Object> day = new LinkedHashMap<>();
            day.put("date", row[0].toString());
            day.put("transactionCount", ((Number) row[1]).longValue());
            day.put("totalAmount", row[2]);
            summaries.add(day);
        }

        return summaries;
    }

    /**
     * Transaction counts grouped by status.
     */
    @Transactional(readOnly = true)
    public Map<String, Long> getTransactionCountsByStatus() {
        List<Object[]> results = transactionRepository.countByStatus();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Object[] row : results) {
            counts.put((String) row[0], ((Number) row[1]).longValue());
        }
        return counts;
    }

    /**
     * Accounts with highest transaction volume.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getTopAccounts(int limit) {
        List<Object[]> results = transactionRepository.topAccountsByVolume(limit);
        List<Map<String, Object>> accounts = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> account = new LinkedHashMap<>();
            account.put("accountNumber", row[0]);
            account.put("holderName", row[1]);
            account.put("transactionCount", ((Number) row[2]).longValue());
            account.put("totalAmount", row[3]);
            accounts.add(account);
        }
        return accounts;
    }

    /**
     * Failed or rejected transactions.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getFailedTransactions() {
        List<Object[]> results = transactionRepository.findFailedOrRejected();
        List<Map<String, Object>> transactions = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> txn = new LinkedHashMap<>();
            txn.put("id", row[0]);
            txn.put("transactionRef", row[1]);
            txn.put("accountId", row[2]);
            txn.put("transactionType", row[3]);
            txn.put("amount", row[4]);
            txn.put("status", row[5]);
            txn.put("description", row[6]);
            txn.put("createdAt", row[7] != null ? row[7].toString() : null);
            transactions.add(txn);
        }
        return transactions;
    }

    /**
     * Duplicate transaction reference detection.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getDuplicateRefs() {
        List<Object[]> results = transactionRepository.findDuplicateRefs();
        List<Map<String, Object>> duplicates = new ArrayList<>();
        for (Object[] row : results) {
            Map<String, Object> dup = new LinkedHashMap<>();
            dup.put("transactionRef", row[0]);
            dup.put("count", ((Number) row[1]).longValue());
            duplicates.add(dup);
        }
        return duplicates;
    }
}
