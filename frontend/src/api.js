// Central API client for the BankRecon backend.
// In development Vite proxies /api to http://localhost:8080 (see vite.config.js),
// so relative URLs work without CORS. Override with VITE_API_BASE if needed.
const BASE = import.meta.env.VITE_API_BASE ?? ''

export class ApiError extends Error {
  constructor(message, status, fieldErrors) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.fieldErrors = fieldErrors || null
  }
}

async function request(path, { method = 'GET', body, headers = {}, isForm = false } = {}) {
  const opts = { method, headers: { ...headers } }
  if (body !== undefined) {
    if (isForm) {
      opts.body = body
    } else {
      opts.headers['Content-Type'] = 'application/json'
      opts.body = JSON.stringify(body)
    }
  }

  let res
  try {
    res = await fetch(`${BASE}${path}`, opts)
  } catch (networkError) {
    throw new ApiError('Cannot reach the API. Is the backend running on :8080?', 0)
  }

  if (res.status === 204) return null

  const text = await res.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }

  if (!res.ok) {
    const message =
      (data && (data.message || data.error)) ||
      (typeof data === 'string' && data) ||
      `Request failed with status ${res.status}`
    throw new ApiError(message, res.status, data && data.fieldErrors)
  }
  return data
}

const qs = (params) => {
  const clean = Object.entries(params || {}).filter(
    ([, v]) => v !== undefined && v !== null && v !== ''
  )
  if (!clean.length) return ''
  return '?' + clean.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(v)}`).join('&')
}

export const api = {
  // Accounts
  createAccount: (payload) => request('/api/v1/accounts', { method: 'POST', body: payload }),
  listAccounts: ({ page = 0, size = 10 } = {}) =>
    request(`/api/v1/accounts${qs({ page, size })}`),
  getAccount: (id) => request(`/api/v1/accounts/${id}`),
  getAccountTransactions: (id, { page = 0, size = 10 } = {}) =>
    request(`/api/v1/accounts/${id}/transactions${qs({ page, size })}`),

  // Transactions
  deposit: (accountId, payload, idempotencyKey) =>
    request(`/api/v1/accounts/${accountId}/transactions/deposit`, {
      method: 'POST',
      body: payload,
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
    }),
  withdrawal: (accountId, payload, idempotencyKey) =>
    request(`/api/v1/accounts/${accountId}/transactions/withdrawal`, {
      method: 'POST',
      body: payload,
      headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {}
    }),
  getTransaction: (id) => request(`/api/v1/transactions/${id}`),
  getTransactionByRef: (ref) => request(`/api/v1/transactions/ref/${encodeURIComponent(ref)}`),
  listTransactions: (params = {}) =>
    request(`/api/v1/transactions${qs(params)}`),

  // Reconciliation
  importStatement: (file) => {
    const form = new FormData()
    form.append('file', file)
    return request('/api/v1/reconciliation/import', { method: 'POST', body: form, isForm: true })
  },
  runReconciliation: (importId) =>
    request('/api/v1/reconciliation/runs', { method: 'POST', body: { importId } }),
  getRun: (id) => request(`/api/v1/reconciliation/runs/${id}`),
  getDiscrepancies: ({ runId, page = 0, size = 10 } = {}) =>
    request(`/api/v1/reconciliation/discrepancies${qs({ runId, page, size })}`),

  // Reports
  dailySummary: (from, to) =>
    request(`/api/v1/reports/daily-summary${qs({ from, to })}`),
  depositWithdrawalSummary: (from, to) =>
    request(`/api/v1/reports/deposit-withdrawal-summary${qs({ from, to })}`),
  statusSummary: () => request('/api/v1/reports/status-summary'),
  accountActivity: (limit = 10) =>
    request(`/api/v1/reports/account-activity${qs({ limit })}`),
  failedTransactions: () => request('/api/v1/reports/failed-transactions'),
  duplicateRefs: () => request('/api/v1/reports/duplicate-refs'),

  health: () => request('/actuator/health')
}

export const formatMoney = (value, currency = 'INR') => {
  if (value === null || value === undefined) return '—'
  const num = Number(value)
  if (Number.isNaN(num)) return String(value)
  try {
    return new Intl.NumberFormat('en-IN', {
      style: 'currency',
      currency,
      minimumFractionDigits: 2
    }).format(num)
  } catch {
    return num.toFixed(2)
  }
}

export const formatDateTime = (value) => {
  if (!value) return '—'
  const d = new Date(value)
  if (Number.isNaN(d.getTime())) return String(value)
  return d.toLocaleString()
}
