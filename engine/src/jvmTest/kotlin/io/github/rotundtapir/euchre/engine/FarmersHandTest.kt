// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.SuitedCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FarmersHandTest {
    private val rules = EuchreRules(farmersHandEnabled = true)

    private fun qualifies(hand: List<io.github.rotundtapir.cardkit.core.Card>): Boolean =
        hand.all { it is SuitedCard && (it.rank == Rank.NINE || it.rank == Rank.TEN) }

    /** A deal with exactly one qualifying seat, so phase transitions are unambiguous. */
    private fun farmersDeal(): EuchreState = rules.newGame(
        rules.findSeed { s ->
            s.phase == EuchrePhase.FARMERS && s.hands.values.count(::qualifies) == 1
        },
    )

    @Test
    fun `a hand of nines and tens may swap three with the kitty`() {
        val state = farmersDeal()
        val farmer = rules.currentActor(state)!!
        val legal = rules.legalActions(state, farmer)
        assertEquals(11, legal.size) // C(5,3) = 10 swaps + decline
        assertTrue(legal.count { it is EuchreAction.DeclineFarmers } == 1)

        val hand = state.hands.getValue(farmer)
        val discards = hand.take(3)
        val bottom = state.kitty.takeLast(3)
        val swapped = rules.apply(state, farmer, EuchreAction.CallFarmers(discards))
        val newHand = swapped.hands.getValue(farmer)
        assertEquals(5, newHand.size)
        assertTrue(newHand.containsAll(bottom))
        assertTrue(discards.none { it in newHand })
        assertTrue(swapped.kitty.containsAll(discards))
        assertEquals(state.kitty.size, swapped.kitty.size)
        assertEquals(EuchrePhase.BIDDING_ROUND_1, swapped.phase)
    }

    @Test
    fun `declining proceeds to bidding`() {
        val state = farmersDeal()
        val farmer = rules.currentActor(state)!!
        val declined = rules.apply(state, farmer, EuchreAction.DeclineFarmers)
        assertEquals(EuchrePhase.BIDDING_ROUND_1, declined.phase)
        assertEquals(state.hands, declined.hands)
        assertEquals(rules.act(declined, EuchreAction.Pass).phase, EuchrePhase.BIDDING_ROUND_1)
    }

    @Test
    fun `a swap of the wrong size is rejected`() {
        val state = farmersDeal()
        val farmer = rules.currentActor(state)!!
        val hand = state.hands.getValue(farmer)
        assertFailsWith<IllegalArgumentException> {
            rules.apply(state, farmer, EuchreAction.CallFarmers(hand.take(2)))
        }
    }

    @Test
    fun `the swapped cards are hidden from other seats`() {
        val state = farmersDeal()
        val farmer = rules.currentActor(state)!!
        val swapped = rules.apply(state, farmer, EuchreAction.CallFarmers(state.hands.getValue(farmer).take(3)))
        val other = EUCHRE_SEATS.first { it != farmer }
        val redacted = rules.view(swapped, other).biddingHistory.single().second
        assertEquals(EuchreAction.CallFarmers(emptyList()), redacted)
        val own = rules.view(swapped, farmer).biddingHistory.single().second
        assertTrue((own as EuchreAction.CallFarmers).discards.size == 3)
    }
}
