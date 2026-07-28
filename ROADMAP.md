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

## Later

- **Tutorial narration audio** — prerecorded clips over the existing text keys (the pipeline and
  player plumbing exist in cardkit-ui; 500 already ships narration).
- **Online multiplayer** — on the **same server as 500**, with game engines as hot-pluggable
  modules (the deferred `cardkit-server`/`cardkit-net-client` extraction). Invite-code lobbies,
  bot fill-in, cross-play Android ↔ web, self-hostable. The engine is already a pure serializable
  reducer with stable wire names, so this is a transport project, not an engine rewrite.
- **Tutorial: "Back" from a lesson primer to the lesson picker.** Picking a lesson opens its
  primer, whose only escape is "Cancel", which drops you out of the tutorial entirely — so
  changing your mind about *which* lesson costs a full round trip through Home → How to play.
  The primer should offer a way back to the picker.

  The wiring is in `App.kt`: the primer's `onDismiss` currently clears `primerLessonId`; pointing
  it back at `showLessonPicker = true` restores the picker in one line. The catch is the label —
  cardkit's `TutorialPagesDialog` hardcodes "Cancel" for the first page's dismiss action (later
  pages get "Back", which walks the pager). Doing this properly wants a `dismissLabel` parameter
  on that composable, defaulting to "Cancel" so 500 is unaffected — i.e. a small cardkit PR plus
  a one-line change here.
- **F-Droid submission** and **Google Play production release**.
- 3-handed (cutthroat) and 2-handed variants.
- Statistics / match history.
- Traditional score-card (5s) visual for the score display.
