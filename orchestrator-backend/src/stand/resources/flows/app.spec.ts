import { test, expect } from '@playwright/test';

test('the app greets the world', { tag: '@AC-1' }, async ({ page }) => {
  await page.goto(process.env.BASE_URL!);
  await expect(page.locator('h1')).toHaveText('Hello, World');
});
