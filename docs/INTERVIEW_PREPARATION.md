# BankRecon — Interview Preparation Guide

This guide explains the project in plain language and anticipates the questions a senior
engineer or interviewer is likely to ask. Answers describe **what the code actually does** —
no invented metrics or unimplemented features.

---

## 1. One-paragraph summary

BankRecon is a Spring Boot + MySQL backend for a simulated bank. It exposes REST APIs to
open accounts and post deposits/withdrawals, guarantees balance consistency under concurrent
requests using database row locks, makes transaction posting safe to retry with idempotency
keys, imports external bank statements from CSV, and reconciles those statements against the
internal ledger — classifying every difference (matched, missing on either side, amount or
type mismatch, duplicate, invalid) without ever touching balances.

---

## 2. Architecture — "walk me through it"

- **Layered modular monolith:** Controller → Service → Repository → Entity, with Request/
  Response DTOs and a manual mapper. Controllers do HTTP only; services own business rules;
  repositories own queries.
- **Why a monolith:** the domain is small and cohesive. Microservices would add network,
  consistency, and operational overhead for no benefit. The spec explicitly asked for a
  modular monolith.
- **Why DTOs:** entities are never exposed, so the persistence model can change without
  breaking the API, and sensitive/irrelevant fields (like internal ids) stay internal.
- **Why manual mapping:** the project is small; MapStruct would be an extra dependency and
  build step for little gain. `EntityMapper` is explicit and readable.

Follow-up: *"What would you change at scale?"* → Extract reconciliation into a separate
worker if imports become large, add read replicas for reporting, and add authentication.

---

## 3. Database design — "explain your schema"

- Seven tables: `accounts`, `transactions`, `idempotency_records`, `statement_imports`,
  `external_statement_entries`, `reconciliation_runs`, `reconciliation_items`.
- **Normalization:** account attributes live once; transactions reference accounts by FK.
  Statement rows and reconciliation results are separate so re-runs don't destroy history.
- **Controlled denormalization:** `transactions.balance_after` stores the post-transaction
  balance. It is derivable, but storing it makes statements cheap and gives an audit value.
  It is safe because it is written in the same locked transaction as the account balance.
- **Constraints:** PKs, FKs, UNIQUE on account number / transaction ref / idempotency key,
  NOT NULL everywhere essential, CHECK constraints on enums and `balance >= 0`,
  `amount > 0`.
- **Indexes:** on `account_id`, `transaction_type`, `status`, `created_at`, and the composite
  `(account_id, created_at)` for account history.
- **Flyway** owns the schema; Hibernate only validates (`ddl-auto: validate`). This prevents
  accidental schema drift and makes migrations reviewable.
- **Money:** `DECIMAL(19,4)` + `BigDecimal` — never `double`/`float`.

---

## 4. ACID, transactions and locking

**ACID in this project:**
- **Atomicity:** a deposit updates the balance and inserts a transaction in one unit; a
  failure rolls back both.
- **Consistency:** business rules + DB checks (e.g., `balance >= 0`) keep data valid.
- **Isolation:** concurrent withdrawals are serialized per account by a row lock.
- **Durability:** committed MySQL writes persist.

**The concurrency problem:** two withdrawals each read a balance of 100, each pass the
"sufficient funds" check, each subtract 100 → balance = -100. Classic lost update.

**The fix — pessimistic write locking:** `AccountRepository.findByIdWithLock` uses
`@Lock(LockModeType.PESSIMISTIC_WRITE)`, which issues `SELECT … FOR UPDATE`. The second
thread blocks until the first commits, then re-reads the updated balance. Overdraft becomes
impossible.

**Trade-off vs optimistic locking:**
| | Pessimistic (chosen) | Optimistic |
|---|---|---|
| Correctness | Guaranteed | Guaranteed with retries |
| Throughput | Lower under contention (blocking) | Higher when contention is low |
| Complexity | Simple | Needs a version column + retry loop |
| Risk | Lock waits / timeouts | Retry storms, starvation under load |

For banking, correctness and simplicity win; per-account contention is usually low (one
user acting at a time). The concurrency integration test proves the invariant.

**Isolation level:** MySQL InnoDB default REPEATABLE READ; `SELECT … FOR UPDATE` locks the
specific account row, so unrelated accounts are unaffected.

---

## 5. Idempotency — "what happens if a client retries?"

A network timeout can leave the client unsure whether a deposit happened. Without protection,
a retry could double-charge.

Mechanism:
1. Client sends a unique `Idempotency-Key` header.
2. The server hashes the request (account, type, amount, description) with SHA-256.
3. It looks up the key in `idempotency_records`:
   - **exists, same hash** → return the stored response, create nothing (safe retry);
   - **exists, different hash** → `409 Conflict` (the key was reused for a different op);
   - **absent** → process normally, then persist key + hash + serialized response.
4. The record is written in the same transaction as the transaction itself.

`idempotency_key` has a UNIQUE constraint, so even a race cannot create two records.

---

## 6. Reconciliation — "explain the matching rules"

Two-step workflow, kept separate from posting:

1. **Import** (`StatementImportService`): stream the CSV line-by-line, validate the header
   and each field, store every row with a status (`ACCEPTED`/`REJECTED`/`DUPLICATE`) and a
   rejection reason. Nothing is silently discarded. Importing never changes balances.
2. **Reconcile** (`ReconciliationService`): compare accepted external entries to internal
   transactions, keyed on `transaction_ref`.

