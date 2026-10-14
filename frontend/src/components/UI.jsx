import React from 'react'

export function Card({ title, subtitle, actions, children, className = '' }) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <header className="card-head">
          <div>
            {title && <h3>{title}</h3>}
            {subtitle && <p className="muted">{subtitle}</p>}
          </div>
          {actions && <div className="card-actions">{actions}</div>}
        </header>
      )}
      <div className="card-body">{children}</div>
    </section>
  )
}

export function StatCard({ label, value, tone = 'default', hint }) {
  return (
    <div className={`stat stat-${tone}`}>
      <span className="stat-label">{label}</span>
      <span className="stat-value">{value}</span>
      {hint && <span className="stat-hint">{hint}</span>}
    </div>
  )
}

export function Spinner({ label = 'Loading…' }) {
  return (
    <div className="spinner">
      <span className="dot" />
      {label}
    </div>
  )
}

export function Alert({ kind = 'error', children, onClose }) {
  if (!children) return null
  return (
    <div className={`alert alert-${kind}`}>
      <span>{children}</span>
      {onClose && (
        <button className="alert-close" onClick={onClose} aria-label="Dismiss">
          ×
        </button>
      )}
    </div>
  )
}

export function Badge({ children, tone }) {
  const t = tone || String(children || '').toLowerCase().replace(/\s+/g, '-')
  return <span className={`badge badge-${t}`}>{children}</span>
}

export function Empty({ children = 'No data available.' }) {
  return <div className="empty">{children}</div>
}

export function Field({ label, hint, children }) {
  return (
    <label className="field">
      <span className="field-label">{label}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  )
}

export function Pagination({ page, totalPages, totalElements, onChange }) {
  if (totalPages === undefined) return null
  const current = (page ?? 0) + 1
  const max = Math.max(totalPages, 1)
  return (
    <div className="pagination">
      <span className="muted">
        Page {current} of {max}
        {totalElements !== undefined ? ` · ${totalElements} total` : ''}
      </span>
      <div className="pager-buttons">
        <button className="btn btn-ghost" disabled={page <= 0} onClick={() => onChange(page - 1)}>
          ← Prev
        </button>
        <button
          className="btn btn-ghost"
          disabled={current >= max}
          onClick={() => onChange(page + 1)}
        >
          Next →
        </button>
      </div>
    </div>
  )
}

export function DataTable({ columns, rows, renderRow, empty = 'No records.', loading }) {
  if (loading) return <Spinner />
  if (!rows || rows.length === 0) return <Empty>{empty}</Empty>
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c}>{c}</th>
            ))}
          </tr>
        </thead>
        <tbody>{rows.map(renderRow)}</tbody>
      </table>
    </div>
  )
}
