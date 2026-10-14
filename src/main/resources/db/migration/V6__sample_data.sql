-- V6: Insert synthetic sample data for demonstration
-- This is SYNTHETIC data only. No real personal or financial information.

INSERT INTO accounts (account_number, holder_name, account_type, currency, status, balance) VALUES
    ('ACC-1001', 'Alice Sharma',   'SAVINGS', 'INR', 'ACTIVE',   50000.0000),
    ('ACC-1002', 'Bob Patel',      'CURRENT', 'INR', 'ACTIVE',  125000.0000),
    ('ACC-1003', 'Charlie Kumar',  'SALARY',  'INR', 'ACTIVE',   30000.0000),
    ('ACC-1004', 'Diana Gupta',    'SAVINGS', 'INR', 'INACTIVE',  5000.0000),
    ('ACC-1005', 'Eve Reddy',      'CURRENT', 'INR', 'ACTIVE',   75000.0000);

INSERT INTO transactions (transaction_ref, account_id, transaction_type, amount, status, description, balance_after, created_at) VALUES
    ('TXN-REF-001', 1, 'DEPOSIT',    50000.0000, 'COMPLETED', 'Initial deposit',           50000.0000, '2026-10-01 09:00:00'),
    ('TXN-REF-002', 2, 'DEPOSIT',   150000.0000, 'COMPLETED', 'Business revenue deposit', 150000.0000, '2026-10-01 10:00:00'),
    ('TXN-REF-003', 2, 'WITHDRAWAL', 25000.0000, 'COMPLETED', 'Office rent payment',     125000.0000, '2026-10-02 11:00:00'),
    ('TXN-REF-004', 3, 'DEPOSIT',    30000.0000, 'COMPLETED', 'Salary credit Oct 2026',   30000.0000, '2026-10-03 08:30:00'),
    ('TXN-REF-005', 1, 'WITHDRAWAL',  5000.0000, 'COMPLETED', 'ATM withdrawal',           45000.0000, '2026-10-04 14:00:00'),
    ('TXN-REF-006', 1, 'DEPOSIT',     5000.0000, 'COMPLETED', 'Cash deposit',             50000.0000, '2026-10-05 10:15:00'),
    ('TXN-REF-007', 5, 'DEPOSIT',    75000.0000, 'COMPLETED', 'Client payment received',  75000.0000, '2026-10-06 09:45:00'),
    ('TXN-REF-008', 2, 'WITHDRAWAL', 10000.0000, 'FAILED',    'NSF — simulated failure', 125000.0000, '2026-10-07 16:00:00'),
    ('TXN-REF-009', 3, 'WITHDRAWAL',  2000.0000, 'COMPLETED', 'Grocery purchase',         28000.0000, '2026-10-08 12:00:00'),
    ('TXN-REF-010', 5, 'WITHDRAWAL',  5000.0000, 'COMPLETED', 'Utility bill',             70000.0000, '2026-10-09 15:30:00');
