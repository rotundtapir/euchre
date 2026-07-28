// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import { expect, test } from '@playwright/test';
import { awaitAppBoot, clickByRole, collectErrors } from './helpers';

// Exercises the localStorage settings backend for real: a changed setting must survive a full page
// reload. Uses the plain entry point (no animationSpeed override) so the dialog reflects the
// persisted value directly. A fresh Playwright context starts with empty localStorage.
test('a settings change persists across a page reload', async ({ page }) => {
  const errors = collectErrors(page);
  await page.goto('/euchre/');
  await awaitAppBoot(page);

  // Open settings; the Animations button shows the current speed and cycles on tap.
  await clickByRole(page, 'button', 'Settings');
  await expect(page.getByRole('button', { name: 'Normal' })).toBeVisible({ timeout: 15_000 });
  await clickByRole(page, 'button', 'Normal'); // Normal → Fast

  // It reached localStorage under the app's prefix, and the dialog reflects it.
  await expect(page.getByRole('button', { name: 'Fast' })).toBeVisible();
  const stored = await page.evaluate(() => window.localStorage.getItem('euchre.animation_speed'));
  expect(stored).toBe('FAST');

  // After a full reload the persisted value is read back.
  await page.reload();
  await awaitAppBoot(page);
  await clickByRole(page, 'button', 'Settings');
  await expect(page.getByRole('button', { name: 'Fast' })).toBeVisible({ timeout: 15_000 });

  expect(errors, 'settings flow must be console-error clean').toEqual([]);
});

// "How to play" opens the four interactive lessons, but the written rules must stay reachable from
// the picker — the reference is not allowed to disappear behind the walkthrough.
test('the lesson picker still reaches the written rules', async ({ page }) => {
  const errors = collectErrors(page);
  await page.goto('/euchre/');
  await awaitAppBoot(page);

  await clickByRole(page, 'button', 'How to play');
  await clickByRole(page, 'button', 'Read the rules');
  await expect(page.getByRole('button', { name: 'Next' })).toBeVisible({ timeout: 15_000 });
  // The bowers — the rule newcomers get wrong — must be covered. Asserted as *attached* rather
  // than visible: the reader measures every page to keep its chrome from jumping, so all of the
  // pages' text sits in the accessibility tree whatever page is on screen.
  await expect(page.getByText(/bower/i).first()).toBeAttached();
  // Back only exists past the first page, so its arrival proves the reader actually paged.
  await clickByRole(page, 'button', 'Next');
  await expect(page.getByRole('button', { name: 'Back' })).toBeVisible();

  expect(errors, 'rules reader must be console-error clean').toEqual([]);
});
