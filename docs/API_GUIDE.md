# BankRecon — API Guide

Base URL: `http://localhost:8080`

Interactive docs: **Swagger UI** → `http://localhost:8080/swagger-ui.html`
OpenAPI JSON → `http://localhost:8080/api-docs`

All request and response bodies are JSON unless noted. Monetary amounts are decimal numbers.
Pagination responses use the Spring `Page` envelope (`content`, `pageable`,
`totalElements`, `totalPages`, etc.).

## Common error format

All errors share one shape (`ApiErrorResponse`):

```json
{
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Insufficient funds. Available balance: 100.0000, requested withdrawal: 500.0000",
  "timestamp": "2026-10-14T22:10:00",
  "path": "/api/v1/accounts/1/transactions/withdrawal",
  "fieldErrors": null
}
```

`fieldErrors` is populated for validation failures (400):

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/v1/accounts",
  "fieldErrors": { "holderName": "Account holder name is required" }
}
```

---

## Accounts

### Create account
`POST /api/v1/accounts`

Request:
```json
{
  "holderName": "Alice Sharma",
  "accountType": "SAVINGS",
  "currency": "INR"
}
```
- `holderName` — required, ≤ 150 chars.
- `accountType` — required, one of `SAVINGS`, `CURRENT`, `SALARY`.
- `currency` — optional, 3 chars, defaults to `INR`.

Response `201 Created`:
```json
{
  "id": 1,
  "accountNumber": "ACC-3F9A12CB",
  "holderName": "Alice Sharma",
  "accountType": "SAVINGS",
  "currency": "INR",
  "status": "ACTIVE",
  "balance": 0.0000,
  "createdAt": "2026-10-14T22:10:00",
  "updatedAt": "2026-10-14T22:10:00"
}
```

### Get account by id
`GET /api/v1/accounts/{id}` → `200 OK` with the account, or `404` if missing.

### List accounts (paged)
`GET /api/v1/accounts?page=0&size=20&sort=createdAt,desc` → `200 OK` with a `Page`.

### Account transaction history
`GET /api/v1/accounts/{id}/transactions?page=0&size=20` → `200 OK` with a `Page` of
transactions for that account, or `404` if the account does not exist.

---

## Transactions

### Create a deposit
`POST /api/v1/accounts/{accountId}/transactions/deposit`

Headers (optional but recommended):
```
Idempotency-Key: <unique-client-key>
```

Request:
```json
{
  "amount": 5000.0000,
  "description": "Salary credit",
  "transactionRef": "TXN-REF-001"
}
```
- `amount` — required, ≥ 0.01.
- `description` — optional, ≤ 500 chars.
- `transactionRef` — optional, ≤ 50 chars. If omitted, one is generated.

Response `201 Created`:
```json
{
  "id": 10,
  "transactionRef": "TXN-REF-001",
  "accountId": 1,
  "accountNumber": "ACC-3F9A12CB",
  "transactionType": "DEPOSIT",
  "amount": 5000.0000,
  "status": "COMPLETED",
  "description": "Salary credit",
  "balanceAfter": 5000.0000,
  "createdAt": "2026-10-14T22:12:00"
}
```

### Create a withdrawal
`POST /api/v1/accounts/{accountId}/transactions/withdrawal`

Same request shape. Business rules:
- account must exist and be `ACTIVE`;
- amount must be `> 0`;
- withdrawal must not exceed the available balance;
- `transactionRef`, if supplied, must be unique.

Responses: `201` on success, `404` (missing account), `422` (business rule violation),
`409` (duplicate ref or idempotency conflict).

### Get transaction by id
`GET /api/v1/transactions/{id}` → `200` / `404`.

### Get transaction by reference
`GET /api/v1/transactions/ref/{ref}` → `200` / `404`.

### List / filter transactions
`GET /api/v1/transactions`

Query parameters (all optional):
| Param | Meaning |
|-------|---------|
| `accountId` | filter by account |
| `type` | `DEPOSIT` or `WITHDRAWAL` |
| `status` | `COMPLETED`, `FAILED`, `REJECTED` |
| `from` | ISO date-time lower bound (inclusive) |
| `to` | ISO date-time upper bound (inclusive) |
| `page`, `size`, `sort` | pagination |

Example: `GET /api/v1/transactions?accountId=1&type=DEPOSIT&from=2026-10-01T00:00:00&to=2026-10-31T23:59:59`

---

## Idempotency

Send a unique `Idempotency-Key` header with deposit/withdrawal requests:

- **Retry with the same key and same body** → the original response is returned; no new
  transaction is created.
- **Reuse the key with a different body** → `409 Conflict` with error
  `Idempotency Conflict`. Each distinct operation must use a distinct key.

The key is compared together with a SHA-256 hash of the request (account, type, amount,
description).

---

## Reconciliation

The workflow is two steps: import a CSV, then run reconciliation against it.

### Import a statement CSV
`POST /api/v1/reconciliation/import`
Content-Type: `multipart/form-data`, field name `file`.

Required CSV columns (exact header):
```
external_ref,account_number,transaction_type,amount,transaction_timestamp
TXN-REF-001,ACC-1001,DEPOSIT,50000.0000,2026-10-01 09:00:00
```

Validation:
- header must match exactly;
- each row must have 5 columns;
- `external_ref` and `account_number` must be non-empty;
- `transaction_type` must be `DEPOSIT` or `WITHDRAWAL`;
- `amount` must be a positive decimal;
- `transaction_timestamp` must be `yyyy-MM-dd HH:mm:ss`.

Malformed rows are stored as `REJECTED` and in-file duplicates as `DUPLICATE` — nothing is
silently dropped.

Response `201 Created`:
```json
{
  "importId": 1,
  "fileName": "sample_statement.csv",
  "status": "COMPLETED",
  "totalRows": 13,
  "acceptedRows": 10,
  "rejectedRows": 2,
  "duplicateRows": 1,
  "createdAt": "2026-10-14T22:15:00",
  "completedAt": "2026-10-14T22:15:01"
}
```

### Run reconciliation
`POST /api/v1/reconciliation/runs`

Request:
```json
{ "importId": 1 }
```

Response `201 Created` (values below correspond to the committed
[`samples/sample_statement.csv`](../samples/sample_statement.csv) reconciled against the V6
sample data):

```json
{
  "id": 1,
  "importId": 1,
  "status": "COMPLETED",
  "totalItems": 13,
  "matched": 7,
  "missingInternal": 1,
  "missingExternal": 0,
  "amountMismatch": 1,
  "typeMismatch": 1,
  "duplicateExternal": 1,
  "invalidExternal": 2,
  "startedAt": "2026-10-14T22:16:00",
  "completedAt": "2026-10-14T22:16:00"
}
```

### Get reconciliation run
`GET /api/v1/reconciliation/runs/{id}` → `200` / `404`.

### Get discrepancies
`GET /api/v1/reconciliation/discrepancies?runId=1&page=0&size=20`

Returns a `Page` of reconciliation items whose `matchStatus` is not `MATCHED`. Omit `runId`
to list discrepancies across all runs.

Item example:
```json
{
  "id": 7,
  "runId": 1,
  "matchStatus": "AMOUNT_MISMATCH",
  "externalRef": "TXN-REF-010",
  "externalAmount": 7500.0000,
  "internalAmount": 5000.0000,
  "externalType": "WITHDRAWAL",
  "internalType": "WITHDRAWAL",
  "discrepancyDetail": "Amount mismatch: external=7500.0000, internal=5000.0000"
}
```

> **Important:** reconciliation is read-only with respect to balances. Importing or
> reconciling a statement never modifies any account balance.

---

## Reports

All report endpoints return `200 OK`.

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/reports/daily-summary?from={iso}&to={iso}` | Daily transaction counts and totals. |
| `GET /api/v1/reports/deposit-withdrawal-summary?from={iso}&to={iso}` | Total deposits, withdrawals, net flow. |
| `GET /api/v1/reports/status-summary` | Transaction counts grouped by status. |
| `GET /api/v1/reports/account-activity?limit=10` | Top accounts by transaction volume. |
| `GET /api/v1/reports/failed-transactions` | Failed/rejected transactions. |
| `GET /api/v1/reports/duplicate-refs` | Duplicate transaction-reference detection (data quality). |

Example — deposit/withdrawal summary:
```json
{
  "dateRange": { "from": "2026-10-01T00:00:00", "to": "2026-10-31T23:59:59" },
  "totalDeposits": 305000.0000,
  "totalWithdrawals": 47000.0000,
  "netFlow": 258000.0000
}
```

---

## Status codes summary

| Code | Meaning |
|------|---------|
| 200 | OK (reads and reports) |
| 201 | Created (account, transaction, import, reconciliation run) |
| 400 | Validation / malformed request |
| 404 | Resource not found |
| 409 | Conflict (duplicate reference or idempotency-key reuse) |
| 413 | Uploaded file too large (max 10 MB) |
| 422 | Business rule violation (e.g., insufficient funds, inactive account) |
| 500 | Unexpected server error |
