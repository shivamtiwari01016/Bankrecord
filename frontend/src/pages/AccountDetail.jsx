import React, { useCallback, useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { api, formatMoney, formatDateTime } from '../api.js'
import { Card, Field, Alert, DataTable, Pagination, Badge, Spinner } from '../components/UI.jsx'

const PAGE_SIZE = 10
const uuid = () =>
  (crypto.randomUUID && crypto.randomUUID()) ||
  'idem-' + Math.random().toString(36).slice(2) + Date.now()

export default function AccountDetail() {
  const { id } = useParams()
  const [account, setAccount] = useState(null)
  const [txs, setTxs] = useState(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [notice, setNotice] = useState(null)

  const [txForm, setTxForm] = useState({ amount: '', description: '', transactionRef: '' })
  const [idemKey, setIdemKey] = useState(uuid())
  const [busy, setBusy] = useState(false)

  const loadAccount = useCallback(async () => {
    try {
      setAccount(await api.getAccount(id))
    } catch (e) {
      setError(e.message)
    }
  }, [id])

  const loadTxs = useCallback(async () => {
    setLoading(true)
    try {
      setTxs(await api.getAccountTransactions(id, { page, size: PAGE_SIZE }))
      setError(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [id, page])

  useEffect(() => {
    loadAccount()
  }, [loadAccount])

  useEffect(() => {
    loadTxs()
  }, [loadTxs])

  const submitTx = async (kind) => {
    if (txForm.amount === '' || Number(txForm.amount) <= 0) {
      setError('Amount must be greater than zero.')
      return
    }
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      const payload = {
        amount: Number(txForm.amount),
        description: txForm.description || undefined,
        transactionRef: txForm.transactionRef || undefined
      }
      const fn = kind === 'deposit' ? api.deposit : api.withdrawal
      const result = await fn(id, payload, idemKey)
      setNotice(`${kind === 'deposit' ? 'Deposit' : 'Withdrawal'} ${result.transactionRef} → balance ${formatMoney(result.balanceAfter)}`)
      setTxForm({ amount: '', description: '', transactionRef: '' })
      setIdemKey(uuid())
      setPage(0)
      await loadAccount()
      await loadTxs()
    } catch (e) {
      setError(e.message)
    } finally {
      setBusy(false)
    }
  }

  if (!account && loading) return <Spinner label="Loading account…" />

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>{account ? account.holderName : 'Account'}</h1>
          <p className="muted mono">{account?.accountNumber}</p>
        </div>
        <Link to="/accounts" className="btn btn-ghost">
          ← Back
        </Link>
      </header>

      <Alert kind="error" onClose={() => setError(null)}>
        {error}
      </Alert>
      <Alert kind="success" onClose={() => setNotice(null)}>
        {notice}
      </Alert>

      {account && (
        <div className="stats-grid">
          <div className="stat stat-primary">
            <span className="stat-label">Current balance</span>
            <span className="stat-value">{formatMoney(account.balance, account.currency)}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Type</span>
            <span className="stat-value sm">{account.accountType}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Status</span>
            <span className="stat-value sm">{account.status}</span>
          </div>
          <div className="stat">
            <span className="stat-label">Currency</span>
            <span className="stat-value sm">{account.currency}</span>
          </div>
        </div>
      )}

      <Card
        title="Post a transaction"
        subtitle="Sent with an Idempotency-Key so retries never double-post."
      >
        <form className="form-row" onSubmit={(e) => e.preventDefault()}>
          <Field label="Amount">
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={txForm.amount}
              onChange={(e) => setTxForm({ ...txForm, amount: e.target.value })}
              placeholder="0.00"
            />
          </Field>
          <Field label="Description (optional)">
            <input
              maxLength={500}
              value={txForm.description}
              onChange={(e) => setTxForm({ ...txForm, description: e.target.value })}
              placeholder="e.g. Cash deposit"
            />
          </Field>
          <Field label="Reference (optional)">
            <input
              maxLength={50}
              value={txForm.transactionRef}
              onChange={(e) => setTxForm({ ...txForm, transactionRef: e.target.value })}
              placeholder="auto-generated if blank"
            />
          </Field>
          <Field label="Idempotency key">
            <div className="inline">
              <input value={idemKey} onChange={(e) => setIdemKey(e.target.value)} />
              <button type="button" className="btn btn-ghost" onClick={() => setIdemKey(uuid())}>
                ↻
              </button>
            </div>
          </Field>
          <div className="form-actions">
            <button className="btn btn-success" disabled={busy} onClick={() => submitTx('deposit')}>
              Deposit
            </button>
            <button className="btn btn-warning" disabled={busy} onClick={() => submitTx('withdrawal')}>
              Withdraw
            </button>
          </div>
        </form>
      </Card>

      <Card title="Transaction history">
        <DataTable
          loading={loading}
          columns={['Ref', 'Type', 'Amount', 'Balance after', 'Status', 'When']}
          rows={txs?.content}
          empty="No transactions for this account yet."
          renderRow={(t) => (
            <tr key={t.id}>
              <td className="mono">{t.transactionRef}</td>
              <td>
                <Badge tone={t.transactionType === 'DEPOSIT' ? 'completed' : 'warning'}>
                  {t.transactionType}
                </Badge>
              </td>
              <td className="num">{formatMoney(t.amount)}</td>
              <td className="num">{formatMoney(t.balanceAfter)}</td>
              <td>
                <Badge>{t.status}</Badge>
              </td>
              <td className="muted small">{formatDateTime(t.createdAt)}</td>
            </tr>
          )}
        />
        <Pagination
          page={page}
          totalPages={txs?.totalPages}
          totalElements={txs?.totalElements}
          onChange={setPage}
        />
      </Card>
    </div>
  )
}
