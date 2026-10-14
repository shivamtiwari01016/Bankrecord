-- ================================================================
-- BankRecon — Analytical SQL Queries
-- ================================================================
-- These queries are designed for MySQL 8.x and operate on the
-- BankRecon schema. Each query serves a specific reporting purpose.
-- ================================================================

-- ----------------------------------------------------------------
-- 1. Total Deposits and Withdrawals for a Date Range
-- ----------------------------------------------------------------
-- Purpose: Shows the aggregate inflow (deposits) and outflow (withdrawals)
-- for completed transactions within the specified date range.
-- Output: One row per transaction type with count and total amount.
-- ----------------------------------------------------------------
SELECT
    t.transaction_type,
    COUNT(*)            AS transaction_count,
    SUM(t.amount)       AS total_amount
FROM transactions t
WHERE t.status = 'COMPLETED'
  AND t.created_at BETWEEN '2026-10-01 00:00:00' AND '2026-10-31 23:59:59'
GROUP BY t.transaction_type;

-- ----------------------------------------------------------------
-- 2. Daily Transaction Counts and Amounts
-- ----------------------------------------------------------------
-- Purpose: Provides a day-by-day breakdown of transaction volume and value.
-- Useful for spotting trends, peaks, and anomalies.
-- Output: One row per day with transaction count and total amount.
-- ----------------------------------------------------------------
SELECT
    DATE(t.created_at)  AS txn_date,
    COUNT(*)            AS transaction_count,
    SUM(t.amount)       AS total_amount
FROM transactions t
WHERE t.created_at BETWEEN '2026-10-01 00:00:00' AND '2026-10-31 23:59:59'
GROUP BY DATE(t.created_at)
ORDER BY txn_date;

-- ----------------------------------------------------------------
-- 3. Transaction Counts Grouped by Status
-- ----------------------------------------------------------------
-- Purpose: Shows how many transactions are in each status (COMPLETED,
-- FAILED, REJECTED). Helps monitor system health and failure rates.
-- Output: One row per status with the count.
-- ----------------------------------------------------------------
SELECT
    t.status,
    COUNT(*) AS transaction_count
FROM transactions t
GROUP BY t.status
ORDER BY transaction_count DESC;

-- ----------------------------------------------------------------
-- 4. Accounts with the Highest Transaction Volume
-- ----------------------------------------------------------------
-- Purpose: Identifies the most active accounts by number of transactions.
-- Useful for identifying power users or accounts needing monitoring.
-- Output: Top 10 accounts with their transaction count and total amount.
-- Uses a JOIN to bring in account details.
-- ----------------------------------------------------------------
SELECT
    a.account_number,
    a.holder_name,
    COUNT(t.id)         AS transaction_count,
    SUM(t.amount)       AS total_amount
FROM transactions t
    JOIN accounts a ON t.account_id = a.id
GROUP BY a.id, a.account_number, a.holder_name
ORDER BY transaction_count DESC
LIMIT 10;

-- ----------------------------------------------------------------
-- 5. Failed or Rejected Transactions
-- ----------------------------------------------------------------
-- Purpose: Lists all transactions that did not complete successfully.
-- Critical for investigating errors, system issues, or suspicious activity.
-- Output: Full transaction details for failed/rejected transactions.
-- ----------------------------------------------------------------
SELECT
    t.id,
    t.transaction_ref,
    a.account_number,
    t.transaction_type,
    t.amount,
    t.status,
    t.description,
    t.created_at
FROM transactions t
    JOIN accounts a ON t.account_id = a.id
WHERE t.status IN ('FAILED', 'REJECTED')
ORDER BY t.created_at DESC;

-- ----------------------------------------------------------------
-- 6. Duplicate Transaction Reference Detection
-- ----------------------------------------------------------------
-- Purpose: Data quality check — finds transaction references that appear
-- more than once. In a correct system, the UNIQUE constraint prevents this,
-- but this query can validate data integrity.
-- Output: Transaction refs that appear more than once, with their count.
-- HAVING clause filters groups to only those with count > 1.
-- ----------------------------------------------------------------
SELECT
    t.transaction_ref,
    COUNT(*) AS occurrence_count
FROM transactions t
GROUP BY t.transaction_ref
HAVING COUNT(*) > 1;

-- ----------------------------------------------------------------
-- 7. Reconciliation Discrepancies
-- ----------------------------------------------------------------
-- Purpose: Lists all non-matched reconciliation items from a given run,
-- showing the nature of each discrepancy.
-- Output: One row per discrepancy with external and internal details.
-- Uses a subquery to filter by the latest reconciliation run.
-- ----------------------------------------------------------------
SELECT
    ri.id,
    ri.match_status,
    ri.external_ref,
    ri.external_amount,
    ri.internal_amount,
    ri.external_type,
    ri.internal_type,
    ri.discrepancy_detail,
    rr.id AS run_id,
    rr.started_at
FROM reconciliation_items ri
    JOIN reconciliation_runs rr ON ri.run_id = rr.id
WHERE ri.match_status != 'MATCHED'
ORDER BY ri.match_status, ri.id;

-- ----------------------------------------------------------------
-- 8. Account Transaction History Ordered by Timestamp
-- ----------------------------------------------------------------
-- Purpose: Shows the full transaction history for a specific account
-- in chronological order, including running balance after each transaction.
-- Output: Full transaction details with balance progression.
-- ----------------------------------------------------------------
SELECT
    t.id,
    t.transaction_ref,
    t.transaction_type,
    t.amount,
    t.balance_after,
    t.status,
    t.description,
    t.created_at
FROM transactions t
    JOIN accounts a ON t.account_id = a.id
WHERE a.account_number = 'ACC-1001'
ORDER BY t.created_at ASC;

-- ----------------------------------------------------------------
-- 9. Reconciliation Summary by Run
-- ----------------------------------------------------------------
-- Purpose: Provides an overview of all reconciliation runs with their
-- match/mismatch counts. Useful for tracking reconciliation history.
-- Output: One row per run with all category counts.
-- ----------------------------------------------------------------
SELECT
    rr.id AS run_id,
    si.file_name,
    rr.status,
    rr.total_items,
    rr.matched,
    rr.missing_internal,
    rr.missing_external,
    rr.amount_mismatch,
    rr.type_mismatch,
    rr.duplicate_external,
    rr.invalid_external,
    rr.started_at,
    rr.completed_at
FROM reconciliation_runs rr
    JOIN statement_imports si ON rr.import_id = si.id
ORDER BY rr.started_at DESC;

-- ----------------------------------------------------------------
-- 10. Account Balance Summary with Last Transaction Date
-- ----------------------------------------------------------------
-- Purpose: Shows current state of all accounts with their last activity.
-- Uses a correlated subquery to find the most recent transaction date.
-- ----------------------------------------------------------------
SELECT
    a.account_number,
    a.holder_name,
    a.account_type,
    a.status,
    a.balance,
    (SELECT MAX(t.created_at) FROM transactions t WHERE t.account_id = a.id) AS last_transaction_date,
    (SELECT COUNT(*) FROM transactions t WHERE t.account_id = a.id) AS total_transactions
FROM accounts a
ORDER BY a.balance DESC;
