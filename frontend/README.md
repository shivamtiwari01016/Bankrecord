# BankRecon Frontend

A React 18 + Vite single-page console for the BankRecon REST API. It provides a UI for
accounts, transactions, CSV statement import, reconciliation, and reporting.

> **Educational project — synthetic data only.** No authentication is implemented.

---

## Requirements

- **Node.js 18+** and **npm**
- The **BankRecon backend** running at `http://localhost:8080`

On Windows PowerShell, the `npm.ps1` shim may be blocked by the execution policy. Use
`npm.cmd` instead (e.g. `npm.cmd install`).

---

## Run

```powershell
npm.cmd install     # first time only
npm.cmd run dev     # http://localhost:5173
```

The Vite dev server proxies `/api` and `/actuator` to `http://localhost:8080`
(see `vite.config.js`), so the browser uses a single origin and no CORS setup is required.

To point at a different backend:

```powershell
$env:VITE_API_BASE="http://my-host:8080"; npm.cmd run dev
```

---

## Build

```powershell
npm.cmd run build       # static assets in dist/
npm.cmd run preview     # serve the production build locally
```

The production build uses relative API URLs, so serve `dist/` from the same origin as the
backend (for example by copying it into the backend's `src/main/resources/static/`) if you
want to avoid CORS.

---

## Structure

```
frontend/
├── index.html
├── vite.config.js       dev proxy + build config
├── package.json
└── src/
    ├── main.jsx         React entry (BrowserRouter)
    ├── App.jsx          routes
    ├── api.js           typed API client + formatting helpers
    ├── styles.css       design system
    ├── components/
    │   ├── Layout.jsx   sidebar + shell
    │   └── UI.jsx       Card, StatCard, DataTable, Pagination, Alert, Badge, …
    └── pages/
        ├── Dashboard.jsx
        ├── Accounts.jsx
        ├── AccountDetail.jsx
        ├── Transactions.jsx
        ├── Reconciliation.jsx
        └── Reports.jsx
```

---

## API usage notes

- **Idempotency:** the account detail page sends an `Idempotency-Key` header on
  deposit/withdraw. Keep the same key and resubmit to safely retry a request.
- **Money:** amounts are `BigDecimal` on the server and rendered with `Intl.NumberFormat`.
- **Pagination:** Spring `Page` responses (`content`, `totalElements`, `totalPages`) are
  handled by the shared `Pagination` component.
- **Errors:** the API client surfaces the backend's `{ message, fieldErrors }` error body.
