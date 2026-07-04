import { test, expect } from '@playwright/test';

const MOCK_ACCOUNT = {
  id: 1,
  firstname: 'Ada',
  lastname: 'Lovelace',
  email: 'ada@example.com',
  role: 'USER',
  createdAt: '2024-01-01T00:00:00Z',
};

// Mock all /api/* requests so the frontend renders without a running backend.
async function mockApi(page: import('@playwright/test').Page) {
  await page.route('/api/**', (route) => {
    const url = route.request().url();

    if (url.includes('/api/v1/auth/account/me')) {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(MOCK_ACCOUNT) });
    }

    // Default: 200 empty — covers any incidental requests (notifications, etc).
    return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });
}

// Inject auth into sessionStorage before navigating. The auth store persists to
// sessionStorage (tab-scoped — see auth.store.ts), so a localStorage value would be
// ignored and every guarded route would redirect to /login. Uses addInitScript so the
// store is populated before React runs (mirrors admin.spec.ts's injectAuth helper).
async function injectAuth(page: import('@playwright/test').Page, role: 'ADMIN' | 'SELLER' | 'USER') {
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

test.describe('Account settings — USER', () => {
  test.beforeEach(async ({ page }) => {
    await injectAuth(page, 'USER');
  });

  test('renders all three sections: Identity, Change password, Danger zone', async ({ page }) => {
    await page.goto('/account/settings');
    await expect(page).not.toHaveURL(/\/login/);

    await expect(page.getByRole('heading', { name: 'Account settings' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Identity' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Change password' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Danger zone' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Delete my account' })).toBeVisible();
  });

  test('editing the email field reveals the current-password field', async ({ page }) => {
    await page.goto('/account/settings');
    await expect(page.getByRole('heading', { name: 'Identity' })).toBeVisible();

    const emailField = page.getByLabel(/email \(used to sign in\)/i);
    await expect(emailField).toHaveValue(MOCK_ACCOUNT.email);

    // Current-password field should not be present until the email actually changes.
    await expect(page.getByLabel(/current password \(required to change email\)/i)).toHaveCount(0);

    await emailField.fill('new-email@example.com');
    await expect(page.getByLabel(/current password \(required to change email\)/i)).toBeVisible();
  });

  test('password mismatch shows inline error and fires no network request', async ({ page }) => {
    let changePasswordRequestFired = false;
    await page.route('**/api/v1/auth/account/change-password', (route) => {
      changePasswordRequestFired = true;
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    });

    await page.goto('/account/settings');
    await expect(page.getByRole('heading', { name: 'Change password' })).toBeVisible();

    await page.getByLabel('Current password', { exact: true }).fill('OldPassword123');
    await page.getByLabel('New password', { exact: true }).fill('NewPassword123');
    await page.getByLabel('Confirm new password', { exact: true }).fill('DifferentPassword123');

    await page.getByRole('button', { name: 'Change password' }).click();

    await expect(page.getByText('Passwords do not match')).toBeVisible();
    expect(changePasswordRequestFired).toBe(false);
  });

  test('delete dialog: confirm button disabled until password typed', async ({ page }) => {
    await page.goto('/account/settings');
    await page.getByRole('button', { name: 'Delete my account' }).click();

    await expect(page.getByRole('heading', { name: 'Delete your account?' })).toBeVisible();
    const confirmButton = page.getByRole('button', { name: 'Delete account' });
    await expect(confirmButton).toBeDisabled();

    await page.getByLabel('Password', { exact: true }).fill('MyPassword123');
    await expect(confirmButton).toBeEnabled();
  });
});

test.describe('Account settings — SELLER', () => {
  test.beforeEach(async ({ page }) => {
    await injectAuth(page, 'SELLER');
  });

  test('/seller/account renders Identity + Change password, Danger zone absent', async ({ page }) => {
    await page.goto('/seller/account');
    await expect(page).not.toHaveURL(/\/login/);

    await expect(page.getByRole('heading', { name: 'Identity' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Change password' })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Danger zone' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Delete my account' })).toHaveCount(0);
  });
});
