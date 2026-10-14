package com.bankrecon.controller;

import com.bankrecon.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Tag(name = "Reports", description = "SQL reporting and analytics APIs")
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @Operation(summary = "Daily transaction summary for a date range")
    @GetMapping("/daily-summary")
    public ResponseEntity<List<Map<String, Object>>> getDailySummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(reportService.getDailySummary(from, to));
    }

    @Operation(summary = "Total deposits and withdrawals for a date range")
    @GetMapping("/deposit-withdrawal-summary")
    public ResponseEntity<Map<String, Object>> getDepositWithdrawalSummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(reportService.getDepositWithdrawalSummary(from, to));
    }

    @Operation(summary = "Transaction counts grouped by status")
    @GetMapping("/status-summary")
    public ResponseEntity<Map<String, Long>> getStatusSummary() {
        return ResponseEntity.ok(reportService.getTransactionCountsByStatus());
    }

    @Operation(summary = "Top accounts by transaction volume")
    @GetMapping("/account-activity")
    public ResponseEntity<List<Map<String, Object>>> getAccountActivity(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(reportService.getTopAccounts(limit));
    }

    @Operation(summary = "Failed or rejected transactions")
    @GetMapping("/failed-transactions")
    public ResponseEntity<List<Map<String, Object>>> getFailedTransactions() {
        return ResponseEntity.ok(reportService.getFailedTransactions());
    }

    @Operation(summary = "Duplicate transaction references (data quality check)")
    @GetMapping("/duplicate-refs")
    public ResponseEntity<List<Map<String, Object>>> getDuplicateRefs() {
        return ResponseEntity.ok(reportService.getDuplicateRefs());
    }
}
