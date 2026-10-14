package com.bankrecon.controller;

import com.bankrecon.dto.request.CreateTransactionRequest;
import com.bankrecon.dto.response.TransactionResponse;
import com.bankrecon.entity.enums.TransactionStatus;
import com.bankrecon.entity.enums.TransactionType;
import com.bankrecon.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "Transactions", description = "Transaction management APIs")
@RestController
@RequestMapping("/api/v1")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Operation(summary = "Create a deposit transaction")
    @PostMapping("/accounts/{accountId}/transactions/deposit")
    public ResponseEntity<TransactionResponse> createDeposit(
            @PathVariable Long accountId,
            @Valid @RequestBody CreateTransactionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TransactionResponse response = transactionService.createDeposit(accountId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Create a withdrawal transaction")
    @PostMapping("/accounts/{accountId}/transactions/withdrawal")
    public ResponseEntity<TransactionResponse> createWithdrawal(
            @PathVariable Long accountId,
            @Valid @RequestBody CreateTransactionRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        TransactionResponse response = transactionService.createWithdrawal(accountId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Retrieve a transaction by ID")
    @GetMapping("/transactions/{id}")
    public ResponseEntity<TransactionResponse> getTransaction(@PathVariable Long id) {
        return ResponseEntity.ok(transactionService.getTransactionById(id));
    }

    @Operation(summary = "Retrieve a transaction by reference")
    @GetMapping("/transactions/ref/{ref}")
    public ResponseEntity<TransactionResponse> getTransactionByRef(@PathVariable String ref) {
        return ResponseEntity.ok(transactionService.getTransactionByRef(ref));
    }

    @Operation(summary = "List all transactions with optional filters")
    @GetMapping("/transactions")
    public ResponseEntity<Page<TransactionResponse>> listTransactions(
            @Parameter(description = "Filter by account ID") @RequestParam(required = false) Long accountId,
            @Parameter(description = "Filter by type: DEPOSIT or WITHDRAWAL") @RequestParam(required = false) TransactionType type,
            @Parameter(description = "Filter by status: COMPLETED, FAILED, REJECTED") @RequestParam(required = false) TransactionStatus status,
            @Parameter(description = "Filter from date (ISO format)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Filter to date (ISO format)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {

        if (accountId != null || type != null || status != null || from != null || to != null) {
            return ResponseEntity.ok(transactionService.filterTransactions(accountId, type, status, from, to, pageable));
        }
        return ResponseEntity.ok(transactionService.listTransactions(pageable));
    }
}
