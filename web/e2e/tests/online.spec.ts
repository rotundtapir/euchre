// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import { test, expect, Page } from '@playwright/test';
import { awaitAppBoot, clickByRole, collectErrors } from './helpers';

// End-to-end online smoke: the browser (wasm) Ktor client talks to a real local game server over a
// WebSocket. This is the only stage that exercises the wasm client against the server, and it earned
// that description immediately — the sealed-to-interface protocol move broke serialization on wasm
// only, and 500's equivalent suite was the thing that caught it while every JVM test stayed green.
//
// Player name and server URL arrive as URL params so we never type into the Compose canvas.
const ONLINE_FIXTURE =
  '/euchre/?serverUrl=ws://localhost:8081&playerName=Tester&animationSpeed=OFF&soundVolume=0';

test('connects to the server and creates a lobby', async ({ page }) => {
  const errors = collectErrors(page);
  await page.goto(ONLINE_FIXTURE);
  await awaitAppBoot(page);

  // Home -> online entry (name prefilled from the URL param) -> create form.
  await clickByRole(page, 'button', 'Play with friends');
  await clickByRole(page, 'button', 'Create a game');
  // Exact match: a plain "Create" substring also matches the entry screen's "Create a game" button,
  // which can still be in the a11y mirror mid-transition.
  await clickByRole(page, 'button', /^Create$/);

  // The server replied with a LobbyState. Codes use an unambiguous uppercase alphabet with no
  // 0/1/I/O — see cardkit's RoomRegistry.CODE_ALPHABET.
  await expect(page.getByText(/^[2-9A-HJ-NP-Z]{4}$/).first()).toBeVisible({ timeout: 15_000 });
  // Four seats, always: a euchre table is two partnerships and the lobby says so before the deal.
  await expect(page.getByText(/Team 1/).first()).toBeVisible();
  await expect(page.getByText(/open/).first()).toBeVisible();
  // The creator sees Start (empty seats become bots) and can share the invite.
  await expect(page.getByRole('button', { name: /^Start/ })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Share invite link' })).toBeVisible();

  expect(errors, `console errors: ${errors.join('\n')}`).toEqual([]);
});

test('the lobby carries the house rules the host chose', async ({ page }) => {
  const errors = collectErrors(page);
  await page.goto(ONLINE_FIXTURE);
  await awaitAppBoot(page);
  await clickByRole(page, 'button', 'Play with friends');
  await clickByRole(page, 'button', 'Create a game');

  // The create screen renders the same four house-rule switches as local setup, so the choice a
  // player already understands is the choice they make for a table. Matched by their labels: Compose
  // testTags are not exposed to the wasm accessibility mirror (they exist for the Android suite), so
  // every locator here goes through a role or visible text, as the other specs do.
  for (const rule of ['Stick the dealer', 'Defend alone', 'Benny (joker)', "Farmer's hand"]) {
    await expect(page.getByText(rule, { exact: false }).first()).toBeVisible();
  }
  await clickByRole(page, 'button', /^Create$/);
  await expect(page.getByText(/^[2-9A-HJ-NP-Z]{4}$/).first()).toBeVisible({ timeout: 15_000 });

  expect(errors, `console errors: ${errors.join('\n')}`).toEqual([]);
});

/** Create a lobby as the host and return its join code — the shared preamble of the tests below. */
async function createLobby(page: Page): Promise<string> {
  await page.goto(ONLINE_FIXTURE);
  await awaitAppBoot(page);
  await clickByRole(page, 'button', 'Play with friends');
  await clickByRole(page, 'button', 'Create a game');
  await clickByRole(page, 'button', /^Create$/);
  const code = page.getByText(/^[2-9A-HJ-NP-Z]{4}$/).first();
  await expect(code).toBeVisible({ timeout: 15_000 });
  return (await code.textContent())!;
}

test('a page reload offers the held lobby back via the persisted session token', async ({ page }) => {
  const errors = collectErrors(page);
  const code = await createLobby(page);

  // A reload tears down the wasm instance and its socket. The token lives in the tab's
  // sessionStorage, and server-side the lobby seat survives a short disconnect grace instead of the
  // room disbanding on the drop — together that is what makes the seat reclaimable.
  await page.reload();
  await awaitAppBoot(page);
  // Re-entering online mode is what opens a socket: nothing connects while the app sits on home, so
  // the offer cannot appear before this click.
  await clickByRole(page, 'button', 'Play with friends');
  // The prompt names the exact room from before the reload, which is the whole assertion: the token
  // survived AND the server held the seat rather than disbanding the lobby on the drop.
  await expect(page.getByText(`You're still in game ${code}`)).toBeVisible({ timeout: 15_000 });
  await expect(page.getByRole('button', { name: /^Rejoin$/ })).toBeVisible();

  // Stops at the offer: once a dialog closes the wasm a11y mirror goes stale, so the actual re-entry
  // is asserted by the invite-link test below instead.
  expect(errors, `console errors: ${errors.join('\n')}`).toEqual([]);
});

test('opening your own invite link returns you to your lobby', async ({ page }) => {
  const errors = collectErrors(page);
  const code = await createLobby(page);

  // Reopening your own link is a formality rejoin: the resumed code matches the one being asked
  // for, so the client drops straight back in rather than asking a question it already knows.
  // A joinCode link opens straight into online mode, so the home screen may never appear — wait for
  // the loading placeholder to clear rather than for a home button.
  await page.goto(`${ONLINE_FIXTURE}&joinCode=${code}`);
  await expect(page.locator('#loading')).toHaveCount(0, { timeout: 60_000 });
  await expect(page.getByText(code).first()).toBeVisible({ timeout: 30_000 });
  // Only the creator's lobby offers the invite — proof we are the host again, not a joiner, and that
  // the room was not disbanded when the tab navigated away.
  await expect(page.getByRole('button', { name: 'Share invite link' })).toBeVisible();

  expect(errors, `console errors: ${errors.join('\n')}`).toEqual([]);
});

test('an invite link from someone else opens the join screen with the code prefilled', async ({ page }) => {
  const errors = collectErrors(page);
  // A fresh tab with no session token: this is what a guest following a shared link sees.
  await page.goto('/euchre/?serverUrl=ws://localhost:8081&playerName=Guest&joinCode=AB12' +
    '&animationSpeed=OFF&soundVolume=0');
  await expect(page.locator('#loading')).toHaveCount(0, { timeout: 60_000 });
  await expect(page.getByText('Join a game')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('button', { name: /^Join$/ })).toBeVisible();

  expect(errors, `console errors: ${errors.join('\n')}`).toEqual([]);
});
