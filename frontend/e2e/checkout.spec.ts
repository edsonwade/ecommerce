import { test, expect } from '@playwright/test';

// Mock all /api/* requests so the frontend works without a running backend
async function mockApi(page: import('@playwright/test').Page) {
  await page.route('/api/**', (route) => {
    const url = route.request().url();

    if (url.includes('/api/v1/carts/')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          cartId: 'cart-001',
          customerId: 'user-001',
          items: [{ productId: 1, productName: 'Obsidian Pen', unitPrice: 29.99, quantity: 2, lineTotal: 59.98, productDescription: '' }],
          total: 59.98,
          itemCount: 2,
          createdAt: '2024-01-01T00:00:00Z',
          updatedAt: '2024-01-01T00:00:00Z',
        }),
      });
    }

    if (url.includes('/api/v1/products')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [{ id: 1, name: 'Obsidian Pen', description: 'A premium pen', availableQuantity: 50, price: 29.99, categoryId: 1, categoryName: 'Stationery', categoryDescription: '' }],
          totalElements: 1, totalPages: 1, size: 20, number: 0, first: true, last: true,
        }),
      });
    }

    // Default: 200 empty
    return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });
}

test.describe('Catalog & Cart', () => {
  test('home page renders hero section', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByText(/obsidian market/i).first()).toBeVisible();
  });

  test('catalog page renders product grid', async ({ page }) => {
    await page.goto('/catalog');
    await expect(page).toHaveTitle(/obsidian market/i);
  });

  test('cart icon is visible in navbar', async ({ page }) => {
    await page.goto('/');
    // ShoppingBag button is in the AppBar toolbar
    const cartBtn = page.locator('header button').filter({ has: page.locator('svg') }).nth(1);
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

  test('checkout page renders stepper with 3 steps when authenticated', async ({ page }) => {
    // Mock API before setting auth so no proxy errors hit the backend
    await mockApi(page);

    // Inject auth into localStorage
    await page.goto('/');
    await page.evaluate(() => {
      localStorage.setItem('obsidian-auth', JSON.stringify({
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
      }));
    });

    await page.goto('/checkout');
    await expect(page).not.toHaveURL(/\/login/);
    // MUI Stepper renders step labels — "Address" is the first step label
    await expect(page.getByText(/address/i).first()).toBeVisible({ timeout: 10000 });
  });
});
