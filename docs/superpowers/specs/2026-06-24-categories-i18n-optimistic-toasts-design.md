# Design — Categories, Optimistic Toasts, i18n, Latency Audit

**Date:** 2026-06-24
**Branch:** fix/customers-order-and-payment-03
**Status:** Approved (user approved design + seed-sample-products=yes + sequence A→B→C)

## Problem (user request, paraphrased)

1. Product categories are incomplete and one is unrealistic ("Screens" duplicates "Monitors").
   Missing: Motherboards, Processors, Headphones, Graphics Cards, Headsets, Memory, Storage, Power Supplies.
   Categories must carry correct images.
2. UI notifications ("product created", "item added to cart", "welcome", "order confirmed")
   feel slow — system feels sluggish ("camaleão").
3. No language selection. A serious e-commerce must let users pick EN / FR / PT / ES from the initial menu.
4. Services must respond in ms, not seconds; the product list must appear fast.

## Evidence (proven in code)

- Categories are backend-driven: `GET /products/categories` → `productsApi.getCategories`
  (`frontend/src/api/products.api.ts:50`). Seed `V2__insert_category_tables.sql` has only 5
  (Keyboards, Monitors, Screens, Mice, Accessories). "Screens" has 5 seeded products
  (`V3__insert_products_tables.sql:18-25`) → merge must re-point those rows first.
- Toasts are client-side and instant: `useUIStore.addToast` is a synchronous Zustand update,
  zero network (`frontend/src/stores/ui.store.ts:44`). Perceived slowness = waiting for the
  API round-trip before firing the toast in `onSuccess`.
- No i18n exists anywhere in `frontend/src`.
- `CatalogPage` already debounces, sets staleTime, and uses placeholderData — frontend list
  fetch is already optimal; any real latency is server-side.

## Decisions (locked via menu)

- Categories: **merge "Screens" into "Monitors", keep the other 4, add the 8 new** with correct images.
- Notifications: **optimistic toast UI only** (frontend). Emails out of scope.
- i18n: **react-i18next, switcher in the top nav, translate nav + key surfaces first**
  (navbar, catalog, cart, checkout, auth); rest stays English, incremental.
- Seed sample products for the 8 new categories: **yes**.
- Sequence: **A → B → C**, with D as a measurement-only task.

## Workstream A — Categories (backend data + frontend images)

`product-service` new forward-only Flyway migration `V10__merge_screens_add_hardware_categories.sql`:
1. `UPDATE product SET category_id = (SELECT id FROM category WHERE name='Monitors') WHERE category_id = (SELECT id FROM category WHERE name='Screens');`
2. `DELETE FROM category WHERE name='Screens';`
3. `INSERT` the 8 new categories, idempotent `ON CONFLICT (name) DO NOTHING`, matching existing seed style.
4. Seed a small realistic product set per new category, with `image_url` (pattern of V3 + V8).

Frontend `frontend/src/utils/productImages.ts`: add normalized image keys
`motherboards, processors, headphones, graphicscards, headsets, memory, powersupplies`
(`storage`, `power` already exist). Every image URL validated to load before commit (no guessed URLs).

**Verify:** `mvn test -pl product-service`; `cd frontend && npm run build && npm run lint`.

## Workstream B — Optimistic toasts (frontend only)

Convert user-facing mutations (add-to-cart, cart qty update/remove, product-create) from
`onSuccess`-fired toasts to optimistic: fire success toast + update cached UI on `onMutate`,
reconcile or roll back + error toast on `onError`/`onSettled` (TanStack Query). No backend change.

**Verify:** build + lint + `npx playwright test` (full suite must stay green).

## Workstream C — i18n (frontend only)

Add `react-i18next` + `i18next-browser-languagedetector`. Locale resources `en/fr/pt/es`.
Language switcher in `Navbar.tsx`, persisted to localStorage, default English (untranslated
keys fall back to English so nothing disappears). Translate navbar, catalog, cart drawer,
checkout, login/register first.

**Verify:** build + lint + `npx playwright test`.

## Workstream D — Latency audit (measure, no speculative change)

Once the stack is up, capture real `/products/search` timing. If server-side, it ties to the
existing TimeLimiter / BCrypt / stale-connection notes — not new work. No code change unless
evidence demands it.

## Guardrails

- Migration forward-only and additive; existing products preserved.
- Optimistic changes isolated to mutation hooks; rollback keeps data correct.
- i18n defaults to English; no string vanishes.
- No Docker run by me — user rebuilds for live proof.
- No git commit/push — work stays on the user's branch.
