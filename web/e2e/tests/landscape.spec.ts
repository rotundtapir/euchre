// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import { expect, test } from '@playwright/test';
import { awaitAppBoot, clickByRole, FIXTURE } from './helpers';

// The bug this pins: the game column was sized for portrait and did not scroll, so on a landscape
// phone the hand fan and the whole action panel fell off the bottom — the bid buttons' top edge was
// visible but the cards were not, and none could be tapped. Portrait is the control case, so a
// green run means "landscape works AND portrait is unchanged", not merely "something rendered".
const VIEWPORTS = [
  { name: 'landscape phone', width: 844, height: 390 },
  { name: 'portrait phone', width: 390, height: 844 },
] as const;

for (const vp of VIEWPORTS) {
  test(`the table is playable — ${vp.name}`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height });
    await page.goto(FIXTURE);
    await awaitAppBoot(page);
    await clickByRole(page, 'button', 'Play with bots');
    // The setup screen scrolls (its house-rule list is taller than a landscape phone), so bring the
    // start button into view first — the same reason GameFlowTest calls performScrollTo on it.
    // scrollIntoViewIfNeeded cannot do it: that moves the a11y mirror's div, while the scrolling
    // happens inside the canvas. A wheel event over the canvas is what Compose actually receives.
    await page.mouse.move(vp.width / 2, vp.height / 2);
    await page.mouse.wheel(0, 400);
    await clickByRole(page, 'button', /^Play$/);

    // Reaching the round-1 prompt proves the table laid out at this size.
    const prompt = page.getByRole('button', { name: 'Pick it up' });
    await expect(prompt).toBeVisible({ timeout: 30_000 });

    // The regression itself: the prompt must sit INSIDE the viewport, not below its bottom edge.
    const promptBox = await prompt.boundingBox();
    expect(promptBox, 'the prompt should have a layout box').not.toBeNull();
    expect(promptBox!.y + promptBox!.height).toBeLessThanOrEqual(vp.height);

    // And the player's own cards must be on screen too — the bid buttons surviving while the fan
    // is clipped is exactly what the bug looked like. During bidding the cards are not tappable, so
    // the mirror exposes them as `img` rather than `button`; the last one is the fan's right edge.
    const card = page.getByRole('img', { name: /^(9|10|J|Q|K|A)[♠♥♦♣]$/ }).last();
    await expect(card).toBeVisible();
    const cardBox = await card.boundingBox();
    expect(cardBox, 'a hand card should have a layout box').not.toBeNull();
    expect(cardBox!.y + cardBox!.height).toBeLessThanOrEqual(vp.height);
  });
}
