// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.of
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.Makers
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The trump line is the only place the turn card's suit is written down, so it is the place a deal
 * can leak it. The engine knows the card the instant it deals; the player does not know it until
 * the animation turns it over, and the line must not run ahead of that.
 */
class TrumpLineTextTest {

    private val botNames = mapOf(Seat(1) to "Ada", Seat(2) to "Bruno", Seat(3) to "Cleo")

    private fun view(
        phase: EuchrePhase = EuchrePhase.BIDDING_ROUND_1,
        makers: Makers? = null,
    ) = EuchrePlayerView(
        seat = Seat(0),
        phase = phase,
        handNumber = 0,
        hand = emptyList(),
        handSizes = emptyMap(),
        dealer = Seat(0),
        scores = mapOf(0 to 0, 1 to 0),
        upcard = Rank.QUEEN of Suit.SPADES,
        upcardSuit = Suit.SPADES,
        makers = makers,
    )

    @Test
    fun `the turn card's suit is withheld until the deal turns it over`() {
        val dealing = trumpLineText(view(), botNames, upcardRevealed = false)
        assertFalse(
            Suit.SPADES.symbol in dealing,
            "the up-card's suit must not be named while it is still face down: was '$dealing'",
        )
        assertEquals("", dealing, "with nothing safe to say the line stays blank, keeping its height")
    }

    @Test
    fun `once revealed the line names the suit on offer`() {
        val revealed = trumpLineText(view(), botNames, upcardRevealed = true)
        assertTrue(Suit.SPADES.symbol in revealed, "was '$revealed'")
        assertTrue("turned up" in revealed, "was '$revealed'")
    }

    @Test
    fun `a turned-down card is described as turned down, still only once revealed`() {
        val phase = EuchrePhase.BIDDING_ROUND_2
        assertEquals("", trumpLineText(view(phase), botNames, upcardRevealed = false))
        assertTrue("turned down" in trumpLineText(view(phase), botNames, upcardRevealed = true))
    }

    @Test
    fun `a made contract is announced whatever the deal is doing`() {
        // Trump being made is public by definition — someone said it out loud — so unlike the turn
        // card it does not wait on the animation.
        val makers = Makers(maker = Seat(1), trump = Suit.HEARTS, orderedUp = true, alone = true)
        val text = trumpLineText(view(EuchrePhase.PLAY, makers), botNames, upcardRevealed = false)
        assertTrue("Ada" in text && Suit.HEARTS.symbol in text && "alone" in text, "was '$text'")
    }
}
