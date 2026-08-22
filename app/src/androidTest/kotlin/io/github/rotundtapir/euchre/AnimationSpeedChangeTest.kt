// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rotundtapir.euchre.engine.HAND_SIZE
import io.github.rotundtapir.euchre.ui.HAND_CARD_TAG_PREFIX
import io.github.rotundtapir.euchre.ui.HUMAN_HAND_TAG
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression for the deal gate: a hand dealt with animations OFF must survive turning them back on
 * mid-hand. Ported from 500's fix for the same bug in the same mirrored code (rotundtapir/500#40).
 *
 * Deliberately NOT an [EuchreUiTest]: that harness pins `ANIMATION_SPEED=OFF` as an intent extra,
 * and the override wins over the persisted setting for the activity's lifetime — so a test running
 * under it can flip the settings dialog all it likes without the game ever seeing a speed change.
 * That is precisely the blind spot that let this bug live behind a green connected suite. Here the
 * speed is persisted through the real dialog, the same path a player takes.
 *
 * `SOUND_VOLUME=0` is still pinned: the SoundPool must never be constructed on the `-no-audio`
 * emulator. The speed extra is the one deliberately absent.
 */
@RunWith(AndroidJUnit4::class)
class AnimationSpeedChangeTest {

    @get:Rule
    val rule = AndroidComposeTestRule(
        activityRule = ActivityScenarioRule<MainActivity>(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SEED, EuchreUiTest.SEED)
                .putExtra(MainActivity.EXTRA_SOUND_VOLUME, 0f),
        ),
        activityProvider = { scenarioRule ->
            var activity: MainActivity? = null
            scenarioRule.scenario.onActivity { activity = it }
            activity!!
        },
    )

    /** Cards in the human's fan — the same scoped count [EuchreUiTest] uses, and for the same reasons. */
    private fun cardsInHand(): Int = rule.onAllNodes(
        SemanticsMatcher("testTag starts with '$HAND_CARD_TAG_PREFIX'") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(HAND_CARD_TAG_PREFIX) == true
        } and hasAnyAncestor(hasTestTag(HUMAN_HAND_TAG)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size

    /**
     * Cycle the settings dialog's speed button until its label reads "Off" (cycle order
     * Slow → Normal → Fast → Off; the label is the only "Off" text in the dialog).
     */
    private fun cycleSpeedToOff() {
        repeat(AnimationSpeedCycleLength) {
            if (rule.onAllNodesWithText("Off").fetchSemanticsNodes().isNotEmpty()) return
            rule.onNodeWithTag("animationSpeed").performScrollTo().performClick()
            rule.waitForIdle()
        }
        error("animationSpeed never reached 'Off'")
    }

    @Test
    fun handSurvivesTurningAnimationsOnMidHand() {
        // Persist OFF via the real settings dialog (no override in play).
        rule.onNodeWithTag("settingsButton").performClick()
        cycleSpeedToOff()
        rule.onNodeWithText("Done").performClick()

        // Start a bots game: at OFF the deal is skipped and the five cards appear instantly.
        rule.onNodeWithText("Play with bots").performClick()
        rule.onNodeWithTag("startBotGame").performScrollTo().performClick()
        rule.waitUntil(EuchreUiTest.STEP_TIMEOUT_MS) { cardsInHand() >= HAND_SIZE }
        val before = cardsInHand()
        assertTrue("expected a dealt hand before the speed change", before >= HAND_SIZE)

        // Mid-hand, turn animations back on (Off → Slow is one tap in the cycle).
        rule.onNodeWithTag("gameSettingsButton").performClick()
        rule.onNodeWithTag("animationSpeed").performScrollTo().performClick()
        rule.onNodeWithText("Done").performClick()
        rule.waitForIdle()

        assertEquals(
            "the hand must not blank when animations turn on mid-hand",
            before,
            cardsInHand(),
        )
    }

    private companion object {
        /** Slow → Normal → Fast → Off: four taps is a full lap, so "Off" is reached or absent. */
        const val AnimationSpeedCycleLength = 4
    }
}