Precedence (first match wins):
1. `INVALID_EXTERNAL` — external row was rejected at import.
2. `DUPLICATE_EXTERNAL` — repeated external reference.
3. `MISSING_INTERNAL` — external ref with no internal transaction.
4. `TYPE_MISMATCH` — refs match, types differ.
5. `AMOUNT_MISMATCH` — refs match, amounts differ.
6. `MATCHED` — refs match, type and amount identical.
7. `MISSING_EXTERNAL` — internal transaction with no external entry.

Determinism: given the same internal data and the same CSV, the classification is identical
every time. Results and reasons are persisted in `reconciliation_items`.

**Why not auto-correct balances?** Reconciliation is a reporting/data-quality exercise.
Silently adjusting balances based on an untrusted file would be dangerous; the system only
reports discrepancies for a human to investigate.

---

## 7. Error handling

A single `@RestControllerAdvice` maps typed exceptions to status codes, so every error has
the same JSON shape: `ResourceNotFoundException` → 404, `BusinessRuleException` → 422,
`DuplicateResourceException` / `IdempotencyConflictException` → 409, validation → 400 with
field errors, fallback → 500 with a safe message (internals never leak). Logging avoids
sensitive values.

---

## 8. SQL / reporting

Eight analytical queries in `sql/analytics.sql` cover: deposit/withdrawal totals by date,
daily summaries, counts by status, top accounts (JOIN + GROUP BY + ORDER BY + LIMIT),
failed/rejected transactions, duplicate-ref detection (`GROUP BY … HAVING COUNT(*) > 1`),
reconciliation discrepancies (JOIN across runs/items), and ordered account history. A tenth
query demonstrates a correlated subquery for last activity per account.

These are also exposed through `/api/v1/reports/*` via native JPA queries.

---

## 9. Testing strategy

- **Unit tests (JUnit 5 + Mockito):** account creation/validation, deposits, withdrawals,
  insufficient funds, zero/negative amounts, missing/inactive account, duplicate ref,
  idempotent retry, conflicting key, CSV validation/duplicates, and all reconciliation
  classifications.
- **Integration tests (`@SpringBootTest` + H2 in MySQL mode):** real JPA stack — balance
  consistency, rollback on failure, idempotency end-to-end, **concurrent withdrawals**, CSV
  import + reconciliation, and report queries.
- **Testcontainers:** the dependency-free design supports a real MySQL container, but Docker
  is unavailable in this environment (CLI present, daemon not running), so the H2 suite is
  the documented local alternative. `application-test.yml` documents the setup.
- **Honesty:** test results are reported exactly as run: **28 tests, 0 failures, 0 errors.**

---

## 10. Trade-offs I can defend

- **Pessimistic vs optimistic locking** — see §4.
- **BigDecimal vs double** — correctness for money; small performance cost, worth it.
- **Cached balance vs recompute** — O(1) reads; must be updated under lock (we do).
- **`balance_after` denormalization** — cheap statements/audit vs derivable data; kept
  consistent by transactional writes.
- **Manual mapper vs MapStruct** — less magic, fewer dependencies for a small project.
- **Monolith vs microservices** — simplicity and local transactions vs distribution.

---

## 11. Likely questions & short answers

- **Why `SELECT … FOR UPDATE` and not just a DB check constraint?** The constraint stops a
  negative balance at write time but wouldn't prevent two reads passing the funds check
  before either writes. The lock prevents the race.
- **What if two identical requests race with the same idempotency key?** The UNIQUE
  constraint on `idempotency_key` lets exactly one insert succeed; the other fails the
  insert and the request is retried/returns the stored response.
- **Why store rejected/duplicate CSV rows?** Data quality and auditability — the spec forbids
  silently discarding rows; users need to see *why* a row failed.
- **How do you prevent reconciliation from altering balances?** The reconciliation service
  only reads transactions and writes reconciliation tables; it never touches `accounts`.
- **How would you add authentication?** Spring Security with JWT, roles for teller vs admin,
  and audit logging — noted as an explicit non-goal of the educational version.

---

## 12. Project summary (for a CV or README)

> BankRecon — a Spring Boot 3 / Java 21 / MySQL 8 banking transaction and reconciliation
> backend. Features account and transaction APIs with `BigDecimal` money handling, atomic
> balance updates under pessimistic row locking, header-based idempotency for safe retries,
> a streaming CSV statement importer with validation and duplicate detection, an engine that
> classifies seven reconciliation discrepancy types, and SQL reporting endpoints. Schema is
> managed by Flyway; a global exception handler yields consistent API errors; and the
> project ships unit, integration, and concurrency tests.

## 13. Resume bullet suggestions (only defensible claims)

- Built a layered Spring Boot REST backend (account and transaction services) with DTOs,
  Bean Validation, pagination, and centralized exception handling.
- Guaranteed balance consistency under concurrent withdrawals using pessimistic database
  row locking, verified with a multi-threaded integration test.
- Implemented request idempotency with SHA-256 request hashing and a UNIQUE key, preventing
  duplicate deposits/withdrawals on client retries.
- Designed a 7-table MySQL schema with Flyway migrations, foreign keys, check constraints,
  and composite indexes for reporting.
- Implemented a streaming CSV import and reconciliation engine classifying matched, missing,
  mismatched, duplicate, and invalid records without mutating balances.
- Wrote JUnit 5/Mockito unit tests and Spring Boot integration tests (28 tests, all passing).

*(Do not add percentages, latency numbers, or "millions of records" claims — none were
measured.)*
