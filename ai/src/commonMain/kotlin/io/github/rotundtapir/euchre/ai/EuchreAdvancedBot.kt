// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.cardkit.ai.MonteCarloSearch
import io.github.rotundtapir.cardkit.ai.SearchLimits
import io.github.rotundtapir.cardkit.ai.reduceEquivalent
import io.github.rotundtapir.cardkit.ai.rollout
import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.EuchreState
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Tuning knobs for [EuchreAdvancedBot]'s search. The defaults are the production budgets; tests
 * use [timeBudgetEnabled] = false with a small [maxDeterminizations] so a decision is a pure
 * function of (view, tracker state, Random).
 */
data class EuchreSearchConfig(
    /** Wall-clock cap per bidding / defend-alone / farmers decision. */
    val bidBudget: Duration = 2.seconds,
    /** Wall-clock cap for the dealer's discard. */
    val discardBudget: Duration = 1.seconds,
    /** Wall-clock cap per card play. */
    val playBudget: Duration = 1.seconds,
    val maxDeterminizations: Int = 192,
    /**
     * Racing-elimination warm-up, and the floor for bid-family decisions: at least this many
     * worlds are sampled even past the deadline, so a slow single-threaded platform never commits
     * to a contract off a statistically meaningless handful of samples.
     */
    val minDeterminizations: Int = 32,
    val batchSize: Int = 8,
    /** False disables the wall clock entirely: fixed-iteration deterministic mode for tests. */
    val timeBudgetEnabled: Boolean = true,
)

/**
 * A search-based AI for Euchre: determinized flat Monte Carlo over cardkit-ai's [MonteCarloSearch],
 * with [EuchreBot] as the rollout policy and the fallback on any search failure. Euchre's small
 * deck makes this cheap — 5-trick rollouts, few arms (and [reduceEquivalent] collapses most
 * card choices late in a hand).
 *
 * Stateful ([EuchreSeenTracker] remembers tricks the view has forgotten): create one instance per
 * bot seat per game. [decide] is `suspend` and yields between worlds; on the JVM run it on a
 * background dispatcher (see [EuchreAdvancedBotPlayer]).
 */
class EuchreAdvancedBot(
    private val rules: EuchreRules,
    private val config: EuchreSearchConfig = EuchreSearchConfig(),
    private val fallback: EuchreBot = EuchreBot(rules.bennyEnabled),
) {
    private val tracker = EuchreSeenTracker(rules.bennyEnabled)
    private val determinizer = EuchreDeterminizer(rules.bennyEnabled)
    private val search = MonteCarloSearch(
        SearchLimits(config.maxDeterminizations, config.minDeterminizations, config.batchSize, config.timeBudgetEnabled),
    )

    /** The action to take for [view]. Always legal; falls back to [fallback] on any search failure. */
    suspend fun decide(view: EuchrePlayerView, random: Random): EuchreAction {
        tracker.observe(view)
        val searched = try {
            searchDecision(view, random)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught", "SwallowedException") e: Throwable,
        ) {
            null // any search failure must degrade to the heuristic, never crash the game
        }
        val action = searched?.takeIf { it in view.legalActions } ?: fallback.decide(view, random)
        when (action) {
            is EuchreAction.DealerDiscard -> tracker.recordMyDiscard(action.card)
            is EuchreAction.CallFarmers -> tracker.recordMyFarmers(action.discards)
            else -> Unit
        }
        return action
    }

    private suspend fun searchDecision(view: EuchrePlayerView, random: Random): EuchreAction? {
        val (budget, minWorlds) = when (view.phase) {
            EuchrePhase.PLAY -> config.playBudget to 1
            EuchrePhase.DEALER_DISCARD -> config.discardBudget to 1
            // Making (or defending) trump is the high-stakes decision family: keep the floor.
            else -> config.bidBudget to config.minDeterminizations
        }
        // Everything the sampled worlds have in common, computed once for the whole decision.
        val setup = determinizer.setup(view, tracker)
        return search.best(
            arms = arms(view, setup.knownGone),
            budget = budget,
            minWorlds = minWorlds,
            sampleWorld = { determinizer.sample(setup, random) },
            evaluate = { world, arm -> evaluate(world, view, arm, random) },
        )
    }

    /** Candidate actions: everything legal, with play arms collapsed to equivalence classes. */
    private fun arms(view: EuchrePlayerView, knownGone: Set<Card>): List<EuchreAction> {
        if (view.phase != EuchrePhase.PLAY) return view.legalActions
        val legal = view.legalActions.filterIsInstance<EuchreAction.PlayCard>().map { it.card }
        if (legal.size <= 1) return legal.map { EuchreAction.PlayCard(it) }
        val known = view.hand.toSet() + knownGone
        val unseen = determinizer.deck.filterNot { it in known }
        val eval = fallback.evaluator(checkNotNull(view.trump))
        return reduceEquivalent(legal, unseen, eval).map { EuchreAction.PlayCard(it) }
    }

    /** Applies [arm] to [world] and plays the hand out with [fallback] as everyone's policy. */
    private fun evaluate(world: EuchreState, view: EuchrePlayerView, arm: EuchreAction, random: Random): Double {
        val startScores = world.scores
        val startHand = world.handNumber
        val end = rollout(
            rules = rules,
            start = rules.apply(world, view.seat, arm),
            policy = { v, r -> fallback.decide(v, r) },
            random = random,
        ) { it.phase == EuchrePhase.COMPLETE || it.handNumber != startHand }
        fun delta(team: Int) = (end.scores[team] ?: 0) - (startScores[team] ?: 0)
        val winBonus = when (end.winner) {
            null -> 0.0
            view.myTeam -> WIN_BONUS
            else -> -WIN_BONUS
        }
        return (delta(view.myTeam) - delta(view.opponentTeam)).toDouble() + winBonus
    }

    private companion object {
        /** Reward bonus when a rollout ends the whole match — winning dwarfs hand points. */
        const val WIN_BONUS = 100.0
    }
}
