// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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
 * On-device integration tests: drive the real [MainActivity] — engine, bots, ViewModel and Compose
 * UI wired together — through complete game flows.
 *
 * The seed is pinned via [MainActivity.EXTRA_SEED] (see [EuchreUiTest.SEED]), so every run deals the
 * same cards and the seeded bots make the same decisions: a failure reproduces exactly. Assertions
 * are still written against *rules invariants* (hand sizes, phase transitions, scoring happened)
 * rather than specific cards, so re-picking the seed changes the path, not the expectations.
 *
 * What the fixture deals, checked against the engine before it was written down: the human holds
 * seat 0 and is the first hand's dealer, so the round-1 prompt offers "Pick it up"; passing it round
 * turns the up-card down and a bot names trump in round 2.
 */
@RunWith(AndroidJUnit4::class)
class GameFlowTest : EuchreUiTest() {

    @Test
    fun homeScreen_showsTitleAndActions() {
        rule.onNodeWithText("Euchre").assertIsDisplayed()
        rule.onNodeWithText("Play with bots").assertIsDisplayed()
        rule.onNodeWithTag("settingsButton").assertIsDisplayed()
        rule.onNodeWithTag("walkthroughButton").assertIsDisplayed()
    }

    @Test
    fun botSetup_offersHouseRules_andBackReturnsHome() {
        rule.onNodeWithText("Play with bots").performClick()
        rule.onNodeWithTag("setup:stickTheDealer").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("setup:farmersHand").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("setupAdvancedAi").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("botSetupBack").performScrollTo().performClick()
        rule.onNodeWithText("Play with bots").assertIsDisplayed()
    }

    @Test
    fun newGame_dealsFiveCards_andRound1BiddingReachesHuman() {
        startGame()
        waitForRound1Bid()

        // Both round-1 choices are offered, and the whole five-card hand is visible while bidding.
        rule.onNodeWithTag("bid:pass").assertIsDisplayed()
        rule.onNodeWithTag("bid:orderUp").assertIsDisplayed()
        rule.onNodeWithTag("aloneToggle").assertIsDisplayed()
        assertEquals("hand should hold 5 cards during bidding", HAND_CARDS, cardsOnScreen())

        // Nothing is playable yet: no card may be tapped during the auction.
        assertEquals(0, clickableCards().fetchSemanticsNodes().size)

        // The score bar starts both sides at zero of the target.
        rule.onNodeWithText("Us: 0/10").assertIsDisplayed()
        rule.onNodeWithText("Them: 0/10").assertIsDisplayed()
    }

    @Test
    fun dealerOrdersUp_mustBuryOneCard_andReturnsToFiveCards() {
        startGame()
        waitForRound1Bid()

        // The human deals the first hand, so ordering the up-card up means taking it into hand.
        rule.onNodeWithTag("bid:orderUp").performClick()
        waitForText(DISCARD_PROMPT, substring = true)

        assertEquals("dealer holds six while burying one", HAND_CARDS + 1, cardsOnScreen())
        rule.onNodeWithTag("discardButton").assertIsNotEnabled()

        clickableCards()[0].performScrollTo().performClick()
        waitForText("(1/1 selected)", substring = true)
        rule.onNodeWithTag("discardButton").assertIsEnabled().performClick()

        // Back to five cards, trump is made, and the hand is under way.
        rule.waitUntil(STEP_TIMEOUT_MS) { !textExists(DISCARD_PROMPT, substring = true) }
        rule.waitUntil(STEP_TIMEOUT_MS) { textExists("Trump:", substring = true) }
        rule.waitUntil(STEP_TIMEOUT_MS) { cardsOnScreen() == HAND_CARDS }
    }

    @Test
    fun discardSelection_isCappedAtOneCard() {
        startGame()
        waitForRound1Bid()
        rule.onNodeWithTag("bid:orderUp").performClick()
        waitForText(DISCARD_PROMPT, substring = true)

        // Tapping a second card cannot arm a two-card discard; the panel stays at one selected.
        repeat(2) { i ->
            clickableCards()[i].performScrollTo().performClick()
            rule.waitForIdle()
        }
        waitForText("(1/1 selected)", substring = true)
        rule.onNodeWithTag("discardButton").assertIsEnabled()
    }

    @Test
    fun passingRound1_turnsTheUpCardDown_andABotNamesTrump() {
        startGame()
        waitForRound1Bid()
        rule.onNodeWithTag("bid:pass").performClick()

        // Either the auction reaches the human again in round 2, or a bot has already made trump.
        rule.waitUntil(STEP_TIMEOUT_MS) { textExists("Trump:", substring = true) || round2BidShowing() }
        if (round2BidShowing()) {
            // With "stick the dealer" on, the dealer (the human, first hand) has no Pass at all.
            callTrumpInRound2()
            rule.waitUntil(STEP_TIMEOUT_MS) { textExists("Trump:", substring = true) }
        }
        assertTrue("trump must be made before play begins", textExists("Trump:", substring = true))
    }

