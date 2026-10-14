# BankRecon — Entity Relationship Diagram

Rendered from the Flyway migrations in `src/main/resources/db/migration`.

```mermaid
erDiagram
    ACCOUNTS ||--o{ TRANSACTIONS : "has"
    ACCOUNTS {
        bigint  id PK
        varchar account_number UK
        varchar holder_name
        varchar account_type
        char    currency
        varchar status
        decimal balance
        timestamp created_at
        timestamp updated_at
    }

    TRANSACTIONS ||--o| IDEMPOTENCY_RECORDS : "referenced by"
    TRANSACTIONS {
        bigint  id PK
        varchar transaction_ref UK
        bigint  account_id FK
        varchar transaction_type
        decimal amount
        varchar status
        varchar description
        decimal balance_after
        timestamp created_at
    }

    IDEMPOTENCY_RECORDS {
        bigint  id PK
        varchar idempotency_key UK
        bigint  transaction_id FK
        varchar request_hash
        int     response_status
        text    response_body
        timestamp created_at
    }

    STATEMENT_IMPORTS ||--o{ EXTERNAL_STATEMENT_ENTRIES : "contains"
    STATEMENT_IMPORTS ||--o{ RECONCILIATION_RUNS : "is reconciled by"
    STATEMENT_IMPORTS {
        bigint  id PK
        varchar file_name
        varchar status
        int     total_rows
        int     accepted_rows
        int     rejected_rows
        int     duplicate_rows
        timestamp created_at
        timestamp completed_at
    }

    EXTERNAL_STATEMENT_ENTRIES {
        bigint  id PK
        bigint  import_id FK
        varchar external_ref
        varchar account_number
        varchar transaction_type
        decimal amount
        timestamp transaction_timestamp
        int     source_row_number
        varchar status
        varchar rejection_reason
        timestamp created_at
    }

    RECONCILIATION_RUNS ||--o{ RECONCILIATION_ITEMS : "produces"
    RECONCILIATION_RUNS {
        bigint  id PK
        bigint  import_id FK
        varchar status
        int     total_items
        int     matched
        int     missing_internal
        int     missing_external
        int     amount_mismatch
        int     type_mismatch
        int     duplicate_external
        int     invalid_external
        timestamp started_at
        timestamp completed_at
    }

    RECONCILIATION_ITEMS {
        bigint  id PK
        bigint  run_id FK
        bigint  external_entry_id FK
        bigint  internal_transaction_id FK
        varchar match_status
        varchar external_ref
        decimal external_amount
        decimal internal_amount
        varchar external_type
        varchar internal_type
        varchar discrepancy_detail
        timestamp created_at
    }

    EXTERNAL_STATEMENT_ENTRIES ||--o{ RECONCILIATION_ITEMS : "compared in"
    TRANSACTIONS ||--o{ RECONCILIATION_ITEMS : "compared in"
```

## Cardinalities

| Relationship | Cardinality | Notes |
|--------------|-------------|-------|
| `accounts` → `transactions` | 1 : N | One account has many transactions. |
| `transactions` → `idempotency_records` | 1 : 0..1 | A transaction may be referenced by one idempotency record. |
| `statement_imports` → `external_statement_entries` | 1 : N | One upload produces many parsed rows. |
| `statement_imports` → `reconciliation_runs` | 1 : N | An import can be reconciled multiple times over time. |
| `reconciliation_runs` → `reconciliation_items` | 1 : N | One run produces one item per compared record. |
| `external_statement_entries` → `reconciliation_items` | 1 : N | Optional link (`external_entry_id` nullable). |
| `transactions` → `reconciliation_items` | 1 : N | Optional link (`internal_transaction_id` nullable). |
