package com.bankrecon.exception;

/**
 * Thrown when a business rule is violated (e.g., insufficient funds, inactive account).
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
