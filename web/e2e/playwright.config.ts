// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
import { defineConfig } from '@playwright/test';

// A smoke net over the production wasm distribution, served under a /euchre/ path prefix so every
// run rehearses the GitHub Pages subpath. Build the app first:
//   ./gradlew :web:wasmJsBrowserDistribution
export default defineConfig({
  testDir: './tests',
  timeout: 90_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['list'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: 'http://localhost:9600',
    // System Chrome instead of a downloaded browser: WasmGC needs a current engine, GitHub
    // runners ship Chrome stable, and it mirrors what real users run.
    channel: 'chrome',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    viewport: { width: 500, height: 950 },
  },
  webServer: [
    {
      command: 'node serve.mjs',
      url: 'http://localhost:9600/euchre/',
      reuseExistingServer: !process.env.CI,
    },
    {
      // The online game server, for online.spec.ts. Built by `./gradlew :server:installDist`.
      // Port 8081, not 8080: 8080 is 500's dev-server convention, and a euchre server sitting on
      // it answers 500's connected-test probe — which then fails at the handshake instead of
      // self-skipping, reading as "500 is broken". One port per game keeps the two suites apart.
      // DEV_MODE relaxes the rate/connection caps and honours a client-supplied seed;
      // ALLOWED_ORIGINS=* lets the localhost page connect; MIN_APP_VERSION=0.0.0 accepts whatever
      // version the built web client reports. DATA_DIR gives it somewhere to snapshot rooms, which
      // restart.spec.ts needs to survive a kill.
      command:
        'PORT=8081 DEV_MODE=true ALLOWED_ORIGINS=* MIN_APP_VERSION=0.0.0 DATA_DIR=.server-data ' +
        '../../server/build/install/server/bin/server',
      url: 'http://localhost:8081/health',
      reuseExistingServer: !process.env.CI,
      timeout: 60_000,
    },
  ],
});
