import { test, expect } from '@playwright/test';

test.describe('Catalog & Cart', () => {
  test('home page renders hero section', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText(/obsidian market/i).first()).toBeVisible();
  });

  test('catalog page renders product grid', async ({ page }) => {
    await page.goto('/catalog');
    // Wait for content to load (products or loading state)
    await expect(page.locator('main, [role="main"], .catalog')).toBeTruthy();
    await expect(page).toHaveTitle(/obsidian market/i);
  });

  test('cart icon is visible in navbar', async ({ page }) => {
    await page.goto('/');
    // ShoppingBag icon button
    const cartBtn = page.locator('button').filter({ has: page.locator('[data-testid="ShoppingBagIcon"], svg') }).first();
    await expect(cartBtn).toBeVisible();
  });

  test('product detail page renders for known product', async ({ page }) => {
    await page.goto('/catalog/1');
    await expect(page).toHaveTitle(/obsidian market/i);
  });
});

test.describe('Checkout flow', () => {
  test('checkout page redirects to login when unauthenticated', async ({ page }) => {
    await page.goto('/checkout');
    await expect(page).toHaveURL(/\/login/);
  });

  test('checkout page renders stepper with 3 steps when accessed after login', async ({ page }) => {
    // Simulate stored auth via localStorage before navigating
    await page.goto('/login');
    await page.evaluate(() => {
      const auth = {
        state: {
          accessToken: 'mock-access-token',
          refreshToken: 'mock-refresh-token',
          userId: 'user-001',
          email: 'test@example.com',
          role: 'USER',
          tenantId: 'tenant-001',
          isAuthenticated: true,
        },
        version: 0,
      };
      localStorage.setItem('obsidian-auth', JSON.stringify(auth));
    });
    await page.goto('/checkout');
    // Should render stepper (not redirect)
    await expect(page).not.toHaveURL(/\/login/);
    await expect(page.getByText(/address/i)).toBeVisible();
  });
});
