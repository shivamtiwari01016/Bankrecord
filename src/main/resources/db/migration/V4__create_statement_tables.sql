-- V4: Create statement import tables
-- statement_imports tracks each CSV file upload
CREATE TABLE statement_imports (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    file_name           VARCHAR(255)    NOT NULL,
    status              VARCHAR(20)     NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING, COMPLETED, FAILED',
    total_rows          INT             NOT NULL DEFAULT 0,
    accepted_rows       INT             NOT NULL DEFAULT 0,
    rejected_rows       INT             NOT NULL DEFAULT 0,
    duplicate_rows      INT             NOT NULL DEFAULT 0,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at        TIMESTAMP       NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- external_statement_entries stores individual parsed rows from the CSV
CREATE TABLE external_statement_entries (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    import_id               BIGINT          NOT NULL,
    external_ref            VARCHAR(50)     NULL,
    account_number          VARCHAR(20)     NULL,
    transaction_type        VARCHAR(20)     NULL,
    amount                  DECIMAL(19,4)   NULL,
    transaction_timestamp   TIMESTAMP       NULL,
    source_row_number       INT             NOT NULL COMMENT 'Original row number in the CSV file',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'ACCEPTED' COMMENT 'ACCEPTED, REJECTED, DUPLICATE',
    rejection_reason        VARCHAR(500)    NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_entry_import FOREIGN KEY (import_id) REFERENCES statement_imports(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_entry_import ON external_statement_entries(import_id);
CREATE INDEX idx_entry_ext_ref ON external_statement_entries(external_ref);
CREATE INDEX idx_entry_account ON external_statement_entries(account_number);
