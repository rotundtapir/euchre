// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import { test, expect, Page } from '@playwright/test';
import { ChildProcess, spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { awaitAppBoot, clickByRole } from './helpers';

// The one full-stack restart test: a real browser client with a real server SIGKILLed and relaunched
// underneath it mid-hand. Both halves are pinned in isolation already (server: cardkit's RoomTest
// snapshot/reclaim cases and euchre's own OnlineServerTest; client: the reconnect loop), and this
// spec is what crosses them — snapshot restore plus token rebind on one side, backoff/Hello/resume on
// the other. It is also the only place the promise in PRIVACY.md and the runbook — that a deploy does
// not cost you your game — is actually demonstrated.
//
// It deliberately does NOT use the shared game server from playwright.config: killing that would take
// every other online test down with it. It spawns its own :server:installDist process on a side port
// with a private DATA_DIR, so the restart disturbs nothing else.
const PORT = 8791;
const SERVER_BIN = path.resolve(__dirname, '../../../server/build/install/server/bin/server');
const FIXTURE = `/euchre/?serverUrl=ws://localhost:${PORT}&playerName=Tester&animationSpeed=OFF&soundVolume=0`;

/** Card labels as cardkit's PlayingCard exposes them (contentDescription = card.label). */
const CARD_NAME = /^(?:9|10|J|Q|K|A)[♠♥♦♣]$|^Joker$/;

/**
 * The fewest cards on screen at a point where it is our turn: five in hand. The up-card is usually
 * there too, but it is gone once trump is settled, so the count is a floor rather than an equality.
 * What the test actually asserts is that the *same* cards come back after the restart.
 *
 * There is no way to scope a locator to the hand region — testTags are not surfaced on wasm — so this
 * counts every card on screen, which is if anything stronger: the up-card is part of the state the
 * snapshot has to restore too.
 */
const MIN_VISIBLE_CARDS = 5;

let dataDir: string;
let server: ChildProcess | undefined;

function startServer(): ChildProcess {
  const proc = spawn(SERVER_BIN, [], {
    env: {
      ...process.env,
      PORT: String(PORT),
      DEV_MODE: 'true',
      ALLOWED_ORIGINS: '*',
      MIN_APP_VERSION: '0.0.0',
      DATA_DIR: dataDir,
    },
    stdio: 'ignore',
  });
  proc.on('error', (err) => {
    throw new Error(`failed to spawn ${SERVER_BIN}: ${err}`);
  });
  return proc;
}

async function waitHealthy(timeoutMs = 60_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const res = await fetch(`http://localhost:${PORT}/health`);
      if (res.ok) return;
    } catch {
      // not up yet
    }
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error(`server on :${PORT} not healthy within ${timeoutMs}ms`);
}

/**
 * Wait until the room snapshot under DATA_DIR exists and has stopped changing. Snapshots are written
 * by a conflated async writer, so a SIGKILL straight after the last action could race the write; the
 * board is quiescent when this is called (it is the human's turn), so "file present and stable" means
 * the snapshot covers the state asserted on after the restart.
 */
async function waitForStableSnapshot(timeoutMs = 15_000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  let last = '';
  while (Date.now() < deadline) {
    const rooms = fs.readdirSync(dataDir).filter((f) => f.endsWith('.json'));
    if (rooms.length > 0) {
      const current = rooms
        .map((f) => {
          const s = fs.statSync(path.join(dataDir, f));
          return `${f}:${s.size}:${s.mtimeMs}`;
        })
        .join(',');
      if (current === last) return;
      last = current;
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`no stable room snapshot in ${dataDir} within ${timeoutMs}ms`);
}

/** Every card on screen, by label. Sorted, so the comparison does not depend on fan or draw order. */
async function visibleCards(page: Page): Promise<string[]> {
  const labels: string[] = [];
  for (const node of [...(await page.getByRole('img').all()), ...(await page.getByRole('button').all())]) {
    const name =
      (await node.getAttribute('aria-label')) ??
      (await node.getAttribute('alt')) ??
      (await node.textContent())?.trim() ??
      '';
    if (CARD_NAME.test(name)) labels.push(name);
  }
  return labels.sort();
}

/**
 * Wait until it is our turn, whatever phase the hand is in. "Waiting for <name>…" is the table's own
 * statement that someone else is to act, so its absence — once cards are on screen — is the
 * phase-agnostic signal that the game is parked on us.
 */
async function awaitOurTurn(page: Page, timeoutMs = 90_000): Promise<void> {
  await expect
    .poll(
      async () => {
        const cards = await visibleCards(page);
        if (cards.length < MIN_VISIBLE_CARDS) return false;
        if (await page.getByRole('button', { name: /^Pass$/ }).isVisible().catch(() => false)) return true;
        return !(await page.getByText(/^Waiting for .+…$/).first().isVisible().catch(() => false));
      },
      { timeout: timeoutMs },
    )
    .toBe(true);
}

test.beforeAll(async () => {
  dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'euchre-restart-e2e-'));
  server = startServer();
  await waitHealthy();
});

