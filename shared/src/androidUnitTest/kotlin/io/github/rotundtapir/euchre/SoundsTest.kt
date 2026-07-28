// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.ui.SoundEffect
import io.github.rotundtapir.euchre.ai.EuchreBot
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [euchreSoundEffectsFor] — the pure trigger logic behind the game's sound wiring. Views come from
 * a real seeded game, so the transitions (a card landing, a trick closing, a hand scoring) are the
 * ones the UI actually observes.
 */
class SoundsTest {

    /** Seat-0 views after each engine step of a seeded game, in order. */
    private fun seatViews(seed: Long, maxHand: Int = 2): List<EuchrePlayerView> {
        val rules = EuchreRules()
        val bot = EuchreBot()
        val randoms = (0 until PLAYER_COUNT).associate { Seat(it) to Random(seed + it) }
        var state = rules.newGame(seed)
        val views = mutableListOf(rules.view(state, Seat(0)))
        var guard = 0
        while (state.phase != EuchrePhase.COMPLETE && state.handNumber <= maxHand && guard++ < STEP_LIMIT) {
            val seat = rules.currentActor(state) ?: break
            state = rules.apply(state, seat, bot.decide(rules.view(state, seat), randoms.getValue(seat)))
            views += rules.view(state, Seat(0))
        }
        return views
    }

    @Test
    fun `a null on either side of the transition triggers nothing`() {
        val some = seatViews(2024L).first()
        assertEquals(emptyList(), euchreSoundEffectsFor(null, some))
        assertEquals(emptyList(), euchreSoundEffectsFor(some, null))
        assertEquals(emptyList(), euchreSoundEffectsFor(null, null))
    }

    @Test
    fun `each transition fires exactly the effects its field deltas imply`() {
        val views = seatViews(2024L)
        assertTrue(views.size > 20, "the game should produce a long run of views")
        var sawCardPlace = false
        var sawTrickTaken = false
        var sawScore = false
        views.zipWithNext { prev, next ->
            val effects = euchreSoundEffectsFor(prev, next)
            assertEquals(
                next.currentTrick.size > prev.currentTrick.size,
                SoundEffect.CARD_PLACE in effects,
                "CARD_PLACE must fire iff the current trick grew",
            )
            assertEquals(
                next.trickNumber > prev.trickNumber,
                SoundEffect.TRICK_TAKEN in effects,
                "TRICK_TAKEN must fire iff the trick number advanced",
            )
            assertEquals(
                next.handResults.size > prev.handResults.size,
                SoundEffect.SCORE in effects,
                "SCORE must fire iff a hand was newly scored",
            )
            sawCardPlace = sawCardPlace || SoundEffect.CARD_PLACE in effects
            sawTrickTaken = sawTrickTaken || SoundEffect.TRICK_TAKEN in effects
            sawScore = sawScore || SoundEffect.SCORE in effects
        }
        assertTrue(sawCardPlace, "a full hand should place cards")
        assertTrue(sawTrickTaken, "a full hand should complete tricks")
        assertTrue(sawScore, "reaching hand 2 means hand 1 scored")
    }

    @Test
    fun `bidding actions are silent`() {
        // Nothing moves on the felt during the auction, so no sound should fire between two
        // consecutive bidding views of the same hand.
        val views = seatViews(2024L)
        val bidding = views.filter { it.phase == EuchrePhase.BIDDING_ROUND_1 && it.handNumber == 1 }
        assertTrue(bidding.size >= 2, "the seeded hand should show several bidding views")
        bidding.zipWithNext { prev, next ->
            assertEquals(emptyList(), euchreSoundEffectsFor(prev, next))
        }
    }

    @Test
    fun `a repeated hand result still fires SCORE, by count not value`() {
        // Two views identical except handResults grew by an entry equal to the previous one: the
        // count-based trigger fires where a value comparison would silently swallow it.
        val views = seatViews(2024L)
        val afterFirstScore = views.first { it.handResults.isNotEmpty() }
        val doubled = afterFirstScore.copy(
            handResults = afterFirstScore.handResults + afterFirstScore.handResults.last(),
        )
        assertTrue(SoundEffect.SCORE in euchreSoundEffectsFor(afterFirstScore, doubled))
    }

    private companion object {
        const val STEP_LIMIT = 400
    }
}
