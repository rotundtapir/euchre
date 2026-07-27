// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HouseRulesTest {

    @Test
    fun `stick the dealer removes the dealer's pass`() {
        val rules = EuchreRules(stickTheDealer = true)
        var state = rules.passToRound2(rules.newGame(1, Seat(0)))
        repeat(3) { state = rules.act(state, EuchreAction.Pass) }
        val dealerActions = rules.legalActions(state, Seat(0))
        assertTrue(dealerActions.none { it is EuchreAction.Pass })
        assertTrue(dealerActions.all { it is EuchreAction.CallTrump })
        assertFailsWith<IllegalStateException> { rules.act(state, EuchreAction.Pass) }
        // The forced call proceeds normally.
        val called = rules.act(state, dealerActions.first())
        assertEquals(EuchrePhase.PLAY, called.phase)
        assertEquals(Seat(0), called.makers!!.maker)
    }

    @Test
    fun `non-dealers may still pass under stick the dealer`() {
        val rules = EuchreRules(stickTheDealer = true)
        val state = rules.passToRound2(rules.newGame(1, Seat(0)))
        assertTrue(EuchreAction.Pass in rules.legalActions(state, Seat(1)))
    }

    @Test
    fun `defend-alone never appears with the toggle off`() {
        val rules = EuchreRules(defendAlone = false)
        val ordered = rules.act(rules.newGame(1, Seat(0)), EuchreAction.OrderUp(alone = true))
        val play = rules.act(ordered, EuchreAction.DealerDiscard(ordered.hands.getValue(Seat(0)).first()))
        // Straight to PLAY: no DEFEND_ALONE phase even though the maker is alone.
        assertEquals(EuchrePhase.PLAY, play.phase)
    }

    @Test
    fun `farmer's hand never triggers with the toggle off`() {
        val withFarmers = EuchreRules(farmersHandEnabled = true)
        val seed = withFarmers.findSeed { it.phase == EuchrePhase.FARMERS }
        val without = EuchreRules(farmersHandEnabled = false)
        assertEquals(EuchrePhase.BIDDING_ROUND_1, without.newGame(seed).phase)
    }
}
