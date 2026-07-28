// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import { expect, test } from '@playwright/test';
import { FIXTURE, awaitAppBoot, clickByRole, clickByText, collectErrors } from './helpers';

// Smoke test for the interactive tutorial on the wasm build: the picker lists all four lessons, the
// written rules stay reachable from it, and picking a lesson pages its primer and deals the hand.
//
// KNOWN PLATFORM LIMITATION (pre-existing — reproducible with the plain Settings dialog too):
// Compose for Web's accessibility mirror keeps the content of the last dialog that was open, so
// once the primer closes nothing on the felt is queryable by role or text. The felt is therefore
// asserted by pixels — that the canvas actually repainted out of the reader — plus a
// console-error-clean run. The hand being played out is covered on device by TutorialFlowTest.
test('the lesson picker opens and lesson one boots its scripted hand', async ({ page }) => {
  test.slow(); // wasm boot plus a four-page primer plus a deal
  const errors = collectErrors(page);
  await page.goto(FIXTURE);
  await awaitAppBoot(page);

  await clickByRole(page, 'button', 'How to play');
  for (const title of ['Your first hand', 'Making trump', 'Going alone', 'Defending']) {
    await expect(page.getByText(title).first()).toBeVisible({ timeout: 15_000 });
  }
  await expect(page.getByRole('button', { name: 'Read the rules' })).toBeVisible();

  // Lesson 1's primer: four pages, so exactly three taps of Next. Not a "tap until Deal appears"
  // loop — Next and Deal share the same slot, so a stale accessibility frame would have the loop
  // tap Deal by accident.
  await clickByText(page, 'Your first hand');
  await expect(page.getByText('Welcome to Euchre').first()).toBeVisible({ timeout: 15_000 });
  for (let i = 0; i < 3; i++) {
    await clickByRole(page, 'button', 'Next');
    await page.waitForTimeout(400);
  }
  await expect(page.getByRole('button', { name: 'Deal' })).toBeVisible({ timeout: 15_000 });

  const primerShot = await page.screenshot();
  await clickByRole(page, 'button', 'Deal');
  await page.waitForTimeout(3_000);
  const feltShot = await page.screenshot();
  expect(feltShot.equals(primerShot), 'Deal must replace the primer with the dealt hand').toBe(false);

  expect(errors, 'the tutorial must be console-error clean').toEqual([]);
});
