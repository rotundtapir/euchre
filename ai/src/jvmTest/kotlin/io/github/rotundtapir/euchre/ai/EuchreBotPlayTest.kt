// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickPlay
import io.github.rotundtapir.cardkit.core.of
import io.github.rotundtapir.euchre.engine.EUCHRE_SEATS
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.Makers
import kotlin.test.Test
import kotlin.test.assertEquals

class EuchreBotPlayTest {
    private val bot = EuchreBot()
    private val trump = Suit.SPADES

    private fun playView(
        hand: List<Card>,
        legal: List<Card> = hand,
        trick: List<TrickPlay> = emptyList(),
        ledSuit: Suit? = trick.firstOrNull()?.let { bot.evaluator(trump).ledSuitOf(it) },
        seat: Int = 0,
        maker: Int = 0,
    ) = EuchrePlayerView(
        seat = Seat(seat),
        phase = EuchrePhase.PLAY,
        handNumber = 0,
        hand = hand,
        handSizes = EUCHRE_SEATS.associateWith { hand.size },
        dealer = Seat(3),
        scores = mapOf(0 to 0, 1 to 0),
        toAct = Seat(seat),
        makers = Makers(maker = Seat(maker), trump = trump, orderedUp = true),
        activeSeats = EUCHRE_SEATS,
        leader = Seat(trick.firstOrNull()?.seat?.index ?: seat),
        currentTrick = trick,
        ledSuit = ledSuit,
        legalActions = legal.map { EuchreAction.PlayCard(it) },
    )

    @Test
    fun `wins as cheaply as possible`() {
        val hand = listOf(Rank.NINE of Suit.HEARTS, Rank.QUEEN of Suit.HEARTS, Rank.ACE of Suit.HEARTS)
        val trick = listOf(TrickPlay(Seat(3), Rank.TEN of Suit.HEARTS))
        // The queen beats the ten; the ace is wasted; the nine loses.
        assertEquals(Rank.QUEEN of Suit.HEARTS, bot.choosePlay(playView(hand, trick = trick, seat = 0, maker = 3)))
    }

    @Test
    fun `dumps the lowest when a partner is winning`() {
        val hand = listOf(Rank.NINE of Suit.HEARTS, Rank.ACE of Suit.HEARTS)
        val trick = listOf(TrickPlay(Seat(2), Rank.KING of Suit.HEARTS)) // seat 2 = partner of seat 0
        assertEquals(Rank.NINE of Suit.HEARTS, bot.choosePlay(playView(hand, trick = trick, seat = 0, maker = 2)))
    }

    @Test
    fun `dumps the lowest when it cannot win`() {
        val hand = listOf(Rank.TEN of Suit.HEARTS, Rank.KING of Suit.HEARTS)
        val trick = listOf(TrickPlay(Seat(1), Rank.ACE of Suit.HEARTS))
        assertEquals(Rank.TEN of Suit.HEARTS, bot.choosePlay(playView(hand, trick = trick, seat = 0, maker = 1)))
    }

    @Test
    fun `maker leads trump from the top`() {
        val hand = listOf(
            Rank.JACK of Suit.SPADES, // right bower
            Rank.NINE of Suit.SPADES,
            Rank.KING of Suit.HEARTS,
        )
        assertEquals(Rank.JACK of Suit.SPADES, bot.choosePlay(playView(hand, seat = 0, maker = 0)))
    }

    @Test
    fun `defender leads a side ace before anything else`() {
        val hand = listOf(
            Rank.KING of Suit.SPADES, // a trump, but we're defending
            Rank.ACE of Suit.HEARTS,
            Rank.QUEEN of Suit.DIAMONDS,
        )
        assertEquals(Rank.ACE of Suit.HEARTS, bot.choosePlay(playView(hand, seat = 0, maker = 1)))
    }

    @Test
    fun `the left bower ruffs cheaper than the right`() {
        // Void in hearts, holding both bowers: ruff with the LEFT (cheaper), not the right.
        val hand = listOf(Rank.JACK of Suit.SPADES, Rank.JACK of Suit.CLUBS)
        val trick = listOf(TrickPlay(Seat(1), Rank.ACE of Suit.HEARTS))
        assertEquals(Rank.JACK of Suit.CLUBS, bot.choosePlay(playView(hand, trick = trick, seat = 0, maker = 1)))
    }
}
