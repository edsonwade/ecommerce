import { test, expect } from '@playwright/test';

// Mirrors the REAL customer-service contract: the field is `customerId` (not `id`) and
// `address` is null for auth-provisioned profiles. The old mock returned [] here, which
// hid a production crash (`r.id.slice` on undefined) on /admin/users.
const MOCK_CUSTOMERS = [
  {
    customerId: 'a1b2c3d4-e5f6-7890-abcd-ef1234567890',
    firstname: 'Jane',
    lastname: 'Doe',
    email: 'jane@example.com',
    address: { street: 'Market St', houseNumber: '1', zipCode: '94103', city: 'San Francisco', country: 'US' },
  },
  {
    customerId: 'b2c3d4e5-f6a7-8901-bcde-f23456789012',
    firstname: 'John',
    lastname: 'NoAddress',
    email: 'john@example.com',
    address: null,
  },
];

const MOCK_TENANT = {
  tenantId: 'tenant-001',
  name: 'Demo Store',
  slug: 'demo-store',
  contactEmail: 'admin@demo.com',
  plan: 'STARTER',
  status: 'ACTIVE',
  rateLimit: 100,
  storageQuota: 1024,
  createdAt: '2024-01-01T00:00:00Z',
  updatedAt: '2024-01-01T00:00:00Z',
};

// Mock all /api/* requests so the frontend renders without a running backend
async function mockApi(page: import('@playwright/test').Page) {
  await page.route('/api/**', (route) => {
    const url = route.request().url();

    if (url.match(/\/api\/v1\/tenants\/[^/]+\/flags/)) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([
        { flagName: 'ADVANCED_ANALYTICS', enabled: true, description: 'Analytics' },
      ]) });
    }
    if (url.match(/\/api\/v1\/tenants\/[^/]+$/)) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_TENANT) });
    }
    if (url.includes('/api/v1/tenants')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([MOCK_TENANT]) });
    }
    if (url.includes('/api/v1/customers')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_CUSTOMERS) });
    }
    if (url.includes('/api/v1/payments')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
    }
    if (url.includes('/api/v1/orders')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
    }
    if (url.includes('/api/v1/products/categories')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    }
    if (url.includes('/api/v1/products')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, size: 20, number: 0, first: true, last: true }) });
    }

    return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });
}

// Inject auth into sessionStorage before navigating.
// The auth store persists to sessionStorage (tab-scoped — prevents cross-tab role
// poisoning); injecting into localStorage would be ignored and every guarded route
// would redirect to /login. Uses addInitScript so the store is populated before React runs.
async function injectAuth(page: import('@playwright/test').Page, role: 'ADMIN' | 'SELLER' | 'USER') {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.addInitScript((r) => {
    sessionStorage.setItem('obsidian-auth', JSON.stringify({
      state: {
        accessToken: 'mock-access-token',
        refreshToken: 'mock-refresh-token',
        userId: `${r.toLowerCase()}-001`,
        email: `${r.toLowerCase()}@example.com`,
        role: r,
        tenantId: 'tenant-001',
        isAuthenticated: true,
      },
      version: 0,
    }));
  }, role);
}

test.beforeEach(async ({ page }) => {
  await mockApi(page);
});

test.describe('Admin — access control', () => {
  test('non-admin cannot access /admin', async ({ page }) => {
    await injectAuth(page, 'USER');
    await page.goto('/admin');
    await expect(page).not.toHaveURL(/\/admin$/);
  });

  test('unauthenticated user redirected from /admin', async ({ page }) => {
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/login/);
  });
});

test.describe('Admin — navigation badge', () => {
  test('ADMIN role badge is visible in the navbar', async ({ page }) => {
    await injectAuth(page, 'ADMIN');
    await page.goto('/admin');
    await expect(page).not.toHaveURL(/\/login/);
    // RoleBadge renders a Chip with label "ADMIN"
    await expect(page.getByText('ADMIN').first()).toBeVisible();
  });
});

test.describe('Admin dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await injectAuth(page, 'ADMIN');
  });

  test('admin navigates to /admin and sees dashboard', async ({ page }) => {
    await page.goto('/admin');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /admin dashboard/i })).toBeVisible();
  });

  test('/admin/tenants renders tenants heading', async ({ page }) => {
    await page.goto('/admin/tenants');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /tenants/i })).toBeVisible();
  });

  test('/admin/users renders users heading', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /users/i })).toBeVisible();
  });

  test('/admin/users renders customer rows without crashing (customerId + null address)', async ({ page }) => {
    await page.goto('/admin/users');
    // Both rows render: one with a full address, one auth-provisioned (address: null).
    await expect(page.getByText('Jane Doe')).toBeVisible();
    await expect(page.getByText('John NoAddress')).toBeVisible();
    await expect(page.getByText('San Francisco, US')).toBeVisible();
    // The truncated customerId is displayed (regression guard for r.id.slice crash).
    await expect(page.getByText('a1b2c3d4…')).toBeVisible();
    // Neither the router's raw error page nor the app error screen appears.
    await expect(page.getByText(/unexpected application error/i)).toHaveCount(0);
    await expect(page.getByText(/something went wrong/i)).toHaveCount(0);
  });

  test('/admin/payments renders payments heading', async ({ page }) => {
    await page.goto('/admin/payments');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /payments/i })).toBeVisible();
  });

  test('/admin/analytics renders analytics heading', async ({ page }) => {
    await page.goto('/admin/analytics');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /analytics/i })).toBeVisible();
  });

  test('tenant detail page renders with tabs', async ({ page }) => {
    await page.goto('/admin/tenants/tenant-001');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /demo store/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /overview/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /feature flags/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /usage/i })).toBeVisible();
  });
});

test.describe('Seller dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await injectAuth(page, 'SELLER');
  });

  test('/seller renders seller dashboard', async ({ page }) => {
    await page.goto('/seller');
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('/seller/products renders product management', async ({ page }) => {
    await page.goto('/seller/products', { waitUntil: 'networkidle' });
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByRole('heading', { name: /products/i })).toBeVisible();
  });

  test('/seller/products/new renders product form', async ({ page }) => {
    await page.goto('/seller/products/new');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByText(/new product/i)).toBeVisible();
  });
});
