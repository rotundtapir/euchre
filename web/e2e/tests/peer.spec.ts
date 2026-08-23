// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
// Not a test: a second human player for scripts/screenshots.py, which needs a real peer in a seat
// so the online listing images do not show three "(bot)" labels. Driven by JOIN_CODE / PEER_HOLD_MS.
import { test } from '@playwright/test';
import { expect } from '@playwright/test';
import { clickByRole } from './helpers';

test('hold a seat as a peer', async ({ page }) => {
  // A helper, not a test: without a live lobby to join there is nothing to assert, and running it
  // as part of the suite would fail every time. Skipping keeps `npx playwright test` honest while
  // leaving it runnable on demand from scripts/screenshots.py's workflow.
  test.skip(!process.env.JOIN_CODE, 'set JOIN_CODE to hold a seat in an existing lobby');
  const code = process.env.JOIN_CODE!;
  const hold = Number(process.env.PEER_HOLD_MS ?? 90_000);
  test.setTimeout(hold + 60_000);
  await page.goto(
    `/euchre/?serverUrl=ws://localhost:8081&playerName=Robin&joinCode=${code}` +
      '&animationSpeed=OFF&soundVolume=0',
  );
  // NOT awaitAppBoot: a ?joinCode= link opens straight on the Join screen, so waiting for the
  // home screen's "Play with bots" waits for a button this launch never shows.
  await expect(page.getByRole('button', { name: /^Join$/ })).toBeVisible({ timeout: 60_000 });
  await clickByRole(page, 'button', /^Join$/);
  await clickByRole(page, 'button', /Sit here/);
  await clickByRole(page, 'button', /Ready up/);
  // Stay connected: the moment this socket drops the seat reverts to a bot and the label the
  // screenshot exists to show turns into "(bot)".
  await page.waitForTimeout(hold);
});
