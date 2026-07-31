<!-- SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception -->

# Roadmap

Direction, not commitment — items land when they're ready. Feedback and votes:
[issues](https://github.com/rotundtapir/euchre/issues).

## v0.1.0 (in progress)

- **Offline Euchre against bots.** 4-player partnerships, play to 10, standard bidding
  (order-up / name-a-suit, going alone). House-rule toggles: stick the dealer, defend alone,
  Benny (joker as best bower), farmer's hand.
- **Bots:** deterministic heuristic + opt-in Monte-Carlo Advanced AI (both from day one — the
  search scaffolding is shared with 500 via `cardkit-ai`).
- **Four-lesson interactive tutorial:** basics (trump, bowers, following suit), bidding,
  going alone, defense. Narration-ready text keys; no audio yet.
- **Android (foss + play flavors) and web (Kotlin/Wasm on GitHub Pages) from day one.**

## v0.2.0 (in progress)

- **Online multiplayer.** Invite-code lobbies, cross-play Android ↔ web, bots filling empty seats
  and covering for anyone who drops, seat reclaim by session token, and games that survive a server
  restart. Runs alongside 500 on one small VPS, routed by hostname.
- **Shared online stack.** The game-agnostic half lives in cardkit (`cardkit-net`, `cardkit-server`);
  this repo holds only euchre's payload types, its `GameDescriptor`, and a thin server binary. The
  server is free software and [self-hostable](docs/self-hosting.md).

## Later

- **Tutorial narration audio** — prerecorded clips over the existing text keys (the pipeline and
  player plumbing exist in cardkit-ui; 500 already ships narration).
- **F-Droid submission** and **Google Play production release**.
- 3-handed (cutthroat) and 2-handed variants.
- Statistics / match history.
- Traditional score-card (5s) visual for the score display.
