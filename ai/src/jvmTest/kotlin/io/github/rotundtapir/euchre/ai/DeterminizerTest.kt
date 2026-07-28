// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.euchre.engine.EUCHRE_SEATS
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.EuchreState
import io.github.rotundtapir.euchre.engine.HAND_SIZE
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterminizerTest {
    private val rules = EuchreRules()

    private fun freshTracker(view: EuchrePlayerView): EuchreSeenTracker =
        EuchreSeenTracker(benny = false).also { it.observe(view) }

    /** One world for [view], as the bot draws it: a fresh tracker, a fresh per-decision setup. */
    private fun EuchreDeterminizer.sampleWorld(view: EuchrePlayerView, random: Random): EuchreState =
        sample(setup(view, freshTracker(view)), random)

    private fun EuchreState.viewOf(seat: Int) = rules.view(this, Seat(seat))

    @Test
    fun `sampled worlds agree with the view's public state and my hand`() {
        val state = rules.newGame(3)
        val view = state.viewOf(1)
        val world = EuchreDeterminizer(false).sampleWorld(view, Random(1))
        assertEquals(view.hand, world.hands.getValue(Seat(1)))
        assertEquals(view.phase, world.phase)
        assertEquals(view.dealer, world.dealer)
        assertEquals(view.upcard, world.upcard)
        assertEquals(view.scores, world.scores)
        EUCHRE_SEATS.forEach {
            assertEquals(HAND_SIZE, world.hands.getValue(it).size, "seat $it")
        }
        // No duplicated cards, and the up-card is nobody's hidden card.
        val dealt = world.hands.values.flatten()
        assertEquals(dealt.size, dealt.toSet().size)
        assertTrue(view.upcard !in world.hands.filterKeys { it != view.dealer }.values.flatten())
    }

    @Test
    fun `a picked-up turn card is credited to the dealer's sampled hand`() {
        val ordered = rules.act(rules.newGame(1, Seat(0)), EuchreAction.OrderUp(alone = false))
        val discarded = rules.act(ordered, EuchreAction.DealerDiscard(ordered.hands.getValue(Seat(0)).first()))
        val view = discarded.viewOf(2) // an opponent of the dealer
        assertTrue(view.upcardTaken)
        repeat(20) { i ->
            val world = EuchreDeterminizer(false).sampleWorld(view, Random(i.toLong()))
            assertTrue(
                view.upcard in world.hands.getValue(Seat(0)),
                "world $i should place the picked-up card with the dealer",
            )
        }
    }

    @Test
    fun `a turned-down card is dealt to nobody`() {
        val r2 = rules.passToRound2(rules.newGame(1, Seat(0)))
        val view = r2.viewOf(2)
        repeat(20) { i ->
            val world = EuchreDeterminizer(false).sampleWorld(view, Random(i.toLong()))
            assertTrue(world.hands.values.flatten().none { it == view.upcard }, "world $i dealt the dead card")
        }
    }

    @Test
    fun `farmers-phase worlds materialize a swappable kitty`() {
        val rules = EuchreRules(farmersHandEnabled = true)
        val state = rules.newGame(rules.findSeed { it.phase == EuchrePhase.FARMERS })
        val farmer = rules.currentActor(state)!!
        val view = rules.view(state, farmer)
        val world = EuchreDeterminizer(false).sampleWorld(view, Random(2))
        assertEquals(state.kitty.size, world.kitty.size)
        // The sampled world must accept a farmers swap without crashing.
        val swap = rules.view(world, farmer).legalActions.first { it is EuchreAction.CallFarmers }
        rules.apply(world, farmer, swap)
    }

    @Test
    fun `sampled worlds replay to the end of the hand`() {
        var state = rules.act(rules.newGame(4, Seat(0)), EuchreAction.OrderUp(alone = false))
        state = rules.act(state, EuchreAction.DealerDiscard(state.hands.getValue(Seat(0)).first()))
        val view = state.viewOf(1)
        val world = EuchreDeterminizer(false).sampleWorld(view, Random(9))
        val bot = EuchreBot()
        val random = Random(1)
        var s = world
        var steps = 0
        while (s.phase == EuchrePhase.PLAY && s.handNumber == world.handNumber) {
            check(steps++ < 100)
            val actor = rules.currentActor(s)!!
            s = rules.apply(s, actor, bot.decide(rules.view(s, actor), random))
        }
        assertTrue(s.handNumber > world.handNumber || s.phase == EuchrePhase.COMPLETE)
    }

    @Test
    fun `same seed samples the same world`() {
        val state = rules.newGame(6)
        val view = state.viewOf(0)
        val d = EuchreDeterminizer(false)
        assertEquals(
            d.sampleWorld(view, Random(5)),
            d.sampleWorld(view, Random(5)),
        )
    }
}
