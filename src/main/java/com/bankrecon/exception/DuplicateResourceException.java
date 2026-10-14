package com.bankrecon.exception;

/**
 * Thrown when a duplicate resource is detected (e.g., duplicate account number, transaction ref).
 */
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
