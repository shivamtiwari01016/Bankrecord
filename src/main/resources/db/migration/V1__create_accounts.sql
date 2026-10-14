-- V1: Create accounts table
CREATE TABLE accounts (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    account_number  VARCHAR(20)     NOT NULL,
    holder_name     VARCHAR(150)    NOT NULL,
    account_type    VARCHAR(20)     NOT NULL COMMENT 'SAVINGS, CURRENT, SALARY',
    currency        VARCHAR(3)      NOT NULL DEFAULT 'INR',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE, INACTIVE, CLOSED',
    balance         DECIMAL(19,4)   NOT NULL DEFAULT 0.0000,
    created_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_account_number UNIQUE (account_number),
    CONSTRAINT chk_account_type CHECK (account_type IN ('SAVINGS', 'CURRENT', 'SALARY')),
    CONSTRAINT chk_account_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED')),
    CONSTRAINT chk_balance_non_negative CHECK (balance >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_accounts_status ON accounts(status);
CREATE INDEX idx_accounts_holder ON accounts(holder_name);
