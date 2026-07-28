// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BiddingRound2Test {
    private val rules = EuchreRules()

    private fun round2(seed: Long = 1) = rules.passToRound2(rules.newGame(seed, Seat(0)))

    @Test
    fun `the turned-down suit may not be named`() {
        val state = round2()
        val legal = rules.legalActions(state, Seat(1))
        val calls = legal.filterIsInstance<EuchreAction.CallTrump>()
        assertEquals(6, calls.size) // 3 suits x { alone, not }
        assertTrue(calls.none { it.suit == state.upcardSuit })
        assertTrue(EuchreAction.Pass in legal)
        assertFailsWith<IllegalStateException> {
            rules.act(state, EuchreAction.CallTrump(state.upcardSuit!!))
        }
    }

    @Test
    fun `naming a suit starts play immediately`() {
        val state = round2()
        val suit = Suit.entries.first { it != state.upcardSuit }
        val play = rules.act(state, EuchreAction.CallTrump(suit))
        assertEquals(EuchrePhase.PLAY, play.phase)
        val makers = assertNotNull(play.makers)
        assertEquals(Seat(1), makers.maker)
        assertEquals(suit, makers.trump)
        assertFalse(makers.orderedUp)
        assertEquals(EUCHRE_SEATS, play.activeSeats)
        assertEquals(Seat(1), play.leader) // left of dealer leads
        assertEquals(5, play.hands.getValue(Seat(0)).size) // no pickup in round 2
    }

    @Test
    fun `a full pass-out is thrown in and redealt by the next dealer`() {
        var state = round2()
        repeat(3) { state = rules.act(state, EuchreAction.Pass) }
        val redealt = rules.act(state, EuchreAction.Pass) // dealer passes: throw-in
        assertEquals(EuchrePhase.BIDDING_ROUND_1, redealt.phase)
        assertEquals(1, redealt.handNumber)
        assertEquals(Seat(1), redealt.dealer)
        assertEquals(mapOf(0 to 0, 1 to 0), redealt.scores)
        assertEquals(nextSeed(1), redealt.rngSeed)
    }
}
