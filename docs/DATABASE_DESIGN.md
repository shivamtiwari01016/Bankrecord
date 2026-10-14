# BankRecon — Database Design

The schema is owned by **Flyway** migrations in `src/main/resources/db/migration`.
Hibernate runs with `ddl-auto: validate`, so the application never mutates the schema at
runtime — Flyway is the single source of truth.

## 1. Tables

### `accounts`
Simulated bank accounts. Balance is cached on the row and protected by a check constraint
so it can never go negative at the database level.

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK, auto-increment |
| `account_number` | VARCHAR(20) | UNIQUE, NOT NULL |
| `holder_name` | VARCHAR(150) | NOT NULL |
| `account_type` | VARCHAR(20) | NOT NULL, CHECK in (SAVINGS, CURRENT, SALARY) |
| `currency` | VARCHAR(3) | NOT NULL, default `INR` |
| `status` | VARCHAR(20) | NOT NULL, CHECK in (ACTIVE, INACTIVE, CLOSED) |
| `balance` | DECIMAL(19,4) | NOT NULL, CHECK `>= 0` |
| `created_at` | TIMESTAMP | NOT NULL, default now |
| `updated_at` | TIMESTAMP | NOT NULL, auto-updated |

Indexes: `idx_accounts_status`, `idx_accounts_holder`.

### `transactions`
Immutable ledger of deposits and withdrawals.

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK, auto-increment |
| `transaction_ref` | VARCHAR(50) | UNIQUE, NOT NULL |
| `account_id` | BIGINT | NOT NULL, FK → accounts(id) |
| `transaction_type` | VARCHAR(20) | NOT NULL, CHECK in (DEPOSIT, WITHDRAWAL) |
| `amount` | DECIMAL(19,4) | NOT NULL, CHECK `> 0` |
| `status` | VARCHAR(20) | NOT NULL, CHECK in (COMPLETED, FAILED, REJECTED) |
| `description` | VARCHAR(500) | NULL |
| `balance_after` | DECIMAL(19,4) | NOT NULL |
| `created_at` | TIMESTAMP | NOT NULL |

Indexes: `idx_txn_account`, `idx_txn_type`, `idx_txn_status`, `idx_txn_created`, and the
composite `idx_txn_account_created(account_id, created_at)` for account history queries.

### `idempotency_records`
Stores idempotency keys so retries cannot repeat a financial operation.

| Column | Type | Constraints |
|--------|------|-------------|
| `id` | BIGINT | PK, auto-increment |
| `idempotency_key` | VARCHAR(100) | UNIQUE, NOT NULL |
| `transaction_id` | BIGINT | NULL, FK → transactions(id) |
| `request_hash` | VARCHAR(64) | NOT NULL (SHA-256 of the request) |
| `response_status` | INT | NOT NULL |
| `response_body` | TEXT | NULL (serialized JSON response) |
| `created_at` | TIMESTAMP | NOT NULL |

### `statement_imports`
Metadata for each CSV upload.

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | PK |
| `file_name` | VARCHAR(255) | NOT NULL |
| `status` | VARCHAR(20) | PROCESSING / COMPLETED / FAILED |
| `total_rows`, `accepted_rows`, `rejected_rows`, `duplicate_rows` | INT | counters |
| `created_at`, `completed_at` | TIMESTAMP | timing |

### `external_statement_entries`
One parsed row per CSV data line. Invalid and duplicate rows are retained with a reason.

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | PK |
| `import_id` | BIGINT | FK → statement_imports(id) |
| `external_ref` | VARCHAR(50) | Nullable (a missing ref is a rejection) |
| `account_number` | VARCHAR(20) | Nullable on malformed rows |
| `transaction_type` | VARCHAR(20) | Nullable on malformed rows |
| `amount` | DECIMAL(19,4) | Nullable on malformed rows |
| `transaction_timestamp` | TIMESTAMP | Nullable on malformed rows |
| `source_row_number` | INT | Original CSV line number (named to avoid the MySQL 8 reserved word `ROW_NUMBER`) |
| `status` | VARCHAR(20) | ACCEPTED / REJECTED / DUPLICATE |
| `rejection_reason` | VARCHAR(500) | Reason for rejection/duplication |

