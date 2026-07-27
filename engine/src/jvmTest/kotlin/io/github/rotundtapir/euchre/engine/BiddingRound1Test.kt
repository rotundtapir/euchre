// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BiddingRound1Test {
    private val rules = EuchreRules()

    @Test
    fun `a fresh deal opens round 1 left of the dealer`() {
        val state = rules.newGame(seed = 1, firstDealer = Seat(2))
        assertEquals(EuchrePhase.BIDDING_ROUND_1, state.phase)
        assertEquals(Seat(3), rules.currentActor(state))
        assertNotNull(state.upcard)
        assertEquals(5, state.hands.getValue(Seat(0)).size)
        assertEquals(3, state.kitty.size)
        assertEquals(
            listOf(EuchreAction.Pass, EuchreAction.OrderUp(false), EuchreAction.OrderUp(true)),
            rules.legalActions(state, Seat(3)),
        )
        assertTrue(rules.legalActions(state, Seat(0)).isEmpty())
    }

    @Test
    fun `ordering up makes trump and hands the up-card to the dealer`() {
        val state = rules.newGame(seed = 1, firstDealer = Seat(0))
        val upcard = state.upcard!!
        val next = rules.act(state, EuchreAction.OrderUp(alone = false))
        assertEquals(EuchrePhase.DEALER_DISCARD, next.phase)
        val makers = assertNotNull(next.makers)
        assertEquals(Seat(1), makers.maker)
        assertEquals(state.upcardSuit, makers.trump)
        assertTrue(makers.orderedUp)
        // The turn card stays public knowledge for the whole hand.
        assertEquals(upcard, next.upcard)
        assertTrue(next.upcardTaken)
        assertEquals(state.upcardSuit, next.upcardSuit)
        assertEquals(6, next.hands.getValue(Seat(0)).size)
        assertTrue(upcard in next.hands.getValue(Seat(0)))
        assertEquals(Seat(0), rules.currentActor(next))
    }

    @Test
    fun `four passes turn the up-card down into round 2`() {
        val state = rules.newGame(seed = 1, firstDealer = Seat(0))
        val upcard = state.upcard!!
        val r2 = rules.passToRound2(state)
        // Turned down: dead but still publicly known (never picked up).
        assertEquals(upcard, r2.upcard)
        assertFalse(r2.upcardTaken)
        assertEquals(state.upcardSuit, r2.upcardSuit)
        assertEquals(Seat(1), rules.currentActor(r2))
    }

    @Test
    fun `acting out of turn is rejected`() {
        val state = rules.newGame(seed = 1, firstDealer = Seat(0))
        assertFailsWith<IllegalStateException> { rules.apply(state, Seat(2), EuchreAction.Pass) }
    }

    @Test
    fun `same seed deals the same hand`() {
        assertEquals(rules.newGame(7), rules.newGame(7))
    }
}
