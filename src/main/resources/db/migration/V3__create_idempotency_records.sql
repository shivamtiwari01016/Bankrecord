-- V3: Create idempotency_records table
-- Stores idempotency keys to prevent duplicate financial operations.
-- When a client retries a request with the same idempotency key,
-- the system returns the original response instead of creating a new transaction.
CREATE TABLE idempotency_records (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    idempotency_key     VARCHAR(100)    NOT NULL,
    transaction_id      BIGINT          NULL COMMENT 'FK to the transaction created by this request',
    request_hash        VARCHAR(64)     NOT NULL COMMENT 'SHA-256 hash of request body to detect conflicting reuse',
    response_status     INT             NOT NULL COMMENT 'HTTP status code of the original response',
    response_body       TEXT            NULL COMMENT 'Serialized JSON of the original response',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_idempotency_transaction FOREIGN KEY (transaction_id) REFERENCES transactions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
