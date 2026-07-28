// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.remember
import io.github.rotundtapir.cardkit.monetization.Monetization
import io.github.rotundtapir.cardkit.ui.AppConfig
import io.github.rotundtapir.cardkit.ui.AppDistribution
import io.github.rotundtapir.cardkit.ui.AppPlatform
import io.github.rotundtapir.cardkit.ui.CardArtWarmup
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
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
    }

    private lateinit var monetization: Monetization

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        monetization = MonetizationProvider.create(this)
        setContent {
            CardkitTheme {
                Box {
                    // Card bitmaps are decoded lazily on first use; warm them all so the first deal
                    // never shows blank backs. Draws nothing and takes no input.
                    CardArtWarmup()
                    EuchreApp(
                        monetization = monetization,
                        settings = remember { androidSettingsRepository(applicationContext) },
                        appConfig = appConfig(),
                        nextSeed = ::newGameSeed,
                        animationSpeedOverride = animationSpeedOverride(),
                        soundVolumeOverride = soundVolumeOverride(),
                        botSkillOverride = botSkillOverride(),
                        aiBudgetMillisOverride = aiBudgetMillisOverride(),
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
