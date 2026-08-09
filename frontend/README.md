# E-Commerce Platform — Frontend (SPA)

React single-page app for the enterprise e-commerce microservices platform. It talks to the backend only through the API Gateway (`:8222`) and is served in production on `:8080` behind nginx, which proxies `/api` to the gateway.

## Tech Stack

| Concern | Technology | Version |
|:---|:---|:---:|
| UI library | React | 19.2 |
| Build tool | Vite | 8.0 |
| Language | TypeScript | 6.0 |
| Component library | MUI (Material UI) | v9 |
| Styling | Tailwind CSS | v4 |
| Server state / data fetching | TanStack Query | 5 |
| Client state | Zustand | 5 |
| Forms + validation | React Hook Form + Zod | 7 / 4 |
| Routing | React Router | 7 |
| HTTP client | Axios | 1 |
| Charts | Recharts | 3 |
| i18n | i18next + react-i18next (EN / FR / PT / ES) | 23 / 14 |
| Unit/component tests | Vitest | 4 |
| E2E tests | Playwright | 1.49 |

## Scripts

```bash
npm run dev            # Vite dev server (HMR)
npm run build          # tsc -b && vite build
npm run lint           # ESLint
npm run test           # Vitest (run mode)
npm run test:watch     # Vitest (watch)
npm run test:coverage  # Vitest with coverage
npm run test:e2e       # Playwright
```

> **Windows/WSL caveat:** DOM/component Vitest runs are unreliable on this box (the pool can hang/crash on MUI/framer imports). Prefer `tsc -b --noEmit` + ESLint for verification, and `--pool=forks` for pure-logic tests. See the project rules under `.claude/rules/frontend.md`.

## Project Structure

```
src/
├── api/         # Axios client + per-domain API modules; response interceptor does silent JWT refresh
├── components/  # Reusable UI (product, feedback, layout primitives, …)
├── hooks/       # Custom React hooks
├── i18n/        # i18next config + EN/FR/PT/ES translation resources
├── pages/       # Route-level pages, grouped by audience:
│               #   public/ (9), customer/ (7), seller/ (6), admin/ (11)
├── routes/      # createBrowserRouter config, layouts, role guards, lazy loading
├── stores/      # Zustand stores (auth in sessionStorage, UI/toasts)
├── theme/       # MUI theme
├── utils/       # Helpers (incl. lazyWithReload — self-heals stale-deploy chunk fetches)
└── test/        # Test setup
```

## Roles & Access

The SPA is role-aware (`USER` / `SELLER` / `ADMIN`), enforced by route guards (`ProtectedRoute`, `RoleRoute`):

- **USER (customer):** catalog, product detail + reviews, cart, checkout, orders, order detail, profile/settings.
- **SELLER:** product management, product form, inventory, order management, seller settings, dashboard. No cart.
- **ADMIN:** tenants, users, products, categories, review moderation, orders, payments, analytics. No cart.

## API Integration

- All requests go through `/api/v1/*` (proxied to the gateway); the Axios client attaches the `Bearer` access token and `X-Tenant-Id`, and a response interceptor performs a **silent token refresh** (401 → `POST /auth/refresh` → retry the original request), redirecting to `/login` only if the refresh itself fails.
- Auth tokens live in `sessionStorage` (per-tab isolation — avoids leaking a role across tabs).

## Notes

- **MUI v9:** use `slotProps={{ htmlInput }}` — `inputProps` was removed.
- **Stale-deploy recovery:** route-level lazy imports are wrapped with `lazyWithReload`, which triggers one guarded full-page reload if a chunk 404s after a redeploy (old tab holding stale hashed asset names) instead of dead-ending on the error boundary.
