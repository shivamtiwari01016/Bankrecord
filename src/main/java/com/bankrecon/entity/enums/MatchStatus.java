package com.bankrecon.entity.enums;

/**
 * Represents the status of a reconciliation match.
 */
public enum MatchStatus {
    MATCHED,
    MISSING_INTERNAL,
    MISSING_EXTERNAL,
    AMOUNT_MISMATCH,
    TYPE_MISMATCH,
    DUPLICATE_EXTERNAL,
    INVALID_EXTERNAL
}