    @Test
    fun playingACard_completesATrick_andBumpsTheTrickCount() {
        startGame()
        playUntilHumanPlaysACard()

        // Bots finish the trick instantly at AnimationSpeed.OFF; somebody's counter must move.
        rule.waitUntil(STEP_TIMEOUT_MS) { textExists("tricks: 1", substring = true) }
        assertTrue(textExists("tricks: 1", substring = true))
    }

    @Test
    fun whenItIsYourTurn_atLeastOneCardIsPlayable_andNeverMoreThanHeld() {
        startGame()
        var checks = 0
        val deadline = System.currentTimeMillis() + HAND_TIMEOUT_MS
        while (checks < 3 && System.currentTimeMillis() < deadline) {
            if (!awaitHumanTurnOrHandEnd()) break
            if (textExists(PLAY_PROMPT)) {
                val playable = clickableCards().fetchSemanticsNodes().size
                assertTrue("at least one legal play", playable >= 1)
                assertTrue("no more legal plays than cards held", playable <= HAND_CARDS)
                checks++
            }
            if (!takeTurn()) break
        }
        assertTrue("expected to reach at least one play turn", checks >= 1)
    }

    @Test
    fun aFullHand_reachesTheResultDialog_andScores() {
        startGame()
        playUntilHandResultOrGameEnd()

        assertTrue("a completed hand must show its breakdown", handResultShowing())
        // The breakdown names the score both sides now hold out of the target.
        assertTrue(textExists("/10", substring = true))
        rule.onNodeWithTag("handResultContinue").performClick()

        // Dismissing it either deals the next hand or ends the match.
        rule.waitUntil(STEP_TIMEOUT_MS) {
            !handResultShowing() && (gameOverShowing() || textExists("Us: ", substring = true))
        }
    }

