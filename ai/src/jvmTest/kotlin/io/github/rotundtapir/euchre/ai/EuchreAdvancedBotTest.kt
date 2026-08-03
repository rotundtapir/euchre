// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.euchre.engine.EUCHRE_SEATS
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.EuchreState
import io.github.rotundtapir.euchre.engine.WINNING_SCORE
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
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
    fun `wall-clock budget bounds a decision`() = runTest {
        val rules = EuchreRules()
        val config = EuchreSearchConfig(
            bidBudget = 200.milliseconds,
            maxDeterminizations = Int.MAX_VALUE,
            minDeterminizations = 1, // floor satisfied immediately: the clock is in charge...
            batchSize = Int.MAX_VALUE, // ...and racing never fires (worlds % batch is never 0)
        )
        val bot = EuchreAdvancedBot(rules, config)
        val state = rules.newGame(5)
        val opener = rules.currentActor(state)!!
        val view = rules.view(state, opener)
        val start = TimeSource.Monotonic.markNow()
        val action = bot.decide(view, Random(1))
        val elapsed = start.elapsedNow()
        assertTrue(action in view.legalActions)
        // Generous CI margin; catches a runaway search that ignores its deadline.
        assertTrue(elapsed < 2.seconds, "bidding took $elapsed against a 200ms budget")
    }

    @Test
    fun `bidding samples the floor even when the budget has already expired`() = runTest {
        val rules = EuchreRules()
        val state = rules.newGame(5)
        val opener = rules.currentActor(state)!!
        val view = rules.view(state, opener)

        // A zero budget with a 16-world floor must behave exactly like a fixed 16-world search:
        // same worlds, same racing points, same Random stream => the identical action.
        val floored = EuchreSearchConfig(
            bidBudget = Duration.ZERO,
            maxDeterminizations = Int.MAX_VALUE,
            minDeterminizations = 16,
        )
        val fixed = floored.copy(maxDeterminizations = 16, timeBudgetEnabled = false)
        val flooredAction = EuchreAdvancedBot(rules, floored).decide(view, Random(3))
        val fixedAction = EuchreAdvancedBot(rules, fixed).decide(view, Random(3))
        assertEquals(fixedAction, flooredAction)
    }

    @Test
    fun `a forced play returns without sampling`() = runTest {
        // Drive a real hand with the heuristic until a seat must follow suit with a single card.
        // Sampling is uncapped and the wall clock disabled, so only the single-arm early exit can
        // answer that view — anything else would grind through Int.MAX_VALUE worlds.
        val rules = EuchreRules()
        val heuristic = EuchreBot()
        var state = rules.newGame(11)
        var steps = 0
        while (!rules.isTerminal(state)) {
            check(steps++ < 20_000) { "no forced play found" }
            val actor = rules.currentActor(state)!!
            val view = rules.view(state, actor)
            val plays = view.legalActions.filterIsInstance<EuchreAction.PlayCard>()
            if (view.phase == EuchrePhase.PLAY && view.legalActions.size == 1 && plays.size == 1) {
                val uncapped = EuchreSearchConfig(
                    maxDeterminizations = Int.MAX_VALUE,
                    timeBudgetEnabled = false,
                )
                val action = EuchreAdvancedBot(rules, uncapped).decide(view, Random(1))
                assertEquals(plays.single(), action)
                return@runTest
            }
            state = rules.apply(state, actor, heuristic.decide(view, Random(steps.toLong())))
        }
        error("match ended without a forced play; pick another seed")
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
