// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

/**
 * The project's public URLs — the single source for every code reference. Two places cannot read
 * these and must be kept in sync by hand: `.github/FUNDING.yml` (GitHub's Sponsor button and
 * F-Droid's Donate metadata parse it) and the foss `FEEDBACK_URI` buildConfigField in
 * `app/build.gradle.kts`.
 */
object ProjectLinks {
    /** The donation page every non-Play distribution points at. */
    const val DONATION_URL = "https://liberapay.com/rotund-tapir"

    /** The public issue tracker — the feedback target for FOSS and web builds (Play uses a mailto). */
    const val ISSUE_TRACKER = "https://github.com/rotundtapir/euchre/issues"

    // Where each Android distribution updates from — the "Update required" dialog's targets. Both
    // 404 until euchre is actually published, which is fine: the dialog is only reachable from a
    // deployed server telling this build it is out of date, and there will be no such server until
    // there is a release for these to point at.
    const val PLAY_LISTING = "https://play.google.com/store/apps/details?id=io.github.rotundtapir.euchre"
    const val FDROID_LISTING = "https://f-droid.org/packages/io.github.rotundtapir.euchre/"
}
