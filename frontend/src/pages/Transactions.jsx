import React, { useCallback, useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api, formatMoney, formatDateTime } from '../api.js'
import { Card, Field, Alert, DataTable, Pagination, Badge } from '../components/UI.jsx'

const PAGE_SIZE = 10

export default function Transactions() {
  const [filters, setFilters] = useState({
    accountId: '',
    type: '',
    status: '',
    from: '',
    to: ''
  })
  const [applied, setApplied] = useState({})
  const [page, setPage] = useState(0)
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

  const [ref, setRef] = useState('')
  const [refResult, setRefResult] = useState(null)
  const [refError, setRefError] = useState(null)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await api.listTransactions({ ...applied, page, size: PAGE_SIZE })
      setData(res)
      setError(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [applied, page])

  useEffect(() => {
    load()
  }, [load])

  const apply = (e) => {
    e.preventDefault()
    setPage(0)
    setApplied({ ...filters })
  }

  const reset = () => {
    setFilters({ accountId: '', type: '', status: '', from: '', to: '' })
    setApplied({})
    setPage(0)
  }

  const lookupRef = async (e) => {
    e.preventDefault()
    setRefError(null)
    setRefResult(null)
    if (!ref) return
    try {
      setRefResult(await api.getTransactionByRef(ref))
    } catch (e2) {
      setRefError(e2.message)
    }
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Transactions</h1>
          <p className="muted">Search and filter the full transaction ledger.</p>
        </div>
      </header>

      <Alert kind="error" onClose={() => setError(null)}>
        {error}
      </Alert>

      <Card title="Lookup by reference" subtitle="Fetch a single transaction by its transaction ref.">
        <form className="form-row" onSubmit={lookupRef}>
          <Field label="Transaction ref">
            <input value={ref} onChange={(e) => setRef(e.target.value)} placeholder="TXN-…" />
          </Field>
          <button className="btn btn-primary" type="submit">
            Find
          </button>
        </form>
        <Alert kind="error" onClose={() => setRefError(null)}>
          {refError}
        </Alert>
        {refResult && (
          <div className="ref-result">
            <Badge>{refResult.status}</Badge>
            <span className="mono">{refResult.transactionRef}</span>
            <span>
              {refResult.transactionType} · {formatMoney(refResult.amount)}
            </span>
            <span className="muted">balance after {formatMoney(refResult.balanceAfter)}</span>
          </div>
        )}
      </Card>

      <Card title="Filter transactions">
        <form className="form-row" onSubmit={apply}>
          <Field label="Account ID">
            <input
              type="number"
              value={filters.accountId}
              onChange={(e) => setFilters({ ...filters, accountId: e.target.value })}
              placeholder="any"
            />
          </Field>
          <Field label="Type">
            <select value={filters.type} onChange={(e) => setFilters({ ...filters, type: e.target.value })}>
              <option value="">Any</option>
              <option value="DEPOSIT">DEPOSIT</option>
              <option value="WITHDRAWAL">WITHDRAWAL</option>
            </select>
          </Field>
          <Field label="Status">
            <select
              value={filters.status}
              onChange={(e) => setFilters({ ...filters, status: e.target.value })}
            >
              <option value="">Any</option>
              <option value="COMPLETED">COMPLETED</option>
              <option value="FAILED">FAILED</option>
              <option value="REJECTED">REJECTED</option>
            </select>
          </Field>
          <Field label="From">
            <input
              type="datetime-local"
              value={filters.from}
              onChange={(e) => setFilters({ ...filters, from: e.target.value })}
            />
          </Field>
          <Field label="To">
            <input
              type="datetime-local"
              value={filters.to}
              onChange={(e) => setFilters({ ...filters, to: e.target.value })}
            />
          </Field>
          <div className="form-actions">
            <button className="btn btn-primary" type="submit">
              Apply
            </button>
            <button className="btn btn-ghost" type="button" onClick={reset}>
              Reset
            </button>
          </div>
        </form>
      </Card>

      <Card title="Results" subtitle={`${data?.totalElements ?? 0} transaction(s)`}>
        <DataTable
          loading={loading}
          columns={['ID', 'Ref', 'Account', 'Type', 'Amount', 'Balance after', 'Status', 'When']}
          rows={data?.content}
          empty="No transactions match the current filters."
          renderRow={(t) => (
            <tr key={t.id}>
              <td className="muted">{t.id}</td>
              <td className="mono">{t.transactionRef}</td>
              <td>
                {t.accountId ? (
                  <Link to={`/accounts/${t.accountId}`} className="link mono">
                    {t.accountNumber}
                  </Link>
                ) : (
                  '—'
                )}
              </td>
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
          totalPages={data?.totalPages}
          totalElements={data?.totalElements}
          onChange={setPage}
        />
      </Card>
    </div>
  )
}
