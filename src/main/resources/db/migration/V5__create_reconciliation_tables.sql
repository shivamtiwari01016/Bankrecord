-- V5: Create reconciliation tables
-- reconciliation_runs tracks each reconciliation execution
CREATE TABLE reconciliation_runs (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    import_id               BIGINT          NOT NULL,
    status                  VARCHAR(20)     NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS, COMPLETED, FAILED',
    total_items             INT             NOT NULL DEFAULT 0,
    matched                 INT             NOT NULL DEFAULT 0,
    missing_internal        INT             NOT NULL DEFAULT 0,
    missing_external        INT             NOT NULL DEFAULT 0,
    amount_mismatch         INT             NOT NULL DEFAULT 0,
    type_mismatch           INT             NOT NULL DEFAULT 0,
    duplicate_external      INT             NOT NULL DEFAULT 0,
    invalid_external        INT             NOT NULL DEFAULT 0,
    started_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at            TIMESTAMP       NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recon_import FOREIGN KEY (import_id) REFERENCES statement_imports(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- reconciliation_items stores individual match/mismatch results
CREATE TABLE reconciliation_items (
    id                      BIGINT          NOT NULL AUTO_INCREMENT,
    run_id                  BIGINT          NOT NULL,
    external_entry_id       BIGINT          NULL,
    internal_transaction_id BIGINT          NULL,
    match_status            VARCHAR(30)     NOT NULL COMMENT 'MATCHED, MISSING_INTERNAL, MISSING_EXTERNAL, AMOUNT_MISMATCH, TYPE_MISMATCH, DUPLICATE_EXTERNAL, INVALID_EXTERNAL',
    external_ref            VARCHAR(50)     NULL,
    external_amount         DECIMAL(19,4)   NULL,
    internal_amount         DECIMAL(19,4)   NULL,
    external_type           VARCHAR(20)     NULL,
    internal_type           VARCHAR(20)     NULL,
    discrepancy_detail      VARCHAR(500)    NULL,
    created_at              TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_recon_item_run FOREIGN KEY (run_id) REFERENCES reconciliation_runs(id),
    CONSTRAINT fk_recon_item_entry FOREIGN KEY (external_entry_id) REFERENCES external_statement_entries(id),
    CONSTRAINT fk_recon_item_txn FOREIGN KEY (internal_transaction_id) REFERENCES transactions(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_recon_item_run ON reconciliation_items(run_id);
CREATE INDEX idx_recon_item_status ON reconciliation_items(match_status);
