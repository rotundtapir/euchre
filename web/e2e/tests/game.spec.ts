// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import { expect, test } from '@playwright/test';
import { awaitAppBoot, clickByRole, collectErrors, FIXTURE } from './helpers';

/** Any face-up card in the human's hand that is currently tappable (playable cards are buttons). */
const PLAYABLE_CARD = /^(9|10|J|Q|K|A)[♠♥♦♣]$/;

// The seed-42 fixture (the same one GameFlowTest pins): the human deals the first hand with J♦
// turned up, so the round-1 prompt reads "Order up ♦?" over a "Pick it up" button. Passing it round
// turns the up-card down and the bot on the left names spades in round 2.
test('seeded game: suit glyphs render, bidding works, a trick completes', async ({ page }) => {
  const errors = collectErrors(page);
  await page.goto(FIXTURE);
  await awaitAppBoot(page);

  // Play with bots -> the house-rule setup screen -> Play.
  await clickByRole(page, 'button', 'Play with bots');
  await clickByRole(page, 'button', /^Play$/);

  // The suit symbols come through the accessibility tree, which catches the missing-glyph (tofu)
  // regression without a pixel diff.
  await expect(page.getByText('Order up ♦?')).toBeVisible({ timeout: 30_000 });
  // The human deals the first hand, so ordering up would mean taking the card into hand.
  await expect(page.getByRole('button', { name: 'Pick it up' })).toBeVisible();
  await expect(page.getByText('Us: 0/10')).toBeVisible();
  // No narration toggle in a regular game: narration is tutorial-only and its audio hasn't
  // shipped, so a "♪ on/off" control here would be a dead switch (the toggle's label is the one
  // ♪ the accessibility mirror could carry — the suit glyphs above are ♦♠♥♣, never ♪).
  await expect(page.getByText(/♪/)).toHaveCount(0);

  await clickByRole(page, 'button', 'Pass');

  // A bot makes spades once the up-card is turned down, and play begins.
  await expect(page.getByText(/Trump: ♠/)).toBeVisible({ timeout: 30_000 });
  await expect(page.getByText('Your turn — tap a card to play')).toBeVisible({ timeout: 30_000 });

  // Playable cards surface as buttons in the accessibility tree (unplayable ones are plain imgs).
  // Bots finish the trick instantly at animationSpeed=OFF and the sweep bumps a trick counter.
  const playable = page.getByRole('button', { name: PLAYABLE_CARD }).first();
  await expect(playable).toBeVisible();
  const box = await playable.boundingBox();
  if (!box) throw new Error('no bounding box for a playable card');
  await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2);

  await expect(page.getByText(/tricks: 1/).first()).toBeVisible({ timeout: 30_000 });

  expect(errors, 'game flow must be console-error clean').toEqual([]);
});

// The dealer's other round-1 option: taking the up-card into hand and burying one. This exercises
// the card-selection panel, which no other spec reaches.
test('the dealer can pick the up-card up and bury one', async ({ page }) => {
  const errors = collectErrors(page);
  await page.goto(FIXTURE);
  await awaitAppBoot(page);

  await clickByRole(page, 'button', 'Play with bots');
  await clickByRole(page, 'button', /^Play$/);
  await expect(page.getByRole('button', { name: 'Pick it up' })).toBeVisible({ timeout: 30_000 });
  await clickByRole(page, 'button', 'Pick it up');

  // Six cards in hand, and the Discard button stays inert until exactly one is chosen.
  await expect(page.getByText(/bury one card \(0\/1 selected\)/)).toBeVisible({ timeout: 30_000 });
  await clickByRole(page, 'button', PLAYABLE_CARD);
  await expect(page.getByText(/bury one card \(1\/1 selected\)/)).toBeVisible();
  await clickByRole(page, 'button', 'Discard');

  // The turned-up J♦ makes diamonds trump, ordered by the human's own side.
  await expect(page.getByText(/Trump: ♦/)).toBeVisible({ timeout: 30_000 });

  expect(errors, 'discard flow must be console-error clean').toEqual([]);
});
