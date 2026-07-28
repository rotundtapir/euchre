// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The interactive tutorial, driven end to end on a device: pick lesson 1, page its primer, play all
 * five scripted tricks, read the epilogue and come back to a picker that now shows it finished.
 *
 * The lesson pins its OWN seed, dealer and house rules, so the harness's `EXTRA_SEED` fixture is
 * deliberately irrelevant here — what this proves is that the scripted hand the unit-level drift
 * gate replays is the same hand the real ViewModel deals.
 */
@RunWith(AndroidJUnit4::class)
class TutorialFlowTest : EuchreUiTest() {

    @Test
    fun lessonOne_playsThroughAndIsMarkedComplete() {
        openLesson("basics")

        // Lesson 1 is pure play: five tricks, and at every one of them EXACTLY one card is tappable.
        var played = 0
        while (played < TRICKS && awaitHumanTurnOrHandEnd()) {
            if (!textExists(PLAY_PROMPT)) break
            rule.waitUntil(STEP_TIMEOUT_MS) { clickableCards().fetchSemanticsNodes().size == 1 }
            assertTrue(
                "the guidance bubble must be up while the lesson waits on the player",
                nodesWithTag("tutorialAdvice").isNotEmpty(),
            )
            clickableCards()[0].performScrollTo().performClick()
            rule.waitForIdle()
            played++
        }
        assertEquals("the lesson must run all five tricks", TRICKS, played)

        // The hand is scored, and dismissing its result opens the epilogue rather than dealing on.
        rule.waitUntil(STEP_TIMEOUT_MS) { handResultShowing() }
        rule.onNodeWithTag("handResultContinue").performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("tutorialEpilogueNext").isNotEmpty() }
        while (nodesWithTag("tutorialEpilogueNext").isNotEmpty()) {
            rule.onNodeWithTag("tutorialEpilogueNext").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("tutorialComplete").assertIsDisplayed()
        rule.onNodeWithTag("tutorialCompleteContinue").performClick()

        // Home again, with the lesson ticked off and the picker pointing at the next one.
        rule.waitUntil(STEP_TIMEOUT_MS) { textExists("Play with bots") }
        rule.onNodeWithTag("walkthroughButton").performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("lessonPicker").isNotEmpty() }
        rule.waitUntil(STEP_TIMEOUT_MS) { textExists("✓") }
        rule.onNodeWithTag("lessonPickerClose").performScrollTo().performClick()
        rule.onNodeWithText("Play with bots").assertIsDisplayed()
    }

    @Test
    fun aLesson_canBeAbandonedFromItsPrimer() {
        rule.onNodeWithTag("walkthroughButton").performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("lessonPicker").isNotEmpty() }
        rule.onNodeWithTag("lesson:alone").performScrollTo().performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("tutorialPrimerNext").isNotEmpty() }
        // The primer's first page offers Cancel in place of Back — no hand is dealt until "Deal".
        rule.onNodeWithText("Cancel").performClick()
        rule.onNodeWithText("Play with bots").assertIsDisplayed()
    }

    /** Home → picker → the lesson's primer → dealt. */
    private fun openLesson(id: String) {
        rule.onNodeWithTag("walkthroughButton").performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("lessonPicker").isNotEmpty() }
        rule.onNodeWithTag("lesson:$id").performScrollTo().performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("tutorialPrimerNext").isNotEmpty() }
        while (nodesWithTag("tutorialPrimerNext").isNotEmpty()) {
            rule.onNodeWithTag("tutorialPrimerNext").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("tutorialPrimerStart").performClick()
    }

    private companion object {
        const val TRICKS = 5
    }
}
