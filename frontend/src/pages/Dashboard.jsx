import React, { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, formatMoney, formatDateTime } from '../api.js'
import { Card, StatCard, Spinner, Alert, DataTable, Badge } from '../components/UI.jsx'

const MIN = '2000-01-01T00:00:00'
const MAX = '2100-01-01T00:00:00'

export default function Dashboard() {
  const [state, setState] = useState({ loading: true, error: null })
  const [data, setData] = useState({})

  useEffect(() => {
    let active = true
    ;(async () => {
      try {
        const [health, statuses, dw, accounts, txs] = await Promise.all([
          api.health().catch(() => ({ status: 'DOWN' })),
          api.statusSummary().catch(() => ({})),
          api.depositWithdrawalSummary(MIN, MAX).catch(() => null),
          api.listAccounts({ page: 0, size: 5 }).catch(() => null),
          api.listTransactions({ page: 0, size: 5 }).catch(() => null)
        ])
        if (!active) return
        setData({ health, statuses, dw, accounts, txs })
        setState({ loading: false, error: null })
      } catch (e) {
        if (active) setState({ loading: false, error: e.message })
      }
    })()
    return () => {
      active = false
    }
  }, [])

  if (state.loading) return <Spinner label="Loading dashboard…" />

  const { health, statuses, dw, accounts, txs } = data
  const totalTx = Object.values(statuses || {}).reduce((a, b) => a + Number(b || 0), 0)

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Dashboard</h1>
          <p className="muted">Live overview of accounts, transactions and reconciliation health.</p>
        </div>
        <Badge tone={health?.status === 'UP' ? 'completed' : 'failed'}>
          API {health?.status || 'UNKNOWN'}
        </Badge>
      </header>

      <Alert kind="error" onClose={() => setState((s) => ({ ...s, error: null }))}>
        {state.error}
      </Alert>

      <div className="stats-grid">
        <StatCard label="Accounts" value={accounts?.totalElements ?? '—'} tone="primary" />
        <StatCard label="Transactions" value={totalTx || '—'} tone="info" />
        <StatCard
          label="Completed"
          value={statuses?.COMPLETED ?? 0}
          tone="success"
          hint="Successful transactions"
        />
        <StatCard
          label="Failed / Rejected"
          value={(Number(statuses?.FAILED || 0) + Number(statuses?.REJECTED || 0)) || 0}
          tone="danger"
        />
        <StatCard
          label="Total Deposits"
          value={dw ? formatMoney(dw.totalDeposits) : '—'}
          tone="success"
        />
        <StatCard
          label="Total Withdrawals"
          value={dw ? formatMoney(dw.totalWithdrawals) : '—'}
          tone="warning"
        />
        <StatCard label="Net Flow" value={dw ? formatMoney(dw.netFlow) : '—'} tone="primary" />
      </div>

      <div className="two-col">
        <Card
          title="Recent Accounts"
          actions={
            <Link className="btn btn-ghost" to="/accounts">
              View all
            </Link>
          }
        >
          <DataTable
            columns={['Account No.', 'Holder', 'Type', 'Balance']}
            rows={accounts?.content}
            empty="No accounts found."
            renderRow={(a) => (
              <tr key={a.id}>
                <td>
                  <Link to={`/accounts/${a.id}`} className="link">
                    {a.accountNumber}
                  </Link>
                </td>
                <td>{a.holderName}</td>
                <td>
                  <Badge>{a.accountType}</Badge>
                </td>
                <td className="num">{formatMoney(a.balance, a.currency)}</td>
              </tr>
            )}
          />
        </Card>

        <Card
          title="Recent Transactions"
          actions={
            <Link className="btn btn-ghost" to="/transactions">
              View all
            </Link>
          }
        >
          <DataTable
            columns={['Ref', 'Type', 'Amount', 'Status']}
            rows={txs?.content}
            empty="No transactions yet."
            renderRow={(t) => (
              <tr key={t.id}>
                <td className="mono">{t.transactionRef}</td>
                <td>
                  <Badge tone={t.transactionType === 'DEPOSIT' ? 'completed' : 'warning'}>
                    {t.transactionType}
                  </Badge>
                </td>
                <td className="num">{formatMoney(t.amount)}</td>
                <td>
                  <Badge>{t.status}</Badge>
                </td>
              </tr>
            )}
          />
        </Card>
      </div>

      <p className="muted small">
        Generated {formatDateTime(new Date())}. Data is read live from the BankRecon REST API.
      </p>
    </div>
  )
}
