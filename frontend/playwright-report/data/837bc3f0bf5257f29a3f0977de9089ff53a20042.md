# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: admin.spec.ts >> Admin — access control >> unauthenticated user redirected from /admin
- Location: e2e\admin.spec.ts:83:3

# Error details

```
Test timeout of 60000ms exceeded.
```

```
Error: page.goto: Test timeout of 60000ms exceeded.
Call log:
  - navigating to "http://localhost:3001/admin", waiting until "load"

```

# Test source

```ts
  1   | import { test, expect } from '@playwright/test';
  2   | 
  3   | const MOCK_TENANT = {
  4   |   tenantId: 'tenant-001',
  5   |   name: 'Demo Store',
  6   |   slug: 'demo-store',
  7   |   contactEmail: 'admin@demo.com',
  8   |   plan: 'STARTER',
  9   |   status: 'ACTIVE',
  10  |   rateLimit: 100,
  11  |   storageQuota: 1024,
  12  |   createdAt: '2024-01-01T00:00:00Z',
  13  |   updatedAt: '2024-01-01T00:00:00Z',
  14  | };
  15  | 
  16  | // Mock all /api/* requests so the frontend renders without a running backend
  17  | async function mockApi(page: import('@playwright/test').Page) {
  18  |   await page.route('/api/**', (route) => {
  19  |     const url = route.request().url();
  20  | 
  21  |     if (url.match(/\/api\/v1\/tenants\/[^/]+\/flags/)) {
  22  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
  23  |         { flagName: 'ADVANCED_ANALYTICS', enabled: true, description: 'Analytics' },
  24  |       ]) });
  25  |     }
  26  |     if (url.match(/\/api\/v1\/tenants\/[^/]+$/)) {
  27  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_TENANT) });
  28  |     }
  29  |     if (url.includes('/api/v1/tenants')) {
  30  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([MOCK_TENANT]) });
  31  |     }
  32  |     if (url.includes('/api/v1/customers')) {
  33  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  34  |     }
  35  |     if (url.includes('/api/v1/payments')) {
  36  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  37  |     }
  38  |     if (url.includes('/api/v1/orders')) {
  39  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  40  |     }
  41  |     if (url.includes('/api/v1/products/categories')) {
  42  |       return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  43  |     }
  44  |     if (url.includes('/api/v1/products')) {
  45  |       return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0, first: true, last: true }) });
  46  |     }
  47  | 
  48  |     return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  49  |   });
  50  | }
  51  | 
  52  | // Inject auth into localStorage before navigating.
  53  | // Uses addInitScript so the store is populated before React runs — no extra navigation needed.
  54  | async function injectAuth(page: import('@playwright/test').Page, role: 'ADMIN' | 'SELLER' | 'USER') {
  55  |   await page.emulateMedia({ reducedMotion: 'reduce' });
  56  |   await page.addInitScript((r) => {
  57  |     localStorage.setItem('obsidian-auth', JSON.stringify({
  58  |       state: {
  59  |         accessToken: 'mock-access-token',
  60  |         refreshToken: 'mock-refresh-token',
  61  |         userId: `${r.toLowerCase()}-001`,
  62  |         email: `${r.toLowerCase()}@example.com`,
  63  |         role: r,
  64  |         tenantId: 'tenant-001',
  65  |         isAuthenticated: true,
  66  |       },
  67  |       version: 0,
  68  |     }));
  69  |   }, role);
  70  | }
  71  | 
  72  | test.beforeEach(async ({ page }) => {
  73  |   await mockApi(page);
  74  | });
  75  | 
  76  | test.describe('Admin — access control', () => {
  77  |   test('non-admin cannot access /admin', async ({ page }) => {
  78  |     await injectAuth(page, 'USER');
  79  |     await page.goto('/admin');
  80  |     await expect(page).not.toHaveURL(/\/admin$/);
  81  |   });
  82  | 
  83  |   test('unauthenticated user redirected from /admin', async ({ page }) => {
> 84  |     await page.goto('/admin');
      |                ^ Error: page.goto: Test timeout of 60000ms exceeded.
  85  |     await expect(page).toHaveURL(/\/login/);
  86  |   });
  87  | });
  88  | 
  89  | test.describe('Admin dashboard', () => {
  90  |   test.beforeEach(async ({ page }) => {
  91  |     await injectAuth(page, 'ADMIN');
  92  |   });
  93  | 
  94  |   test('admin navigates to /admin and sees dashboard', async ({ page }) => {
  95  |     await page.goto('/admin');
  96  |     await expect(page).not.toHaveURL(/\/login/);
  97  |     await expect(page.getByRole('heading', { name: /admin dashboard/i })).toBeVisible();
  98  |   });
  99  | 
  100 |   test('/admin/tenants renders tenants heading', async ({ page }) => {
  101 |     await page.goto('/admin/tenants');
  102 |     await expect(page).not.toHaveURL(/\/login/);
  103 |     await expect(page.getByRole('heading', { name: /tenants/i })).toBeVisible();
  104 |   });
  105 | 
  106 |   test('/admin/users renders users heading', async ({ page }) => {
  107 |     await page.goto('/admin/users');
  108 |     await expect(page).not.toHaveURL(/\/login/);
  109 |     await expect(page.getByRole('heading', { name: /users/i })).toBeVisible();
  110 |   });
  111 | 
  112 |   test('/admin/payments renders payments heading', async ({ page }) => {
  113 |     await page.goto('/admin/payments');
  114 |     await expect(page).not.toHaveURL(/\/login/);
  115 |     await expect(page.getByRole('heading', { name: /payments/i })).toBeVisible();
  116 |   });
  117 | 
  118 |   test('/admin/analytics renders analytics heading', async ({ page }) => {
  119 |     await page.goto('/admin/analytics');
  120 |     await expect(page).not.toHaveURL(/\/login/);
  121 |     await expect(page.getByRole('heading', { name: /analytics/i })).toBeVisible();
  122 |   });
  123 | 
  124 |   test('tenant detail page renders with tabs', async ({ page }) => {
  125 |     await page.goto('/admin/tenants/tenant-001');
  126 |     await expect(page).not.toHaveURL(/\/login/);
  127 |     await expect(page.getByRole('heading', { name: /demo store/i })).toBeVisible();
  128 |     await expect(page.getByRole('tab', { name: /overview/i })).toBeVisible();
  129 |     await expect(page.getByRole('tab', { name: /feature flags/i })).toBeVisible();
  130 |     await expect(page.getByRole('tab', { name: /usage/i })).toBeVisible();
  131 |   });
  132 | });
  133 | 
  134 | test.describe('Seller dashboard', () => {
  135 |   test.beforeEach(async ({ page }) => {
  136 |     await injectAuth(page, 'SELLER');
  137 |   });
  138 | 
  139 |   test('/seller renders seller dashboard', async ({ page }) => {
  140 |     await page.goto('/seller');
  141 |     await expect(page).not.toHaveURL(/\/login/);
  142 |   });
  143 | 
  144 |   test('/seller/products renders product management', async ({ page }) => {
  145 |     await page.goto('/seller/products', { waitUntil: 'networkidle' });
  146 |     await expect(page).not.toHaveURL(/\/login/);
  147 |     await expect(page.getByRole('heading', { name: /products/i })).toBeVisible();
  148 |   });
  149 | 
  150 |   test('/seller/products/new renders product form', async ({ page }) => {
  151 |     await page.goto('/seller/products/new');
  152 |     await expect(page).not.toHaveURL(/\/login/);
  153 |     await expect(page.getByText(/new product/i)).toBeVisible();
  154 |   });
  155 | });
  156 | 
```