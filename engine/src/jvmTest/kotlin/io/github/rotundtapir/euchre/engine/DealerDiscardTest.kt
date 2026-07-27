// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DealerDiscardTest {
    private val rules = EuchreRules()

    private fun atDiscard(seed: Long = 1): EuchreState =
        rules.act(rules.newGame(seed, Seat(0)), EuchreAction.OrderUp(alone = false))

    @Test
    fun `the dealer chooses among all six cards`() {
        val state = atDiscard()
        val legal = rules.legalActions(state, Seat(0))
        assertEquals(6, legal.size)
        assertTrue(legal.all { it is EuchreAction.DealerDiscard })
    }

    @Test
    fun `discarding buries the card and starts play`() {
        val state = atDiscard()
        val discard = state.hands.getValue(Seat(0)).first()
        val play = rules.apply(state, Seat(0), EuchreAction.DealerDiscard(discard))
        assertEquals(EuchrePhase.PLAY, play.phase)
        assertEquals(5, play.hands.getValue(Seat(0)).size)
        assertTrue(discard !in play.hands.getValue(Seat(0)))
        assertTrue(discard in play.kitty)
        assertEquals(Seat(1), play.leader)
    }

    @Test
    fun `a card not in hand is rejected`() {
        val state = atDiscard()
        val notHeld = euchreDeck().first { it !in state.hands.getValue(Seat(0)) }
        assertFailsWith<IllegalArgumentException> {
            rules.apply(state, Seat(0), EuchreAction.DealerDiscard(notHeld))
        }
    }
}
