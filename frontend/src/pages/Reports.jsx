import React, { useCallback, useEffect, useState } from 'react'
import { api, formatMoney } from '../api.js'
import { Card, Field, Alert, DataTable, Badge, Spinner, Empty, StatCard } from '../components/UI.jsx'

const todayISO = () => new Date().toISOString().slice(0, 16)

export default function Reports() {
  const [from, setFrom] = useState('2000-01-01T00:00')
  const [to, setTo] = useState('2100-01-01T00:00')
  const [statuses, setStatuses] = useState(null)
  const [dw, setDw] = useState(null)
  const [daily, setDaily] = useState(null)
  const [activity, setActivity] = useState(null)
  const [failed, setFailed] = useState(null)
  const [dupes, setDupes] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(true)

  const loadAll = useCallback(async () => {
    setLoading(true)
    try {
      const [s, d, act, f, dup] = await Promise.all([
        api.statusSummary(),
        api.depositWithdrawalSummary(from, to),
        api.accountActivity(10),
        api.failedTransactions(),
        api.duplicateRefs()
      ])
      setStatuses(s)
      setDw(d)
      setActivity(act)
      setFailed(f)
      setDupes(dup)
      setError(null)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [from, to])

  useEffect(() => {
    loadAll()
  }, [loadAll])

  const loadDaily = async () => {
    try {
      setDaily(await api.dailySummary(from, to))
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Reports &amp; Analytics</h1>
          <p className="muted">Aggregated SQL reporting exposed by the ReportService.</p>
        </div>
      </header>

      <Alert kind="error" onClose={() => setError(null)}>
        {error}
      </Alert>

      <Card title="Date range" subtitle="Applies to the deposit/withdrawal and daily summaries.">
        <form
          className="form-row"
          onSubmit={(e) => {
            e.preventDefault()
            loadAll()
          }}
        >
          <Field label="From">
            <input type="datetime-local" value={from} onChange={(e) => setFrom(e.target.value)} />
          </Field>
          <Field label="To">
            <input type="datetime-local" value={to} onChange={(e) => setTo(e.target.value)} />
          </Field>
          <div className="form-actions">
            <button className="btn btn-primary" type="submit">
              Refresh
            </button>
            <button className="btn btn-ghost" type="button" onClick={loadDaily}>
              Load daily summary
            </button>
          </div>
        </form>
      </Card>

      {loading ? (
        <Spinner label="Loading reports…" />
      ) : (
        <>
          <div className="stats-grid">
            {statuses &&
              Object.entries(statuses).map(([k, v]) => (
                <StatCard
                  key={k}
                  label={k}
                  value={v}
                  tone={
                    k === 'COMPLETED' ? 'success' : k === 'FAILED' || k === 'REJECTED' ? 'danger' : 'default'
                  }
                />
              ))}
          </div>

          {dw && (
            <div className="stats-grid">
              <StatCard label="Total deposits" value={formatMoney(dw.totalDeposits)} tone="success" />
              <StatCard
                label="Total withdrawals"
                value={formatMoney(dw.totalWithdrawals)}
                tone="warning"
              />
              <StatCard label="Net flow" value={formatMoney(dw.netFlow)} tone="primary" />
            </div>
          )}

          {daily && (
            <Card title="Daily summary" subtitle="Per-day transaction counts and totals.">
              <DataTable
                columns={Object.keys(daily[0] || {})}
                rows={daily}
                empty="No activity in this range."
                renderRow={(row, i) => (
                  <tr key={i}>
                    {Object.entries(row).map(([k, v]) => (
                      <td key={k} className={typeof v === 'number' ? 'num' : ''}>
                        {typeof v === 'number' && /amount|sum|total|deposit|withdrawal/i.test(k)
                          ? formatMoney(v)
                          : String(v)}
                      </td>
                    ))}
                  </tr>
                )}
              />
            </Card>
          )}

          <div className="two-col">
            <Card title="Top accounts by activity">
              <DataTable
                columns={Object.keys(activity?.[0] || {})}
                rows={activity}
                empty="No account activity."
                renderRow={(row, i) => (
                  <tr key={i}>
                    {Object.entries(row).map(([k, v]) => (
                      <td key={k} className={typeof v === 'number' ? 'num' : ''}>
                        {String(v)}
                      </td>
                    ))}
                  </tr>
                )}
              />
            </Card>

            <Card title="Failed / rejected transactions">
              <DataTable
                columns={Object.keys(failed?.[0] || {})}
                rows={failed}
                empty="No failed transactions."
                renderRow={(row, i) => (
                  <tr key={i}>
                    {Object.entries(row).map(([k, v]) => (
                      <td key={k} className={typeof v === 'number' ? 'num' : ''}>
                        {String(v)}
                      </td>
                    ))}
                  </tr>
                )}
              />
            </Card>
          </div>

          <Card title="Duplicate references" subtitle="Data-quality check across transaction refs.">
            <DataTable
              columns={Object.keys(dupes?.[0] || {})}
              rows={dupes}
              empty="No duplicate references."
              renderRow={(row, i) => (
                <tr key={i}>
                  {Object.entries(row).map(([k, v]) => (
                    <td key={k} className={k === 'transactionRef' ? 'mono' : ''}>
                      {String(v)}
                    </td>
                  ))}
                </tr>
              )}
            />
          </Card>
        </>
      )}
    </div>
  )
}
