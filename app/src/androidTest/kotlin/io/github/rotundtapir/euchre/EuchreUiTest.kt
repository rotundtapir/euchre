// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.content.Intent
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import io.github.rotundtapir.euchre.ui.HAND_CARD_TAG_PREFIX
import io.github.rotundtapir.euchre.ui.HUMAN_HAND_TAG
import org.junit.Rule

/**
 * The shared harness for the on-device suites: a [MainActivity] launched with the deterministic test
 * fixture, plus the semantics helpers every flow needs.
 *
 * The fixture pins three things through intent extras. [SEED] makes the deal and the seeded bots
 * reproducible. `ANIMATION_SPEED=OFF` makes every pacing mechanism inert, so no test waits on a
 * presentation delay. `SOUND_VOLUME=0` keeps the SoundPool from ever being constructed — native
 * audio playback crashes the instrumented process on the `-no-audio` CI emulator.
 */
abstract class EuchreUiTest {

    @get:Rule
    val rule = AndroidComposeTestRule(
        activityRule = ActivityScenarioRule<MainActivity>(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_SEED, SEED)
                .putExtra(MainActivity.EXTRA_ANIMATION_SPEED, "OFF")
                .putExtra(MainActivity.EXTRA_SOUND_VOLUME, 0f),
        ),
        activityProvider = { scenarioRule ->
            var activity: MainActivity? = null
            scenarioRule.scenario.onActivity { activity = it }
            activity!!
        },
    )

    // --- Semantics helpers -----------------------------------------------------------------------

    /** Matches nodes whose testTag starts with [prefix] (cards are tagged `card:<code>`). */
    protected fun hasTestTagPrefix(prefix: String) =
        SemanticsMatcher("testTag starts with '$prefix'") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }

    /** A card the human may currently tap: CardHand puts the tag and the click action on one node. */
    private val clickableCard = hasClickAction() and hasTestTagPrefix(HAND_CARD_TAG_PREFIX)

    protected fun textExists(text: String, substring: Boolean = false): Boolean =
        rule.onAllNodes(
            SemanticsMatcher("has text '$text'") { node ->
                node.config.getOrNull(SemanticsProperties.Text)
                    ?.any { it.text == text || (substring && it.text.contains(text)) } == true
            },
            useUnmergedTree = true,
        ).fetchSemanticsNodes().isNotEmpty()

    protected fun waitForText(text: String, substring: Boolean = false) =
        rule.waitUntil(STEP_TIMEOUT_MS) { textExists(text, substring) }

    protected fun nodesWithTag(tag: String): List<SemanticsNode> =
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes()

    protected fun nodesWithTagPrefix(prefix: String): List<SemanticsNode> =
        rule.onAllNodes(hasTestTagPrefix(prefix), useUnmergedTree = true).fetchSemanticsNodes()

    /**
     * Cards in the human's fan. Uses the game's own `hand:` prefix rather than cardkit's `card:`,
     * which is applied to every card cardkit draws — the nested image inside each of these cards
     * included, as well as the trick, the up-card and `CardArtWarmup`'s off-screen deck.
     */
    protected fun cardsInHand(): Int = rule.onAllNodes(
        hasTestTagPrefix(HAND_CARD_TAG_PREFIX) and hasAnyAncestor(hasTestTag(HUMAN_HAND_TAG)),
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size

    protected fun clickableCards() = rule.onAllNodes(clickableCard, useUnmergedTree = true)

    /** Whether the Switch tagged [tag] currently reads as on (null when it isn't on screen). */
    protected fun switchIsOn(tag: String): Boolean? =
        nodesWithTag(tag).firstOrNull()
            ?.config?.getOrNull(SemanticsProperties.ToggleableState)
            ?.let { it == ToggleableState.On }

    // --- Flow helpers ----------------------------------------------------------------------------

    /** Home → bot setup → deal. */
    protected fun startGame() {
        rule.onNodeWithText("Play with bots").performClick()
        rule.onNodeWithTag("startBotGame").performScrollTo().performClick()
    }

    protected fun round1BidShowing(): Boolean = nodesWithTag("bid:orderUp").isNotEmpty()

    /** Round 2 is the suit picker: the pass button may be absent when the dealer is stuck. */
    protected fun round2BidShowing(): Boolean =
        !round1BidShowing() && nodesWithTagPrefix("bid:trump:").isNotEmpty()

    protected fun handResultShowing(): Boolean = nodesWithTag("handResultContinue").isNotEmpty()

    protected fun gameOverShowing(): Boolean = nodesWithTag("backToMenu").isNotEmpty()

    protected fun waitForRound1Bid() = rule.waitUntil(STEP_TIMEOUT_MS) { round1BidShowing() }

    /** Names the first trump the round-2 panel offers (the dealer stuck with it has no Pass). */
    protected fun callTrumpInRound2() {
        val tag = nodesWithTagPrefix("bid:trump:")
            .firstNotNullOfOrNull { it.config.getOrNull(SemanticsProperties.TestTag) }
            ?: throw AssertionError("round 2 offered no trump to call")
        rule.onNodeWithTag(tag).performScrollTo().performClick()
    }

    /**
     * Waits until the engine is asking the human for something (or the hand/game has ended).
     * Returns false when nothing more is owed — the caller should stop driving.
     */
    protected fun awaitHumanTurnOrHandEnd(): Boolean {
        rule.waitUntil(STEP_TIMEOUT_MS) {
            round1BidShowing() || round2BidShowing() || textExists(DISCARD_PROMPT, substring = true) ||
                textExists(FARMERS_PROMPT, substring = true) || nodesWithTag("defend:decline").isNotEmpty() ||
                textExists(PLAY_PROMPT) || handResultShowing() || gameOverShowing()
        }
        return !handResultShowing() && !gameOverShowing()
    }

    /**
     * Answers whatever the engine is currently asking, always taking the passive option: pass the
     * auction, decline the house-rule offers, play the first legal card. Returns false when there
     * was nothing to answer.
     */
    protected fun takeTurn(): Boolean {
        when {
            handResultShowing() || gameOverShowing() -> return false
            round1BidShowing() -> rule.onNodeWithTag("bid:pass").performClick()
            round2BidShowing() ->
                if (nodesWithTag("bid:pass").isNotEmpty()) {
                    rule.onNodeWithTag("bid:pass").performScrollTo().performClick()
                } else {
                    callTrumpInRound2()
                }
            nodesWithTag("defend:decline").isNotEmpty() -> rule.onNodeWithTag("defend:decline").performClick()
            textExists(FARMERS_PROMPT, substring = true) ->
                rule.onNodeWithTag("farmersDecline").performClick()
            textExists(DISCARD_PROMPT, substring = true) -> {
                clickableCards()[0].performScrollTo().performClick()
                rule.onNodeWithTag("discardButton").performClick()
            }
            textExists(PLAY_PROMPT) -> {
                val playable = clickableCards()
                if (playable.fetchSemanticsNodes().isEmpty()) return false
                playable[0].performScrollTo().performClick()
            }
            else -> return false
        }
        rule.waitForIdle()
        return true
    }

    companion object {
        /**
         * The fixture seed, shared with the web e2e suite (`?seed=42`). Chosen by replaying the
         * engine and the seeded bots: on this seed the human's first hand reaches a round-1 bid
         * (they deal it, so the button reads "Pick it up") and the match runs to a winner.
         */
        const val SEED = 42L
        const val STEP_TIMEOUT_MS = 20_000L

        /** Prompts the action panels show, matched as substrings (they carry a selection count). */
        const val DISCARD_PROMPT = "bury one card"
        const val FARMERS_PROMPT = "Farmer's hand"
        const val PLAY_PROMPT = "Your turn — tap a card to play"
    }
}
