// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rotundtapir.euchre.ui.FELT_TAG
import io.github.rotundtapir.euchre.ui.HUMAN_HAND_TAG
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The table must not rearrange itself as a hand progresses.
 *
 * Euchre's felt takes whatever height the rest of the column leaves and sizes the cards on it from
 * its own measurements, so anything that changes height — a panel gaining a go-alone toggle, an
 * opponent's auction line disappearing, a seat sitting out — used to move the felt and rescale
 * every card on it. Those were found by a human noticing, three releases in; this notices instead.
 *
 * Runs at `AnimationSpeed.OFF` like every other suite here. That is not a limitation: layout
 * stability is about where things come to rest, not how they travel, so the assertions hold with
 * the animations short-circuited (which is also what keeps them free of frame-timing flake).
 */
@RunWith(AndroidJUnit4::class)
class TableLayoutStabilityTest : EuchreUiTest() {

    @Test
    fun theFeltAndTheHandHoldTheirPlaceFromBiddingIntoPlay() {
        startGame()
        waitForRound1Bid()
        val bidding = tableGeometry()

        // Answer whatever the auction asks until the first card is owed.
        driveUntil { textExists(PLAY_PROMPT) }
        assertHolds("bidding → play", bidding, tableGeometry())

        // And again with a card actually on the felt: the trick's slots are laid out in their final
        // geometry from the first card, so the felt must not move as the rest of the trick lands.
        takeTurn()
        rule.waitForIdle()
        assertHolds("first card played", bidding, tableGeometry())
    }

    @Test
    fun theFeltHoldsItsPlaceAcrossTheWholeFirstHand() {
        startGame()
        waitForRound1Bid()
        val bidding = tableGeometry()

        // Every human turn of the hand, checked as it happens rather than only at the end.
        var turns = 0
        while (awaitHumanTurnOrHandEnd() && turns++ < MAX_TURNS) {
            assertHolds("turn $turns", bidding, tableGeometry())
            if (!takeTurn()) break
        }
        assertTrue("the hand should have asked the human for something", turns > 0)
    }

    // --- Geometry ------------------------------------------------------------------------------

    /** Where the felt and the human's fan sit right now. */
    private fun tableGeometry(): Map<String, DpRect> =
        listOf(FELT_TAG, HUMAN_HAND_TAG).associateWith { tag ->
            val bounds = rule.onNodeWithTag(tag, useUnmergedTree = true).getUnclippedBoundsInRoot()
            // A node scrolled out of view reports an empty rect at the origin, which would make
            // every comparison against it pass silently. Fail loudly instead.
            assertTrue(
                "$tag reported an empty rect — scrolled out of view, so this test can see nothing",
                bounds.right - bounds.left > 0.dp && bounds.bottom - bounds.top > 0.dp,
            )
            bounds
        }

    private fun assertHolds(moment: String, before: Map<String, DpRect>, now: Map<String, DpRect>) {
        before.forEach { (tag, was) ->
            val is0 = now.getValue(tag)
            assertTrue(
                "$tag moved or resized at '$moment': was $was, now $is0 — something above or below " +
                    "the felt changed height and shoved it",
                closeEnough(was, is0),
            )
        }
    }

    /** Equal to within a device pixel's worth of rounding. */
    private fun closeEnough(a: DpRect, b: DpRect): Boolean =
        abs((a.left - b.left).value) <= TOLERANCE &&
            abs((a.top - b.top).value) <= TOLERANCE &&
            abs((a.right - b.right).value) <= TOLERANCE &&
            abs((a.bottom - b.bottom).value) <= TOLERANCE

    /** Answers turns until [done] holds, so a test can say "get me to the first trick". */
    private fun driveUntil(done: () -> Boolean) {
        var turns = 0
        while (!done() && turns++ < MAX_TURNS) {
            if (!awaitHumanTurnOrHandEnd()) break
            if (done()) return
            if (!takeTurn()) break
        }
        assertTrue("never reached the expected point within $MAX_TURNS turns", done())
    }

    private companion object {
        /** A whole Euchre hand is five tricks plus the auction; well under this. */
        const val MAX_TURNS = 30

        /** Dp are fractional on most densities; a dp of slack absorbs rounding, not movement. */
        const val TOLERANCE = 1f
    }
}
