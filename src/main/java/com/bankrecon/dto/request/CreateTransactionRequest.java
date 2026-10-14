package com.bankrecon.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Request DTO for creating a transaction (deposit or withdrawal).
 */
public class CreateTransactionRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /**
     * Optional unique reference. If not provided, one will be auto-generated.
     */
    @Size(max = 50, message = "Transaction reference must not exceed 50 characters")
    private String transactionRef;

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTransactionRef() { return transactionRef; }
    public void setTransactionRef(String transactionRef) { this.transactionRef = transactionRef; }
}
