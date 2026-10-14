package com.bankrecon.controller;

import com.bankrecon.dto.request.CreateAccountRequest;
import com.bankrecon.dto.response.AccountResponse;
import com.bankrecon.dto.response.TransactionResponse;
import com.bankrecon.service.AccountService;
import com.bankrecon.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Accounts", description = "Account management APIs")
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransactionService transactionService;

    public AccountController(AccountService accountService, TransactionService transactionService) {
        this.accountService = accountService;
        this.transactionService = transactionService;
    }

    @Operation(summary = "Create a new simulated bank account")
    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody CreateAccountRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "Retrieve an account by ID")
    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountService.getAccountById(id));
    }

    @Operation(summary = "List accounts with pagination")
    @GetMapping
    public ResponseEntity<Page<AccountResponse>> listAccounts(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(accountService.listAccounts(pageable));
    }

    @Operation(summary = "Get an account's transaction history")
    @GetMapping("/{id}/transactions")
    public ResponseEntity<Page<TransactionResponse>> getAccountTransactions(
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(transactionService.listAccountTransactions(id, pageable));
    }
}
