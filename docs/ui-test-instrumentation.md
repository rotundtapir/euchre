# Instrumenting the web and Android UIs for tests

Handoff from the 500 project (written 2026-07-28 by the Claude session stewarding
`../500`). Everything below is hard-won from 500's test suites — the reference
implementations live in that repo and in cardkit, and euchre consumes the same
cardkit-ui machinery, so almost all of it transfers directly.

## The core contract: seed + OFF = deterministic and instant

1. **Everything derives from the seed.** Same `rngSeed` ⇒ identical match. Never
   introduce wall-clock nondeterminism into anything a test replays (500 pins its
   Monte-Carlo bot with `SearchConfig(timeBudgetEnabled = false)` in tests).
2. **Every pacing mechanism must be inert at `AnimationSpeed.OFF`.** cardkit-ui's
   `PacingGates` already honors this; anything you add (delays, animations,
   holds) must check OFF too. 500's 22-test connected suite (`GameFlowTest`) and
   the whole web e2e depend on it.
3. **Plumb test overrides through the shells:**
   - Android: intent extras in MainActivity — 500 uses `EXTRA_SEED=42`,
     `EXTRA_ANIMATION_SPEED="OFF"`, `EXTRA_SOUND_VOLUME=0f`, plus
     `EXTRA_SERVER_URL` / `EXTRA_PLAYER_NAME` for online flows.
   - Web: URL params — `?seed=42&animationSpeed=OFF&soundVolume=0` (500's
     `web/.../Main.kt`; `?serverUrl=` is honored session-only, never persisted).
4. **Volume 0 must mean "no audio object is ever created"** — not "muted".
   Native audio playback crashes the instrumented process on a `-no-audio`
   emulator; cardkit's SoundManager is lazy, keep it that way.

## Android connected tests

- Drive via Compose `testTag`s + semantics. Copy 500's `GameFlowTest` /
  `OnlineFlowTest` (`app/src/androidTest`) patterns.
- **Always pin the device:** `ANDROID_SERIAL=emulator-5554 ./gradlew
  :app:connectedDebugAndroidTest` — otherwise Gradle also grabs (and afterwards
  uninstalls from) any attached phone. Jack's phones: skip test runs on them
  (often locked ⇒ "No compose hierarchies found"), and leave the app installed
  after any work.
- Emulator: `fivehundred_api35`-style AVD, boot flags
  `-no-snapshot-save -gpu swiftshader_indirect -no-boot-anim -no-audio`.
  Parallel agents must use distinct ports (5554/5556/5558…) AND pin the serial —
  a lock file alone is not enough, adb attaches to whoever owns the device.
- CI emulator job: use a `pixel_5` (or similar) profile explicitly — the
  runner's default AVD screen is too small and below-the-fold clicks miss.
- The connected task **uninstalls the app** from every device it ran against.
- Online tests against a local server: host-run `DEV_MODE=true ./gradlew
  :server:run`, app pointed at `ws://10.0.2.2:8080`; make the test self-skip
  when the server isn't up (500's `OnlineFlowTest` does).

## Web (Kotlin/Wasm) e2e — Playwright over the production dist

Model on `../500/web/e2e` (Playwright config, `serve.mjs`, specs). Key facts:

- Test the **production distribution served under the real Pages subpath
  prefix** (500 uses `/500/`) — that's where resource-path bugs live. Build the
  dist first; if online specs boot a server, build its `installDist` too
  (playwright.config `webServer`).
- Use system Chrome (`channel: 'chrome'`), no browser download. Ubuntu 26.04
  needs Playwright ≥1.61.
- **CMP ≥1.9 exposes a semantics/a11y mirror to the browser**, so role/text
  locators work — but the canvas intercepts pointer events, so plain `.click()`
  fails actionability. Pattern: *locate semantically, click with
  `page.mouse.click()` at the locator's boundingBox centre* (or `force: true`).
- Cards: playable cards surface as a11y **buttons named like `Q♣`**; idle/
  unplayable cards are **`img`s named by the card label** — read hands via
  aria-label.
