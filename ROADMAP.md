<!-- SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception -->

# Roadmap

Direction, not commitment — items land when they're ready. Feedback and votes:
[issues](https://github.com/rotundtapir/euchre/issues).

## Shipped

- **v0.1.0 — offline Euchre.** 4-player partnerships, play to 10, standard bidding
  (order-up / name-a-suit, going alone). House-rule toggles: stick the dealer, defend alone,
  Benny (joker as best bower), farmer's hand. A deterministic heuristic bot plus an opt-in
  Monte-Carlo Advanced AI (the search scaffolding is shared with 500 via `cardkit-ai`). A
  four-lesson interactive tutorial: basics, bidding, going alone, defense. Android (foss + play
  flavors) and web (Kotlin/Wasm on GitHub Pages).
- **v0.2.0 — online multiplayer.** Invite-code lobbies, cross-play Android ↔ web, bots filling
  empty seats and covering for anyone who drops, seat reclaim by session token, and games that
  survive a server restart. The game-agnostic half lives in cardkit (`cardkit-net`,
  `cardkit-server`); the server is free software and [self-hostable](docs/self-hosting.md).
  Also: tutorial narration audio, and a table that fits short and landscape screens.
- **v0.2.1 — polish.** The final launcher icon, a real dealer button, and a tidier landscape
  table.

## Next

- **F-Droid submission.** The FOSS release APK is already verified reproducible on every tag.
- **Google Play production release** — needs targetSdk 36, Play Billing 8, live AdMob ids and the
  `remove_ads` product, the data-safety form, and a feature graphic.

## Later

- 3-handed (cutthroat) and 2-handed variants.
- Statistics / match history.
- Traditional score-card (5s) visual for the score display.
