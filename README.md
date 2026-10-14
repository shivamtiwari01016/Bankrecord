# BankRecon — Banking Transaction & Reconciliation System

An educational Spring Boot backend that simulates a bank's transaction processing and
statement reconciliation. It demonstrates clean layered architecture, relational database
design, transactional consistency, idempotency, CSV data import, discrepancy detection, and
SQL reporting.

> **Educational project — synthetic data only.** It does **not** integrate with real banks,
> payment gateways, or customer accounts, and it does **not** implement production-grade
> banking security, authentication, regulatory compliance, or real payment processing.

---

## Table of contents

- [Features](#features)
- [Tech stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Setup and run](#setup-and-run)
- [Configuration](#configuration)
- [API overview](#api-overview)
- [End-to-end walkthrough](#end-to-end-walkthrough)
- [Frontend console](#frontend-console)
- [Project structure](#project-structure)
- [Testing](#testing)
- [Documentation](#documentation)
- [Limitations](#limitations)

---

## Features

- **Account management** — create accounts, fetch by id, list with pagination, view
  transaction history.
- **Transactions** — deposits and withdrawals with `BigDecimal` money handling, atomic
  balance updates, and pessimistic row locking so concurrent withdrawals can never overdraw
  an account.
- **Idempotency** — an `Idempotency-Key` header makes retries safe; conflicting reuse is
  rejected with `409 Conflict`.
- **CSV statement import** — streaming, line-by-line parsing with header/value validation,
  duplicate detection, and an import summary. Rejected rows are preserved with reasons.
- **Reconciliation engine** — classifies `MATCHED`, `MISSING_INTERNAL`, `MISSING_EXTERNAL`,
  `AMOUNT_MISMATCH`, `TYPE_MISMATCH`, `DUPLICATE_EXTERNAL`, and `INVALID_EXTERNAL`. It never
  modifies balances.
- **SQL reports** — date-range summaries, daily summaries, status counts, top accounts,
  failed transactions, and duplicate-reference detection. Executable versions live in
  [`sql/analytics.sql`](sql/analytics.sql).
- **Cross-cutting concerns** — global exception handling, Bean Validation, OpenAPI/Swagger,
  Actuator (`health`, `info` only), and Flyway-managed schema.

---

## Tech stack

| Area | Choice |
|------|--------|
| Language | Java 21 (baseline; builds and runs on Java 25) |
| Framework | Spring Boot 3.5.3 |
| Build | Maven (wrapper included: `mvnw` / `mvnw.cmd`) |
| Database | MySQL 8 |
| Persistence | Spring Data JPA / Hibernate |
| Migrations | Flyway |
| Validation | Jakarta Bean Validation |
| API docs | springdoc-openapi (Swagger UI) |
| Testing | JUnit 5, Mockito, Spring Boot Test, H2 |
| Ops | Spring Boot Actuator |
| Frontend | React 18 + Vite 5 (optional console under [`frontend/`](frontend/)) |

---

## Prerequisites

- **JDK 21 or newer** (`java -version`).
- **MySQL 8** running locally, with a database named `bankrecon` created:
  ```sql
  CREATE DATABASE bankrecon CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
  ```
- Maven is **not required** — the project ships the Maven wrapper.
- **Node.js 18+ and npm** — only needed to run the optional React frontend.

---

## Setup and run

1. **Create the database** (see above).
2. **Set credentials** via environment variables (defaults are `root` / `root`):

   ```powershell
   $env:DB_USERNAME="root"
   $env:DB_PASSWORD="your_password"
   # optional: $env:DB_HOST="localhost"; $env:DB_PORT="3306"; $env:DB_NAME="bankrecon"
   ```

   See [`.env.example`](.env.example). Never commit real credentials.

3. **Build and test**

   ```powershell
   .\mvnw.cmd clean test        # Windows
   ./mvnw clean test            # Linux/macOS
   ```

4. **Run the application**

   ```powershell
   .\mvnw.cmd spring-boot:run
   ```

   The app starts on `http://localhost:8080`. Flyway creates the schema on first startup and
   seeds synthetic sample data (V6 migration).

5. **Explore**

   - Swagger UI: `http://localhost:8080/swagger-ui.html`
   - OpenAPI JSON: `http://localhost:8080/api-docs`
   - Health: `http://localhost:8080/actuator/health`

---

## Configuration

All settings live in [`application.yml`](src/main/resources/application.yml) and are
overridable by environment variables:

| Variable | Default | Purpose |
|----------|---------|---------|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `bankrecon` | Schema name |
| `DB_USERNAME` | `root` | DB user |
| `DB_PASSWORD` | `root` | DB password |

Hibernate runs `ddl-auto: validate` — **Flyway owns the schema**, not Hibernate.

---

## API overview

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/accounts` | Create account |
| GET | `/api/v1/accounts/{id}` | Get account |
| GET | `/api/v1/accounts` | List accounts (paged) |
| GET | `/api/v1/accounts/{id}/transactions` | Account transaction history |
| POST | `/api/v1/accounts/{id}/transactions/deposit` | Deposit |
| POST | `/api/v1/accounts/{id}/transactions/withdrawal` | Withdrawal |
| GET | `/api/v1/transactions/{id}` | Get transaction |
| GET | `/api/v1/transactions/ref/{ref}` | Get transaction by reference |
| GET | `/api/v1/transactions` | List / filter transactions |
| POST | `/api/v1/reconciliation/import` | Import statement CSV |
| POST | `/api/v1/reconciliation/runs` | Run reconciliation |
| GET | `/api/v1/reconciliation/runs/{id}` | Get reconciliation run |
| GET | `/api/v1/reconciliation/discrepancies` | List discrepancies |
| GET | `/api/v1/reports/daily-summary` | Daily summary |
| GET | `/api/v1/reports/deposit-withdrawal-summary` | Deposit/withdrawal totals |
| GET | `/api/v1/reports/status-summary` | Counts by status |
| GET | `/api/v1/reports/account-activity` | Top accounts |
| GET | `/api/v1/reports/failed-transactions` | Failed/rejected transactions |
| GET | `/api/v1/reports/duplicate-refs` | Duplicate refs (data quality) |

Full request/response contracts: [`docs/API_GUIDE.md`](docs/API_GUIDE.md). A ready-to-import
Postman collection is in [`postman/`](postman/).

---

## End-to-end walkthrough

```powershell
# 1. Create an account
curl -X POST http://localhost:8080/api/v1/accounts `
  -H "Content-Type: application/json" `
  -d '{ "holderName": "Alice Sharma", "accountType": "SAVINGS", "currency": "INR" }'

# 2. Deposit 5000 with an idempotency key (repeat safely)
curl -X POST http://localhost:8080/api/v1/accounts/1/transactions/deposit `
  -H "Content-Type: application/json" -H "Idempotency-Key: dep-001" `
  -d '{ "amount": 5000.0000, "description": "Initial deposit" }'

# 3. Import a statement CSV
curl -X POST http://localhost:8080/api/v1/reconciliation/import `
  -F "file=@samples/sample_statement.csv"

# 4. Run reconciliation for import id 1
curl -X POST http://localhost:8080/api/v1/reconciliation/runs `
  -H "Content-Type: application/json" -d '{ "importId": 1 }'

# 5. Inspect discrepancies
curl "http://localhost:8080/api/v1/reconciliation/discrepancies?runId=1"
```

A sample reconciliation result is committed at
[`samples/sample_reconciliation_output.json`](samples/sample_reconciliation_output.json).

---

## Frontend console

A React + Vite single-page console lives in [`frontend/`](frontend/). It talks to the same
REST API and gives a UI for accounts, transactions, CSV import + reconciliation, and reports.

**Prerequisites:** the backend must be running on `http://localhost:8080`.

```powershell
# from the frontend/ directory
npm.cmd install         # first time only (use npm.cmd on Windows PowerShell)
npm.cmd run dev         # dev server on http://localhost:5173
```

Open `http://localhost:5173`. During development Vite proxies `/api` and `/actuator` to the
backend ([`frontend/vite.config.js`](frontend/vite.config.js)), so there is no CORS friction.
The backend also allows `http://localhost:*` origins for direct calls
([`WebConfig.java`](src/main/java/com/bankrecon/config/WebConfig.java)).

**Production build:**

```powershell
npm.cmd run build       # outputs static assets to frontend/dist
```

**Pages**

| Page | What it does |
|------|--------------|
| Dashboard | API health, account/transaction counts, deposit/withdrawal/net-flow totals, recent lists |
| Accounts | Create accounts; browse the paginated account list |
| Account detail | Balance card, deposit/withdraw with an editable `Idempotency-Key`, transaction history |
| Transactions | Lookup by reference; filter by account/type/status/date range |
| Reconciliation | Upload a statement CSV, run reconciliation, inspect discrepancies |
| Reports | Status counts, date-range summaries, daily summary, top accounts, failed txs, duplicate refs |

See [`frontend/README.md`](frontend/README.md) for details.

---

## Project structure

```
Bankreco/
├── pom.xml
├── mvnw / mvnw.cmd / .mvn
├── src/main/java/com/bankrecon/
│   ├── BankReconApplication.java
│   ├── config/          OpenAPI + CORS configuration
│   ├── controller/      Account, Transaction, Reconciliation, Report
│   ├── dto/             request/ and response/ DTOs
│   ├── entity/          JPA entities + enums
│   ├── exception/       typed exceptions + global handler
│   ├── mapper/          EntityMapper
│   ├── repository/      Spring Data JPA repositories
│   └── service/         business logic
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/    V1..V6 Flyway migrations
├── src/test/java/com/bankrecon/
│   ├── service/         unit tests (Mockito)
│   └── integration/     end-to-end tests (H2)
├── sql/analytics.sql    documented analytical queries
├── samples/             sample CSV + sample reconciliation output
├── postman/             Postman collection + environment
├── frontend/            React + Vite console (optional)
│   ├── package.json
│   ├── vite.config.js   dev proxy to :8080
│   └── src/             api client, components, pages, styles
└── docs/                architecture, DB design, ER diagram, API, interview guide
```

---

## Testing

```powershell
.\mvnw.cmd clean test
```

- **Unit tests** (`src/test/java/.../service`): business rules with mocked repositories.
- **Integration tests** (`src/test/java/.../integration`): full Spring context on in-memory
  H2 (MySQL mode) covering balance consistency, rollback, idempotency, concurrent
  withdrawals, CSV import + reconciliation, and report queries.

**Last verified result: 28 tests, 0 failures, 0 errors, 0 skipped.**

> **Testcontainers note:** the project is designed to support a real MySQL Testcontainer for
> integration tests, but Docker must be running. In the environment where this was built the
> Docker daemon was unavailable, so the H2 suite (documented in
> `docs/INTERVIEW_PREPARATION.md`) is used instead.

---

## Documentation

| Document | Contents |
|----------|----------|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Layers, request lifecycle, concurrency, reconciliation flow |
| [`docs/DATABASE_DESIGN.md`](docs/DATABASE_DESIGN.md) | Tables, constraints, indexes, normalization, transaction boundaries |
| [`docs/ER_DIAGRAM.md`](docs/ER_DIAGRAM.md) | Mermaid ER diagram and cardinalities |
| [`docs/API_GUIDE.md`](docs/API_GUIDE.md) | Endpoint contracts, examples, status codes |
| [`docs/INTERVIEW_PREPARATION.md`](docs/INTERVIEW_PREPARATION.md) | Plain-language explanations, ACID, locking, trade-offs |
| [`sql/analytics.sql`](sql/analytics.sql) | Executable reporting queries with purpose notes |

---

## Limitations

- **Synthetic data only** — no real financial information.
- **No authentication/authorization** — not a production security posture.
- **Single currency default (`INR`)**, no FX handling.
- **Reconciliation matches on `transaction_ref`** only; timestamps are captured but not used
  as a matching key.
- **No Docker/Testcontainers run in CI here** because the Docker daemon was unavailable.
- Do not treat any of the above as production-grade banking guarantees — it is an
  educational reference implementation.
