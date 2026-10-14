import React, { useCallback, useEffect, useState } from 'react'
import { api, formatMoney } from '../api.js'
import { Card, Field, Alert, DataTable, Pagination, Badge, Empty } from '../components/UI.jsx'

const PAGE_SIZE = 10

function SummaryGrid({ items }) {
  return (
    <div className="stats-grid compact">
      {items.map((it) => (
        <div key={it.label} className={`stat stat-${it.tone || 'default'}`}>
          <span className="stat-label">{it.label}</span>
          <span className="stat-value">{it.value}</span>
        </div>
      ))}
    </div>
  )
}

export default function Reconciliation() {
  const [file, setFile] = useState(null)
  const [importSummary, setImportSummary] = useState(null)
  const [importError, setImportError] = useState(null)
  const [importing, setImporting] = useState(false)

  const [importId, setImportId] = useState('')
  const [run, setRun] = useState(null)
  const [runError, setRunError] = useState(null)
  const [running, setRunning] = useState(false)

  const [disc, setDisc] = useState(null)
  const [discPage, setDiscPage] = useState(0)
  const [discError, setDiscError] = useState(null)
  const [discLoading, setDiscLoading] = useState(false)

  const doImport = async (e) => {
    e.preventDefault()
    if (!file) {
      setImportError('Choose a CSV file first.')
      return
    }
    setImporting(true)
    setImportError(null)
    setImportSummary(null)
    try {
      const res = await api.importStatement(file)
      setImportSummary(res)
      setImportId(String(res.importId))
    } catch (e2) {
      setImportError(e2.message)
    } finally {
      setImporting(false)
    }
  }

  const doRun = async (e) => {
    e?.preventDefault()
    const idNum = Number(importId)
    if (!idNum) {
      setRunError('Enter a valid import ID.')
      return
    }
    setRunning(true)
    setRunError(null)
    setRun(null)
    try {
      const res = await api.runReconciliation(idNum)
      setRun(res)
      setDiscPage(0)
    } catch (e2) {
      setRunError(e2.message)
    } finally {
      setRunning(false)
    }
  }

  const loadDisc = useCallback(async () => {
    if (!run?.id) return
    setDiscLoading(true)
    try {
      const res = await api.getDiscrepancies({ runId: run.id, page: discPage, size: PAGE_SIZE })
      setDisc(res)
      setDiscError(null)
    } catch (e) {
      setDiscError(e.message)
    } finally {
      setDiscLoading(false)
    }
  }, [run, discPage])

  useEffect(() => {
    if (run?.id) loadDisc()
  }, [loadDisc, run])

  return (
    <div className="page">
      <header className="page-head">
        <div>
          <h1>Reconciliation</h1>
          <p className="muted">
            Import an external statement, run reconciliation, and inspect discrepancies.
          </p>
        </div>
      </header>

      <div className="two-col">
        <Card title="1 · Import statement CSV" subtitle="Expected columns: external_ref, account_number, transaction_type, amount, transaction_timestamp">
          <form className="stack" onSubmit={doImport}>
            <input
              type="file"
              accept=".csv,text/csv"
              onChange={(e) => setFile(e.target.files?.[0] || null)}
            />
            <button className="btn btn-primary" disabled={importing}>
              {importing ? 'Uploading…' : 'Upload & import'}
            </button>
          </form>
          <Alert kind="error" onClose={() => setImportError(null)}>
            {importError}
          </Alert>
          {importSummary && (
            <>
              <Alert kind="success">{`Imported "${importSummary.fileName}" (import #${importSummary.importId}).`}</Alert>
              <SummaryGrid
                items={[
                  { label: 'Total rows', value: importSummary.totalRows, tone: 'primary' },
                  { label: 'Accepted', value: importSummary.acceptedRows, tone: 'success' },
                  { label: 'Rejected', value: importSummary.rejectedRows, tone: 'danger' },
                  { label: 'Duplicate', value: importSummary.duplicateRows, tone: 'warning' }
                ]}
              />
            </>
          )}
        </Card>

        <Card title="2 · Run reconciliation" subtitle="Matches external entries against internal transactions.">
          <form className="form-row" onSubmit={doRun}>
            <Field label="Import ID" hint="Auto-filled after import; you can also enter one manually.">
              <input
                type="number"
                value={importId}
                onChange={(e) => setImportId(e.target.value)}
                placeholder="e.g. 1"
              />
            </Field>
            <button className="btn btn-primary" disabled={running}>
              {running ? 'Running…' : 'Run reconciliation'}
            </button>
          </form>
          <Alert kind="error" onClose={() => setRunError(null)}>
            {runError}
          </Alert>
          {run && (
            <>
              <Alert kind="success">{`Run #${run.id} — ${run.status}`}</Alert>
              <SummaryGrid
                items={[
                  { label: 'Total items', value: run.totalItems, tone: 'primary' },
                  { label: 'Matched', value: run.matched, tone: 'success' },
                  { label: 'Missing internal', value: run.missingInternal, tone: 'danger' },
                  { label: 'Missing external', value: run.missingExternal, tone: 'warning' },
                  { label: 'Amount mismatch', value: run.amountMismatch, tone: 'warning' },
                  { label: 'Type mismatch', value: run.typeMismatch, tone: 'warning' },
                  { label: 'Duplicate external', value: run.duplicateExternal, tone: 'danger' },
                  { label: 'Invalid external', value: run.invalidExternal, tone: 'danger' }
                ]}
              />
            </>
          )}
        </Card>
      </div>

      <Card
        title="3 · Discrepancies"
        subtitle={run ? `Non-matched items for run #${run.id}` : 'Run a reconciliation to view discrepancies.'}
      >
        <Alert kind="error" onClose={() => setDiscError(null)}>
          {discError}
        </Alert>
        {!run ? (
          <Empty>No reconciliation run selected yet.</Empty>
        ) : (
          <>
            <DataTable
              loading={discLoading}
              columns={['Status', 'External ref', 'Ext amount', 'Int amount', 'Ext type', 'Int type', 'Detail']}
              rows={disc?.content}
              empty="No discrepancies — everything matched."
              renderRow={(it) => (
                <tr key={it.id}>
                  <td>
                    <Badge tone={it.matchStatus === 'MATCHED' ? 'completed' : 'danger'}>
                      {it.matchStatus}
                    </Badge>
                  </td>
                  <td className="mono">{it.externalRef || '—'}</td>
                  <td className="num">{it.externalAmount != null ? formatMoney(it.externalAmount) : '—'}</td>
                  <td className="num">{it.internalAmount != null ? formatMoney(it.internalAmount) : '—'}</td>
                  <td>{it.externalType || '—'}</td>
                  <td>{it.internalType || '—'}</td>
                  <td className="muted small">{it.discrepancyDetail || '—'}</td>
                </tr>
              )}
            />
            <Pagination
              page={discPage}
              totalPages={disc?.totalPages}
              totalElements={disc?.totalElements}
              onChange={setDiscPage}
            />
          </>
        )}
      </Card>
    </div>
  )
}