Indexes: `idx_entry_import`, `idx_entry_ext_ref`, `idx_entry_account`.

### `reconciliation_runs`
One row per reconciliation execution with category counters.

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | PK |
| `import_id` | BIGINT | FK → statement_imports(id) |
| `status` | VARCHAR(20) | IN_PROGRESS / COMPLETED / FAILED |
| `total_items`, `matched`, `missing_internal`, `missing_external`, `amount_mismatch`, `type_mismatch`, `duplicate_external`, `invalid_external` | INT | counters |
| `started_at`, `completed_at` | TIMESTAMP | timing |

### `reconciliation_items`
One row per compared record (matched or discrepant), preserving the reason.

| Column | Type | Notes |
|--------|------|-------|
| `id` | BIGINT | PK |
| `run_id` | BIGINT | FK → reconciliation_runs(id) |
| `external_entry_id` | BIGINT | FK → external_statement_entries(id), nullable |
| `internal_transaction_id` | BIGINT | FK → transactions(id), nullable |
| `match_status` | VARCHAR(30) | One of the 7 statuses |
| `external_ref`, `external_type`, `internal_type` | VARCHAR | comparison fields |
| `external_amount`, `internal_amount` | DECIMAL(19,4) | comparison fields |
| `discrepancy_detail` | VARCHAR(500) | Human-readable reason |

Indexes: `idx_recon_item_run`, `idx_recon_item_status`.

## 2. Normalization

The schema is in **third normal form** for the core ledger:

- Account attributes are stored once in `accounts`; transactions reference the account by
  foreign key rather than repeating holder/type/currency data.
- Statement rows are stored independently of reconciliation results, so an import can be
  re-reconciled without re-uploading, and history is preserved.
- Reconciliation results are separated from reconciliation run metadata, so a run's
  summary counters can be read without aggregating potentially many item rows.

`balance_after` on `transactions` is a deliberate, controlled denormalization: storing the
post-transaction balance makes account-statement generation cheap and provides an audit
value, at the cost of being derivable data. It is only ever written inside the same locked
transaction that updates `accounts.balance`, so the two never disagree.

## 3. Constraints and integrity

- **Primary keys** on every table.
- **Foreign keys** from transactions/entries/items back to their parents.
- **Unique constraints** on `account_number`, `transaction_ref`, and `idempotency_key`.
- **NOT NULL** on all essential columns.
- **CHECK constraints** for enumerated columns and `balance >= 0` / `amount > 0`.
- **Indexes** on frequent filters: account, type, status, created_at, and external ref.

## 4. Transaction boundaries

| Operation | Boundary |
|-----------|----------|
| Create account | Single transaction (`AccountService.createAccount`). |
| Deposit / withdrawal | Single transaction (`TransactionService`); locks the account row, updates balance, inserts transaction, and writes the idempotency record atomically. |
| CSV import | Single transaction per import (`StatementImportService`), batched inserts every 100 rows. |
| Reconciliation | Single transaction (`ReconciliationService.runReconciliation`): creates the run, computes items, saves them, and updates counters. |

Because the balance mutation and the ledger insert share one transaction, a failure at any
step rolls back both — the balance can never be changed without a corresponding transaction
row, and vice-versa.

## 5. Money precision

All amounts are `DECIMAL(19,4)` (up to 19 digits, 4 decimal places), mapped to
`BigDecimal` in Java. This avoids binary floating-point rounding errors and supports up to
four fractional currency units (e.g., paise).

## 6. Why check constraints *and* service validation?

Check constraints are the last line of defence at the storage layer; service validation
produces helpful, typed errors before a statement is even sent. Keeping both means bad data
cannot enter through any path (including a future script or a manual SQL load), while API
clients still receive meaningful messages.
