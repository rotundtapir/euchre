<!-- SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception -->

# v0.1.0 release checklist

One-time setup and the release gate, in order. Mirrors 500's release process.

## One-time setup (before the first tag)

- [ ] **Signing keystore** (new key, do NOT reuse 500's): generate, then add repo secrets
      `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- [ ] **AdMob**: create the Euchre app + banner and interstitial ad units; put the AdMob APPLICATION_ID
      in `app/src/play/AndroidManifest.xml` and the unit ids in the play `MonetizationProvider`
      (debug builds keep Google's test units — never point debug at real units).
- [ ] **Play Console**: create the app, the `remove_ads` in-app product, data-safety declarations
      (ads SDK only; the app itself collects nothing).
- [ ] **GitHub Pages**: repo Settings → Pages → source "GitHub Actions".
- [ ] **Launcher icon + fastlane images**: final icon, feature graphic, phone screenshots.

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
      all four jobs green and the GitHub release has the FOSS APK.
- [ ] Manual: upload the play AAB to the Play Console track; smoke-test one full game on device and
      one in the browser; run all four tutorial lessons.
