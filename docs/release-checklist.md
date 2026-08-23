<!-- SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception -->

# v0.1.0 release checklist

One-time setup and the release gate, in order. Mirrors 500's release process.

## One-time setup (before the first tag)

- [x] **GitHub Pages** — enabled 2026-07-28, source "GitHub Actions", HTTPS enforced.
      Publishes to <https://rotundtapir.github.io/euchre/> from the `deploy-web` job on `v*` tags.
- [x] **Signing keystore** — created 2026-07-28 at `~/keystores/euchre-release.jks` (its own fresh
      key, deliberately NOT 500's), and the four repo secrets `KEYSTORE_BASE64`,
      `KEYSTORE_PASSWORD`, `KEY_ALIAS` (`euchre`), `KEY_PASSWORD` are set. Key parameters match
      500's cert so the two pipelines stay uniform: RSA 4096, SHA384withRSA, same DN
      (`C=GB, ST=Unknown, L=Unknown, O=Rotund Tapir, OU=Unknown, CN=Jack de Kleuver`); validity
      runs 30 years to 2056 (500's ends 2053 — longer is safer for an unrotatable key).

      Signing stays *optional* in `app/build.gradle.kts`: with `KEYSTORE_FILE` unset the release
      build is unsigned, which is correct and load-bearing for local builds and for F-Droid, whose
      reproducible flow signs and compares on its own.

      **Certificate SHA-256** (public — this becomes `AllowedAPKSigningKeys` in a future fdroiddata
      recipe):
      `668393be0bc620d27bb00557429f626935a7dab2d6aa23fa35c5f0bb4971155f`

      > **Jack:** `~/keystores/` needs an OFFLINE backup — the key is unrecoverable and
      > unrotatable once an APK ships. Move the two password files into your password manager,
      > after which the plaintext copies can be deleted (CI has its own copies in the secrets).
- [ ] **Deploy secrets** — `DEPLOY_SSH_KEY`, `DEPLOY_KNOWN_HOSTS`, `DEPLOY_HOST`, `DEPLOY_PORT`,
      for the `deploy-server` job. Three things learned from 500's setup rather than rediscovered:

      - **Its own key**, not 500's. Both authorise `root`, so this is not about privilege — it is
        that euchre's key can be revoked after a leak without breaking 500's deploys mid-incident,
        and GitHub secrets are per-repo regardless. One `ssh-keygen`, one `authorized_keys` line.
      - Authorise it with **`restrict`** (plus `pty` if the deploy needs a terminal — ours runs
        `docker compose` over ssh). `restrict` implies the `no-*` forwarding flags and any added
        later, so it ages better than listing them. Note what it does *not* buy: `from=` is not
        practical for GitHub-hosted runners, whose egress ranges are large and change, and a
        `command=` wrapper would have to permit the whole multi-step deploy anyway.
      - `DEPLOY_KNOWN_HOSTS` is plain `ssh-keyscan -p <port> <host>` output, pasted unedited. Scan
        **whatever `DEPLOY_HOST` actually contains** — scanning the hostname while the secret holds
        an IP (or vice versa) yields a host-key mismatch on the first deploy. A non-22 port makes
        keyscan emit `[host]:port` lines, which is what ssh then expects to match.

- [x] **Launcher icon** — a generated placeholder set is in place (adaptive `mipmap-anydpi-v26`
      plus every density). Deliberately provisional; final artwork lands with the store release.

### Deferred past v0.1.0 — dummy values are intentional

v0.1.0 ships the FOSS build (GitHub release) and the web build (Pages). The `play` flavor still
compiles and runs, but is **not** published, so its monetization ids stay placeholders:

- `app/src/play/AndroidManifest.xml` carries Google's **sample** AdMob APPLICATION_ID.
- The play `MonetizationProvider` uses Google's **test** ad units for every build type.
- `remove_ads` refers to a Play Console product that does not exist yet.
- **Store artwork**: the launcher icon is a generated placeholder, and there is no feature graphic.
  Phone screenshots now exist — five, captured from the real app by `scripts/screenshots.py`, two of
  them showing genuine cross-play — so re-shooting after a UI change is a scripted step rather than a
  manual one. The fastlane *text* metadata (title, descriptions, changelog) is written and current.

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
- [ ] Permission allowlist: `aapt dump permissions` on the FOSS APK shows `INTERNET` and **nothing
      else**. CI gates this; re-check by hand whenever the manifest changes, and update PRIVACY.md in
      the same change if the list grows.
- [ ] **First tag only:** make `ghcr.io/rotundtapir/euchre-server` public (package → Settings →
      Change visibility). The package does not exist until that first tag pushes it, and until it is
      public the VPS's `docker compose pull` fails with a 403/manifest-unknown that reads like a
      missing tag rather than a permissions problem. It persists across later pushes; this is a
      one-time step. (Same trap 500 hit — see its runbook.)
- [ ] Server: the `v*` tag published `ghcr.io/rotundtapir/euchre-server` and the deploy job reported
      `/health` ok on the euchre hostname. If this release changed `RoomSnapshot.CURRENT_VERSION`,
      drain the server **first** (`POST /admin/drain`, wait for `activeGames:0`) so in-flight games
      finish on the old code instead of being dropped at restore.
- [ ] `MIN_APP_VERSION` still admits the oldest build you intend to support online; raising it tells
      every older client to update, so raise it deliberately, not as housekeeping.
- [ ] Emulator suite green: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedFossDebugAndroidTest`.
- [ ] Web e2e green: `cd web/e2e && npx playwright test`.
- [ ] Tag `vX.Y.Z` → CI runs release → verify-reproducible → publish-release + deploy-web; confirm
      all four jobs green, the GitHub release has the FOSS APK, and the Pages site serves the new build.
- [ ] Manual smoke: one full game on device and one in the browser; run all four tutorial lessons.
      (No Play upload for v0.1.0 — see the deferred section above.)
