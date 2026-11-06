import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/auth/csrf', (route) => route.fulfill({ json: { data: { token: 'csrf-test', headerName: 'X-XSRF-TOKEN' } } }));
  await page.route('**/api/v1/auth/refresh', (route) => route.fulfill({ status: 401, contentType: 'application/problem+json', json: { title: 'Unauthorized', status: 401 } }));
});

test('login screen offers email or phone credentials', async ({ page }) => {
  await page.goto('/login');
  await expect(page.getByRole('heading', { name: 'Welcome back' })).toBeVisible();
  await expect(page.getByLabel('Email or phone')).toBeVisible();
  await expect(page.getByLabel('Password')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Forgot password?' })).toBeVisible();
});

test('forgot password screen submits without exposing account state', async ({ page }) => {
  await page.route('**/api/v1/auth/csrf', (route) => route.fulfill({ json: { data: { token: 'csrf-test' } } }));
  await page.route('**/api/v1/auth/forgot-password', (route) => route.fulfill({ json: { data: { message: 'If the account is eligible, a reset link has been sent' } } }));
  await page.goto('/forgot-password');
  await page.getByLabel('Email or phone').fill('person@example.test');
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(page.getByText(/check your email|reset link/i)).toBeVisible();
});
