<!-- SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception -->

# Invite links and Android App Links

An online lobby is shared as a link:

```
https://rotundtapir.github.io/euchre/?joinCode=AB12
```

One URL serves both platforms. On the web it opens the app straight on the Join screen with the code
filled in. On Android, if App Links verification has succeeded, the same URL opens the installed app
instead of the browser; if it has not, it opens the web build, which still works. That is the whole
design: **verification failing degrades to the browser rather than to a dead link.**

## How the Android side is wired

1. `AndroidManifest.xml` declares an `autoVerify` intent filter for `https://rotundtapir.github.io/euchre/`.
2. Android fetches `https://rotundtapir.github.io/.well-known/assetlinks.json` and checks that it
   lists this app's package and signing certificate.
3. `MainActivity` reads the code from `intent.data` and hands it to the shared app as
   `joinCodeOverride`, which routes online mode to the Join screen.

The activity is `singleTask`, so a *second* invite arriving while the app is already open comes
through `onNewIntent`, not a fresh `onCreate`. The code is therefore held in state and refreshed
there — reading `intent` at composition time would silently ignore every invite after the first, and
the failure would look like "the link did nothing".

## assetlinks.json

It lives in the [Pages root repo](https://github.com/rotundtapir/rotundtapir.github.io) under
`.well-known/`, shared with 500. Euchre's entry lists two certificates:

- the **FOSS release** key. Read it from a signed APK rather than a keystore, so what you publish is
  provably what users install:
  ```bash
  apksigner verify --print-certs app-foss-release.apk
  ```
- the **debug keystore**, which is what makes App Links verify on sideloaded `fossDebug` builds.

**The debug entry is a deliberate, temporary trade, and it comes out at v1.0.0** — tracked in
[euchre#1](https://github.com/rotundtapir/euchre/issues/1), mirroring
[500#38](https://github.com/rotundtapir/500/issues/38). A debug signing key is a development
convenience and does not carry the protections a release key does, so it earns its place in a
production trust list only while tapping a real invite on a test build is still part of the loop.

Nothing automated depends on it: `DeepLinkTest` launches an explicit-component intent, which bypasses
verification entirely, and CI runners generate their own debug key. When it is removed, the degraded
behaviour is that an invite tapped on a debug build opens the web client, which joins the game
normally.

If euchre ever ships on Google Play, Play re-signs the app with its own key, and **that** fingerprint
must be added too or verification will fail for every Play install. Take it from the Play Console
under Setup → App integrity.

## Checking verification on a device

```bash
adb shell pm get-app-links io.github.rotundtapir.euchre
```

Look for `verified`. If it says `legacy_failure` or the domain is missing, Android either could not
fetch the file or found no matching fingerprint. Re-trigger a check with:

```bash
adb shell pm verify-app-links --re-verify io.github.rotundtapir.euchre
```

Verification only happens on install, so a sideloaded debug build that predates an assetlinks change
keeps its old verdict until reinstalled.

## Testing the routing without any of that

The intent path can be exercised directly, which is what `DeepLinkTest` does:

```bash
adb shell am start -a android.intent.action.VIEW \
  -d 'https://rotundtapir.github.io/euchre/?joinCode=ABCD' \
  io.github.rotundtapir.euchre
```

Naming the package bypasses verification, so this tests the app's own handling rather than Android's
decision about who owns the link.
