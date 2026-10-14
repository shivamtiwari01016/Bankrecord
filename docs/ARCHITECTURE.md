# BankRecon — Architecture

## 1. Overview

BankRecon is a **modular monolith** built with Spring Boot 3.5 (Java 21 baseline, run on
Java 25). It provides a simulated banking transaction API and a CSV-based reconciliation
engine. It is intentionally **not** a microservice system: there is no Kafka, Redis,
message broker, or frontend. The goal is a correct, understandable backend that a reviewer
can read end-to-end.

The application is split into clean layers so that HTTP concerns, business rules, and
persistence never bleed into one another.

```
┌──────────────────────────────────────────────────────────────┐
│                         HTTP Clients                           │
│            (Postman, Swagger UI, curl, browser)                │
└───────────────────────────────┬────────────────────────────────┘
                                │ JSON / multipart
┌───────────────────────────────▼────────────────────────────────┐
│  Controllers (@RestController)                                 │
│  AccountController  TransactionController                      │
│  ReconciliationController  ReportController                    │
└───────────────────────────────┬────────────────────────────────┘
                                │ DTOs (request / response)
┌───────────────────────────────▼────────────────────────────────┐
│  Services (@Service, @Transactional)                           │
│  AccountService  TransactionService  StatementImportService    │
│  ReconciliationService  ReportService                          │
└───────────────────────────────┬────────────────────────────────┘
                                │ Entities
┌───────────────────────────────▼────────────────────────────────┐
│  Repositories (Spring Data JPA)                                │
│  @Lock(PESSIMISTIC_WRITE), JPQL, native SQL reporting queries  │
└───────────────────────────────┬────────────────────────────────┘
                                │ JDBC (HikariCP)
┌───────────────────────────────▼────────────────────────────────┐
│  MySQL 8  (schema owned by Flyway migrations)                  │
└──────────────────────────────────────────────────────────────┘
```

## 2. Layers and responsibilities

| Layer | Package | Responsibility |
|-------|---------|----------------|
| Controller | `com.bankrecon.controller` | HTTP mapping, status codes, pagination. No business logic. |
| Service | `com.bankrecon.service` | Business rules, transaction boundaries, orchestration. |
| Repository | `com.bankrecon.repository` | Persistence + query definitions. |
| Entity | `com.bankrecon.entity` | JPA mappings of the relational schema. |
| DTO | `com.bankrecon.dto.request` / `.response` | Transport contracts; entities are never exposed. |
| Mapper | `com.bankrecon.mapper` | Manual entity ↔ DTO conversion (`EntityMapper`). |
| Exception | `com.bankrecon.exception` | Typed exceptions + `GlobalExceptionHandler`. |
| Config | `com.bankrecon.config` | OpenAPI configuration. |

**Design rule:** business logic lives only in services. Controllers translate HTTP to
service calls; repositories never contain rules.

## 3. Request lifecycle

1. Request hits a controller, which validates the body with Bean Validation (`@Valid`).
2. The controller calls a service method.
3. The service opens a database transaction (`@Transactional`), applies business rules,
   locks rows when needed, and calls repositories.
4. The service returns a DTO; the mapper builds the response.
5. Exceptions propagate to `GlobalExceptionHandler`, which converts them into a consistent
   `ApiErrorResponse` JSON body with the right HTTP status.

## 4. Transaction processing and consistency

`TransactionService.processTransaction` is the heart of the system:

1. **Idempotency check** — if an `Idempotency-Key` header is present and already stored:
   - same request hash → return the cached response (safe retry);
   - different hash → throw `IdempotencyConflictException` (HTTP 409).
2. **Row lock** — `AccountRepository.findByIdWithLock` issues `SELECT … FOR UPDATE`
   (pessimistic write lock), so only one writer can touch a given account balance at a time.
3. **Business-rule validation** — account must be `ACTIVE`, amount must be `> 0`,
   withdrawal must not exceed the balance.
