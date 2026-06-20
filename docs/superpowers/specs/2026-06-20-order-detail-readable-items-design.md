# Order Detail — Readable Line Items (frontend-only)

**Date:** 2026-06-20
**Branch:** fix/order-details-missing-line-items
**Scope decision:** Option A (user-approved) — show product **name + image + quantity**. No variant, no per-item status, no price-per-line.

## Problem

The order-history detail page ("Order items") shows only `#{productId}` + quantity. A user cannot tell *what they bought* — no name, no image. This is unacceptable for a production e-commerce order history.

## Why the other requested fields were excluded (evidence-based)

The user originally asked for name, image, variant, quantity, unit price at purchase, item total, and per-item status. Verified against code:

| Field | Status | Evidence |
|---|---|---|
| Product name | available | product-service `GET /products/{id}` → `name` |
| Product image | available | `ProductResponse.imageUrl` (+ category fallback) |
| Quantity | available | `OrderLine.quantity` |
| Unit price at purchase | NOT persisted | checkout sends only `productId`+`quantity` ([CheckoutPage.tsx:124]); `OrderLine` stores no price; only order `totalAmount` saved |
| Item total at purchase | derivable only from current catalog price | same root cause |
| Variant (color/size) | does not exist | no variant model in `Product` or `OrderLine` |
| Per-item status | does not exist | only one order-level saga status (`OrderStatus`); no fulfillment subsystem |

Variant, per-item status, and price snapshot require new persistence/subsystems and would not fix existing orders. Deferred. This spec covers only the immediately deliverable, evidence-backed fields.

## Design

Frontend-only. No backend change, no DB migration. Read-only against the existing, proven `GET /api/v1/products/{id}`.

### Data flow (`OrderDetailPage.tsx`)
1. Keep existing `lines` query (`OrderLineResponse[]` = `id, productId, quantity`).
2. Add enrichment via TanStack **`useQueries`**: one query per distinct `line.productId`, calling `productsApi.getById(productId)`, keyed `[QUERY_KEYS.PRODUCT, productId]` (shared cache, deduped with the catalog).
3. Build a `Map<number, ProductResponse>` from results and render rows from it.

### Component boundary — `frontend/src/components/order/OrderItemRow.tsx`
- Props: `{ line: OrderLineResponse; product?: ProductResponse; isLoading: boolean }`.
- Owns its own image-error state (mirrors `ProductCard`'s `imgError`), so it can run the image fallback ladder per row without hooks-in-`.map()`.
- Renders: thumbnail (`product.imageUrl` → `getCategoryFallbackImage(product.categoryName)` → initials gradient, reusing `@utils/productImages`), product **name** as primary text, `#{productId}` small/mono as secondary reference, **quantity**.

### States / error handling
- Loading a product → row shows a placeholder (`#id` + muted "Loading…") until resolved; the page never blocks on product fetches.
- Product 404 / deleted / fetch failure → fallback: name = `Product #{productId}` + placeholder image. All rows still render.
- Order + lines query failures unchanged.

### Table change
- "Product ID" column → "Product" (thumbnail + name + small id). "Quantity" stays.

## Testing
- Vitest + MSW: add a `/products/:id` handler; assert the row renders product name, image `alt`, and quantity; add a 404 → `Product #id` fallback case.
- Windows caveat (memory `reference_rolldown_win32_binding`): if Vitest/rolldown can't run on Windows, fall back to `tsc -b --noEmit` for a type check and report runtime as **unverified locally** — user verifies in prod.

## Out of scope
Variant, per-item fulfillment status, price-at-purchase snapshot. Backend untouched. (Note: backend `productId`/`OrderLineResponse.productId` already added in a prior change on this branch; no further backend work here.)

## Process note
Per user memory rule, the spec is **not** git-committed (overrides the brainstorming skill's commit step).
