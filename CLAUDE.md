# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

The Android **and web (Kotlin/Wasm)** app for the card game **Euchre** (4-player North American
rules), and the second consumer of the shared **`cardkit`** library (the first is the sibling
[500](https://github.com/rotundtapir/500) app — when in doubt, mirror its patterns). `cardkit`
lives in its own repo and is included here as a git submodule at `./cardkit`, wired into Gradle as
a composite build. Only Euchre-specific code lives in this repo; game-agnostic infrastructure lives
in `cardkit`. The web build deploys to GitHub Pages (https://rotundtapir.github.io/euchre/) on `v*`
release tags.

Play against bots (heuristic + opt-in Monte-Carlo "Advanced AI"), a four-lesson interactive
tutorial, and — since 0.2.0 — **online multiplayer**: invite-code lobbies, cross-play between
Android and the browser, bot fill and substitution, and games that survive a server restart. The
online stack is shared with 500 through `cardkit-net` (wire protocol + client) and `cardkit-server`
(rooms, seats, reconnect, snapshots); only euchre's own payloads and a `GameDescriptor` live here,
in `:net` and `:server`. The engine must stay a pure, seed-deterministic, fully `@Serializable`
reducer with stable `@SerialName`s and a redacting `view()` — that is what the server runs on.
Tutorial narration audio is still planned; tutorial text keys are narration-ready (stable
per-lesson ids) so clips can drop in without rework.

## Toolchain (read first — non-obvious and will waste time otherwise)

- **Gradle must run on JDK 21** (the machine default `java` is JDK 25, and the Android Gradle Plugin
  fails on JDK 25+). `gradle/gradle-daemon-jvm.properties` pins the daemon to a version-21 toolchain
  (vendor-agnostic — do NOT let Android Studio regenerate it with `toolchainVendor=JETBRAINS`; that
  breaks CI, which has Temurin). So `JAVA_HOME` is not required for `./gradlew`, but every
  invocation still needs:
  ```bash
  export ANDROID_HOME="$HOME/Android/Sdk"
  ```
  Kotlin/JVM modules pin `jvmToolchain(21)`; Android modules pin `jvmTarget = 17`.
- `gradle` is not on PATH; use the committed wrapper `./gradlew`.
- The Android SDK here has `platforms;android-36` + `build-tools;36.0.0` (compileSdk 36).

## Common commands

```bash
# Pure-Kotlin logic (fast, no Android SDK needed) — the engine is the important part.
# engine/ai/cardkit-core/cardkit-ai are Kotlin Multiplatform (jvm + wasmJs); unit tests live in jvmTest.
./gradlew :engine:jvmTest
./gradlew :ai:jvmTest

# A single test class (JUnit 5 platform)
./gradlew :engine:jvmTest --tests "io.github.rotundtapir.euchre.engine.EuchreRulesTest"

# Build both distribution flavors
./gradlew assembleFossDebug assemblePlayDebug

# Lint + static analysis (CI's `build` runs `qualityCheck lint`; the pre-commit hook runs
# `qualityCheck lint jvmTest`). qualityCheck = detekt + CPD — the local `lint` task is Android-only,
# so run qualityCheck too or detekt-only findings slip through to CI.
./gradlew qualityCheck lint

# On-device integration tests (Compose UI driving real games). Pin the serial or the task also
# grabs (and later uninstalls from) any attached phone.
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest

# CRITICAL F-Droid gate: the FOSS build must contain NO proprietary dependency.
./gradlew :app:dependencies --configuration fossDebugRuntimeClasspath \
  | grep -Ei 'gms|billing|firebase|monetization-play'   # must print nothing

# Web (Kotlin/Wasm): dev server with live reload, and the static production site
# (build/dist/wasmJs/productionExecutable — what the Pages deploy publishes).
./gradlew :web:wasmJsBrowserRun          # http://localhost:8080
./gradlew :web:wasmJsBrowserDistribution
# Web analogue of the test intent extras: ?seed=42&animationSpeed=OFF&soundVolume=0

# Web E2E (Playwright over the production dist, served under the Pages /euchre/ prefix).
cd web/e2e && npm ci && npx playwright test
# Locate semantically but click via page.mouse at the locator's box centre — the canvas
# intercepts pointer events, so plain .click() fails actionability.
```

Enable the pre-commit hook once per clone: `git config core.hooksPath scripts/hooks` (runs
`./gradlew qualityCheck lint jvmTest`; skips doc-only commits; auto-selects JDK 21; bypass with
`--no-verify`).

## Architecture

### Module layout (this repo + the submodule)
Most modules are Kotlin Multiplatform; `wasmJs` is the browser target throughout.
- `cardkit/` (submodule) — reusable infra: `cardkit-core` (pure Kotlin: cards, `GameRules`/
  `GameDriver`/`Player` seams, `TrickEvaluator` with bowers/`JokerRole`, seat helpers),
  `cardkit-ai` (Monte-Carlo search scaffolding), `cardkit-ui` (Compose Multiplatform),
  `cardkit-monetization` (interface + FOSS/browser no-ops), `cardkit-monetization-play`
  (Google Ads + Billing, Android-only).
- `engine/` — pure-Kotlin Euchre rules, KMP jvm+wasmJs. **No Android or JVM-only imports** (the wasm
  target won't compile a leak). This keeps the authoritative engine runnable server-side for future
  online multiplayer. Unit tests live in `src/jvmTest`.
- `ai/` — bots, pure Kotlin (KMP jvm+wasmJs): `EuchreBot` (deterministic heuristic) and the
  Monte-Carlo `EuchreAdvancedBot` on cardkit-ai. Depends on `engine`. Engine must NEVER depend on
  `ai` or cardkit-ai.
- `shared/` — the whole game UI (screens, `EuchreViewModel`, tutorial) as Compose Multiplatform
  common code, android+wasmJs.
- `app/` — the Android shell: `MainActivity` (intent-extra test overrides), flavors, monetization
  providers. Depends on `shared`.
- `web/` — the browser shell: `ComposeViewport` entry, URL-param test overrides.

### The engine is a pure state machine (the core idea)
`EuchreRules : GameRules<EuchreState, EuchreAction, EuchrePlayerView>` (cardkit-core interface) —
`apply(state, seat, action)` is a pure reducer; `GameDriver` loops it, asking each seat's `Player`
to decide.
- **Determinism:** the whole match derives from `rngSeed` in `EuchreState`, which evolves per deal
  (`nextSeed(seed) = Random(seed).nextLong()`). Same seed ⇒ identical match. Tests, the tutorial
  traces, and reproducibility depend on this — don't introduce nondeterminism. One deliberate
  exception: the opt-in Advanced AI is wall-clock-budgeted in production; tests pin it with
  `timeBudgetEnabled = false`.
- **`EuchrePlayerView` is redacted per-seat** (own hand + public info only; the up-card is public,
  the kitty and other hands are not). This is the future multiplayer seam. Never widen it to expose
  hidden information.
- **House rules are `EuchreRules` constructor toggles** (stick the dealer, defend alone,
  Benny/joker, farmer's hand), settings-backed at the app layer. When a toggle is off its actions
  must be absent from `legalActions` — tests assert this.
- **Trick logic lives in cardkit-core's `TrickEvaluator`**: Euchre standard =
  `TrickEvaluator(trump, JokerRole.ABSENT)`, Benny = `JokerRole.HIGHEST_TRUMP`. Cards are
  deliberately not `Comparable`; strength is always relative to (trump, ledSuit).
- **Actions carry stable `@SerialName`s** and the whole state is `@Serializable` — the wire format
  is decided now, before networking exists. Don't rename serial names.

### UI pacing is signal-driven, and tests turn it off (don't break either)
`EuchreViewModel` paces bots with **signals, not timers** (cardkit-ui `PacingGates`): bot turns
await deal-animation/trick-ack/hand-result-ack signals the UI raises. **Every pacing mechanism must
be inert at `AnimationSpeed.OFF`** — the connected test suite depends on it, pinning seed,
`AnimationSpeed.OFF` and `soundVolume=0` via intent extras / URL params (volume 0 also means the
SoundPool is never created — native audio playback crashes the instrumented process on the
`-no-audio` emulator).

The interactive tutorial is FOUR scripted lessons (basics, bidding, going alone, defense), each a
fixed-seed hand replayed through the normal ViewModel wiring. Each lesson's trace is generated by a
`@Disabled` test in `ai/` and drift-gated by `TutorialScriptTest` — if an engine/bot change alters
a trace, regenerate the lesson script and its texts together. Tutorial text ids
(`{lessonId}-step-N` etc.) are the future narration clip names — keep them stable and unique.

### Distribution flavors & monetization (the reason for the module split)
Two flavors on dimension `distribution`: **`foss`** (no ads, donation link; what F-Droid builds) and
**`play`** (Google Ads + a remove-ads IAP). Shared code only references the `Monetization` interface;
the concrete impl is chosen by a **flavor-specific `MonetizationProvider`** in `app/src/foss` vs
`app/src/play`. All proprietary code is quarantined in the `cardkit-monetization-play` module, which
**only the `play` flavor depends on** (`"playImplementation"(...)`), so the FOSS build graph is
provably free of non-free code. Do not add GMS/Billing/Firebase anywhere the `foss` build can reach.

**v0.1.0 privacy invariant:** the foss/main manifest declares NO `INTERNET` permission — the FOSS
and web builds make no network connections at all (PRIVACY.md promises this). The play flavor
inherits `INTERNET` via manifest merge from the ads module only.

## Working across the submodule

Editing shared/infra behaviour means changing files under `cardkit/`, which is a **separate repo**
(never edit it via this checkout — use the standalone clone at `../cardkit`, branch, and PR):
1. Land the cardkit change (PR on rotundtapir/cardkit).
2. Advance the submodule pointer here: `git -C cardkit fetch origin && git -C cardkit checkout <sha>`,
   then `git add cardkit` and commit. The referenced commit must exist on the cardkit remote before
   pushing this repo.

## Conventions

- New source files get the SPDX header:
  `// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception`
- Commits require a DCO sign-off (`git commit -s`); no CLA.
- Merge PRs by fast-forward (or GitHub's rebase merge when the branch is behind `main`) — keep
  `main`'s history linear; no merge commits.
- Namespace is `io.github.rotundtapir.*`; `applicationId` is `io.github.rotundtapir.euchre`.
  Store listing title is fastlane's `title.txt` ("Euchre - Card game"); the launcher label stays
  "Euchre".
- Game-agnostic code goes to `cardkit`, not here. If a thing would be copy-pasted into a third
  game, extract it.
- Version catalog (`gradle/libs.versions.toml`) is kept in lockstep with 500's and cardkit's — bump
  shared versions together across the three repos.
- **Lint gotcha (inherited from 500):** conditional `Modifier.then(if (…) Modifier.x() else
  Modifier)` chains crash `SuspiciousModifierThenDetector`. Use factory-style modifier extensions
  that take the condition instead.

### Web target notes
- The Kotlin plugin's Node.js/Binaryen/Yarn download repositories are declared in
  `settings.gradle.kts` (both builds) because `PREFER_SETTINGS` ignores project-level repositories.
- Compose resource URLs are remapped relative (`configureWebResources` in `web/.../Main.kt`) so the
  app works from the GitHub Pages `/euchre/` subpath.
- `viewModel { EuchreViewModel() }` (explicit initializer) is required: the reflection-based default
  ViewModel factory is JVM-only and throws on wasm.
- Page refresh loses an in-progress game (`rememberSaveable` is memory-only on web); the first
  sound needs a prior user gesture (browser autoplay policy).

## Releasing

- Release artifacts are signed when `KEYSTORE_FILE`/`KEYSTORE_PASSWORD`/`KEY_ALIAS`/`KEY_PASSWORD`
  are set (env vars or gradle properties); absent ⇒ unsigned, which is correct for local builds and
  F-Droid. CI's tag-triggered release job (`v*` tags) exports them from repo secrets and builds
  `bundlePlayRelease` + `assembleFossRelease`; the FOSS APK is only published to the GitHub release
  after `verify-reproducible` passes (fresh-runner rebuild, byte-identical via `apksigcopier
  compare`). The same `v*` tags trigger `deploy-web` (GitHub Pages). `dependenciesInfo` is disabled
  — F-Droid rejects the Google-encrypted blob. Release stays un-minified until a release-QA pass
  justifies R8.
- fastlane metadata (`fastlane/metadata/android/en-US/`) is the store listing: `title.txt`,
  descriptions, `changelogs/<versionCode>.txt`, and `images/phoneScreenshots/`. Keep the changelog
  file in sync with `versionCode` bumps.
- `docs/release-checklist.md` tracks the one-time v0.1.0 setup (keystore, AdMob ids, Play Console
  product, Pages enablement).