test.afterAll(() => {
  server?.kill('SIGKILL');
  fs.rmSync(dataDir, { recursive: true, force: true });
});

// The whole journey in one test: generous budget because it boots the wasm app, deals a hand, and
// rides a JVM restart plus the client's reconnect backoff (≤ 8 s).
// No console-error assertion, deliberately: the killed WebSocket and the reconnect attempts that fail
// while the server is down log errors that are exactly the point of the test.
test('an online game survives a server SIGKILL and restart under a live client', async ({ page }) => {
  test.setTimeout(240_000);

  await page.goto(FIXTURE);
  await awaitAppBoot(page);

  // Host a lobby (the same preamble as online.spec.ts, but against our private server).
  await clickByRole(page, 'button', 'Play with friends');
  await clickByRole(page, 'button', 'Create a game');
  await clickByRole(page, 'button', /^Create$/);
  await expect(page.getByText(/^[2-9A-HJ-NP-Z]{4}$/).first()).toBeVisible({ timeout: 15_000 });

  // The host needs no ready toggle — Start is their readiness — and the three empty seats become bots.
  await clickByRole(page, 'button', /^Start/);

  // Wait for any turn of ours — a stable state, because the game cannot advance without us.
  // Deliberately not "wait for a Pass button": the server deals from a random seed and euchre's
  // auction can END before it reaches us (a bot orders up), in which case we are first prompted to
  // play a card instead and a Pass prompt never appears. The phase-agnostic signal is that the table
  // has stopped telling us it is waiting for someone else.
  await awaitOurTurn(page);
  const cardsBefore = await visibleCards(page);
  expect(cardsBefore.length).toBeGreaterThanOrEqual(MIN_VISIBLE_CARDS);

  // Crash-restart the server: SIGKILL rather than a graceful stop, because the graceful path flushes
  // and the crash must be survivable from the already-written snapshot alone — exactly like an
  // OOM-kill or a hard deploy under a live table.
  await waitForStableSnapshot();
  server!.kill('SIGKILL');

  // The client notices the dead socket and shows the reconnect banner (after a 1.5 s grace, so a
  // blip never flashes it).
  await expect(page.getByText('Reconnecting…')).toBeVisible({ timeout: 15_000 });

  server = startServer();
  await waitHealthy();

  // Reconnect is silent and in place: the backoff loop's Hello presents the session token, the
  // restored server rebinds it to the seat, and the banner clears — with no rejoin prompt, because
  // the room is the one already on screen.
  await expect(page.getByText('Reconnecting…')).toBeHidden({ timeout: 30_000 });
  await expect(page.getByText('Rejoin your game?')).not.toBeVisible();
  await expect(page.getByText(/You're still in game/)).not.toBeVisible();

  // Same cards, same turn: the snapshot restored the exact state — this hand, this up-card, our move
  // to make — rather than dealing a new one.
  await awaitOurTurn(page);
  expect(await visibleCards(page)).toEqual(cardsBefore);

  // And it is playable onward: our move round-trips through the restarted server. Pass if the auction
  // is still open, otherwise play a card — whichever this hand has reached.
  // Through the helper, not locator.click(): the a11y mirror has real bounding boxes but the canvas
  // swallows pointer events, so a direct click never passes actionability.
  if (await page.getByRole('button', { name: /^Pass$/ }).isVisible().catch(() => false)) {
    await clickByRole(page, 'button', /^Pass$/);
  } else {
    await clickByRole(page, 'button', CARD_NAME);
  }
  // Progress means the table moved, in any of the ways it can: our pass is now on the record, or
  // someone else is to act, or the cards changed (our played card leaving the hand, or a redeal in
  // the all-pass corner). All three are needed — with animations off the bots answer instantly, so
  // the auction can come back around to us with the same hand before the wait ever observes another
  // seat acting.
  await expect
    .poll(
      async () => {
        if (await page.getByText(/passed/).first().isVisible().catch(() => false)) return true;
        if (await page.getByText(/^Waiting for .+…$/).first().isVisible().catch(() => false)) return true;
        const cards = await visibleCards(page);
        return cards.join() !== cardsBefore.join();
      },
      { timeout: 30_000 },
    )
    .toBe(true);
});
