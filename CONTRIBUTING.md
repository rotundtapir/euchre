<!-- SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception -->

# Contributing to Euchre

Thanks for your interest! This is the Android + web app for the card game
**Euchre**, built on the shared [`cardkit`](cardkit) library (included as a git
submodule).

## Getting the source

```bash
git clone --recurse-submodules <repo-url>
# or, if you already cloned without submodules:
git submodule update --init --recursive
```

The `cardkit` submodule is wired in as a Gradle composite build, so a normal
`./gradlew` build compiles the library from source alongside the app.

## License of contributions

This project is licensed under the **GNU General Public License v3.0 or later,
WITH** the Google Mobile Ads / Play Billing linking exception described in
[`LICENSE-EXCEPTION.md`](LICENSE-EXCEPTION.md).

**Inbound = outbound:** by submitting a contribution you agree it is licensed
under exactly those terms. This lets contributed code ship in both the
free/libre (F-Droid) build *and* the ad-supported (Google Play) build without a
separate CLA.

### Developer Certificate of Origin (DCO)

We use the [DCO](https://developercertificate.org/) instead of a CLA. Sign off
every commit:

```bash
git commit -s -m "your message"
```

Commits without a `Signed-off-by` trailer will not be merged.

## Where code goes

- **`engine/`** — pure Kotlin Euchre rules (deck, bidding, trick play,
  scoring), KMP jvm+wasmJs. No Android APIs; keep it deterministic and
  unit-tested.
- **`ai/`** — bot strategy, pure Kotlin (KMP jvm+wasmJs).
- **`shared/`** — the Compose Multiplatform game UI, android+wasmJs.
- **`app/`** — the Android shell. Ads and billing live only in the `play`
  source set / the `cardkit-monetization-play` module, never in shared code.
- **`web/`** — the browser (Kotlin/Wasm) shell.
- Reusable, game-agnostic infrastructure belongs in `cardkit`, not here.

Add a SPDX header to new source files:
`// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception`

## Building

```bash
./gradlew :engine:jvmTest           # fast, pure-Kotlin unit tests
./gradlew assembleFossDebug         # ad-free build
./gradlew assemblePlayDebug         # ad-supported build
```

Requires JDK 21 and, for the app modules, the Android SDK (`compileSdk 36`).

## Git hooks

A pre-commit hook runs `./gradlew qualityCheck lint jvmTest` so CI lint/test failures are caught
locally. Enable it once per clone:

```bash
git config core.hooksPath scripts/hooks
```

It skips doc-only commits and selects a JDK 21 automatically. Bypass in a pinch
with `git commit --no-verify`.
