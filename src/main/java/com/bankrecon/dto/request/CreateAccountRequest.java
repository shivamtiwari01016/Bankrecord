package com.bankrecon.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request DTO for creating a new bank account.
 */
public class CreateAccountRequest {

    @NotBlank(message = "Account holder name is required")
    @Size(max = 150, message = "Holder name must not exceed 150 characters")
    private String holderName;

    @NotNull(message = "Account type is required (SAVINGS, CURRENT, SALARY)")
    private String accountType;

    @Size(max = 3, message = "Currency code must be 3 characters")
    private String currency = "INR";

    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }

    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
