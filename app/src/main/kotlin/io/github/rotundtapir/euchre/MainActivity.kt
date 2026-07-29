// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.app.ShareCompat
import io.github.rotundtapir.euchre.online.JoinLink
import io.github.rotundtapir.cardkit.monetization.Monetization
import io.github.rotundtapir.cardkit.ui.AppConfig
import io.github.rotundtapir.cardkit.ui.AppDistribution
import io.github.rotundtapir.cardkit.ui.AppPlatform
import io.github.rotundtapir.cardkit.ui.CardArtWarmup
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.euchre.engine.euchreDeck
import io.github.rotundtapir.cardkit.ui.theme.CardkitTheme

/**
 * The Android shell. It owns nothing about the game: it supplies the platform seams the shared
 * [EuchreApp] asks for (a [Monetization] implementation chosen by the build flavor, a DataStore
 * settings backend, the build constants multiplatform code cannot read, and a seed source) and
 * translates the instrumentation test overrides from intent extras.
 */
class MainActivity : ComponentActivity() {
    companion object {
        /** Intent extra overriding the game seed — set by instrumentation tests for reproducibility. */
        const val EXTRA_SEED = "io.github.rotundtapir.euchre.SEED"

        /**
         * Intent extra (an [AnimationSpeed] name) overriding the persisted animation speed — set by
         * instrumentation tests to run without bot pacing.
         */
        const val EXTRA_ANIMATION_SPEED = "io.github.rotundtapir.euchre.ANIMATION_SPEED"

        /** Intent extra (Float) overriding the persisted sound volume — tests pass 0f. */
        const val EXTRA_SOUND_VOLUME = "io.github.rotundtapir.euchre.SOUND_VOLUME"

        /**
         * Intent extra (a [BotSkill] name) overriding the persisted bot AI — set by instrumentation
         * tests to exercise the advanced path without touching the persisted setting.
         */
        const val EXTRA_BOT_SKILL = "io.github.rotundtapir.euchre.BOT_SKILL"

        /**
         * Intent extra (Long, milliseconds) shrinking the advanced bot's per-move search budget so
         * instrumented games think at test speed.
         */
        const val EXTRA_AI_BUDGET_MS = "io.github.rotundtapir.euchre.AI_BUDGET_MS"

        /**
         * Intent extra pointing online play at a different server — `ws://10.0.2.2:8080` reaches a
         * dev server on the emulator's host. Session-only and never persisted: see [EuchreApp]'s note
         * on why a link must not be able to repoint someone's saved online settings.
         */
        const val EXTRA_SERVER_URL = "io.github.rotundtapir.euchre.SERVER_URL"

        /**
         * Intent extra prefilling the online display name. The name gates the create/join buttons,
         * so an instrumented run needs this to get past the entry screen without typing on the
         * canvas. Session-only, never persisted.
         */
        const val EXTRA_PLAYER_NAME = "io.github.rotundtapir.euchre.PLAYER_NAME"
    }

    private lateinit var monetization: Monetization

    /**
     * A join code from an App Link, held in state rather than read once: Android delivers a second
     * link to a warm activity through [onNewIntent], not a fresh [onCreate], so reading `intent` at
     * composition time would silently ignore every invite after the first.
     */
    private var joinCode by mutableStateOf<String?>(null)

    /** The seed a new game starts from: pinned by the launching intent, else the wall clock. */
    private fun newGameSeed(): Long =
        if (intent?.hasExtra(EXTRA_SEED) == true) intent.getLongExtra(EXTRA_SEED, 0) else System.currentTimeMillis()

    /** The sound volume forced by the launching intent, or null to use the persisted setting. */
    private fun soundVolumeOverride(): Float? =
        if (intent?.hasExtra(EXTRA_SOUND_VOLUME) == true) intent.getFloatExtra(EXTRA_SOUND_VOLUME, 0f) else null

    /** The animation speed forced by the launching intent, or null to use the persisted setting. */
    private fun animationSpeedOverride(): AnimationSpeed? =
        AnimationSpeed.fromName(intent?.getStringExtra(EXTRA_ANIMATION_SPEED))

    /** The bot skill forced by the launching intent, or null to use the persisted setting. */
    private fun botSkillOverride(): BotSkill? = BotSkill.fromName(intent?.getStringExtra(EXTRA_BOT_SKILL))

    /** The advanced-AI budget forced by the launching intent, or null for the production budgets. */
    private fun aiBudgetMillisOverride(): Long? =
        if (intent?.hasExtra(EXTRA_AI_BUDGET_MS) == true) intent.getLongExtra(EXTRA_AI_BUDGET_MS, 0) else null

    private fun appConfig() = AppConfig(
        feedbackUri = BuildConfig.FEEDBACK_URI,
        version = BuildConfig.VERSION_NAME,
        platform = AppPlatform.ANDROID,
        flavor = when (BuildConfig.FLAVOR) {
            "play" -> AppDistribution.PLAY
            "foss" -> AppDistribution.FOSS
            else -> AppDistribution.UNKNOWN
        },
        commit = BuildConfig.GIT_COMMIT,
    )

    /** The invite code carried by an App Link, or null when the app was opened normally. */
    private fun joinCodeFrom(intent: Intent?): String? =
        intent?.takeIf { it.action == Intent.ACTION_VIEW }
            ?.data?.getQueryParameter(JoinLink.PARAM)
            ?.let(JoinLink::normalizeCode)

    /** Hands an invite link to the system share sheet. Returns false: the sheet shows its own UI. */
    private fun shareInvite(message: String, url: String): Boolean {
        ShareCompat.IntentBuilder(this)
            .setType("text/plain")
            .setText("$message\n$url")
            .startChooser()
        return false
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // A warm start from a second invite link: replace the code so the app navigates to the new
        // lobby instead of the one it was already showing.
        setIntent(intent)
        joinCodeFrom(intent)?.let { joinCode = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monetization = MonetizationProvider.create(this)
        joinCode = joinCodeFrom(intent)
        setContent {
            CardkitTheme {
                Box {
                    // Card bitmaps are decoded lazily on first use; warm them so the first deal
                    // never shows blank backs. Draws nothing and takes no input. Only the cards
                    // Euchre can deal — Benny's Joker included, since the warmup runs before a game
                    // exists and the house rule is not yet known.
                    CardArtWarmup(cards = euchreDeck(benny = true))
                    EuchreApp(
                        monetization = monetization,
                        settings = remember { androidSettingsRepository(applicationContext) },
                        appConfig = appConfig(),
                        nextSeed = ::newGameSeed,
                        animationSpeedOverride = animationSpeedOverride(),
                        soundVolumeOverride = soundVolumeOverride(),
                        botSkillOverride = botSkillOverride(),
                        aiBudgetMillisOverride = aiBudgetMillisOverride(),
                        linkSharer = ::shareInvite,
                        joinCodeOverride = joinCode,
                        serverUrlOverride = intent?.getStringExtra(EXTRA_SERVER_URL),
                        playerNameOverride = intent?.getStringExtra(EXTRA_PLAYER_NAME),
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        monetization.dispose()
        super.onDestroy()
    }
}
