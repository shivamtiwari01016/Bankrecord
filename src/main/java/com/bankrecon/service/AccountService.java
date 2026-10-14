package com.bankrecon.service;

import com.bankrecon.dto.request.CreateAccountRequest;
import com.bankrecon.dto.response.AccountResponse;
import com.bankrecon.entity.Account;
import com.bankrecon.entity.enums.AccountStatus;
import com.bankrecon.entity.enums.AccountType;
import com.bankrecon.exception.DuplicateResourceException;
import com.bankrecon.exception.ResourceNotFoundException;
import com.bankrecon.mapper.EntityMapper;
import com.bankrecon.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final EntityMapper mapper;

    public AccountService(AccountRepository accountRepository, EntityMapper mapper) {
        this.accountRepository = accountRepository;
        this.mapper = mapper;
    }

    @Transactional
    public AccountResponse createAccount(CreateAccountRequest request) {
        AccountType type;
        try {
            type = AccountType.valueOf(request.getAccountType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid account type: " + request.getAccountType()
                    + ". Must be one of: SAVINGS, CURRENT, SALARY");
        }

        // Generate unique account number
        String accountNumber = generateAccountNumber();

        Account account = new Account();
        account.setAccountNumber(accountNumber);
        account.setHolderName(request.getHolderName());
        account.setAccountType(type);
        account.setCurrency(request.getCurrency() != null ? request.getCurrency().toUpperCase() : "INR");
        account.setStatus(AccountStatus.ACTIVE);
        account.setBalance(BigDecimal.ZERO);

        Account saved = accountRepository.save(account);
        log.info("Account created: id={}, number={}", saved.getId(), saved.getAccountNumber());
        return mapper.toAccountResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with id: " + id));
        return mapper.toAccountResponse(account);
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> listAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable).map(mapper::toAccountResponse);
    }

    /**
     * Generates a unique account number in format ACC-XXXXXX.
     * Uses a combination of timestamp and random to minimize collisions.
     */
    private String generateAccountNumber() {
        String candidate;
        int attempts = 0;
        do {
            candidate = "ACC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            attempts++;
            if (attempts > 10) {
                throw new RuntimeException("Failed to generate unique account number after 10 attempts");
            }
        } while (accountRepository.existsByAccountNumber(candidate));
        return candidate;
    }
}
