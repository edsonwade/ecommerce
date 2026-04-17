# RBAC: Admin / Seller / User — Design Spec

**Date:** 2026-04-17  
**Status:** Approved  
**Scope:** Authentication bootstrap, per-service JWT validation, role-based endpoint enforcement, ownership checks, frontend role visibility and user management UI

---

## 1. Role Model

| Role | Who | Capabilities |
|------|-----|-------------|
| `ADMIN` | Platform operator | Full CRUD across all services and all tenants; user role management; tenant lifecycle |
| `SELLER` | Merchant within a tenant | CRUD on own products/inventory; view own orders and payments |
| `USER` | End customer | Browse products (public); cart; checkout; own orders and profile |

All new registrations default to `USER`. Role promotion is performed by an `ADMIN` via a secured endpoint.

---

## 2. Trust Model (Production-Grade)

Three defense layers:

### Layer 1 — Network Isolation
Services are on `services-net` (Docker / K8s). Not reachable from the public internet directly. Already in place.

### Layer 2 — JWT Passthrough + Per-Service Validation
The gateway:
1. Validates the incoming JWT (signature + expiry)
2. Forwards the original `Authorization: Bearer <token>` header to every downstream service unchanged

Each microservice:
1. Independently re-validates the JWT signature using the **same secret from Vault** (`secret/jwt/signing-key`)
2. Extracts `sub` (userId), `role`, and `tenantId` directly from the verified claims
3. Never trusts `X-User-Role` / `X-User-ID` headers as a security source (those headers are convenience extras for logging only)

### Layer 3 — Spring Security Resource Server per Service
Each service has a `JwtSecurityConfig` + `JwtAuthConverter` that:
- Reads the Vault JWT secret via Spring Cloud Config
- Validates the JWT on every secured request
- Maps the `role` claim → `ROLE_ADMIN` / `ROLE_SELLER` / `ROLE_USER` `GrantedAuthority`
- Enables `@PreAuthorize` / `@PostAuthorize` natively

```
Client → Gateway (validates JWT, forwards Authorization header)
              ↓
         Each Service (re-validates JWT from Authorization header via Vault secret)
              ↓
         @PreAuthorize checks verified role claim
```

---

## 3. Admin Bootstrap

**Component:** `AdminBootstrapRunner` (`ApplicationRunner`) in `authentication-service`

**Behaviour:**
1. On startup, queries DB: `SELECT COUNT(*) FROM app_user WHERE role = 'ADMIN'`
2. If count == 0, creates one admin user:
   - Email: `$ADMIN_EMAIL` (default: `admin@platform.com`)
   - Password: `$ADMIN_PASSWORD` (default: `Admin@123!`) — BCrypt cost 12
   - Role: `ADMIN`
   - TenantId: system tenant
3. Logs a **`WARN`-level** message if default credentials are used: `"[SECURITY] Admin account created with DEFAULT credentials — rotate immediately"`
4. Idempotent — safe on every restart

**Environment variables** (set in `docker-compose.yml` and K8s secrets):
```
ADMIN_EMAIL=admin@platform.com
ADMIN_PASSWORD=Admin@123!
```

---

## 4. Role Management Endpoint

**Endpoint:** `PATCH /api/v1/auth/users/{userId}/role`  
**Auth:** `ADMIN` JWT required  
**Body:** `{ "role": "SELLER" | "USER" | "ADMIN" }`  
**Guards:**
- Admin cannot change their own role (prevents self-lockout)
- Audit log entry written: `changedBy`, `targetUserId`, `previousRole`, `newRole`, `timestamp`

---

## 5. Backend RBAC Enforcement

### 5.1 Shared Infrastructure (tenant-context library)

New class: `JwtAuthConverter` — reads Vault secret, validates JWT, maps `role` claim to `GrantedAuthority`.  
New class: `MicroserviceSecurityConfig` — base security config used by all services:
- Stateless session
- Permits `/actuator/health`, `/swagger-ui/**`, `/v3/api-docs/**`
- All other requests require authentication
- Installs `JwtAuthConverter`

Each service imports `tenant-context` and extends or includes `MicroserviceSecurityConfig`.

### 5.2 Product Service

Product entity additions:
- `tenantId` (String, non-null) — populated from verified JWT `tenantId` claim on create
- `createdBy` (String, non-null) — populated from verified JWT `sub` claim on create
- `updatedBy` (String) — updated on every mutation

