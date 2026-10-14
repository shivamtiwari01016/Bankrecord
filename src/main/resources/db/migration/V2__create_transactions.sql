-- V2: Create transactions table
CREATE TABLE transactions (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    transaction_ref     VARCHAR(50)     NOT NULL,
    account_id          BIGINT          NOT NULL,
    transaction_type    VARCHAR(20)     NOT NULL COMMENT 'DEPOSIT, WITHDRAWAL',
    amount              DECIMAL(19,4)   NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'COMPLETED' COMMENT 'COMPLETED, FAILED, REJECTED',
    description         VARCHAR(500)    NULL,
    balance_after       DECIMAL(19,4)   NOT NULL COMMENT 'Account balance after this transaction',
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_transaction_ref UNIQUE (transaction_ref),
    CONSTRAINT fk_transaction_account FOREIGN KEY (account_id) REFERENCES accounts(id),
    CONSTRAINT chk_transaction_type CHECK (transaction_type IN ('DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT chk_transaction_status CHECK (status IN ('COMPLETED', 'FAILED', 'REJECTED')),
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_txn_account ON transactions(account_id);
CREATE INDEX idx_txn_type ON transactions(transaction_type);
CREATE INDEX idx_txn_status ON transactions(status);
CREATE INDEX idx_txn_created ON transactions(created_at);
CREATE INDEX idx_txn_account_created ON transactions(account_id, created_at);
