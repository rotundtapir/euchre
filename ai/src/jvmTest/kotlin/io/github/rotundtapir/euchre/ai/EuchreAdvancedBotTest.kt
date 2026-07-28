// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.euchre.engine.EUCHRE_SEATS
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.EuchreState
import io.github.rotundtapir.euchre.engine.WINNING_SCORE
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class EuchreAdvancedBotTest {

    /** Fixed-iteration config: a decision is a pure function of (view, tracker, Random). */
    private fun testConfig(maxWorlds: Int = 8) = EuchreSearchConfig(
        maxDeterminizations = maxWorlds,
        minDeterminizations = 4,
        batchSize = 2,
        timeBudgetEnabled = false,
    )

    private suspend fun playAdvancedMatch(rules: EuchreRules, seed: Long): EuchreState {
        val bots = EUCHRE_SEATS.associateWith { seat ->
            EuchreAdvancedBot(rules, testConfig()) to Random(seed + seat.index)
        }
        var state = rules.newGame(seed)
        var steps = 0
        while (!rules.isTerminal(state)) {
            check(steps++ < 20_000) { "Match did not terminate" }
            val actor = checkNotNull(rules.currentActor(state))
            val (bot, random) = bots.getValue(actor)
            val view = rules.view(state, actor)
            val action = bot.decide(view, random)
            assertTrue(action in view.legalActions, "illegal $action in ${state.phase}")
            state = rules.apply(state, actor, action)
        }
        return state
    }

    @Test
    fun `advanced bots play a full legal match`() = runTest {
        val end = playAdvancedMatch(EuchreRules(stickTheDealer = true), seed = 21)
        val winner = assertNotNull(end.winner)
        assertTrue(end.scores.getValue(winner) >= WINNING_SCORE)
    }

    @Test
    fun `advanced bots handle every house rule`() = runTest {
        val rules = EuchreRules(
            stickTheDealer = true,
            defendAlone = true,
            bennyEnabled = true,
            farmersHandEnabled = true,
        )
        assertNotNull(playAdvancedMatch(rules, seed = 33).winner)
    }

    @Test
    fun `fixed-iteration advanced matches are deterministic`() = runTest {
        val rules = EuchreRules()
        assertEquals(playAdvancedMatch(rules, 55), playAdvancedMatch(rules, 55))
    }

    @Test
    fun `search failure degrades to the heuristic`() = runTest {
        val rules = EuchreRules()
        val bot = EuchreAdvancedBot(rules, testConfig())
        val state = rules.newGame(2)
        val actor = rules.currentActor(state)!!
        // Poison the view: impossible hand sizes make the sampler's pool run dry and throw.
        val view = rules.view(state, actor).copy(
            handSizes = EUCHRE_SEATS.associateWith { 10 },
        )
        val action = bot.decide(view, Random(1))
        assertTrue(action in view.legalActions)
    }

    @Test
    fun `a fresh bidding view yields a legal decision after arm reduction`() = runTest {
        val rules = EuchreRules()
        val bot = EuchreAdvancedBot(rules, testConfig(maxWorlds = 4))
        val state = rules.newGame(8)
        val actor = rules.currentActor(state)!!
        val view = rules.view(state, actor)
        assertTrue(bot.decide(view, Random(3)) in view.legalActions)
    }

    @Test
    fun `advanced bot beats the heuristic over a small match series`() = runTest {
        val rules = EuchreRules()
        var advancedWins = 0
        val series = 6
        repeat(series) { game ->
            val heuristic = EuchreBot()
            val bots = EUCHRE_SEATS.associateWith { seat ->
                if (seat.index % 2 == 0) EuchreAdvancedBot(rules, testConfig(maxWorlds = 16)) else null
            }
            val randoms = EUCHRE_SEATS.associateWith { Random(9000L + game * 10 + it.index) }
            var state = rules.newGame(9000L + game)
            var steps = 0
            while (!rules.isTerminal(state)) {
                check(steps++ < 20_000)
                val actor = rules.currentActor(state)!!
                val view = rules.view(state, actor)
                val advanced = bots.getValue(actor)
                val action = advanced?.decide(view, randoms.getValue(actor))
                    ?: heuristic.decide(view, randoms.getValue(actor))
                state = rules.apply(state, actor, action)
            }
            if (state.winner == 0) advancedWins++
        }
        // Team 0 (advanced) should win a clear majority against the heuristic.
        assertTrue(advancedWins * 2 > series, "advanced won only $advancedWins/$series")
    }
}