| Endpoint | Roles | Ownership |
|----------|-------|-----------|
| `GET /api/v1/products` | Public | — |
| `GET /api/v1/products/{id}` | Public | — |
| `POST /api/v1/products/create` | `SELLER`, `ADMIN` | Sets `createdBy = sub`, `tenantId` from claim |
| `PUT /api/v1/products/update/{id}` | `SELLER` (own), `ADMIN` (any) | SELLER: `createdBy == sub` |
| `DELETE /api/v1/products/delete/{id}` | `SELLER` (own), `ADMIN` (any) | SELLER: `createdBy == sub` |
| `POST /api/v1/products/purchase` | Authenticated | — |

### 5.3 Cart Service

| Endpoint | Roles | Ownership |
|----------|-------|-----------|
| All cart endpoints | `USER`, `SELLER`, `ADMIN` | `customerId` in path must match `sub` claim (ADMIN exempt) |

### 5.4 Order Service

| Endpoint | Roles | Ownership |
|----------|-------|-----------|
| `POST /api/v1/orders` | `USER` | Sets `customerId = sub` |
| `GET /api/v1/orders` | `USER` (own), `ADMIN` (all) | USER: filtered by `customerId == sub` |
| `GET /api/v1/orders/{id}` | `USER` (own), `ADMIN` | Ownership check |

### 5.5 Payment Service

| Endpoint | Roles | Ownership |
|----------|-------|-----------|
| `GET /api/v1/payments` | `ADMIN` only | — |
| `GET /api/v1/payments/{id}` | `ADMIN` or owner | Ownership check |

### 5.6 Tenant Service

| Endpoint | Roles |
|----------|-------|
| All lifecycle endpoints (create, update, suspend, delete) | `ADMIN` only |
| `GET /api/v1/tenants/{id}` | Authenticated |

### 5.7 Customer Service

| Endpoint | Roles | Ownership |
|----------|-------|-----------|
| `GET /api/v1/customers` | `ADMIN` only | — |
| `GET /api/v1/customers/{id}` | `ADMIN` or self (`sub == id`) | — |
| `PUT /api/v1/customers/{id}` | `ADMIN` or self | — |

---

## 6. Frontend Changes

### 6.1 Role Badge
- Navbar displays a role badge (colour-coded: red=ADMIN, blue=SELLER, grey=USER) next to avatar
- Always visible so the operator knows which account is active

### 6.2 Admin Dashboard Additions
- **User Management tab** — paginated table of all users, role badge per row, promote/demote dropdown calling `PATCH /api/v1/auth/users/{id}/role`
- **Product Management tab** — full CRUD across all products (admin view, no ownership filter)
- **Tenant Management tab** — already exists; now backed by secured endpoints

### 6.3 Seller Dashboard
- Product management filtered to `createdBy == me` only
- No access to payment list, customer list, or tenant management

### 6.4 User (Customer) Experience
- Product listing: public, no auth required
- Cart / checkout: requires authenticated `USER` — redirects to `/login` if not
- My Orders / My Profile: ownership enforced server-side

### 6.5 Route Guards (complete existing partial implementation)
| Route pattern | Guard |
|---------------|-------|
| `/admin/*` | `ADMIN` only |
| `/seller/*` | `SELLER` or `ADMIN` |
| `/account/*` | Any authenticated user |
| `/products/*` | Public |

### 6.6 Error Handling
- `403 Forbidden` response → toast: "You don't have permission to perform this action"
- No silent failures, no infinite redirect loops

---

## 7. Data Model Changes

### Product entity (product-service)
```
+ tenantId     VARCHAR(255) NOT NULL
+ createdBy    VARCHAR(255) NOT NULL   -- userId (sub claim)
+ updatedBy    VARCHAR(255)
```
Migration: Flyway `V{next}__add_product_ownership_fields.sql`

### AuditLog entity (authentication-service)
```
+ id           UUID PK
+ changedBy    VARCHAR(255)
+ targetUserId VARCHAR(255)
+ previousRole VARCHAR(50)
+ newRole       VARCHAR(50)
+ timestamp    TIMESTAMPTZ
```
Migration: Flyway `V{next}__add_role_audit_log.sql`

---

## 8. Testing

- **Unit tests**: `@WebMvcTest` with mock JWT for each role; assert 200/403/404 per scenario
- **Integration tests**: Testcontainers + real PostgreSQL; full login → action flow per role
- **BDD scenarios** (Cucumber): "Given I am logged in as ADMIN / SELLER / USER, When I call X, Then I get Y"

---

## 9. Out of Scope

- Email-verification flow on registration (deferred to Phase 4)
- SELLER self-registration with invite token (future)
- Fine-grained permission scopes beyond role-level (future)
