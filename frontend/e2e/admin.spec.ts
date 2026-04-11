import { test, expect } from '@playwright/test';

// Helper: inject admin auth into localStorage before navigating
async function loginAsAdmin(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.evaluate(() => {
    const auth = {
      state: {
        accessToken: 'mock-access-token',
        refreshToken: 'mock-refresh-token',
        userId: 'admin-001',
        email: 'admin@example.com',
        role: 'ADMIN',
        tenantId: 'tenant-001',
        isAuthenticated: true,
      },
      version: 0,
    };
    localStorage.setItem('obsidian-auth', JSON.stringify(auth));
  });
}

test.describe('Admin — access control', () => {
  test('non-admin cannot access /admin', async ({ page }) => {
    await page.goto('/login');
    await page.evaluate(() => {
      const auth = {
        state: {
          accessToken: 'mock-access-token',
          refreshToken: 'mock-refresh-token',
          userId: 'user-001',
          email: 'user@example.com',
          role: 'USER',
          tenantId: 'tenant-001',
          isAuthenticated: true,
        },
        version: 0,
      };
      localStorage.setItem('obsidian-auth', JSON.stringify(auth));
    });
    await page.goto('/admin');
    // Should be redirected away (to / or /login) — not show admin content
    await expect(page).not.toHaveURL(/\/admin$/);
  });

  test('unauthenticated user redirected from /admin', async ({ page }) => {
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/login/);
  });
});

test.describe('Admin dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('admin navigates to /admin and sees dashboard', async ({ page }) => {
    await page.goto('/admin');
    await expect(page).not.toHaveURL(/\/login/);
    // Dashboard heading or stat cards are present
    await expect(page.getByText(/admin/i).first()).toBeVisible();
  });

  test('/admin/tenants renders tenants heading', async ({ page }) => {
    await page.goto('/admin/tenants');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByText(/tenants/i).first()).toBeVisible();
  });

  test('/admin/users renders users heading', async ({ page }) => {
    await page.goto('/admin/users');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByText(/users/i).first()).toBeVisible();
  });

  test('/admin/payments renders payments heading', async ({ page }) => {
    await page.goto('/admin/payments');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByText(/payments/i).first()).toBeVisible();
  });

  test('/admin/analytics renders analytics heading', async ({ page }) => {
    await page.goto('/admin/analytics');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByText(/analytics/i).first()).toBeVisible();
  });

  test('tenant detail page renders with tabs', async ({ page }) => {
    await page.goto('/admin/tenants/tenant-001');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByRole('tab', { name: /overview/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /feature flags/i })).toBeVisible();
    await expect(page.getByRole('tab', { name: /usage/i })).toBeVisible();
  });
});

test.describe('Seller dashboard', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/login');
    await page.evaluate(() => {
      const auth = {
        state: {
          accessToken: 'mock-access-token',
          refreshToken: 'mock-refresh-token',
          userId: 'seller-001',
          email: 'seller@example.com',
          role: 'SELLER',
          tenantId: 'tenant-001',
          isAuthenticated: true,
        },
        version: 0,
      };
      localStorage.setItem('obsidian-auth', JSON.stringify(auth));
    });
  });

  test('/seller renders seller dashboard', async ({ page }) => {
    await page.goto('/seller');
    await expect(page).not.toHaveURL(/\/login/);
  });

  test('/seller/products renders product management', async ({ page }) => {
    await page.goto('/seller/products');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByText(/products/i).first()).toBeVisible();
  });

  test('/seller/products/new renders product form', async ({ page }) => {
    await page.goto('/seller/products/new');
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByText(/new product/i)).toBeVisible();
  });
});
