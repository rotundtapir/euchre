<!-- SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception -->

# Euchre

Android + web app for the classic trick-taking card game **Euchre** (4-player partnerships,
North American rules), built on the shared [cardkit](https://github.com/rotundtapir/cardkit)
library — a sibling of the [500](https://github.com/rotundtapir/500) app.

- **Play offline against bots** — a fast deterministic bot, plus an opt-in Monte-Carlo
  "Advanced AI" for stronger opponents.
- **Learn with the interactive tutorial** — four scripted lessons: the basics (trump, bowers,
  following suit), bidding, going alone, and defense.
- **House rules** — stick the dealer, defend alone, Benny (joker as best bower), and farmer's
  hand, each toggleable in settings.
- **Online multiplayer** — invite-code lobbies with cross-play between Android and the browser,
  bots filling any empty seat, and a bot covering for anyone who drops until they reconnect. Games
  survive a server restart. Play on the project's server or
  [run your own](docs/self-hosting.md) — the server is in this repository and is free software too.
- **Web build** — the same game in any modern browser (Kotlin/Wasm) at
  <https://rotundtapir.github.io/euchre/>.

## Flavors

| Flavor | Distribution | Monetization |
| --- | --- | --- |
| `foss` | GitHub releases, F-Droid (planned) | None. No ads, no trackers, no analytics; online play is opt-in and self-hostable; a donation link. |
| `play` | Google Play (planned) | Banner + interstitial ads, remove-ads purchase, UMP consent. |

The `foss` release build is reproducible: rebuild it from a clean checkout and only the signing
block differs.

## Building

```bash
git clone --recurse-submodules https://github.com/rotundtapir/euchre.git
cd euchre
./gradlew :engine:jvmTest        # pure-Kotlin rules tests (JDK 21 only)
./gradlew assembleFossDebug      # Android app (needs the Android SDK, compileSdk 36)
./gradlew :web:wasmJsBrowserRun  # web build at http://localhost:8080
```

Requires JDK 21 (the Android Gradle Plugin does not run on newer JDKs). See
[CONTRIBUTING.md](CONTRIBUTING.md) for the pre-commit hook and contribution terms.

## License

[GPL-3.0-or-later](LICENSE) WITH a [Google Mobile Ads / Play Billing linking
exception](LICENSE-EXCEPTION.md) (GPLv3 §7 additional permission). The `foss` and web builds link
no proprietary code and are plain GPLv3. Card artwork and audio are public domain / CC0 — see the
in-app acknowledgments.
