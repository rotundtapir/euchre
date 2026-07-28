// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import { expect, Locator, Page } from '@playwright/test';

/** The deterministic fixture shared with the Android instrumentation suite (intent-extra mirror). */
export const FIXTURE = '/euchre/?seed=42&animationSpeed=OFF&soundVolume=0';

/**
 * Compose Multiplatform mirrors semantics into the accessibility tree with real bounding boxes,
 * but the canvas intercepts pointer events, so Playwright's actionability check on the mirror
 * element times out. Locate semantically, then click the canvas at the element's centre.
 */
export async function clickByRole(page: Page, role: 'button' | 'img' | 'checkbox', name: string | RegExp) {
  await clickCentre(page, page.getByRole(role, { name }).first(), `${role} "${name}"`);
}

/** The same trick for elements the a11y tree exposes only as text (the lesson picker's rows). */
export async function clickByText(page: Page, text: string | RegExp) {
  await clickCentre(page, page.getByText(text).first(), `text "${text}"`);
}

/**
 * Clicks the canvas at [locator]'s centre. The mirror element is rebuilt whenever the semantics
 * tree changes, so a box measured on a frame that is being replaced comes back null (or empty) —
 * poll until one settles rather than failing the whole flow on a paging animation.
 */
async function clickCentre(page: Page, locator: Locator, label: string) {
  for (let attempt = 0; attempt < 20; attempt++) {
    await expect(locator).toBeVisible();
    const box = await locator.boundingBox();
    if (box && box.width > 0 && box.height > 0) {
      // Hover first, then a press with a real hold: Compose's canvas tracks the pointer from move
      // events, and back-to-back zero-delay clicks at the same spot arrive as a double/triple tap
      // that it does not treat as two separate presses.
      await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2);
      await page.waitForTimeout(50);
      await page.mouse.down();
      await page.waitForTimeout(50);
      await page.mouse.up();
      return;
    }
    await page.waitForTimeout(100);
  }
  throw new Error(`no bounding box for ${label}`);
}

/**
 * Start collecting console errors and uncaught page errors. Register before goto().
 * Skiko probes WebGL extensions with warnings; only `error`-level entries are collected.
 */
export function collectErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') errors.push(msg.text());
  });
  page.on('pageerror', (err) => errors.push(String(err)));
  return errors;
}

/** Wait for the wasm app to boot: the static placeholder is retired right before the first frame. */
export async function awaitAppBoot(page: Page) {
  await expect(page.locator('#loading')).toHaveCount(0, { timeout: 60_000 });
  await expect(page.getByRole('button', { name: 'Play with bots' })).toBeVisible({ timeout: 30_000 });
}
