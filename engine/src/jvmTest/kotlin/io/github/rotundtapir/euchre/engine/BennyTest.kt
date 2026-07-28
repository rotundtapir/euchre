// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.of
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BennyTest {
    private val rules = EuchreRules(bennyEnabled = true)

    @Test
    fun `a turned-up joker forces the dealer to name trump and take it up`() {
        val seed = rules.findSeed { it.upcard is Joker }
        val state = rules.newGame(seed, Seat(0))
        assertEquals(EuchrePhase.BIDDING_ROUND_1, state.phase)
        assertNull(state.upcardSuit)
        assertEquals(Seat(0), rules.currentActor(state))
        val legal = rules.legalActions(state, Seat(0))
        assertEquals(8, legal.size) // 4 suits x { alone, not }, no pass
        assertTrue(legal.all { it is EuchreAction.CallTrump })

        val called = rules.act(state, EuchreAction.CallTrump(Suit.HEARTS))
        assertEquals(EuchrePhase.DEALER_DISCARD, called.phase)
        val makers = called.makers!!
        assertEquals(Seat(0), makers.maker)
        assertEquals(Suit.HEARTS, makers.trump)
        assertTrue(Joker in called.hands.getValue(Seat(0)))
    }

    @Test
    fun `the joker is the highest trump in play`() {
        // Synthetic two-trick position: seat 1 leads the right bower, seat 2 holds the Joker.
        val makers = Makers(maker = Seat(1), trump = Suit.SPADES, orderedUp = false)
        val state = EuchreState(
            rngSeed = 1,
            handNumber = 0,
            dealer = Seat(0),
            phase = EuchrePhase.PLAY,
            hands = mapOf(
                Seat(0) to listOf(Rank.NINE of Suit.HEARTS),
                Seat(1) to listOf(Rank.JACK of Suit.SPADES),
                Seat(2) to listOf(Joker),
                Seat(3) to listOf(Rank.TEN of Suit.HEARTS),
            ),
            bidding = EuchreBiddingState(toAct = Seat(1)),
            makers = makers,
            activeSeats = EUCHRE_SEATS,
            leader = Seat(1),
            trickNumber = TRICKS_PER_HAND - 1,
            tricksWon = mapOf(Seat(0) to 2, Seat(1) to 2, Seat(2) to 0, Seat(3) to 0),
        )
        var s = rules.act(state, EuchreAction.PlayCard(Rank.JACK of Suit.SPADES))
        // The Joker is effectively a spade: seat 2 must follow with it.
        assertEquals(listOf(EuchreAction.PlayCard(Joker)), rules.legalActions(s, Seat(2)))
        s = rules.act(s, EuchreAction.PlayCard(Joker))
        s = rules.act(s, EuchreAction.PlayCard(Rank.TEN of Suit.HEARTS))
        s = rules.act(s, EuchreAction.PlayCard(Rank.NINE of Suit.HEARTS))
        // The Joker beat the right bower, stealing the last trick: makers (team 1) end on two
        // tricks and are euchred.
        val result = s.lastHandResult!!
        assertEquals(2, result.makerTricks)
        assertEquals(mapOf(0 to 2), result.teamDeltas)
    }
}
