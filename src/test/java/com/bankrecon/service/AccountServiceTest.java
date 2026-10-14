package com.bankrecon.service;

import com.bankrecon.dto.request.CreateAccountRequest;
import com.bankrecon.dto.response.AccountResponse;
import com.bankrecon.entity.Account;
import com.bankrecon.entity.enums.AccountStatus;
import com.bankrecon.entity.enums.AccountType;
import com.bankrecon.exception.ResourceNotFoundException;
import com.bankrecon.mapper.EntityMapper;
import com.bankrecon.repository.AccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EntityMapper mapper;

    @InjectMocks
    private AccountService accountService;

    private Account sampleAccount;
    private AccountResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleAccount = new Account();
        sampleAccount.setId(1L);
        sampleAccount.setAccountNumber("ACC-12345678");
        sampleAccount.setHolderName("Test User");
        sampleAccount.setAccountType(AccountType.SAVINGS);
        sampleAccount.setCurrency("INR");
        sampleAccount.setStatus(AccountStatus.ACTIVE);
        sampleAccount.setBalance(BigDecimal.ZERO);
        sampleAccount.setCreatedAt(LocalDateTime.now());
        sampleAccount.setUpdatedAt(LocalDateTime.now());

        sampleResponse = new AccountResponse();
        sampleResponse.setId(1L);
        sampleResponse.setAccountNumber("ACC-12345678");
        sampleResponse.setHolderName("Test User");
        sampleResponse.setAccountType("SAVINGS");
        sampleResponse.setCurrency("INR");
        sampleResponse.setStatus("ACTIVE");
        sampleResponse.setBalance(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Should create account successfully")
    void createAccount_success() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setHolderName("Test User");
        request.setAccountType("SAVINGS");
        request.setCurrency("INR");

        when(accountRepository.existsByAccountNumber(anyString())).thenReturn(false);
        when(accountRepository.save(any(Account.class))).thenReturn(sampleAccount);
        when(mapper.toAccountResponse(any(Account.class))).thenReturn(sampleResponse);

        AccountResponse response = accountService.createAccount(request);

        assertNotNull(response);
        assertEquals("Test User", response.getHolderName());
        assertEquals("SAVINGS", response.getAccountType());
        verify(accountRepository).save(any(Account.class));
    }

    @Test
    @DisplayName("Should reject invalid account type")
    void createAccount_invalidType() {
        CreateAccountRequest request = new CreateAccountRequest();
        request.setHolderName("Test User");
        request.setAccountType("INVALID");

        assertThrows(IllegalArgumentException.class, () -> accountService.createAccount(request));
        verify(accountRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should retrieve account by ID")
    void getAccountById_success() {
        when(accountRepository.findById(1L)).thenReturn(Optional.of(sampleAccount));
        when(mapper.toAccountResponse(sampleAccount)).thenReturn(sampleResponse);

        AccountResponse response = accountService.getAccountById(1L);

        assertNotNull(response);
        assertEquals(1L, response.getId());
    }

    @Test
    @DisplayName("Should throw not found when account does not exist")
    void getAccountById_notFound() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.getAccountById(99L));
    }
}
