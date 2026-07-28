// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EuchreRulesTest {

    @Test
    fun `a random match reaches a winner with standard rules`() {
        val end = EuchreRules().randomMatch(seed = 42)
        val winner = assertNotNull(end.winner)
        assertTrue(end.scores.getValue(winner) >= WINNING_SCORE)
        assertEquals(EuchrePhase.COMPLETE, end.phase)
        assertTrue(end.handResults.isNotEmpty())
        assertEquals(
            end.scores.getValue(0),
            end.handResults.sumOf { it.teamDeltas[0] ?: 0 },
        )
    }

    @Test
    fun `random matches stay legal with every house rule on`() {
        val rules = EuchreRules(
            stickTheDealer = true,
            defendAlone = true,
            bennyEnabled = true,
            farmersHandEnabled = true,
        )
        // randomMatch validates every chosen action against the view's legalActions.
        repeat(5) { i -> assertNotNull(rules.randomMatch(seed = 100L + i).winner) }
    }

    @Test
    fun `the same seed replays the same match`() {
        val rules = EuchreRules(stickTheDealer = true, defendAlone = true)
        assertEquals(rules.randomMatch(seed = 7), rules.randomMatch(seed = 7))
    }

    @Test
    fun `views never leak hidden information`() {
        val rules = EuchreRules()
        var state = rules.newGame(3)
        var steps = 0
        while (!rules.isTerminal(state) && steps++ < 200) {
            val actor = rules.currentActor(state)!!
            for (seat in EUCHRE_SEATS) {
                val view = rules.view(state, seat)
                assertEquals(state.hands[seat].orEmpty(), view.hand)
                // The view type has no field for other hands or the kitty; check sizes only.
                assertEquals(state.hands.mapValues { it.value.size }, view.handSizes)
                if (seat == actor) {
                    assertTrue(view.isMyTurn)
                    assertTrue(view.legalActions.isNotEmpty())
                } else {
                    assertTrue(view.legalActions.isEmpty())
                }
            }
            state = rules.apply(state, actor, rules.view(state, actor).legalActions.first())
        }
    }

    @Test
    fun `stuck matches cannot happen - every non-terminal state has an actor with actions`() {
        val rules = EuchreRules(defendAlone = true, farmersHandEnabled = true)
        var state = rules.newGame(11, Seat(2))
        var steps = 0
        while (!rules.isTerminal(state)) {
            check(steps++ < 50_000)
            val actor = assertNotNull(rules.currentActor(state), "no actor in ${state.phase}")
            val legal = rules.legalActions(state, actor)
            assertTrue(legal.isNotEmpty(), "no legal actions for $actor in ${state.phase}")
            state = rules.apply(state, actor, legal.last())
        }
    }
}