4. **Atomic mutation** — the balance update and the transaction INSERT happen in the same
   database transaction. If anything fails, the whole unit rolls back.
5. **Idempotency record** — the key, request hash, and serialized response are persisted so
   retries are safe.

### Concurrency strategy trade-off (documented in code)

- **Chosen — pessimistic locking:** simple, guarantees correctness, prevents overdraft.
  Cost: threads block on the row lock, lowering throughput under high contention.
- **Alternative — optimistic locking:** a version column + retry on
  `OptimisticLockException`. Higher throughput under low contention, but retry storms and
  starvation risk under heavy contention.

For banking, correctness and predictability win, and per-account contention is typically
low (a single user acts at a time), so pessimistic locking is the safer default.

## 5. Money arithmetic

All monetary values use `BigDecimal` with `DECIMAL(19,4)` columns. Floating-point types
are never used for money.

## 6. CSV import and reconciliation

The workflow is deliberately split into two independent steps:

```
CSV file ──► StatementImportService ──► statement_imports + external_statement_entries
                                              │
                                              ▼
                          ReconciliationService ──► reconciliation_runs + reconciliation_items
```

- **Import** parses the CSV line-by-line (`BufferedReader`) so large files are not loaded
  into memory, validating headers, required values, numeric amounts, and timestamps.
  Malformed and duplicate rows are stored (never silently dropped) with a rejection reason.
- **Reconciliation** compares accepted external entries with internal transactions and
  classifies each as one of seven statuses (see below). It is read-only with respect to
  balances: importing or reconciling a statement **never** changes an account balance.

### Reconciliation matching rules (precedence order)

1. `INVALID_EXTERNAL` — external row rejected during import.
2. `DUPLICATE_EXTERNAL` — repeated external reference.
3. `MISSING_INTERNAL` — external ref has no matching internal transaction.
4. `TYPE_MISMATCH` — refs match, transaction types differ.
5. `AMOUNT_MISMATCH` — refs match, amounts differ.
6. `MATCHED` — refs match and type + amount are identical.
7. `MISSING_EXTERNAL` — internal transaction has no corresponding external entry.

Matching is deterministic: it keys on `transaction_ref` across the account numbers present
in the import.

## 7. Error handling

`GlobalExceptionHandler` (`@RestControllerAdvice`) centralizes error responses:

| Exception | HTTP status |
|-----------|-------------|
| `ResourceNotFoundException` | 404 Not Found |
| `BusinessRuleException` | 422 Unprocessable Entity |
| `DuplicateResourceException` | 409 Conflict |
| `IdempotencyConflictException` | 409 Conflict |
| `MethodArgumentNotValidException` | 400 Bad Request (with `fieldErrors`) |
| `IllegalArgumentException` | 400 Bad Request |
| `MaxUploadSizeExceededException` | 413 Payload Too Large |
| `Exception` (fallback) | 500 Internal Server Error (safe message) |

## 8. Observability

- **Logging:** SLF4J on `com.bankrecon`; sensitive values are not logged.
- **Actuator:** only `health` and `info` are exposed; health details are hidden.
- **OpenAPI/Swagger UI:** served at `/swagger-ui.html`, spec at `/api-docs`.

## 9. Testing strategy

| Type | Technology | Scope |
|------|-----------|-------|
| Unit | JUnit 5 + Mockito | Service business rules with mocked repositories. |
| Integration | `@SpringBootTest` + H2 (MySQL mode) | Full context: balance consistency, rollback, idempotency, concurrency, CSV + reconciliation, reporting. |

Testcontainers-based MySQL integration tests are provided for but cannot run in this
environment because Docker is unavailable (the CLI is installed but the daemon is not
running). The H2 suite is the documented, practical local alternative.

## 10. Explicit non-goals / limitations

This is an **educational** project using synthetic data. It does **not** implement
production-grade banking security, authentication/authorization, regulatory compliance,
audit trails, or real payment processing. Do not invent security guarantees that the code
does not provide.
