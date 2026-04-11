# Resume Session — Obsidian Market Frontend Build

> **Branch:** `docs/ECO-110-frontend-design-plan`
> **Session Date:** 2026-04-11
> **Skill Executed:** `Fontend-dev` (Frontend Development Plan & UI/UX Design — SaaS E-Commerce Microservice Platform)
> **Execution Status:** Phase 1 ✅ | Phase 2 ✅ | Phase 3 ✅ | Phase 4 ✅ | Phase 5 ✅ (partial) | Phase 6 ⬜

---

## What Was Built

A production-grade React 19 + TypeScript + Vite frontend for a multi-tenant SaaS e-commerce platform called **Obsidian Market**. The UI follows a warm-dark, editorial design system with DM Serif Display + DM Sans typography. The app connects to 9 Spring Boot microservices through the Spring Cloud Gateway at `http://localhost:8222`.

---

## Features Implemented

| Feature | Status | Notes |
|---------|--------|-------|
| Vite + React + TS scaffold | ✅ Done | React 19, TS 6, Vite 8 |
| Tailwind CSS 4 via @tailwindcss/vite | ✅ Done | Integrated in vite.config.ts |
| Material UI v9 | ✅ Done | Full dependency set |
| "Obsidian Market" design tokens | ✅ Done | Dark + light palette, typography scale |
| MUI custom theme (createAppTheme) | ✅ Done | All component overrides |
| CSS custom properties + grain texture | ✅ Done | src/index.css |
| Google Fonts (DM Serif, DM Sans, JetBrains Mono) | ✅ Done | index.html preloads |
| Axios client + JWT interceptor | ✅ Done | Refresh mutex, tenant header |
| All 7 API modules | ✅ Done | auth, products, cart, orders, customers, payments, tenants |
| TypeScript DTO types (mirrors backend) | ✅ Done | src/api/types.ts |
| Zustand auth store (persisted) | ✅ Done | localStorage key: obsidian-auth |
| Zustand UI store (persisted theme) | ✅ Done | localStorage key: obsidian-ui |
| Zustand tenant store | ✅ Done | Session-scoped |
| LoginPage (RHF + Zod) | ✅ Done | Email/password, server error, post-login redirect |
| RegisterPage (RHF + Zod) | ✅ Done | Field-level errors from backend |
| ProtectedRoute | ✅ Done | Redirects to /login with state.from |
| RoleRoute | ✅ Done | USER / SELLER / ADMIN guards |
| Navbar | ✅ Done | Cart badge, theme toggle, user menu |
| Footer | ✅ Done | Links, copyright |
| Sidebar | ✅ Done | Permanent (desktop) + temporary (mobile) |
| MobileNav | ✅ Done | Bottom tab bar for mobile |
| PublicLayout | ✅ Done | Navbar + Footer + CartDrawer |
| CustomerLayout | ✅ Done | Same as public, auth-gated |
| SellerLayout | ✅ Done | Navbar + sidebar (Dashboard/Products/Orders/Inventory) |
| AdminLayout | ✅ Done | Navbar + sidebar (Dashboard/Tenants/Users/Payments/Analytics) |
| Toast notification system | ✅ Done | AnimatePresence, auto-dismiss, progress bar |
| ErrorBoundary | ✅ Done | Class component, recovery button |
| LoadingSkeleton variants | ✅ Done | ProductCard, ProductGrid, Table, StatCard |
| EmptyState component | ✅ Done | With optional CTA |
| ConfirmDialog | ✅ Done | Destructive variant |
| ProductCard | ✅ Done | Hover animation, out-of-stock state, quick view |
| ProductGrid | ✅ Done | Responsive 1→2→3→4 cols, add-to-cart mutation |
| CatalogPage | ✅ Done | Paginated, server-side, smooth page change |
| ProductPage | ✅ Done | Detail view, quantity selector, add-to-cart |
| HomePage | ✅ Done | Hero section + featured products grid |
| NotFoundPage | ✅ Done | 404 with mono numeric display |
| CartDrawer | ✅ Done | Optimistic mutations (add/remove/update) |
| CartItem | ✅ Done | Quantity +/- controls, line total |
| OrderStatusBadge | ✅ Done | All 4 saga statuses |
| OrderTimeline | ✅ Done | Real-time polling (3s), stops on CONFIRMED/CANCELLED |
| CheckoutPage | ✅ Done | 3-step wizard: address → payment → confirm → live tracking |
| OrdersPage | ✅ Done | Full order table |
| OrderDetailPage | ✅ Done | Order lines, summary, status badge |
| DashboardPage (customer) | ✅ Done | Stats + recent orders |
| ProfilePage | ✅ Done | Customer profile form (GET + PUT /customers/{id}) |
| SellerDashboard | ✅ Done | Metrics, product stats, low-stock alerts |
| ProductManagement | ✅ Done | DataTable with CRUD actions |
| ProductForm | ✅ Done | Create/edit with Zod validation |
| InventoryPage | ✅ Done | Low-stock table view |
| OrderManagement (seller) | ✅ Done | Read-only order table |
| AdminDashboard | ✅ Done | Platform-wide stats (tenants, revenue, users) |
| TenantsPage | ✅ Done | Tenant DataTable with plan/status badges and actions |
| DataTable component | ✅ Done | Generic with pagination, sort, row actions |
| Route config (createBrowserRouter) | ⚠️ Missing | App.tsx not wired up — still Vite placeholder |
| main.tsx providers | ⚠️ Missing | QueryClientProvider, ThemeProvider, RouterProvider not assembled |
| TenantDetailPage | ⚠️ Missing | Tabbed: Overview / Feature Flags / Usage |
| UsersPage (admin) | ⚠️ Missing | Customer list DataTable |
| PaymentsPage (admin) | ⚠️ Missing | Payments DataTable |
| AnalyticsPage (admin) | ⚠️ Missing | Recharts usage/revenue charts |
| Nginx config | ⚠️ Missing | nginx/default.conf |
| Dockerfile | ⚠️ Missing | Multi-stage node → nginx |
| docker-compose integration | ⚠️ Missing | frontend service in docker-compose.yml |
| GitHub Actions CI | ⚠️ Missing | .github/workflows/frontend.yml |
| E2E tests (Playwright) | ⚠️ Missing | e2e/*.spec.ts |
| MSW mock handlers | ⚠️ Missing | src/test/mocks/ |

---

## Step-by-Step Execution (Mapped to SKILL Phases)

### Phase 1 — Foundation ✅ COMPLETE

**Step 1.1 — Project Scaffold**
- Ran `npm create vite@latest . -- --template react-ts`
- Installed all dependencies: MUI, TanStack Query, Zustand, Axios, RHF+Zod, Framer Motion, Recharts, Tailwind

**Step 1.2 — Configuration**
- `vite.config.ts` — Tailwind plugin, path aliases (`@/`, `@components`, `@api`, `@hooks`, etc.), dev proxy to :8222, manual chunks
- `tsconfig.app.json` — baseUrl + paths matching vite aliases, noUnusedLocals: false
- `index.html` — Google Fonts preloads, Obsidian Market title

**Step 1.3 — Design System**
- `src/theme/tokens.ts` — Dark/light color palettes, typography families, radius, transitions
- `src/theme/mui-theme.ts` — `createAppTheme(mode)` with full palette + component overrides (Button, Card, Paper, TextField, Chip, Tooltip, Divider, TableHead)
- `src/index.css` — Tailwind import, CSS custom properties, grain overlay, scrollbar, focus-visible, selection, reduced-motion

**Step 1.4 — API Layer**
- `src/api/types.ts` — All TypeScript DTOs mirroring backend Java records
- `src/api/client.ts` — Axios instance, request interceptor (Bearer + X-Tenant-Id), response interceptor (silent refresh via mutex queue), `normalizeError()`
- `src/api/auth.api.ts` — register, login, refresh, logout
- `src/api/products.api.ts` — getAll (paginated), getById, create, createBatch, update, delete, purchase
- `src/api/cart.api.ts` — get, addItem, updateQuantity, removeItem, clear, checkout
- `src/api/orders.api.ts` — create (202), getStatus, getAll, getById, getLines
- `src/api/customers.api.ts` — getAll, getById, getByEmail, create, update, delete
- `src/api/payments.api.ts` — getAll, getById
- `src/api/tenants.api.ts` — full CRUD + plan change + suspend/reactivate + feature flags + usage metrics

**Step 1.5 — Zustand Stores**
- `src/stores/auth.store.ts` — accessToken, refreshToken, userId, email, role, tenantId; persisted as `obsidian-auth`
- `src/stores/ui.store.ts` — themeMode, sidebarOpen, toastQueue; theme persisted as `obsidian-ui`
- `src/stores/tenant.store.ts` — activeTenant, featureFlags, isFeatureEnabled()

**Step 1.6 — Utilities**
- `src/utils/constants.ts` — ROUTES object + QUERY_KEYS object
- `src/utils/format.ts` — formatCurrency, formatDate, formatDateTime, truncate
- `src/utils/jwt.ts` — decodeJwt, isTokenExpired

**Step 1.7 — Auth Pages**
- `src/pages/public/LoginPage.tsx` — RHF+Zod, show/hide password, server error, post-login redirect
- `src/pages/public/RegisterPage.tsx` — RHF+Zod, field-level error mapping, 409 conflict handling

**Step 1.8 — Route Guards**
- `src/routes/ProtectedRoute.tsx` — isAuthenticated check + redirect with state.from
- `src/routes/RoleRoute.tsx` — role check against allowedRoles[], toast on 403

**Step 1.9 — AppShell**
- `src/components/layout/Navbar.tsx` — logo, catalog link, theme toggle, cart badge, user avatar menu
- `src/components/layout/Footer.tsx` — brand, nav links, copyright
- `src/components/layout/Sidebar.tsx` — permanent (desktop) + temporary overlay (mobile), active route highlight
- `src/components/layout/MobileNav.tsx` — BottomNavigation with Home/Catalog/Cart/Account tabs

**Step 1.10 — Feedback Components**
- `src/components/feedback/Toast.tsx` — AnimatePresence stack, 4 variants, progress bar, auto-dismiss 5s
- `src/components/feedback/ErrorBoundary.tsx` — getDerivedStateFromError, recovery → home
- `src/components/feedback/LoadingSkeleton.tsx` — ProductCardSkeleton, ProductGridSkeleton, TableSkeleton, StatCardSkeleton
- `src/components/feedback/EmptyState.tsx` — icon, title, description, optional CTA button
- `src/components/feedback/ConfirmDialog.tsx` — title, description, destructive prop, loading state

**Step 1.11 — Route Layouts**
- `src/routes/layouts/PublicLayout.tsx` — Navbar + Footer + CartDrawer
- `src/routes/layouts/CustomerLayout.tsx` — Same as public
- `src/routes/layouts/SellerLayout.tsx` — Navbar + Sidebar (4 seller links)
- `src/routes/layouts/AdminLayout.tsx` — Navbar + Sidebar (5 admin links)

---

### Phase 2 — Catalog & Cart ✅ COMPLETE

**Step 2.1 — Product Components**
- `src/components/product/ProductCard.tsx` — 4:3 image, category label, serif name, mono price, add-to-cart, hover scale, quick-view overlay, out-of-stock state
- `src/components/product/ProductGrid.tsx` — Responsive grid, add-to-cart mutations with cache invalidation, EmptyState fallback

**Step 2.2 — Public Pages**
- `src/pages/public/HomePage.tsx` — Framer Motion hero, featured products (8 items), catalog CTA
- `src/pages/public/CatalogPage.tsx` — Paginated grid (20/page), product count, MUI Pagination
- `src/pages/public/ProductPage.tsx` — Detail layout (2-col), quantity field, add-to-cart, out-of-stock chip
- `src/pages/public/NotFoundPage.tsx` — 404 page with mono numeral

**Step 2.3 — Cart**
- `src/components/cart/CartDrawer.tsx` — 420px drawer, optimistic add/remove/update mutations, empty states, sticky footer with subtotal
- `src/components/cart/CartItem.tsx` — Thumbnail, name, unit price, quantity +/- controls, remove button

---

### Phase 3 — Checkout & Orders ✅ COMPLETE

**Step 3.1 — Order Components**
- `src/components/order/OrderStatusBadge.tsx` — Chip with saga status colors (REQUESTED/INVENTORY_RESERVED/CONFIRMED/CANCELLED)
- `src/components/order/OrderTimeline.tsx` — 3-step vertical stepper, 3s polling via refetchInterval, pulse animation on active step, cancel error state

**Step 3.2 — Customer Pages**
- `src/pages/customer/CheckoutPage.tsx` — MUI Stepper (3 steps): address form → payment method → review + place order → live timeline tracking
- `src/pages/customer/OrdersPage.tsx` — Orders table with reference, amount, payment method, view button
- `src/pages/customer/OrderDetailPage.tsx` — Order + lines table + summary sidebar
- `src/pages/customer/DashboardPage.tsx` — 3 stat cards + recent 5 orders list
- `src/pages/customer/ProfilePage.tsx` — Customer profile GET/PUT form

---

### Phase 4 — Seller Dashboard ✅ COMPLETE

- `src/components/data-display/DataTable.tsx` — Generic MUI Table wrapper with pagination and row actions
- `src/pages/seller/SellerDashboard.tsx` — Revenue, product count, low-stock alerts
- `src/pages/seller/ProductManagement.tsx` — Product DataTable with edit/delete/create actions
- `src/pages/seller/ProductForm.tsx` — RHF+Zod create/edit form (create: POST /products/create, edit: PUT /products/update/{id})
- `src/pages/seller/InventoryPage.tsx` — Low-stock filter view
- `src/pages/seller/OrderManagement.tsx` — Read-only order table

---

### Phase 5 — Admin Dashboard ✅ PARTIAL

- `src/pages/admin/AdminDashboard.tsx` — Platform stats (tenant count, revenue, user count)
- `src/pages/admin/TenantsPage.tsx` — Tenant DataTable with plan/status badges and suspend/reactivate/delete actions

**Still missing from Phase 5:**
- `TenantDetailPage.tsx` — Tabbed detail (Overview / Feature Flags / Usage charts)
- `UsersPage.tsx` — Customer list DataTable
- `PaymentsPage.tsx` — Payment history DataTable
- `AnalyticsPage.tsx` — Recharts usage/revenue line charts

---

### Phase 6 — Polish & Ship ⬜ NOT STARTED

- Nginx configuration
- Dockerfile (multi-stage)
- docker-compose frontend service
- GitHub Actions pipeline
- E2E tests (Playwright)
- MSW mock handlers
- Accessibility audit
- Performance audit / bundle analysis

---

## Files Created / Modified

### Configuration Files Modified

| File | Change |
|------|--------|
| `frontend/index.html` | Google Fonts preloads, Obsidian Market title, theme-color meta |
| `frontend/vite.config.ts` | Tailwind plugin, path aliases, dev proxy → :8222, manual chunks |
| `frontend/tsconfig.app.json` | baseUrl + paths, noUnusedLocals/Params: false |
| `frontend/src/index.css` | Full rewrite: Tailwind + CSS custom properties + grain overlay |

### New Files Created (58 files)

```
frontend/src/
├── api/
│   ├── client.ts            Axios instance + JWT interceptor + refresh mutex
│   ├── types.ts             All TypeScript DTOs matching backend Java records
│   ├── auth.api.ts          register / login / refresh / logout
│   ├── products.api.ts      getAll (paginated) / getById / create / update / delete / purchase
│   ├── cart.api.ts          get / addItem / updateQuantity / removeItem / clear / checkout
│   ├── orders.api.ts        create (202) / getStatus / getAll / getById / getLines
│   ├── customers.api.ts     getAll / getById / getByEmail / create / update / delete
│   ├── payments.api.ts      getAll / getById
│   └── tenants.api.ts       CRUD + plan + suspend/reactivate + feature flags + usage metrics
├── stores/
│   ├── auth.store.ts        JWT tokens, userId, email, role, tenantId — persisted
│   ├── ui.store.ts          themeMode, sidebarOpen, toastQueue — theme persisted
│   └── tenant.store.ts      activeTenant, featureFlags, isFeatureEnabled()
├── theme/
│   ├── tokens.ts            Color palettes, font families, radius, transitions
│   └── mui-theme.ts         createAppTheme(mode) — full MUI palette + overrides
├── utils/
│   ├── constants.ts         ROUTES + QUERY_KEYS
│   ├── format.ts            formatCurrency / formatDate / formatDateTime / truncate
│   └── jwt.ts               decodeJwt / isTokenExpired
├── components/
│   ├── layout/
│   │   ├── Navbar.tsx       Top nav: logo, catalog, theme toggle, cart badge, user menu
│   │   ├── Footer.tsx       Brand, nav links, copyright
│   │   ├── Sidebar.tsx      Permanent (desktop) / temporary overlay (mobile), active highlight
│   │   └── MobileNav.tsx    Bottom tab bar: Home/Catalog/Cart/Account
│   ├── product/
│   │   ├── ProductCard.tsx  4:3 image, category, serif name, mono price, hover, out-of-stock
│   │   └── ProductGrid.tsx  Responsive 1→2→3→4 col grid, add-to-cart with invalidation
│   ├── cart/
│   │   ├── CartDrawer.tsx   420px slide drawer, optimistic mutations, sticky footer
│   │   └── CartItem.tsx     Thumbnail, name, unit price, qty controls, remove
│   ├── order/
│   │   ├── OrderStatusBadge.tsx  Saga status chip (4 states)
│   │   └── OrderTimeline.tsx     3-step stepper, 3s polling, pulse on active
│   ├── data-display/
│   │   └── DataTable.tsx    Generic MUI Table with pagination + row actions
│   └── feedback/
│       ├── Toast.tsx        AnimatePresence stack, 4 variants, progress bar
│       ├── ErrorBoundary.tsx  getDerivedStateFromError, home recovery
│       ├── LoadingSkeleton.tsx  4 skeleton variants (ProductCard/Grid/Table/StatCard)
│       ├── EmptyState.tsx   Icon + title + description + optional CTA
│       └── ConfirmDialog.tsx  Modal confirm with destructive variant
├── pages/
│   ├── public/
│   │   ├── LoginPage.tsx    RHF+Zod, show/hide password, server errors, redirect
│   │   ├── RegisterPage.tsx RHF+Zod, field-level errors, 409 handling
│   │   ├── HomePage.tsx     Hero + featured 8 products + CTA
│   │   ├── CatalogPage.tsx  Paginated grid, product count, MUI Pagination
│   │   ├── ProductPage.tsx  2-col detail, qty selector, add-to-cart
│   │   └── NotFoundPage.tsx 404 with large mono numeral
│   ├── customer/
│   │   ├── DashboardPage.tsx    3 stat cards + recent 5 orders
│   │   ├── OrdersPage.tsx       Orders table with view links
│   │   ├── OrderDetailPage.tsx  Order + lines + summary sidebar
│   │   ├── CheckoutPage.tsx     3-step wizard → live OrderTimeline
│   │   └── ProfilePage.tsx      Customer GET/PUT form
│   ├── seller/
│   │   ├── SellerDashboard.tsx  Revenue + product count + low-stock
│   │   ├── ProductManagement.tsx  CRUD DataTable
│   │   ├── ProductForm.tsx      Create/edit with Zod
│   │   ├── InventoryPage.tsx    Low-stock filter
│   │   └── OrderManagement.tsx  Read-only order table
│   └── admin/
│       ├── AdminDashboard.tsx   Platform stats
│       └── TenantsPage.tsx      Tenant DataTable + actions
└── routes/
    ├── ProtectedRoute.tsx   Auth guard + redirect with state.from
    ├── RoleRoute.tsx        RBAC guard — USER/SELLER/ADMIN
    └── layouts/
        ├── PublicLayout.tsx     Navbar + Footer + CartDrawer
        ├── CustomerLayout.tsx   Same as public
        ├── SellerLayout.tsx     Navbar + Sidebar (4 items)
        └── AdminLayout.tsx      Navbar + Sidebar (5 items)
```

---

## Current State of the Project

The frontend is **not yet runnable**. All 58 feature files exist, but the entry point wiring is missing:

- `src/App.tsx` — Still contains the Vite demo counter
- `src/main.tsx` — Still the Vite default (no providers, no router)
- `src/routes/index.tsx` — Does not exist yet (no createBrowserRouter call)

**Everything is built; the routing glue is what connects it all.**

---

## What is Missing / Next Steps

### Priority 1 — Make the app runnable (next session start here)

- [ ] **`src/main.tsx`** — Wire up:
  ```tsx
  ReactDOM.createRoot(...).render(
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={createAppTheme(themeMode)}>
        <CssBaseline />
        <ErrorBoundary>
          <RouterProvider router={router} />
          <ToastStack />
        </ErrorBoundary>
      </ThemeProvider>
    </QueryClientProvider>
  )
  ```
- [ ] **`src/routes/index.tsx`** — `createBrowserRouter` with all nested routes (public / customer / seller / admin / 404)

### Priority 2 — Complete Phase 5 (Admin pages)

- [ ] `src/pages/admin/TenantDetailPage.tsx` — Tabbed (Overview → plan/suspend; Flags → toggle each; Usage → Recharts line chart)
- [ ] `src/pages/admin/UsersPage.tsx` — Customer list via `GET /customers`
- [ ] `src/pages/admin/PaymentsPage.tsx` — Payment history via `GET /payments`
- [ ] `src/pages/admin/AnalyticsPage.tsx` — Usage/revenue Recharts charts via `GET /tenants/{id}/usage/range`

### Priority 3 — Phase 6 (Deploy & Ship)

- [ ] `nginx/default.conf` — gzip, caching headers, API proxy to gateway, SPA fallback
- [ ] `Dockerfile` — multi-stage: `node:20-alpine` build → `nginx:1.25-alpine` serve
- [ ] `docker-compose.yml` — add `frontend` service (port 80, depends_on gateway, services-net)
- [ ] `.github/workflows/frontend.yml` — lint → test → build → e2e pipeline

### Priority 4 — Testing

- [ ] Install Vitest + Testing Library + MSW
- [ ] `src/test/setup.ts` — global test configuration
- [ ] `src/test/mocks/handlers.ts` — MSW handlers matching all backend endpoints
- [ ] E2E: `e2e/auth.spec.ts`, `e2e/checkout.spec.ts`, `e2e/admin.spec.ts`

### Priority 5 — Optional Refactors

- [ ] Extract `src/hooks/useAuth.ts`, `useCart.ts`, `useProducts.ts`, `useOrders.ts`
- [ ] Populate `src/components/forms/` with extracted form components
- [ ] Add `src/components/product/ProductDetail.tsx` and `ProductQuickView.tsx`
- [ ] Add `src/components/cart/CartSummary.tsx`

---

## Deviations from Skill Instructions

| Item | Skill Plan | Actual | Reason |
|------|-----------|--------|--------|
| `src/routes/index.tsx` | Specified as separate router file | Not yet created | Session ended before wiring phase |
| `App.tsx` | Router outlet only | Still Vite placeholder | Session ended before wiring phase |
| `main.tsx` | All providers mounted | Still Vite default | Session ended before wiring phase |
| `UsersPage`, `PaymentsPage`, `AnalyticsPage` | Phase 5 deliverables | Missing | Phase 5 was partial |
| `TenantDetailPage` (tabbed) | Phase 5 | Missing | Most complex admin page, deferred |
| Custom hooks in `src/hooks/` | Listed in folder structure | Empty | Not extracted from pages yet |
| `src/components/forms/` | Listed in folder structure | Empty | Forms embedded inline in pages |
| Phase 6 (all) | Week 11–12 | Not started | Session scope ended at Phase 5 partial |

---

## Quick Reference — Key Patterns to Continue With

```typescript
// Add toast (used everywhere)
const addToast = useUIStore(s => s.addToast);
addToast({ message: 'Done', variant: 'success' });

// Protected query
const { data } = useQuery({
  queryKey: [QUERY_KEYS.CART, userId],
  queryFn: () => cartApi.get(userId!),
  enabled: isAuthenticated && !!userId,
});

// Optimistic cart mutation pattern
const { mutate } = useMutation({
  mutationFn: ...,
  onMutate: async () => { /* snapshot + optimistic update */ },
  onError: (_err, _vars, ctx) => { /* restore snapshot */ },
  onSettled: () => queryClient.invalidateQueries(...),
});

// Role-aware navigation
const role = useAuthStore(s => s.role);
const dashboardRoute = role === 'ADMIN' ? ROUTES.ADMIN
  : role === 'SELLER' ? ROUTES.SELLER : ROUTES.ACCOUNT;

// Theme creation (in main.tsx)
const { themeMode } = useUIStore();
const theme = createAppTheme(themeMode); // from src/theme/mui-theme.ts
```

---

## Verification Checklist (Next Session)

```bash
cd frontend

# 1. Install any missing deps
npm install

# 2. Type-check
npx tsc --noEmit

# 3. Start dev server
npm run dev
# → Should start at http://localhost:3001
# → / → HomePage (featured products from GET /api/v1/products)
# → /login → LoginPage
# → /register → RegisterPage
# → /catalog → CatalogPage (paginated)
# → /catalog/1 → ProductPage
# → /account → redirect to /login (ProtectedRoute)
# → /seller → redirect to /login (RoleRoute)
# → /admin → redirect to /login (RoleRoute)

# 4. Build
npm run build
# → dist/ should be produced with vendor/mui/query/charts chunks
```
