import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('login page renders sign-in form', async ({ page }) => {
    await page.goto('/login');
    // Heading is rendered as component="div" (not a semantic h1), subtitle is body1 text
    await expect(page.getByText('Obsidian Market').first()).toBeVisible();
    await expect(page.getByText(/sign in to your account/i)).toBeVisible();
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/password/i)).toBeVisible();
    await expect(page.getByRole('button', { name: /sign in/i })).toBeVisible();
  });

  test('unauthenticated user is redirected from /account to /login', async ({ page }) => {
    await page.goto('/account');
    await expect(page).toHaveURL(/\/login/);
  });

  test('unauthenticated user is redirected from /seller to /login', async ({ page }) => {
    await page.goto('/seller');
    await expect(page).toHaveURL(/\/login/);
  });

  test('unauthenticated user is redirected from /admin to /login', async ({ page }) => {
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/login/);
  });

  test('register page renders form', async ({ page }) => {
    await page.goto('/register');
    await expect(page.getByText('Obsidian Market').first()).toBeVisible();
    await expect(page.getByText(/create your account/i)).toBeVisible();
    await expect(page.getByLabel(/first name/i)).toBeVisible();
    await expect(page.getByLabel(/last name/i)).toBeVisible();
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/password/i)).toBeVisible();
  });

  test('login form shows validation errors on empty submit', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('button', { name: /sign in/i }).click();
    await expect(page.getByText(/required|valid email/i).first()).toBeVisible();
  });

  test('register form shows validation errors on empty submit', async ({ page }) => {
    await page.goto('/register');
    await page.getByRole('button', { name: /create account/i }).click();
    await expect(page.getByText(/required/i).first()).toBeVisible();
  });
});
