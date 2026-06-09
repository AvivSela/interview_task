# Frontend Screens & Components

## Routes

| Path | Component | Auth required |
|------|-----------|---------------|
| `/` | `LandingPage` | No (redirects to `/dashboard` if logged in) |
| `/login` | `LandingPage` | No (redirects to `/dashboard` if logged in) |
| `/dashboard` | dashboard (inline in `App`) | Yes — `ProtectedRoute` redirects to `/` if no token |
| `/register` | `RegisterForm` | No |
| `/link-expired` | `LinkExpired` | No |
| `*` | `NotFound` | No |

---

## `/` and `/login` — `LandingPage`

Two-panel layout. On mount, if a JWT token is in localStorage, calls `GET /api/auth/me` to verify the user still exists. Redirects to `/dashboard` on success; clears stale token and shows the landing page on 401. Renders nothing while the check is in flight.

### Left panel — Anon shortener

State: `url`, `creating`, `createError`, `result`, `copied`

| Sub-state | Rendered |
|-----------|----------|
| Default (`result` is null) | URL input + Shorten button |
| After shorten (`result` set) | Short link display, Copy button, "Shorten another" link, upsell text |

### Right panel — Login form

State: `email`, `password`, `loginError`, `loginLoading`

- Email + password inputs
- Error message when `loginError` is set
- Link to `/register`
- On success: stores token + email in localStorage, navigates to `/dashboard`

---

## `/register` — `RegisterForm`

Standalone card, no layout wrapper.

State: `email`, `password`, `confirmPassword`, `error`, `loading`

- Client-side validation: password ≥ 8 chars, passwords match
- Error cases: `409` → email taken, `400` → invalid input
- On success: stores token + email in localStorage, navigates to `/dashboard`
- Link back to `/` (login)

---

## `/dashboard` — Protected (`ProtectedRoute` → `App`)

`ProtectedRoute` checks `localStorage.getItem('token')`; redirects to `/` if absent.

App-level state: `links`, `editTarget`, `analyticsShortCode`, `tagFilter`, `loadError`

### Header
- App title
- Logout button (shown when token is present)

### `LinkForm`
State: `originalUrl`, `customCode`, `tags`, `maxClicks`, `expiresAt`, `strategy`, `allStrategyParams`, `strategySchemas`, `schemaLoading`, `schemaError`, `error`, `loading`

| Mode | Condition | Behaviour |
|------|-----------|-----------|
| Create | `editTarget` is null | Submits new link; resets form on success |
| Edit | `editTarget` is set | Pre-fills fields; strategy selector disabled; Cancel button shown |

- Strategy selector loads available strategies from `/api/strategies` on mount
- Dynamic strategy params rendered per schema (integer / string / boolean inputs)
- Strategy is fixed at creation and cannot be changed during edit

### `LinksTable`
- Displays `links` filtered by `tagFilter` when set
- Per-row actions: Edit (`setEditTarget`), Delete, View Stats (`setAnalyticsShortCode`), QR code (`QrPopover`)
- Tag chips act as filters via `onTagFilter`

### `AnalyticsPanel`
- Rendered below the table when `analyticsShortCode` is set
- Closed by setting `analyticsShortCode = null`

### `Footer`
- Static footer, always rendered on dashboard

---

## `/link-expired` — `LinkExpired`

Static error page. No auth required. Shown when a redirect hits an expired or invalid short code (backend redirects to this route instead of returning 410).

---

## `*` — `NotFound`

Static 404 page for any unmatched route.

---

## Dead / Orphaned Components

| Component | Status | Notes |
|-----------|--------|-------|
| `AnonHome.jsx` | Untracked, not routed | Superseded by `LandingPage`. Has its own header with Login/Register buttons and uses `LinkForm` directly. |
| `LoginForm.jsx` | Deleted | Login UI was consolidated into the right panel of `LandingPage`. |