    @Test
    fun gameEnd_showsTheFinalHandBreakdownFirst_thenTheScoreSheet() {
        startGame()
        val deadline = System.currentTimeMillis() + GAME_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            playUntilHandResultOrGameEnd()
            if (handResultShowing()) {
                // Every hand — the last one included — shows its breakdown before anything else.
                assertTrue(
                    "the game-over sheet must wait for the hand result to be dismissed",
                    !gameOverShowing(),
                )
                rule.onNodeWithTag("handResultContinue").performClick()
                rule.waitForIdle()
            }
            if (gameOverShowing()) {
                // The score sheet tallies the hands played and offers the way out.
                rule.waitUntil(STEP_TIMEOUT_MS) { textExists("Hand") }
                rule.onNodeWithTag("backToMenu").performClick()
                rule.waitUntil(STEP_TIMEOUT_MS) { textExists("Play with bots") }
                return
            }
        }
        throw AssertionError("game did not end within ${GAME_TIMEOUT_MS}ms")
    }

    @Test
    fun sortToggle_keepsEveryCard() {
        startGame()
        waitForRound1Bid()
        assertEquals(HAND_CARDS, cardsOnScreen())
        rule.onNodeWithTag("sortToggle").performClick()
        rule.waitForIdle()
        assertEquals("toggling sort must not add or drop cards", HAND_CARDS, cardsOnScreen())
    }

    @Test
    fun menuButton_confirmsThenReturnsHome() {
        startGame()
        waitForRound1Bid()
        rule.onNodeWithTag("menuButton").performClick()
        waitForText("Leave game?")
        rule.onNodeWithTag("confirmLeave").performClick()
        rule.onNodeWithText("Play with bots").assertIsDisplayed()
    }

    @Test
    fun menuCancel_staysInTheGame() {
        startGame()
        waitForRound1Bid()
        rule.onNodeWithTag("menuButton").performClick()
        waitForText("Leave game?")
        rule.onNodeWithText("Cancel").performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { !textExists("Leave game?") }
        assertTrue("cancelling must keep the table up", nodesWithTag("menuButton").isNotEmpty())
        assertTrue("home must not be shown after Cancel", !textExists("Play with bots"))
    }

    @Test
    fun inProgressGame_survivesActivityRecreation() {
        startGame()
        waitForRound1Bid()

        // Rotation / theme change / process-driven recreation: the game lives in the ViewModel and
        // the current screen is saveable, so the table must still be there afterwards.
        rule.activityRule.scenario.recreate()

        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("menuButton").isNotEmpty() }
        assertEquals(HAND_CARDS, cardsOnScreen())
    }

    @Test
    fun inGameSettingsCog_opensTheDialog_andDisablesHouseRules() {
        // From home the house-rule switches are live…
        rule.onNodeWithTag("settingsButton").performClick()
        rule.onNodeWithTag("stickTheDealer").performScrollTo().assertIsEnabled()
        rule.onNodeWithTag("bennyEnabled").performScrollTo().assertIsEnabled()
        rule.onNodeWithText("Done").performClick()

        // …but in a game they could only apply to the next one, so the same dialog disables them.
        startGame()
        waitForRound1Bid()
        rule.onNodeWithTag("gameSettingsButton").performClick()
        rule.onNodeWithTag("stickTheDealer").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("advancedAi").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithTag("holdTricks").performScrollTo().assertIsEnabled()
        rule.onNodeWithText("Done").performClick()
        assertTrue("dismissing settings returns to the table", nodesWithTag("menuButton").isNotEmpty())
    }

    @Test
    fun settingsDialog_offersTheAnimationToggle_supportAndFeedback() {
        rule.onNodeWithTag("settingsButton").performClick()
        rule.onNodeWithTag("animationSpeed").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("volumeSlider").performScrollTo().assertIsDisplayed()
        // FOSS flavor: a donation link, never an ads purchase.
        rule.onNodeWithText("Support development").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("feedbackButton").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Play with bots").assertIsDisplayed()
    }

    @Test
    fun helpRules_openFromSettings_andDocumentTheBowers() {
        rule.onNodeWithTag("settingsButton").performClick()
        rule.onNodeWithTag("helpButton").performScrollTo().performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("rulesNext").isNotEmpty() }
        // Page to the end; the bowers are the rule newcomers get wrong, so they must be covered.
        var sawBowers = textExists("bower", substring = true)
        while (nodesWithTag("rulesNext").isNotEmpty()) {
            rule.onNodeWithTag("rulesNext").performClick()
            rule.waitForIdle()
            sawBowers = sawBowers || textExists("bower", substring = true)
        }
        assertTrue("the rules must explain the bowers", sawBowers)
        rule.onNodeWithTag("rulesClose").performClick()
        rule.onNodeWithText("Done").performClick()
        rule.onNodeWithText("Play with bots").assertIsDisplayed()
    }

    @Test
    fun howToPlay_opensTheLessonPicker_andStillReachesTheWrittenRules() {
        // "How to play" now offers the four interactive lessons — but the written rules must stay
        // reachable from it, so the reference is never lost behind the walkthrough.
        rule.onNodeWithTag("walkthroughButton").performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("lessonPicker").isNotEmpty() }
        listOf("basics", "bidding", "alone", "defense").forEach { id ->
            rule.onNodeWithTag("lesson:$id").performScrollTo().assertIsDisplayed()
        }
        rule.onNodeWithTag("readRules").performScrollTo().performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("rulesNext").isNotEmpty() }
        while (nodesWithTag("rulesNext").isNotEmpty()) {
            rule.onNodeWithTag("rulesNext").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("rulesClose").performClick()
        rule.onNodeWithText("Play with bots").assertIsDisplayed()
    }

    @Test
    fun sortByDefaultSwitch_isOfferedAndToggles() {
        rule.onNodeWithTag("settingsButton").performClick()
        val initial = switchIsOn("sortDefault") == true
        // The tap round-trips through DataStore before the switch recomposes — wait, don't assert.
        rule.onNodeWithTag("sortDefault").performScrollTo().performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { switchIsOn("sortDefault") == !initial }
        // Put it back so the other tests see the default.
        rule.onNodeWithTag("sortDefault").performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { switchIsOn("sortDefault") == initial }
        rule.onNodeWithText("Done").performClick()
    }

    /** Plays generic turns until the human has actually put a card on the table. */
    private fun playUntilHumanPlaysACard() {
        val deadline = System.currentTimeMillis() + HAND_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!awaitHumanTurnOrHandEnd()) return
            val wasPlay = textExists(PLAY_PROMPT)
            if (!takeTurn()) return
            if (wasPlay) return
        }
        throw AssertionError("the human never got a play turn")
    }

    /** Plays whole turns until a hand-result dialog is up or the match is over. */
    private fun playUntilHandResultOrGameEnd() {
        val deadline = System.currentTimeMillis() + HAND_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!awaitHumanTurnOrHandEnd()) return
            if (!takeTurn()) return
        }
        throw AssertionError("no hand result or game end within ${HAND_TIMEOUT_MS}ms")
    }

    private companion object {
        /** Euchre deals five; the dealer briefly holds six while burying the up-card. */
        const val HAND_CARDS = 5
        const val HAND_TIMEOUT_MS = 120_000L
        const val GAME_TIMEOUT_MS = 300_000L
    }
}
