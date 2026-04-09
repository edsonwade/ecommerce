# E-Commerce Microservice Platform — API Reference

> **Version:** 1.0
> **Base URL:** `http://localhost:8222` (API Gateway)
> **API Style:** RESTful, JSON over HTTP
> **Versioning:** Path-based (`/api/v1/...`)

---

## Table of Contents

- [Authentication & Authorization](#authentication--authorization)
- [Multi-Tenancy](#multi-tenancy)
- [Common Conventions](#common-conventions)
- [Gateway & Resilience](#gateway--resilience)
- [Authentication Service](#authentication-service)
- [Tenant Service](#tenant-service)
- [Customer Service](#customer-service)
- [Product Service](#product-service)
- [Cart Service](#cart-service)
- [Order Service](#order-service)
- [Payment Service](#payment-service)
- [Enumerations](#enumerations)
- [Event-Driven Architecture](#event-driven-architecture)
- [Infrastructure Services](#infrastructure-services)
- [Swagger UI](#swagger-ui)
- [Service Port Reference](#service-port-reference)

---

## Authentication & Authorization

All API requests (except public endpoints) require a valid JWT Bearer token in the `Authorization` header.

### Token Lifecycle

| Token | Lifetime | Purpose |
|-------|----------|---------|
| Access Token | 24 hours | Authenticates API requests |
| Refresh Token | 7 days | Obtains a new access + refresh token pair |

### Authentication Flow

```
1. POST /api/v1/auth/register   -->  { accessToken, refreshToken }
          or
   POST /api/v1/auth/login      -->  { accessToken, refreshToken }

2. All subsequent requests:
   Authorization: Bearer <accessToken>

3. When accessToken expires:
   POST /api/v1/auth/refresh
   Authorization: Bearer <refreshToken>
   -->  { new accessToken, new refreshToken }

4. Logout (revokes all tokens for the user):
   POST /api/v1/auth/logout
   Authorization: Bearer <accessToken>
```

### Request Headers

| Header | Required | Description |
|--------|----------|-------------|
| `Authorization` | Yes (protected endpoints) | `Bearer <accessToken>` |
| `X-Tenant-Id` | Auto-injected by gateway | Tenant identifier extracted from JWT `tenantId` claim |
| `X-Request-Id` | Optional | Client correlation ID; auto-generated UUID if absent |
| `Content-Type` | Yes (request bodies) | `application/json` |

### Public Endpoints (No Authentication)

```
POST   /api/v1/auth/register
POST   /api/v1/auth/login
POST   /api/v1/auth/refresh
GET    /actuator/health
GET    /actuator/info
GET    /swagger-ui/**
GET    /api-docs/**
GET    /fallback/**
```

### CORS Policy

| Setting | Value |
|---------|-------|
| Allowed Origins | `*` (all origins) |
| Allowed Methods | `GET, POST, PUT, PATCH, DELETE, OPTIONS` |
| Allowed Headers | `*` |
| Allow Credentials | `true` |

---

## Multi-Tenancy

Every authenticated request is scoped to a tenant. The gateway extracts `tenantId` from the JWT and injects it as the `X-Tenant-Id` header to all downstream services.

### Tenant Validation Flow

1. `JwtAuthenticationFilter` extracts `tenantId` from JWT claims
2. `TenantValidationFilter` calls `GET /api/v1/tenants/{tenantId}/validate`
3. If tenant status is `ACTIVE` — request proceeds
4. If tenant status is `SUSPENDED` — **403 Forbidden**
5. If tenant status is `CANCELLED` — **403 Forbidden**
6. If tenant not found — **404 Not Found**

### Subscription Plans & Rate Limits

| Plan | Rate Limit | Storage Quota |
|------|-----------|---------------|
| `FREE` | 100 req/min | 1 GB |
| `STARTER` | 1,000 req/min | 10 GB |
| `GROWTH` | 10,000 req/min | 100 GB |
| `ENTERPRISE` | Unlimited | Unlimited |

---

## Common Conventions

### Standard Error Response

All services return errors in this format:

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "customer.validation.failed",
  "message": "Validation failed for one or more fields",
  "path": "/api/v1/customers",
  "fieldErrors": {
    "email": "must be a well-formed email address",
    "firstname": "must not be null"
  }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `timestamp` | `string` (ISO 8601) | When the error occurred |
| `status` | `integer` | HTTP status code |
| `error` | `string` | HTTP reason phrase |
| `errorCode` | `string` | Machine-readable error key from `messages.properties` |
| `message` | `string` | Human-readable error description |
| `path` | `string` | Request path that caused the error |
| `fieldErrors` | `object` (optional) | Per-field validation errors (only on 400) |

### HTTP Status Codes

| Code | Usage |
|------|-------|
| `200 OK` | Successful read or update |
| `201 Created` | Resource created successfully |
| `202 Accepted` | Async operation accepted (order saga) |
| `204 No Content` | Successful delete or logout |
| `400 Bad Request` | Validation error or malformed input |
| `401 Unauthorized` | Missing or invalid JWT token |
| `403 Forbidden` | Tenant suspended/cancelled or insufficient permissions |
| `404 Not Found` | Resource does not exist |
| `409 Conflict` | Duplicate resource (email, slug, reference) |
| `422 Unprocessable Entity` | Business rule violation (insufficient stock) |
| `429 Too Many Requests` | Rate limit exceeded |
| `500 Internal Server Error` | Unexpected server error |
| `503 Service Unavailable` | Downstream service down or load shedding active |

### Pagination

Paginated endpoints return a Spring `Page<T>` envelope:

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | `integer` | `0` | Zero-based page index |
| `size` | `integer` | `20` | Page size |
| `sort` | `string` | `id,asc` | Sort field and direction (`name,desc`) |

**Response Envelope:**

```json
{
  "content": [ ... ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": { "sorted": true, "unsorted": false, "empty": false }
  },
  "totalElements": 150,
  "totalPages": 8,
  "size": 20,
  "number": 0,
  "first": true,
  "last": false,
  "empty": false
}
```

---

## Gateway & Resilience

All client traffic enters through the API Gateway (`localhost:8222`). The gateway applies the following filter chain in order:

### Filter Chain (Execution Order)

| Order | Filter | Purpose |
|-------|--------|---------|
| `HIGHEST + 0` | `RequestIdFilter` | Injects `X-Request-Id` (UUID) if not present |
| `HIGHEST + 10` | `JwtAuthenticationFilter` | Validates JWT; injects `X-User-Id`, `X-Tenant-Id`, `X-User-Role` |
| `HIGHEST + 20` | `TenantValidationFilter` | Validates tenant status via tenant-service |
| `HIGHEST + 20` | `LoadSheddingFilter` | Rejects requests when >5,000 concurrent connections |

### Gateway Route Table

| Route ID | Path | Target Service | Circuit Breaker | Rate Limit | Retry |
|----------|------|----------------|-----------------|------------|-------|
| `auth-service` | `/api/v1/auth/**` | `AUTHENTICATION-SERVICE` | None | 100/s default | None |
| `customer-service` | `/api/v1/customers/**` | `CUSTOMER-SERVICE` | `customer-cb` | 100/s default | 2x (100ms→500ms) |
| `cart-service` | `/api/v1/carts/**` | `CART-SERVICE` | `cart-cb` | 200/s custom | 1x (50ms→200ms) |
| `order-service` | `/api/v1/orders/**` | `ORDER-SERVICE` | `order-cb` | 100/s default | 1x (200ms→1s) |
| `order-lines-service` | `/api/v1/order-lines/**` | `ORDER-SERVICE` | `order-cb` | 100/s default | None |
| `product-service` | `/api/v1/products/**` | `PRODUCT-SERVICE` | `product-cb` | 100/s default | 2x (100ms→500ms) |
| `payment-service` | `/api/v1/payments/**` | `PAYMENT-SERVICE` | `payment-cb` | 20/s custom | None |

### Rate Limiting (Redis Token Bucket)

Per-tenant rate limiting via `X-Tenant-Id` header. Resolution priority:

1. `X-Tenant-Id` header → bucket key: `rl:tenant:{tenantId}`
2. `X-Forwarded-For` IP → bucket key: `rl:ip:{clientIp}`
3. Remote address → bucket key: `rl:ip:{remoteIp}`
4. Fallback → bucket key: `rl:anonymous`

| Service | Sustained (req/s) | Burst Capacity |
|---------|-------------------|----------------|
| Default (all routes) | 100 | 200 |
| Cart Service | 200 | 400 |
| Payment Service | 20 | 30 |

**429 Response:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 429,
  "error": "Too Many Requests",
  "errorCode": "gateway.ratelimit.exceeded",
  "message": "Rate limit exceeded. Try again later.",
  "path": "/api/v1/orders"
}
```

### Circuit Breakers (Resilience4j)

| Circuit Breaker | Sliding Window | Failure Threshold | Wait Duration | Half-Open Calls | Slow Call Threshold | Slow Call Duration |
|-----------------|---------------|-------------------|---------------|-----------------|---------------------|-------------------|
| `customer-cb` | 10 | 50% | 30s | 3 | 80% | 3s |
| `cart-cb` | 20 | 60% | 15s | 5 | 90% | 1s |
| `order-cb` | 10 | 50% | 30s | 3 | 80% | 3s |
| `product-cb` | 20 | 60% | 20s | 5 | 80% | 2s |
| `payment-cb` | 10 | 30% | 60s | 2 | 70% | 5s |

### Fallback Responses (503)

When a circuit breaker opens, the gateway returns:

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 503,
  "error": "Service Unavailable",
  "message": "Order service is temporarily unavailable. Please retry later.",
  "service": "order-service",
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "retryAfter": 30
}
```

### Response Headers (All Routes)

| Header | Value | Description |
|--------|-------|-------------|
| `API-Version` | `v1` | Current API version |
| `Sunset` | `TBD` | Deprecation date (future use) |
| `X-Request-Id` | UUID | Correlation ID for distributed tracing |

---

## Authentication Service

> **Base Path:** `/api/v1/auth`
> **Port:** `8085`
> **Database:** PostgreSQL
> **Swagger:** `http://localhost:8085/swagger-ui.html`
> **Description:** JWT-based authentication and authorization with BCrypt(12) password encoding and token rotation

### Endpoints Overview

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `POST` | `/api/v1/auth/register` | Register a new user | Public |
| `POST` | `/api/v1/auth/login` | Authenticate and get tokens | Public |
| `POST` | `/api/v1/auth/refresh` | Refresh token pair | Public |
| `POST` | `/api/v1/auth/logout` | Revoke all user tokens | Bearer |

---

### POST /api/v1/auth/register

Creates a new user account and returns a JWT access + refresh token pair.

**Auth:** None (public)

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `firstname` | `string` | Yes | `@NotBlank` | User's first name |
| `lastname` | `string` | Yes | `@NotBlank` | User's last name |
| `email` | `string` | Yes | `@NotBlank`, `@Email` | Unique email address |
| `password` | `string` | Yes | `@NotBlank`, `@Size(min=8)` | Minimum 8 characters |
| `tenantId` | `string` | No | — | Tenant identifier (defaults to `"default"`) |

**Example Request:**

```json
{
  "firstname": "John",
  "lastname": "Doe",
  "email": "john.doe@example.com",
  "password": "SecureP@ss123",
  "tenantId": "acme-corp"
}
```

**Success Response — `201 Created`:**

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJ0ZW5hbnRJZCI6ImFjbWUtY29ycCIsInJvbGUiOiJVU0VSIiwiaWF0IjoxNzEyNTc2MDAwLCJleHAiOjE3MTI2NjI0MDB9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiI1NTBlODQwMC1lMjliLTQxZDQtYTcxNi00NDY2NTU0NDAwMDAiLCJ0b2tlblR5cGUiOiJSRUZSRVNIIiwiaWF0IjoxNzEyNTc2MDAwLCJleHAiOjE3MTMxODA4MDB9...",
  "tokenType": "Bearer",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john.doe@example.com",
  "role": "USER",
  "tenantId": "acme-corp"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `auth.validation.failed` | Invalid or missing fields |
| `409` | `auth.email.already.exists` | Email address already registered |

**Error Example — `400 Bad Request`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "auth.validation.failed",
  "message": "Validation failed",
  "path": "/api/v1/auth/register",
  "fieldErrors": {
    "password": "size must be between 8 and 2147483647",
    "email": "must be a well-formed email address"
  }
}
```

**Error Example — `409 Conflict`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 409,
  "error": "Conflict",
  "errorCode": "auth.email.already.exists",
  "message": "A user with email john.doe@example.com already exists",
  "path": "/api/v1/auth/register"
}
```

---

### POST /api/v1/auth/login

Authenticates credentials and returns a JWT access + refresh token pair. All previous tokens for the user are revoked.

**Auth:** None (public)

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `email` | `string` | Yes | `@NotBlank`, `@Email` | Registered email |
| `password` | `string` | Yes | `@NotBlank` | Account password |

**Example Request:**

```json
{
  "email": "john.doe@example.com",
  "password": "SecureP@ss123"
}
```

**Success Response — `200 OK`:**

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john.doe@example.com",
  "role": "USER",
  "tenantId": "acme-corp"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `auth.validation.failed` | Invalid or missing fields |
| `401` | `auth.invalid.credentials` | Wrong email or password |

**Error Example — `401 Unauthorized`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 401,
  "error": "Unauthorized",
  "errorCode": "auth.invalid.credentials",
  "message": "Invalid email or password",
  "path": "/api/v1/auth/login"
}
```

---

### POST /api/v1/auth/refresh

Exchanges a valid refresh token for a new access + refresh token pair. The old token pair is revoked (token rotation). Validates that the token is of type `REFRESH` and has not been revoked.

**Auth:** None (public) — requires a valid refresh token in the `Authorization` header

**Request Headers:**

| Header | Value |
|--------|-------|
| `Authorization` | `Bearer <refreshToken>` |

**Example Request:**

```http
POST /api/v1/auth/refresh HTTP/1.1
Host: localhost:8222
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
```

**Success Response — `200 OK`:**

```json
{
  "accessToken": "eyJhbGciOiJSUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJSUzI1NiJ9...",
  "tokenType": "Bearer",
  "userId": "550e8400-e29b-41d4-a716-446655440000",
  "email": "john.doe@example.com",
  "role": "USER",
  "tenantId": "acme-corp"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `401` | `auth.token.expired` | Refresh token has expired |
| `401` | `auth.token.revoked` | Refresh token was revoked (e.g., after logout) |
| `401` | `auth.token.invalid` | Token is not a valid refresh token (e.g., access token was sent) |

**Error Example — `401 Unauthorized`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 401,
  "error": "Unauthorized",
  "errorCode": "auth.token.revoked",
  "message": "Token has been revoked",
  "path": "/api/v1/auth/refresh"
}
```

---

### POST /api/v1/auth/logout

Revokes all tokens for the authenticated user. Subsequent requests with any of the user's tokens are rejected.

**Auth:** Required — `Bearer <accessToken>`

**Example Request:**

```http
POST /api/v1/auth/logout HTTP/1.1
Host: localhost:8222
Authorization: Bearer eyJhbGciOiJSUzI1NiJ9...
```

**Success Response — `204 No Content`:**

_(empty body)_

---

## Tenant Service

> **Base Path:** `/api/v1/tenants`
> **Port:** `8095`
> **Database:** PostgreSQL
> **Swagger:** `http://localhost:8095/swagger-ui.html`
> **Description:** SaaS tenant lifecycle management, feature flags, and usage metering

### Endpoints Overview

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `POST` | `/api/v1/tenants` | Onboard new tenant | Bearer |
| `GET` | `/api/v1/tenants` | List all tenants | Bearer |
| `GET` | `/api/v1/tenants/{tenantId}` | Get tenant by ID | Bearer |
| `GET` | `/api/v1/tenants/by-slug/{slug}` | Get tenant by slug | Bearer |
| `GET` | `/api/v1/tenants/{tenantId}/validate` | Validate tenant (gateway) | Bearer |
| `PUT` | `/api/v1/tenants/{tenantId}` | Update tenant | Bearer |
| `PATCH` | `/api/v1/tenants/{tenantId}/plan` | Change subscription plan | Bearer |
| `PATCH` | `/api/v1/tenants/{tenantId}/suspend` | Suspend tenant | Bearer |
| `PATCH` | `/api/v1/tenants/{tenantId}/reactivate` | Reactivate tenant | Bearer |
| `DELETE` | `/api/v1/tenants/{tenantId}` | Delete tenant | Bearer |
| `GET` | `/api/v1/tenants/{tenantId}/flags` | List feature flags | Bearer |
| `PUT` | `/api/v1/tenants/{tenantId}/flags/{flagName}` | Set feature flag | Bearer |
| `GET` | `/api/v1/tenants/{tenantId}/flags/{flagName}/status` | Check flag status | Bearer |
| `POST` | `/api/v1/tenants/{tenantId}/usage` | Record usage metric | Bearer |
| `GET` | `/api/v1/tenants/{tenantId}/usage` | Query usage by date | Bearer |
| `GET` | `/api/v1/tenants/{tenantId}/usage/range` | Query usage by date range | Bearer |
| `GET` | `/api/v1/tenants/{tenantId}/usage/sum` | Sum metric over range | Bearer |

---

### POST /api/v1/tenants

Onboards a new tenant with the specified subscription plan.

**Auth:** Required

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `name` | `string` | Yes | `@NotBlank` | Tenant display name (unique) |
| `slug` | `string` | Yes | `@NotBlank`, `@Pattern(^[a-z0-9][a-z0-9\-]{1,98}[a-z0-9]$)` | URL-safe identifier (immutable after creation, unique) |
| `contactEmail` | `string` | Yes | `@NotBlank`, `@Email` | Primary contact email |
| `plan` | `string` | No | `FREE` \| `STARTER` \| `GROWTH` \| `ENTERPRISE` | Subscription plan (defaults to `FREE`) |

**Example Request:**

```json
{
  "name": "Acme Corporation",
  "slug": "acme-corp",
  "contactEmail": "admin@acme.com",
  "plan": "STARTER"
}
```

**Success Response — `201 Created`:**

```json
{
  "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "name": "Acme Corporation",
  "slug": "acme-corp",
  "contactEmail": "admin@acme.com",
  "plan": "STARTER",
  "status": "ACTIVE",
  "rateLimit": 1000,
  "storageQuota": 10737418240,
  "createdAt": "2026-04-08T12:00:00",
  "updatedAt": "2026-04-08T12:00:00"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `tenant.validation.failed` | Invalid or missing fields |
| `409` | `tenant.already.exists` | Name or slug already taken |

**Error Example — `409 Conflict`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 409,
  "error": "Conflict",
  "errorCode": "tenant.already.exists",
  "message": "A tenant with slug 'acme-corp' already exists",
  "path": "/api/v1/tenants"
}
```

---

### GET /api/v1/tenants

Returns all tenants.

**Auth:** Required

**Success Response — `200 OK`:**

```json
[
  {
    "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "name": "Acme Corporation",
    "slug": "acme-corp",
    "contactEmail": "admin@acme.com",
    "plan": "STARTER",
    "status": "ACTIVE",
    "rateLimit": 1000,
    "storageQuota": 10737418240,
    "createdAt": "2026-04-08T12:00:00",
    "updatedAt": "2026-04-08T12:00:00"
  }
]
```

---

### GET /api/v1/tenants/{tenantId}

Returns a tenant by UUID.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Success Response — `200 OK`:**

```json
{
  "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "name": "Acme Corporation",
  "slug": "acme-corp",
  "contactEmail": "admin@acme.com",
  "plan": "STARTER",
  "status": "ACTIVE",
  "rateLimit": 1000,
  "storageQuota": 10737418240,
  "createdAt": "2026-04-08T12:00:00",
  "updatedAt": "2026-04-08T12:00:00"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `tenant.not.found` | Tenant does not exist |

---

### GET /api/v1/tenants/by-slug/{slug}

Returns a tenant by its URL-safe slug.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `slug` | `string` | Tenant slug (e.g., `acme-corp`) |

**Success Response — `200 OK`:** Same schema as [GET /api/v1/tenants/{tenantId}](#get-apiv1tenantstenantid)

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `tenant.not.found` | Tenant with this slug does not exist |

---

### GET /api/v1/tenants/{tenantId}/validate

Validates a tenant for gateway routing. Returns tenant data including status and rate limit. Used internally by the `TenantValidationFilter`.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Success Response — `200 OK`:**

```json
{
  "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "name": "Acme Corporation",
  "slug": "acme-corp",
  "contactEmail": "admin@acme.com",
  "plan": "STARTER",
  "status": "ACTIVE",
  "rateLimit": 1000,
  "storageQuota": 10737418240,
  "createdAt": "2026-04-08T12:00:00",
  "updatedAt": "2026-04-08T12:00:00"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `403` | `tenant.suspended` | Tenant is suspended |
| `403` | `tenant.cancelled` | Tenant is cancelled |
| `404` | `tenant.not.found` | Tenant does not exist |

---

### PUT /api/v1/tenants/{tenantId}

Updates tenant name and contact email. The `slug` is immutable and cannot be changed.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `name` | `string` | Yes | `@NotBlank` | Updated display name |
| `contactEmail` | `string` | Yes | `@NotBlank`, `@Email` | Updated contact email |

**Example Request:**

```json
{
  "name": "Acme Corp International",
  "contactEmail": "support@acme.com"
}
```

**Success Response — `200 OK`:** Returns updated `TenantResponse`.

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `tenant.validation.failed` | Invalid fields |
| `404` | `tenant.not.found` | Tenant does not exist |

---

### PATCH /api/v1/tenants/{tenantId}/plan

Changes the tenant's subscription plan. Automatically updates rate limit and storage quota to match the new plan.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `plan` | `string` | Yes | Target plan: `FREE`, `STARTER`, `GROWTH`, `ENTERPRISE` |

**Example Request:**

```
PATCH /api/v1/tenants/f47ac10b-58cc-4372-a567-0e02b2c3d479/plan?plan=GROWTH
```

**Success Response — `200 OK`:**

```json
{
  "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "name": "Acme Corporation",
  "slug": "acme-corp",
  "contactEmail": "admin@acme.com",
  "plan": "GROWTH",
  "status": "ACTIVE",
  "rateLimit": 10000,
  "storageQuota": 107374182400,
  "createdAt": "2026-04-08T12:00:00",
  "updatedAt": "2026-04-08T14:30:00"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `tenant.plan.not.found` | Invalid plan name |
| `404` | `tenant.not.found` | Tenant does not exist |

---

### PATCH /api/v1/tenants/{tenantId}/suspend

Suspends a tenant. All API calls for this tenant will be rejected with `403 Forbidden`.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Success Response — `204 No Content`:** _(empty body)_

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `tenant.not.found` | Tenant does not exist |

---

### PATCH /api/v1/tenants/{tenantId}/reactivate

Reactivates a suspended tenant. Restores API access.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Success Response — `204 No Content`:** _(empty body)_

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `tenant.not.found` | Tenant does not exist |

---

### DELETE /api/v1/tenants/{tenantId}

Permanently deletes a tenant and all associated data.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Success Response — `204 No Content`:** _(empty body)_

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `tenant.not.found` | Tenant does not exist |

---

### GET /api/v1/tenants/{tenantId}/flags

Returns all feature flags for a tenant.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Success Response — `200 OK`:**

```json
[
  {
    "id": 1,
    "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "flagName": "dark-mode",
    "enabled": true,
    "description": "Enable dark mode UI for tenant users",
    "createdAt": "2026-04-01T10:00:00",
    "updatedAt": "2026-04-08T12:00:00"
  },
  {
    "id": 2,
    "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "flagName": "beta-checkout",
    "enabled": false,
    "description": "New checkout flow (beta)",
    "createdAt": "2026-04-05T09:00:00",
    "updatedAt": "2026-04-05T09:00:00"
  }
]
```

---

### PUT /api/v1/tenants/{tenantId}/flags/{flagName}

Creates or updates a feature flag for a tenant.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |
| `flagName` | `string` | Feature flag name (e.g., `dark-mode`) |

**Request Body:**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `enabled` | `boolean` | Yes | Whether the flag is enabled |
| `description` | `string` | No | Human-readable description |

**Example Request:**

```json
{
  "enabled": true,
  "description": "Enable dark mode UI for tenant users"
}
```

**Success Response — `200 OK`:**

```json
{
  "id": 1,
  "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "flagName": "dark-mode",
  "enabled": true,
  "description": "Enable dark mode UI for tenant users",
  "createdAt": "2026-04-01T10:00:00",
  "updatedAt": "2026-04-08T14:30:00"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `tenant.not.found` | Tenant does not exist |

---

### GET /api/v1/tenants/{tenantId}/flags/{flagName}/status

Returns whether a specific feature flag is enabled.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |
| `flagName` | `string` | Feature flag name |

**Success Response — `200 OK`:**

```json
true
```

---

### POST /api/v1/tenants/{tenantId}/usage

Records a usage metric increment for a tenant.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `metricName` | `string` | Yes | `@NotBlank` | Metric identifier (e.g., `api-calls`, `storage-bytes`) |
| `delta` | `long` | Yes | `@Min(1)` | Increment value |

**Example Request:**

```json
{
  "metricName": "api-calls",
  "delta": 1
}
```

**Success Response — `201 Created`:**

```json
{
  "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
  "metricName": "api-calls",
  "metricValue": 1542,
  "periodDate": "2026-04-08",
  "createdAt": "2026-04-08T12:00:00"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `tenant.not.found` | Tenant does not exist |

---

### GET /api/v1/tenants/{tenantId}/usage

Queries usage metrics for a tenant on a given date.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Query Parameters:**

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `date` | `string` (ISO date) | No | Today | Date to query (`YYYY-MM-DD`) |

**Example Request:**

```
GET /api/v1/tenants/f47ac10b-.../usage?date=2026-04-08
```

**Success Response — `200 OK`:**

```json
[
  {
    "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "metricName": "api-calls",
    "metricValue": 1542,
    "periodDate": "2026-04-08",
    "createdAt": "2026-04-08T00:00:00"
  },
  {
    "tenantId": "f47ac10b-58cc-4372-a567-0e02b2c3d479",
    "metricName": "storage-bytes",
    "metricValue": 5368709120,
    "periodDate": "2026-04-08",
    "createdAt": "2026-04-08T00:00:00"
  }
]
```

---

### GET /api/v1/tenants/{tenantId}/usage/range

Queries usage metrics across a date range.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `startDate` | `string` (ISO date) | Yes | Range start (`YYYY-MM-DD`) |
| `endDate` | `string` (ISO date) | Yes | Range end (`YYYY-MM-DD`) |

**Example Request:**

```
GET /api/v1/tenants/f47ac10b-.../usage/range?startDate=2026-04-01&endDate=2026-04-08
```

**Success Response — `200 OK`:** Returns `List<UsageMetricResponse>` (same schema as usage by date).

---

### GET /api/v1/tenants/{tenantId}/usage/sum

Sums a specific metric over a date range.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `tenantId` | `string` (UUID) | Tenant identifier |

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `metricName` | `string` | Yes | Metric to sum (e.g., `api-calls`) |
| `startDate` | `string` (ISO date) | Yes | Range start |
| `endDate` | `string` (ISO date) | Yes | Range end |

**Example Request:**

```
GET /api/v1/tenants/f47ac10b-.../usage/sum?metricName=api-calls&startDate=2026-04-01&endDate=2026-04-08
```

**Success Response — `200 OK`:**

```json
12450
```

---

## Customer Service

> **Base Path:** `/api/v1/customers`
> **Port:** `8090`
> **Database:** MongoDB (3-node replica set)
> **Cache:** Redis L2 (10-minute TTL, keys: `customers`, `customer-list`)
> **Swagger:** `http://localhost:8090/swagger-ui.html`
> **Description:** Customer profile management

### Endpoints Overview

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/api/v1/customers` | List all customers | Bearer |
| `GET` | `/api/v1/customers/{id}` | Get customer by ID | Bearer |
| `GET` | `/api/v1/customers/by-email` | Get customer by email | Bearer |
| `POST` | `/api/v1/customers` | Create a customer | Bearer |
| `PUT` | `/api/v1/customers/{id}` | Update a customer | Bearer |
| `DELETE` | `/api/v1/customers/{id}` | Delete a customer | Bearer |

---

### GET /api/v1/customers

Returns all customers.

**Auth:** Required

**Success Response — `200 OK`:**

```json
[
  {
    "customerId": "663f1a2b4c5d6e7f8a9b0c1d",
    "firstname": "John",
    "lastname": "Doe",
    "email": "john.doe@example.com",
    "address": {
      "street": "123 Main St",
      "houseNumber": "4A",
      "zipCode": "10001",
      "country": "US",
      "city": "New York"
    }
  }
]
```

---

### GET /api/v1/customers/{id}

Returns a customer by ID.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `string` | Customer ID (MongoDB ObjectId) |

**Success Response — `200 OK`:**

```json
{
  "customerId": "663f1a2b4c5d6e7f8a9b0c1d",
  "firstname": "John",
  "lastname": "Doe",
  "email": "john.doe@example.com",
  "address": {
    "street": "123 Main St",
    "houseNumber": "4A",
    "zipCode": "10001",
    "country": "US",
    "city": "New York"
  }
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `customer.not.found.by.id` | Customer does not exist |

**Error Example — `404 Not Found`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 404,
  "error": "Not Found",
  "errorCode": "customer.not.found.by.id",
  "message": "Customer with ID 663f1a2b4c5d6e7f8a9b0c1d not found",
  "path": "/api/v1/customers/663f1a2b4c5d6e7f8a9b0c1d"
}
```

---

### GET /api/v1/customers/by-email

Returns a customer by email address.

**Auth:** Required

**Query Parameters:**

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `address` | `string` | Yes | Customer email address |

**Example Request:**

```
GET /api/v1/customers/by-email?address=john.doe@example.com
```

**Success Response — `200 OK`:** Returns `CustomerResponse`.

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | — | Missing `address` query parameter |
| `404` | `customer.not.found.by.email` | No customer with this email |

---

### POST /api/v1/customers

Creates a new customer profile.

**Auth:** Required

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `customerId` | `string` | No | — | Custom ID (auto-generated if omitted) |
| `firstname` | `string` | Yes | `@NotNull` | First name |
| `lastname` | `string` | Yes | `@NotNull` | Last name |
| `email` | `string` | Yes | `@NotNull`, `@NotEmpty`, `@Email` | Unique email address |
| `address` | `object` | No | — | Customer address |
| `address.street` | `string` | No | — | Street name |
| `address.houseNumber` | `string` | No | — | House/apartment number |
| `address.zipCode` | `string` | No | `max 8 chars` | Postal code |
| `address.country` | `string` | No | — | Country |
| `address.city` | `string` | No | — | City |

**Example Request:**

```json
{
  "firstname": "John",
  "lastname": "Doe",
  "email": "john.doe@example.com",
  "address": {
    "street": "123 Main St",
    "houseNumber": "4A",
    "zipCode": "10001",
    "country": "US",
    "city": "New York"
  }
}
```

**Success Response — `201 Created`:**

```json
"663f1a2b4c5d6e7f8a9b0c1d"
```

_(Returns the customer ID as a plain string)_

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `customer.validation.failed` | Invalid or missing fields |
| `409` | `customer.email.already.exists` | Email already registered |

**Error Example — `400 Bad Request`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "customer.validation.failed",
  "message": "Validation failed",
  "path": "/api/v1/customers",
  "fieldErrors": {
    "email": "must be a well-formed email address",
    "firstname": "must not be null"
  }
}
```

---

### PUT /api/v1/customers/{id}

Updates an existing customer profile.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `string` | Customer ID |

**Request Body:** Same schema as [POST /api/v1/customers](#post-apiv1customers).

**Success Response — `200 OK`:** Returns updated `CustomerResponse`.

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `customer.validation.failed` | Invalid fields |
| `404` | `customer.not.found.by.id` | Customer does not exist |

---

### DELETE /api/v1/customers/{id}

Deletes a customer profile.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `string` | Customer ID |

**Success Response — `204 No Content`:** _(empty body)_

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `customer.not.found.by.id` | Customer does not exist |

---

## Product Service

> **Base Path:** `/api/v1/products`
> **Port:** `8082`
> **Database:** PostgreSQL
> **Cache:** Redis (30-minute TTL, keys: `products`, `product-list`)
> **Swagger:** `http://localhost:8082/swagger-ui.html`
> **Description:** Product catalog management and inventory stock reservation

### Endpoints Overview

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/api/v1/products` | List all products (paginated) | Bearer |
| `GET` | `/api/v1/products/{id}` | Get product by ID | Bearer |
| `POST` | `/api/v1/products/create` | Create a product | Bearer |
| `POST` | `/api/v1/products/batch` | Batch create products | Bearer |
| `PUT` | `/api/v1/products/update/{id}` | Update a product | Bearer |
| `DELETE` | `/api/v1/products/delete/{id}` | Delete a product | Bearer |
| `POST` | `/api/v1/products/purchase` | Reserve stock (saga) | Bearer |

---

### GET /api/v1/products

Returns all products with pagination.

**Auth:** Required

**Query Parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `page` | `integer` | `0` | Zero-based page index |
| `size` | `integer` | `20` | Page size |
| `sort` | `string` | `id,asc` | Sort field and direction |

**Example Request:**

```
GET /api/v1/products?page=0&size=10&sort=name,asc
```

**Success Response — `200 OK`:**

```json
{
  "content": [
    {
      "id": 1,
      "name": "Wireless Mouse",
      "description": "Ergonomic wireless mouse with USB receiver",
      "availableQuantity": 250.0,
      "price": 29.99,
      "categoryId": 3,
      "categoryName": "Electronics",
      "categoryDescription": "Electronic devices and accessories"
    },
    {
      "id": 2,
      "name": "Mechanical Keyboard",
      "description": "RGB mechanical keyboard with Cherry MX switches",
      "availableQuantity": 100.0,
      "price": 89.99,
      "categoryId": 3,
      "categoryName": "Electronics",
      "categoryDescription": "Electronic devices and accessories"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 10,
    "sort": { "sorted": true, "unsorted": false, "empty": false }
  },
  "totalElements": 85,
  "totalPages": 9,
  "size": 10,
  "number": 0,
  "first": true,
  "last": false,
  "empty": false
}
```

---

### GET /api/v1/products/{id}

Returns a product by ID.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `integer` | Product ID |

**Success Response — `200 OK`:**

```json
{
  "id": 1,
  "name": "Wireless Mouse",
  "description": "Ergonomic wireless mouse with USB receiver",
  "availableQuantity": 250.0,
  "price": 29.99,
  "categoryId": 3,
  "categoryName": "Electronics",
  "categoryDescription": "Electronic devices and accessories"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `product.not.found` | Product does not exist |

---

### POST /api/v1/products/create

Creates a new product.

**Auth:** Required

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `name` | `string` | Yes | `@NotNull` | Product name |
| `description` | `string` | Yes | `@NotNull` | Product description |
| `availableQuantity` | `double` | Yes | `@Positive` | Initial stock quantity |
| `price` | `decimal` | Yes | `@Positive` | Unit price |
| `category` | `object` | Yes | — | Category reference |
| `category.id` | `integer` | Yes | — | Existing category ID |

**Example Request:**

```json
{
  "name": "Wireless Mouse",
  "description": "Ergonomic wireless mouse with USB receiver",
  "availableQuantity": 250,
  "price": 29.99,
  "category": {
    "id": 3
  }
}
```

**Success Response — `201 Created`:**

```json
{
  "id": 1,
  "name": "Wireless Mouse",
  "description": "Ergonomic wireless mouse with USB receiver",
  "availableQuantity": 250.0,
  "price": 29.99,
  "categoryId": 3
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `product.validation.failed` | Invalid or missing fields |

---

### POST /api/v1/products/batch

Creates multiple products in a single request.

**Auth:** Required

**Request Body:** Array of product objects (same schema as [POST /create](#post-apiv1productscreate)).

**Example Request:**

```json
[
  {
    "name": "Wireless Mouse",
    "description": "Ergonomic wireless mouse",
    "availableQuantity": 250,
    "price": 29.99,
    "category": { "id": 3 }
  },
  {
    "name": "Mechanical Keyboard",
    "description": "RGB mechanical keyboard",
    "availableQuantity": 100,
    "price": 89.99,
    "category": { "id": 3 }
  }
]
```

**Success Response — `201 Created`:** Returns `List<ProductRequest>` with created product data.

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `product.validation.failed` | Invalid product data in batch |

---

### PUT /api/v1/products/update/{id}

Updates an existing product.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `integer` | Product ID |

**Request Body:** Same schema as [POST /create](#post-apiv1productscreate).

**Success Response — `200 OK`:** Returns updated `ProductRequest`.

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `product.not.found` | Product does not exist |

---

### DELETE /api/v1/products/delete/{id}

Deletes a product by ID.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `id` | `integer` | Product ID |

**Success Response — `204 No Content`:** _(empty body)_

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `product.not.found` | Product does not exist |

---

### POST /api/v1/products/purchase

Reserves stock for a list of products. Used internally by the order saga (inventory reservation step). Atomically reserves **all** items or fails entirely — no partial reservations.

**Auth:** Required

**Request Body:** Array of purchase items:

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `productId` | `integer` | Yes | `@NotNull` | Product to reserve |
| `quantity` | `double` | Yes | `@Positive` | Quantity to reserve |

**Example Request:**

```json
[
  { "productId": 1, "quantity": 2 },
  { "productId": 5, "quantity": 1 }
]
```

**Success Response — `200 OK`:**

```json
[
  {
    "productId": 1,
    "name": "Wireless Mouse",
    "description": "Ergonomic wireless mouse with USB receiver",
    "price": 29.99,
    "quantity": 2.0
  },
  {
    "productId": 5,
    "name": "USB-C Hub",
    "description": "7-port USB-C hub with HDMI",
    "price": 49.99,
    "quantity": 1.0
  }
]
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `422` | `product.purchase.insufficient.stock` | One or more products have insufficient stock |
| `404` | `product.purchase.not.found` | One or more product IDs do not exist |

**Error Example — `422 Unprocessable Entity`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "errorCode": "product.purchase.insufficient.stock",
  "message": "Insufficient stock for product 'USB-C Hub': requested 10, available 3",
  "path": "/api/v1/products/purchase"
}
```

---

## Cart Service

> **Base Path:** `/api/v1/carts`
> **Port:** `8091`
> **Storage:** Redis with 24-hour sliding TTL
> **Swagger:** `http://localhost:8091/swagger-ui.html`
> **Description:** Shopping cart management. Carts are stored in Redis and expire 24 hours after the last write activity. Cart ID format: `cart:{tenantId}:{customerId}`.

### Endpoints Overview

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `GET` | `/api/v1/carts/{customerId}` | Get cart | Bearer |
| `POST` | `/api/v1/carts/{customerId}/items` | Add item to cart | Bearer |
| `PATCH` | `/api/v1/carts/{customerId}/items/{productId}` | Update item quantity | Bearer |
| `DELETE` | `/api/v1/carts/{customerId}/items/{productId}` | Remove item | Bearer |
| `DELETE` | `/api/v1/carts/{customerId}` | Clear entire cart | Bearer |
| `GET` | `/api/v1/carts/{customerId}/checkout` | Checkout snapshot | Bearer |

---

### GET /api/v1/carts/{customerId}

Returns the cart for a customer.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `customerId` | `string` | Customer ID |

**Success Response — `200 OK`:**

```json
{
  "cartId": "cart:acme-corp:663f1a2b4c5d6e7f8a9b0c1d",
  "customerId": "663f1a2b4c5d6e7f8a9b0c1d",
  "items": [
    {
      "productId": 1,
      "productName": "Wireless Mouse",
      "productDescription": "Ergonomic wireless mouse with USB receiver",
      "unitPrice": 29.99,
      "quantity": 2.0,
      "lineTotal": 59.98
    },
    {
      "productId": 5,
      "productName": "USB-C Hub",
      "productDescription": "7-port USB-C hub with HDMI",
      "unitPrice": 49.99,
      "quantity": 1.0,
      "lineTotal": 49.99
    }
  ],
  "total": 109.97,
  "itemCount": 2,
  "createdAt": "2026-04-08T10:00:00",
  "updatedAt": "2026-04-08T11:30:00"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `cart.not.found` | No cart exists for this customer |

---

### POST /api/v1/carts/{customerId}/items

Adds an item to the cart. Creates the cart if it doesn't exist. If the product is already in the cart, the quantity is **added** to the existing quantity.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `customerId` | `string` | Customer ID |

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `productId` | `integer` | Yes | `@NotNull` | Product ID |
| `productName` | `string` | Yes | `@NotBlank` | Product display name (snapshotted) |
| `productDescription` | `string` | No | — | Product description |
| `unitPrice` | `decimal` | Yes | `@NotNull` | Unit price at time of add (snapshotted) |
| `quantity` | `double` | Yes | `@Positive` | Quantity to add |

**Example Request:**

```json
{
  "productId": 1,
  "productName": "Wireless Mouse",
  "productDescription": "Ergonomic wireless mouse with USB receiver",
  "unitPrice": 29.99,
  "quantity": 2
}
```

**Success Response — `201 Created`:** Returns updated `CartResponse`.

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `cart.validation.failed` | Invalid item data |

**Error Example — `400 Bad Request`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "cart.validation.failed",
  "message": "Validation failed",
  "path": "/api/v1/carts/663f1a2b/items",
  "fieldErrors": {
    "productId": "Product ID is required",
    "quantity": "Quantity must be greater than zero"
  }
}
```

---

### PATCH /api/v1/carts/{customerId}/items/{productId}

Sets the quantity of an existing cart item. Resets the 24h cart TTL.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `customerId` | `string` | Customer ID |
| `productId` | `integer` | Product ID in the cart |

**Query Parameters:**

| Parameter | Type | Required | Constraints | Description |
|-----------|------|----------|-------------|-------------|
| `quantity` | `double` | Yes | `@Positive` | New quantity value |

**Example Request:**

```
PATCH /api/v1/carts/663f1a2b4c5d6e7f8a9b0c1d/items/1?quantity=5
```

**Success Response — `200 OK`:** Returns updated `CartResponse`.

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `cart.item.quantity.invalid` | Quantity must be positive |
| `404` | `cart.not.found` | Cart does not exist |
| `404` | `cart.item.not.found` | Product not in cart |

---

### DELETE /api/v1/carts/{customerId}/items/{productId}

Removes an item from the cart.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `customerId` | `string` | Customer ID |
| `productId` | `integer` | Product ID to remove |

**Success Response — `200 OK`:** Returns updated `CartResponse` without the removed item.

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `cart.not.found` | Cart does not exist |
| `404` | `cart.item.not.found` | Product not in cart |

---

### DELETE /api/v1/carts/{customerId}

Clears the entire cart by removing it from Redis. Called by order-service after successful checkout.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `customerId` | `string` | Customer ID |

**Success Response — `204 No Content`:** _(empty body)_

---

### GET /api/v1/carts/{customerId}/checkout

Returns current cart contents as a read-only checkout snapshot for order creation. The cart is **NOT** cleared — call `DELETE /{customerId}` after the order is persisted to avoid cart loss.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `customerId` | `string` | Customer ID |

**Success Response — `200 OK`:** Returns `CartResponse` (same schema as GET cart).

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `cart.checkout.empty` | Cart has no items |
| `404` | `cart.not.found` | No cart exists for this customer |

---

## Order Service

> **Base Path:** `/api/v1/orders`, `/api/v1/order-lines`
> **Port:** `8083`
> **Database:** PostgreSQL
> **Swagger:** `http://localhost:8083/swagger-ui.html`
> **Description:** Async order management using an event-driven choreography saga. Orders are created asynchronously — `POST` returns `202 Accepted` with a `correlationId` for status polling. Uses the Transactional Outbox pattern for guaranteed at-least-once Kafka delivery.

### Order Saga Flow

```
Client                Order Service        Product Service       Payment Service       Notification
  |                        |                     |                      |                    |
  |-- POST /orders ------->|                     |                      |                    |
  |<-- 202 {correlationId} |                     |                      |                    |
  |                        |                     |                      |                    |
  |                        |-- order-requested ->|                      |                    |
  |                        |                     |-- reserve stock      |                    |
  |                        |                     |                      |                    |
  |                        |           [sufficient stock]               |                    |
  |                        |                     |-- inventory.reserved>|                    |
  |                        |                     |                      |-- process payment  |
  |                        |                     |                      |                    |
  |                        |              [payment authorized]          |                    |
  |                        |<------------ payment.authorized ----------|                    |
  |                        |-- status=CONFIRMED  |                      |                    |
  |                        |                     |                      |                    |
  |                        |-- order-confirmation ----------------------------------------->|
  |                        |                     |                      |-- payment-confirm->|
  |                        |                     |                      |                    |
  |-- GET /status/{id} --->|                     |                      |                    |
  |<-- {status: CONFIRMED} |                     |                      |                    |
  |                        |                     |                      |                    |
  |                        |         [insufficient stock]               |                    |
  |                        |<---- inventory.insufficient                |                    |
  |                        |-- status=CANCELLED  |                      |                    |
  |                        |                     |                      |                    |
  |                        |               [payment failed]             |                    |
  |                        |<------------ payment.failed --------------|                    |
  |                        |-- status=CANCELLED  |                      |                    |
```

### Endpoints Overview

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `POST` | `/api/v1/orders` | Create order (async) | Bearer |
| `GET` | `/api/v1/orders/status/{correlationId}` | Poll order status | Bearer |
| `GET` | `/api/v1/orders` | List all orders | Bearer |
| `GET` | `/api/v1/orders/{order-id}` | Get order by ID | Bearer |
| `GET` | `/api/v1/order-lines/{order-id}` | Get order line items | Bearer |

---

### POST /api/v1/orders

Creates a new order asynchronously. Validates the customer exists, persists the order in `REQUESTED` state, and publishes an `OrderRequestedEvent` to Kafka via the Outbox pattern.

**Auth:** Required

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `reference` | `string` | No | Unique | Order reference (auto-generated if omitted) |
| `amount` | `decimal` | Yes | `@Positive`, `@NotNull` | Total order amount |
| `paymentMethod` | `string` | Yes | `@NotNull` | `PAYPAL` \| `CREDIT_CARD` \| `VISA` \| `MASTER_CARD` \| `BITCOIN` |
| `customerId` | `string` | Yes | `@NotNull`, `@NotBlank` | Valid customer ID |
| `products` | `array` | Yes | `@NotNull`, `@NotEmpty`, `@Valid` | List of products to order |
| `products[].productId` | `integer` | Yes | `@NotNull` | Product ID |
| `products[].quantity` | `double` | Yes | `@Positive` | Quantity to order |

**Example Request:**

```json
{
  "amount": 109.97,
  "paymentMethod": "CREDIT_CARD",
  "customerId": "663f1a2b4c5d6e7f8a9b0c1d",
  "products": [
    { "productId": 1, "quantity": 2 },
    { "productId": 5, "quantity": 1 }
  ]
}
```

**Success Response — `202 Accepted`:**

```json
{
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "status": "REQUESTED",
  "message": "Order accepted for processing. Poll GET /api/v1/orders/status/{correlationId} for updates."
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `order.validation.failed` | Invalid or missing fields |
| `404` | `order.customer.not.found` | Customer ID does not exist |
| `409` | `order.duplicate.reference` | Order reference already used |
| `503` | `order.customer.service.unavailable` | Customer service is down (Feign circuit breaker open) |
| `503` | `order.product.service.unavailable` | Product service is down |
| `503` | `order.payment.service.unavailable` | Payment service is down |

**Error Example — `503 Service Unavailable`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 503,
  "error": "Service Unavailable",
  "errorCode": "order.customer.service.unavailable",
  "message": "Customer service is temporarily unavailable. Please try again later.",
  "path": "/api/v1/orders"
}
```

---

### GET /api/v1/orders/status/{correlationId}

Polls the current status of an asynchronous order by its correlation ID.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `correlationId` | `string` (UUID) | Correlation ID returned from `POST /api/v1/orders` |

**Success Response — `200 OK`:**

```json
{
  "orderId": 42,
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "reference": "ORD-20260408-001",
  "status": "CONFIRMED",
  "totalAmount": 109.97,
  "createdDate": "2026-04-08T12:00:00"
}
```

**Order Status Values:**

| Status | Description |
|--------|-------------|
| `REQUESTED` | Order submitted, awaiting inventory check |
| `INVENTORY_RESERVED` | Stock reserved, awaiting payment |
| `INVENTORY_INSUFFICIENT` | Insufficient stock — order will be cancelled |
| `PENDING_PAYMENT` | Payment in progress |
| `PAYMENT_AUTHORIZED` | Payment successful |
| `PAYMENT_FAILED` | Payment declined — order will be cancelled |
| `CONFIRMED` | Order fully confirmed and completed |
| `CANCELLED` | Order cancelled (insufficient stock or payment failure) |

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `order.not.found` | No order with this correlation ID |

---

### GET /api/v1/orders

Returns all orders for the current tenant.

**Auth:** Required

**Success Response — `200 OK`:**

```json
[
  {
    "id": 42,
    "reference": "ORD-20260408-001",
    "amount": 109.97,
    "paymentMethod": "CREDIT_CARD",
    "customerId": "663f1a2b4c5d6e7f8a9b0c1d"
  },
  {
    "id": 43,
    "reference": "ORD-20260408-002",
    "amount": 29.99,
    "paymentMethod": "PAYPAL",
    "customerId": "774a2b3c5d6e7f8a9b0c1d2e"
  }
]
```

---

### GET /api/v1/orders/{order-id}

Returns an order by ID.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `order-id` | `integer` | Order ID |

**Success Response — `200 OK`:**

```json
{
  "id": 42,
  "reference": "ORD-20260408-001",
  "amount": 109.97,
  "paymentMethod": "CREDIT_CARD",
  "customerId": "663f1a2b4c5d6e7f8a9b0c1d"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `order.not.found` | Order does not exist |

---

### GET /api/v1/order-lines/{order-id}

Returns order line items for a given order.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `order-id` | `integer` | Order ID |

**Success Response — `200 OK`:**

```json
[
  {
    "id": 1,
    "quantity": 2.0
  },
  {
    "id": 2,
    "quantity": 1.0
  }
]
```

---

## Payment Service

> **Base Path:** `/api/v1/payments`
> **Port:** `8086`
> **Database:** PostgreSQL
> **Swagger:** `http://localhost:8086/swagger-ui.html`
> **Rate Limit:** 20 req/s (stricter than other services — financial endpoint)
> **Description:** Payment processing with idempotency protection. Duplicate payments for the same `orderReference` return the existing payment ID instead of double-charging.

### Idempotency

The payment service derives an `idempotencyKey` from the `orderReference`. If a payment with the same key already exists, the existing `paymentId` is returned without creating a duplicate charge. This protects against:

- Network timeouts causing client retries
- Duplicate Feign calls from upstream services
- Kafka consumer redelivery

### Endpoints Overview

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `POST` | `/api/v1/payments` | Create payment (idempotent) | Bearer |
| `GET` | `/api/v1/payments` | List all payments | Bearer |
| `GET` | `/api/v1/payments/{payment-id}` | Get payment by ID | Bearer |

---

### POST /api/v1/payments

Creates a new payment. **Idempotent** — if a payment for the same `orderReference` already exists, the existing payment ID is returned without creating a duplicate.

**Auth:** Required

**Request Body:**

| Field | Type | Required | Constraints | Description |
|-------|------|----------|-------------|-------------|
| `amount` | `decimal` | Yes | `@Positive`, `@NotNull` | Payment amount |
| `paymentMethod` | `string` | Yes | `@NotNull` | `PAYPAL` \| `CREDIT_CARD` \| `VISA` \| `MASTER_CARD` \| `BITCOIN` |
| `orderId` | `integer` | Yes | `@NotNull` | Associated order ID |
| `orderReference` | `string` | Yes | `@NotBlank` | Unique order reference (serves as idempotency key) |
| `customer` | `object` | Yes | `@NotNull`, `@Valid` | Customer data |
| `customer.customerId` | `string` | Yes | — | Customer ID |
| `customer.firstname` | `string` | Yes | — | Customer first name |
| `customer.lastname` | `string` | Yes | — | Customer last name |
| `customer.email` | `string` | Yes | — | Customer email |

**Example Request:**

```json
{
  "amount": 109.97,
  "paymentMethod": "CREDIT_CARD",
  "orderId": 42,
  "orderReference": "ORD-20260408-001",
  "customer": {
    "customerId": "663f1a2b4c5d6e7f8a9b0c1d",
    "firstname": "John",
    "lastname": "Doe",
    "email": "john.doe@example.com"
  }
}
```

**Success Response — `201 Created`:**

```json
1
```

_(Returns the payment ID as a plain integer. Returns the same ID on duplicate requests with the same `orderReference`.)_

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `400` | `payment.error.bad.request` | Invalid or missing fields |

**Error Example — `400 Bad Request`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "errorCode": "payment.error.bad.request",
  "message": "Validation failed",
  "path": "/api/v1/payments",
  "fieldErrors": {
    "amount": "Amount must be positive",
    "orderReference": "Order reference is required"
  }
}
```

---

### GET /api/v1/payments

Returns all payments.

**Auth:** Required

**Success Response — `200 OK`:**

```json
[
  {
    "paymentId": 1,
    "amount": 109.97,
    "paymentMethod": "CREDIT_CARD",
    "orderId": 42,
    "orderReference": "ORD-20260408-001",
    "createdDate": "2026-04-08T12:05:00"
  },
  {
    "paymentId": 2,
    "amount": 29.99,
    "paymentMethod": "PAYPAL",
    "orderId": 43,
    "orderReference": "ORD-20260408-002",
    "createdDate": "2026-04-08T13:10:00"
  }
]
```

---

### GET /api/v1/payments/{payment-id}

Returns a payment by ID.

**Auth:** Required

**Path Parameters:**

| Parameter | Type | Description |
|-----------|------|-------------|
| `payment-id` | `integer` | Payment ID |

**Success Response — `200 OK`:**

```json
{
  "paymentId": 1,
  "amount": 109.97,
  "paymentMethod": "CREDIT_CARD",
  "orderId": 42,
  "orderReference": "ORD-20260408-001",
  "createdDate": "2026-04-08T12:05:00"
}
```

**Error Responses:**

| Status | Error Code | Condition |
|--------|-----------|-----------|
| `404` | `payment.not.found` | Payment does not exist |

**Error Example — `404 Not Found`:**

```json
{
  "timestamp": "2026-04-08T12:00:00.000Z",
  "status": 404,
  "error": "Not Found",
  "errorCode": "payment.not.found",
  "message": "Payment with ID 999 not found",
  "path": "/api/v1/payments/999"
}
```

---

## Enumerations

### PaymentMethod

Used by Order Service and Payment Service (each owns a local copy):

| Value | Description |
|-------|-------------|
| `PAYPAL` | PayPal payment |
| `CREDIT_CARD` | Generic credit card |
| `VISA` | Visa card |
| `MASTER_CARD` | MasterCard |
| `BITCOIN` | Bitcoin cryptocurrency |

### OrderStatus

| Value | Description |
|-------|-------------|
| `REQUESTED` | Order submitted, awaiting processing |
| `INVENTORY_RESERVED` | Stock reserved successfully |
| `INVENTORY_INSUFFICIENT` | Insufficient stock — triggers cancellation |
| `PENDING_PAYMENT` | Awaiting payment processing |
| `PAYMENT_AUTHORIZED` | Payment successful |
| `PAYMENT_FAILED` | Payment declined — triggers cancellation |
| `CONFIRMED` | Order fully confirmed and completed |
| `CANCELLED` | Order cancelled |

### TenantPlan

| Value | Rate Limit | Storage Quota | Description |
|-------|-----------|---------------|-------------|
| `FREE` | 100 req/min | 1 GB | Free tier |
| `STARTER` | 1,000 req/min | 10 GB | Small business |
| `GROWTH` | 10,000 req/min | 100 GB | Growing business |
| `ENTERPRISE` | Unlimited | Unlimited | Enterprise |

### TenantStatus

| Value | API Impact | Description |
|-------|-----------|-------------|
| `ACTIVE` | Requests allowed | Tenant is operational |
| `SUSPENDED` | 403 Forbidden | Tenant is suspended |
| `CANCELLED` | 403 Forbidden | Tenant is cancelled |

### Role

| Value | Description |
|-------|-------------|
| `USER` | Standard user |
| `SELLER` | Seller with product management access |
| `ADMIN` | Administrator with full access |

### NotificationType

| Value | Description |
|-------|-------------|
| `ORDER_CONFIRMATION` | Order confirmation email |
| `PAYMENT_CONFIRMATION` | Payment confirmation email |

---

## Event-Driven Architecture

### Kafka Topics

| Topic | Producer | Consumer | Description |
|-------|----------|----------|-------------|
| `order-requested` | Order Service | Product Service | Triggers inventory reservation |
| `inventory-reserved` | Product Service | Payment Service | Triggers payment processing |
| `inventory-insufficient` | Product Service | Order Service | Cancels order (no stock) |
| `payment-authorized` | Payment Service | Order Service | Confirms order |
| `payment-failed` | Payment Service | Order Service | Cancels order (payment declined) |
| `order-confirmation` | Order Service | Notification Service | Triggers order confirmation email |
| `payment-confirmation` | Payment Service | Notification Service | Triggers payment confirmation email |
| `payment-topic.DLQ` | Kafka (auto) | Notification Service | Dead-letter queue for failed payment events |
| `order-topic.DLQ` | Kafka (auto) | Notification Service | Dead-letter queue for failed order events |

### Kafka Configuration

| Setting | Value |
|---------|-------|
| Partitions per topic | 3 |
| Replication factor | 3 |
| Authentication | SASL/PLAIN |
| Idempotent producer | `true` |
| Acknowledgements | `all` |
| Retries | 3 |
| Max in-flight requests | 1 |

### Consumer Groups

| Group | Service | Topics |
|-------|---------|--------|
| `order-saga-group` | Order Service | `payment-authorized`, `payment-failed`, `inventory-insufficient` |
| `inventory-reservation-group` | Product Service | `order-requested` |
| `payment-saga-group` | Payment Service | `inventory-reserved` |
| `notification-group` | Notification Service | `order-confirmation`, `payment-confirmation`, DLQs |

### Outbox Pattern (Order Service)

The order service uses the Transactional Outbox pattern to solve the dual-write problem:

1. `Order` and `OutboxEvent` are persisted in the **same database transaction**
2. A scheduled publisher polls for `PENDING` events every **5 seconds**
3. Events are published to Kafka with `correlationId` as partition key (ensures saga ordering)
4. On success: status → `PUBLISHED`, `publishedAt` timestamp set
5. On failure: `retryCount++`; after **5 retries** status → `FAILED` (requires manual intervention)

### Idempotency Mechanisms

| Mechanism | Service | Strategy |
|-----------|---------|----------|
| Payment idempotency | Payment Service | `idempotencyKey` derived from `orderReference` with DB unique constraint |
| Event deduplication | Notification Service | `ProcessedEvent` collection in MongoDB tracks consumed `eventId` values |
| Consumer idempotency | All saga consumers | Manual offset commit only after successful DB write |

### Event Schemas

**OrderRequestedEvent:**

```json
{
  "eventId": "b47ac10b-58cc-4372-a567-0e02b2c3d479",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "customerId": "663f1a2b4c5d6e7f8a9b0c1d",
  "customerEmail": "john.doe@example.com",
  "customerFirstname": "John",
  "customerLastname": "Doe",
  "products": [
    { "productId": 1, "quantity": 2.0 },
    { "productId": 5, "quantity": 1.0 }
  ],
  "totalAmount": 109.97,
  "paymentMethod": "CREDIT_CARD",
  "orderReference": "ORD-20260408-001",
  "occurredAt": "2026-04-08T12:00:00Z",
  "schemaVersion": 1
}
```

**InventoryReservedEvent:**

```json
{
  "eventId": "c58bd21c-69dd-4483-b678-1f13c4d5e590",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "orderReference": "ORD-20260408-001",
  "customerId": "663f1a2b4c5d6e7f8a9b0c1d",
  "customerEmail": "john.doe@example.com",
  "customerFirstname": "John",
  "customerLastname": "Doe",
  "reservedItems": [
    { "productId": 1, "name": "Wireless Mouse", "quantity": 2.0, "unitPrice": 29.99 },
    { "productId": 5, "name": "USB-C Hub", "quantity": 1.0, "unitPrice": 49.99 }
  ],
  "totalAmount": 109.97,
  "paymentMethod": "CREDIT_CARD",
  "occurredAt": "2026-04-08T12:00:05Z",
  "schemaVersion": 1
}
```

**InventoryInsufficientEvent:**

```json
{
  "eventId": "d69ce32d-7aee-5594-c789-2g24d5e6f6a1",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "orderReference": "ORD-20260408-001",
  "productId": 5,
  "productName": "USB-C Hub",
  "requestedQty": 10.0,
  "availableQty": 3.0,
  "occurredAt": "2026-04-08T12:00:05Z",
  "schemaVersion": 1
}
```

**PaymentAuthorizedEvent:**

```json
{
  "eventId": "e7adf43e-8bff-6605-d890-3h35e6f7g7b2",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "orderReference": "ORD-20260408-001",
  "paymentId": 1,
  "occurredAt": "2026-04-08T12:00:10Z",
  "schemaVersion": 1
}
```

**PaymentFailedEvent:**

```json
{
  "eventId": "f8beg54f-9cgg-7716-e901-4i46f7g8h8c3",
  "correlationId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "orderReference": "ORD-20260408-001",
  "reason": "Insufficient funds",
  "occurredAt": "2026-04-08T12:00:10Z",
  "schemaVersion": 1
}
```

**OrderConfirmation:**

```json
{
  "orderReference": "ORD-20260408-001",
  "totalAmount": 109.97,
  "paymentMethod": "CREDIT_CARD",
  "customer": {
    "customerId": "663f1a2b4c5d6e7f8a9b0c1d",
    "firstname": "John",
    "lastname": "Doe",
    "email": "john.doe@example.com"
  },
  "products": [
    { "productId": 1, "name": "Wireless Mouse", "description": "Ergonomic wireless mouse", "price": 29.99, "quantity": 2.0 },
    { "productId": 5, "name": "USB-C Hub", "description": "7-port USB-C hub", "price": 49.99, "quantity": 1.0 }
  ]
}
```

**PaymentConfirmation:**

```json
{
  "orderReference": "ORD-20260408-001",
  "amount": 109.97,
  "paymentMethod": "CREDIT_CARD",
  "customerFirstname": "John",
  "customerLastname": "Doe",
  "customerEmail": "john.doe@example.com"
}
```

---

## Infrastructure Services

### Config Server

| Property | Value |
|----------|-------|
| Port | `8888` |
| Auth | HTTP Basic Auth |
| Username | `config` |
| Password | `${CONFIG_SERVER_PASSWORD}` (env var) |
| Config location | `classpath:/configurations/*.yml` |

**Standard Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/{application}/{profile}` | Get config for app + profile |
| `GET` | `/{application}-{profile}.yml` | Get config as YAML |
| `GET` | `/{application}-{profile}.properties` | Get config as properties |

**Public endpoints:** `/actuator/health`, `/actuator/info`

### Discovery Service (Eureka)

| Property | Value |
|----------|-------|
| Port | `8761` |
| Auth | HTTP Basic Auth |
| Username | `eureka` (default) |
| Password | `eureka-secret-2024` (default) |
| HA mode | Peer-aware with `discovery-service-2` |

**Standard Eureka Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/eureka/apps` | List all registered services |
| `GET` | `/eureka/apps/{appName}` | Get instances of a service |
| `POST` | `/eureka/apps/{appName}` | Register a service instance |
| `DELETE` | `/eureka/apps/{appName}/{instanceId}` | Deregister an instance |

### Notification Service

The notification service has **no REST API**. It is an event-driven consumer that:

- Consumes from `order-confirmation` and `payment-confirmation` Kafka topics
- Sends email notifications via SMTP (default: Gmail, port 587, STARTTLS)
- Generates PDF invoices for order confirmations
- Deduplicates events via `ProcessedEvent` collection in MongoDB
- Consumes dead-letter queue events from `payment-topic.DLQ` and `order-topic.DLQ`
- Async email thread pool: core=4, max=16, queue=100

### Vault (Secrets Management)

| Property | Value |
|----------|-------|
| Port | `8200` |
| UI | `http://localhost:8200` |
| Dev token | `root-token` |
| KV backend | `secret` |
| Shared path | `ecommerce/shared` (JWT, Kafka, Redis credentials) |
| Per-service path | `ecommerce/{service-name}` |

---

## Swagger UI

Each service exposes interactive Swagger UI documentation:

| Service | Swagger UI | OpenAPI JSON |
|---------|-----------|-------------|
| Authentication Service | `http://localhost:8085/swagger-ui.html` | `http://localhost:8085/api-docs` |
| Tenant Service | `http://localhost:8095/swagger-ui.html` | `http://localhost:8095/api-docs` |
| Customer Service | `http://localhost:8090/swagger-ui.html` | `http://localhost:8090/api-docs` |
| Product Service | `http://localhost:8082/swagger-ui.html` | `http://localhost:8082/api-docs` |
| Cart Service | `http://localhost:8091/swagger-ui.html` | `http://localhost:8091/api-docs` |
| Order Service | `http://localhost:8083/swagger-ui.html` | `http://localhost:8083/api-docs` |
| Payment Service | `http://localhost:8086/swagger-ui.html` | `http://localhost:8086/api-docs` |

---

## Service Port Reference

| Service | Port | Database | Description |
|---------|------|----------|-------------|
| Gateway API | `8222` | Redis (rate limiting) | API Gateway (Spring Cloud Gateway) |
| Config Server | `8888` | — | Centralized configuration |
| Discovery Service | `8761` | — | Service discovery (Eureka) |
| Authentication Service | `8085` | PostgreSQL | JWT auth + token management |
| Tenant Service | `8095` | PostgreSQL | SaaS tenant lifecycle |
| Customer Service | `8090` | MongoDB (replica set) | Customer profiles |
| Product Service | `8082` | PostgreSQL | Product catalog + inventory |
| Order Service | `8083` | PostgreSQL | Async order management (saga) |
| Payment Service | `8086` | PostgreSQL | Idempotent payment processing |
| Cart Service | `8091` | Redis | Shopping cart (24h TTL) |
| Notification Service | `8040` | MongoDB | Event-driven email notifications |

### Monitoring & Observability

| Tool | Port | URL | Purpose |
|------|------|-----|---------|
| Zipkin | `9411` | `http://localhost:9411` | Distributed tracing |
| Prometheus | `9090` | `http://localhost:9090` | Metrics collection |
| Grafana | `3000` | `http://localhost:3000` | Metrics dashboards |
| MailHog | `8025` | `http://localhost:8025` | Email testing (dev only) |
| Vault UI | `8200` | `http://localhost:8200` | Secrets management |

### Health Check

All services expose a public health endpoint:

```
GET http://localhost:<port>/actuator/health
```

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```
