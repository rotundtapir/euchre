// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.app.Activity
import io.github.rotundtapir.cardkit.monetization.Monetization
import io.github.rotundtapir.cardkit.monetization.play.PlayMonetization

/**
 * Google Play flavor: Google Mobile Ads plus a one-time "remove ads" purchase.
 *
 * TODO(release-checklist): every ad unit below is currently Google's official **test** id, so no
 * build can generate invalid traffic before the AdMob units exist. Creating them (and the
 * `remove_ads` Play Console product) is a v0.1.0 release-checklist item; swap the release branch of
 * each id then, and never point a debug build at a real unit.
 */
object MonetizationProvider {
    private val config = PlayMonetization.Config(
        bannerAdUnitId = "ca-app-pub-3940256099942544/6300978111", // AdMob test banner
        interstitialAdUnitId = "ca-app-pub-3940256099942544/1033173712", // AdMob test interstitial
        removeAdsProductId = "remove_ads",
        // Debug builds force the EEA consent form so the UMP flow is always exercisable.
        consentDebugGeographyEea = BuildConfig.DEBUG,
    )

    fun create(activity: Activity): Monetization = PlayMonetization(activity, config)
}
