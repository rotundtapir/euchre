<!-- SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception -->

# v0.1.0 release checklist

One-time setup and the release gate, in order. Mirrors 500's release process.

## One-time setup (before the first tag)

- [x] **GitHub Pages** — enabled 2026-07-28, source "GitHub Actions", HTTPS enforced.
      Publishes to <https://rotundtapir.github.io/euchre/> from the `deploy-web` job on `v*` tags.
- [ ] **Signing keystore** (new key, do NOT reuse 500's): generate, then add repo secrets
      `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`. Absent, release builds
      are unsigned — correct for F-Droid, but the APK attached to the GitHub release is unsigned
      too, and `verify-reproducible` has no signed artifact to compare against.
- [ ] **Launcher icon + fastlane images**: replace the generated placeholder icon in
      `app/src/main/res/mipmap-*` with the final artwork; add the feature graphic and phone screenshots.

### Deferred past v0.1.0 — dummy values are intentional

v0.1.0 ships the FOSS build (GitHub release) and the web build (Pages). The `play` flavor still
compiles and runs, but is **not** published, so its monetization ids stay placeholders:

- `app/src/play/AndroidManifest.xml` carries Google's **sample** AdMob APPLICATION_ID.
- The play `MonetizationProvider` uses Google's **test** ad units for every build type.
- `remove_ads` refers to a Play Console product that does not exist yet.

Nothing here blocks a v0.1.0 tag. When a Play release is scheduled: create the AdMob app +
banner/interstitial units and the Play Console app + `remove_ads` product, complete the
data-safety declarations (ads SDK only; the app itself collects nothing), then swap the real ids
in for **release builds only** — never point debug builds at live units (invalid-traffic risk).

## Release gate (every tag)

- [ ] `gradle.properties`: bump `appVersionName` / `appVersionCode`; add
      `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`.
- [ ] Full local gate: `./gradlew qualityCheck lint :engine:jvmTest :ai:jvmTest
      :shared:testDebugUnitTest :engine:koverVerify :ai:koverVerify assembleFossRelease`.
- [ ] FOSS purity: `./gradlew :app:dependencies --configuration fossReleaseRuntimeClasspath | grep -Ei
      'gms|billing|firebase|monetization-play'` prints nothing.
- [ ] No-network invariant: `aapt dump permissions` on the FOSS APK shows **no** INTERNET permission.
- [ ] Emulator suite green: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest`.
- [ ] Web e2e green: `cd web/e2e && npx playwright test`.
- [ ] Tag `vX.Y.Z` → CI runs release → verify-reproducible → publish-release + deploy-web; confirm
      all four jobs green, the GitHub release has the FOSS APK, and the Pages site serves the new build.
- [ ] Manual smoke: one full game on device and one in the browser; run all four tutorial lessons.
      (No Play upload for v0.1.0 — see the deferred section above.)
