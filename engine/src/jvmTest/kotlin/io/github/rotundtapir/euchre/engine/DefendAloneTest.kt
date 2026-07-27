// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DefendAloneTest {
    private val rules = EuchreRules(defendAlone = true)

    /** Dealer 0, seat 1 orders up alone (partner 3 sits out), dealer discards. */
    private fun atDefendChoice(): EuchreState {
        val state = rules.act(rules.newGame(1, Seat(0)), EuchreAction.OrderUp(alone = true))
        return rules.act(state, EuchreAction.DealerDiscard(state.hands.getValue(Seat(0)).first()))
    }

    @Test
    fun `defenders are asked in order from the dealer's left`() {
        val state = atDefendChoice()
        assertEquals(EuchrePhase.DEFEND_ALONE, state.phase)
        // Maker is seat 1 (team 1): defenders are 0 and 2; clockwise from seat 1 -> seat 2 first.
        assertEquals(Seat(2), rules.currentActor(state))
        assertEquals(
            listOf(EuchreAction.DefendAlone, EuchreAction.DeclineDefend),
            rules.legalActions(state, Seat(2)),
        )
    }

    @Test
    fun `both defenders declining starts a normal three-seat hand`() {
        var state = atDefendChoice()
        state = rules.act(state, EuchreAction.DeclineDefend)
        assertEquals(Seat(0), rules.currentActor(state))
        state = rules.act(state, EuchreAction.DeclineDefend)
        assertEquals(EuchrePhase.PLAY, state.phase)
        assertNull(state.makers!!.loneDefender)
        assertEquals(listOf(Seat(0), Seat(1), Seat(2)), state.activeSeats)
    }

    @Test
    fun `defending alone sits the other defender out`() {
        var state = atDefendChoice()
        state = rules.act(state, EuchreAction.DefendAlone) // seat 2 defends alone
        assertEquals(EuchrePhase.PLAY, state.phase)
        assertEquals(Seat(2), state.makers!!.loneDefender)
        // Maker 1 + lone defender 2; seats 0 and 3 sit out. Leader: first active left of dealer.
        assertEquals(listOf(Seat(1), Seat(2)), state.activeSeats)
        assertEquals(Seat(1), state.leader)
    }

    @Test
    fun `no defend-alone question for a partnered maker`() {
        val state = rules.act(rules.newGame(1, Seat(0)), EuchreAction.OrderUp(alone = false))
        val play = rules.act(state, EuchreAction.DealerDiscard(state.hands.getValue(Seat(0)).first()))
        assertEquals(EuchrePhase.PLAY, play.phase)
    }
}
