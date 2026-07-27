// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AloneTest {
    private val rules = EuchreRules()

    @Test
    fun `going alone sits the partner out for the hand`() {
        // Dealer 0; seat 1 orders up alone -> partner seat 3 sits out; dealer still exchanges.
        val state = rules.newGame(1, Seat(0))
        val discard = rules.act(state, EuchreAction.OrderUp(alone = true))
        assertEquals(EuchrePhase.DEALER_DISCARD, discard.phase)
        val play = rules.act(discard, EuchreAction.DealerDiscard(discard.hands.getValue(Seat(0)).first()))
        assertEquals(listOf(Seat(0), Seat(1), Seat(2)), play.activeSeats)
        assertEquals(Seat(1), play.leader)
        assertTrue(play.makers!!.alone)
    }

    @Test
    fun `the leader skips a sitting-out seat`() {
        // Dealer 0; seat 3 calls alone in round 2, sitting partner seat 1 out. Seat 1 would have
        // led (left of dealer), so the lead falls to the next active seat clockwise: seat 2.
        var state = rules.newGame(1, Seat(0))
        state = rules.passToRound2(state)
        // Round 2 order from dealer's left: 1, 2, 3. Pass twice so seat 3 may call.
        state = rules.act(state, EuchreAction.Pass)
        state = rules.act(state, EuchreAction.Pass)
        val suit = io.github.rotundtapir.cardkit.core.Suit.entries.first { it != state.upcardSuit }
        val play = rules.act(state, EuchreAction.CallTrump(suit, alone = true))
        assertEquals(EuchrePhase.PLAY, play.phase)
        assertEquals(listOf(Seat(0), Seat(2), Seat(3)), play.activeSeats)
        assertEquals(Seat(2), play.leader)
    }

    @Test
    fun `dealer sits out without exchanging when the partner orders alone`() {
        // Dealer 0; maker must be seat 2 (dealer's partner). Round 1 order: 1 passes, 2 orders alone.
        var state = rules.newGame(1, Seat(0))
        val upcard = state.upcard!!
        state = rules.act(state, EuchreAction.Pass)
        val play = rules.act(state, EuchreAction.OrderUp(alone = true))
        // No DEALER_DISCARD: the dealer's hand is dead and the up-card was never picked up.
        assertEquals(EuchrePhase.PLAY, play.phase)
        assertEquals(5, play.hands.getValue(Seat(0)).size)
        assertTrue(upcard in play.kitty)
        assertEquals(listOf(Seat(1), Seat(2), Seat(3)), play.activeSeats)
        assertEquals(Seat(1), play.leader)
        // Trump is still the up-card's suit even though nobody holds the card.
        assertEquals(state.upcardSuit, play.makers!!.trump)
    }
}