- **Compose `Switch` is a nameless button on wasm and `testTag` is NOT
  queryable from Playwright.** If e2e must toggle something, give it accessible
  text — prefer a labeled `Button` over a `Switch` (500 PR #26 converted its
  lobby ready Switch to a button for exactly this reason).
- **Dialog dismissal freezes the a11y mirror** (upstream CMP bug): after an
  AlertDialog closes, the aria snapshot stays stuck on the dialog's nodes while
  the canvas paints on. Clicks from the stale mirror still land, but assertions
  after a dismissal are impossible — end specs at the dialog, or route around it
  (promptless auto-flows).
  *Confirmed in euchre (2026-07-28), and it is not AlertDialog-specific*: the
  mirror freezes on the content of the last dialog of ANY kind — the plain
  Settings dialog does it too — and never recovers, not after seconds of idle,
  a mouse move, or a viewport resize. euchre's tutorial spec ends its
  role/text assertions at the primer and proves the hand was dealt by
  screenshotting before and after the final tap and asserting the PNGs differ;
  the played-out hand is covered on device instead.
- **Coordinate-clicking paged dialogs needs fixed geometry**: the mirror
  refreshes async, so a Next button that moves between pages gets a stale-box
  click on the scrim (dismisses the dialog). Pin the pager body height
  (cardkit's `TutorialPagesDialog` does), re-query the locator every iteration,
  tolerate a null boundingBox, settle ~350ms.
- `document.querySelectorAll` sees **nothing** (canvas + shadow DOM) — debug
  with Playwright locators / `ariaSnapshot`, never the light DOM.
- Screenshots of the canvas can *render* dark-tinted when viewed even though
  the pixels are fine — pixel-sample the PNG (PIL `getpixel`) before concluding
  a color bug.
- First sound needs a prior user gesture (autoplay policy) — make the flow's
  first tap count.

## Wasm toolchain traps that masquerade as test failures

- **Incremental wasm compilation can silently ship a stale binary** after many
  edit-compile cycles in a long-lived daemon (Kotlin JS-IC bug; no
  gradle.properties lever short of `kotlin.incremental=false`). Before trusting
  a wasm build in a verification run: `./gradlew clean` (or delete
  `*/build/kotlin/compileKotlinWasmJs/{cacheable,local-state}`), and always
  verify by driving the app, not by a green build.
- A green Android/JVM compile does **not** prove commonMain is wasm-clean —
  JVM-only APIs (`toSortedMap`, `String.format`, …) only fail on
  `compileKotlinWasmJs`. Run it.
- Adding a wasmJs dependency changes the committed yarn lock:
  `./gradlew kotlinWasmUpgradeYarnLock`, commit `kotlin-js-store/wasm/yarn.lock`.
- The wasm canvas has **no system fonts** — bundle a symbol subset (500 ships a
  DejaVu Sans subset; euchre's web/ already carries the license file, so this is
  probably done) or ♠♥♦♣ render as tofu.
- `viewModel { … }` needs the explicit initializer on wasm — the reflection
  factory is JVM-only and throws.

## Where the shared machinery lives

cardkit main (`ad37ad57` onward) has the extracted UI scaffolding: `PacingGates`
over the `TableTransitions` adapter, the schedule-driven deal animation,
tutorial anchors/bubble/pager, `KeyValueStore` settings seam, felt color
helpers. 500's adapters are the worked example of wiring a game onto it —
see `shared/src/commonMain/.../Pacing.kt` and the `GameScreen.kt` deal wiring on
500 PR #29 (`feat/cardkit-ui-scaffold` branch), and cardkit's
`templates/app-ci.yml` for the CI job shapes (unit + lint gate, web e2e,
emulator connected job).

Questions (on this or anything 500/cardkit): append them to
`agent-mail/questions-for-500.md` (gitignored) — the 500 session monitors that file and answers
in place, usually within a few minutes. Protocol at the top of that file.
Corrections/confirmations from the euchre side: edit this file directly (the
dialog-freeze note above is one such — thanks, folded into the lore).
