// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.of
import io.github.rotundtapir.cardkit.ui.deal.DealTarget
import io.github.rotundtapir.euchre.engine.HAND_SIZE
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT
import kotlin.test.Test
import kotlin.test.assertEquals

/** The deal animation's packet schedule: cosmetic, but it must add up to the real deal. */
class DealScheduleTest {

    @Test
    fun `every seat receives a full hand and the turn card lands last`() {
        for (dealerIndex in 0 until PLAYER_COUNT) {
            val schedule = euchreDealSchedule(Seat(dealerIndex))
            val perSeat = (0 until PLAYER_COUNT).associate { i ->
                Seat(i) to schedule.filter { it.target == DealTarget.SeatPile(Seat(i)) }.sumOf { it.cards }
            }
            assertEquals(
                (0 until PLAYER_COUNT).associate { Seat(it) to HAND_SIZE },
                perSeat,
                "dealer $dealerIndex: every seat must end on $HAND_SIZE cards",
            )
            assertEquals(UpcardTarget, schedule.last().target, "the turn card is the final packet")
            assertEquals(1, schedule.last().cards)
        }
    }

    @Test
    fun `packets are dealt from the dealer's left in two passes of three and two`() {
        val schedule = euchreDealSchedule(Seat(1))
        val seatPackets = schedule.dropLast(1)
        assertEquals(PLAYER_COUNT * 2, seatPackets.size, "two passes around the table")
        assertEquals(
            listOf(Seat(2), Seat(3), Seat(0), Seat(1)),
            seatPackets.take(PLAYER_COUNT).map { (it.target as DealTarget.SeatPile).seat },
            "the first pass starts at the dealer's left",
        )
        assertEquals(listOf(3, 2, 3, 2), seatPackets.take(PLAYER_COUNT).map { it.cards })
        assertEquals(listOf(2, 3, 2, 3), seatPackets.drop(PLAYER_COUNT).map { it.cards })
    }
}

/** [sortedForDisplay] — the sort toggle's whole value is that it understands bowers. */
class SortedForDisplayTest {

    private fun codes(cards: List<Card>) = cards.map { it.code }

    @Test
    fun `the left bower sorts into trump, not its printed suit`() {
        val hand = listOf(
            Rank.ACE of Suit.CLUBS,
            Rank.JACK of Suit.CLUBS, // left bower when spades are trump
            Rank.NINE of Suit.SPADES,
            Rank.JACK of Suit.SPADES, // right bower
            Rank.KING of Suit.SPADES,
        )
        assertEquals(
            listOf("JS", "JC", "KS", "9S", "AC"),
            codes(sortedForDisplay(hand, Suit.SPADES)),
            "right bower, left bower, then the trump suit high to low, then the plain suits",
        )
    }

    @Test
    fun `with no trump yet the hand groups by suit, strongest first`() {
        val hand = listOf(
            Rank.NINE of Suit.HEARTS,
            Rank.ACE of Suit.SPADES,
            Rank.QUEEN of Suit.HEARTS,
            Rank.TEN of Suit.SPADES,
        )
        // Suits run in alternating colours: ♠ ♥ ♣ ♦.
        assertEquals(listOf("AS", "TS", "QH", "9H"), codes(sortedForDisplay(hand, trump = null)))
    }

    @Test
    fun `the Benny joker leads the hand as the highest trump`() {
        val hand = listOf(Rank.ACE of Suit.HEARTS, Joker, Rank.JACK of Suit.HEARTS)
        assertEquals(listOf("JK", "JH", "AH"), codes(sortedForDisplay(hand, Suit.HEARTS)))
    }

    @Test
    fun `sorting never adds or drops a card`() {
        val hand = listOf(
            Rank.NINE of Suit.DIAMONDS,
            Rank.JACK of Suit.HEARTS,
            Rank.KING of Suit.CLUBS,
            Rank.ACE of Suit.DIAMONDS,
            Rank.TEN of Suit.HEARTS,
        )
        Suit.entries.forEach { trump ->
            assertEquals(hand.toSet(), sortedForDisplay(hand, trump).toSet())
            assertEquals(hand.size, sortedForDisplay(hand, trump).size)
        }
    }
}
