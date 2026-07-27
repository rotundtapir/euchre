<!-- SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception -->

# Privacy Policy — Euchre

**Effective: 2026-07-27**

Euchre is a free, open-source card game. This policy covers every distribution of the app.

## The short version

The game is fully offline. The FOSS build and the web version collect nothing and make **no
network connections at all**. The Google Play build shows ads, and only its advertising SDKs
communicate with the network.

## FOSS build (GitHub releases / F-Droid)

- No ads, no analytics, no trackers, no accounts.
- The app does not request the `INTERNET` permission — it cannot make network connections.
- Settings (animation speed, house rules, sound volume, tutorial progress) are stored only on
  your device.

## Web version (rotundtapir.github.io/euchre)

- The game runs entirely in your browser; after the page loads it makes no further network
  requests. Settings are stored in your browser's local storage.
- The site is hosted by GitHub Pages; GitHub may log requests (IP address, user agent) when the
  page and its assets load, per [GitHub's privacy
  statement](https://docs.github.com/en/site-policy/privacy-policies/github-privacy-statement).

## Google Play build

Identical gameplay, plus advertising and an optional remove-ads purchase:

- **Google AdMob** shows a banner and an occasional interstitial. AdMob may collect device
  identifiers and ad-interaction data as described in
  [Google's privacy policy](https://policies.google.com/privacy) and
  [how Google uses data from partner apps](https://policies.google.com/technologies/partner-sites).
- In regions requiring consent, Google's **User Messaging Platform** consent form is shown before
  any ad; you can revisit it via Settings → Privacy options.
- **Google Play Billing** processes the remove-ads purchase; we never see your payment details.
  After the purchase, ads stop being requested.
- The app itself still has no analytics, accounts, or data collection of its own.

## Contact

Questions or concerns: rotund_tapir@protonmail.com or
[GitHub issues](https://github.com/rotundtapir/euchre/issues).
