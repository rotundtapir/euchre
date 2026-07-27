// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import kotlin.random.Random

/** Applies [action] as whoever is to act. */
fun EuchreRules.act(state: EuchreState, action: EuchreAction): EuchreState =
    apply(state, checkNotNull(currentActor(state)) { "No actor in ${state.phase}" }, action)

/** Passes all four seats out of round 1. */
fun EuchreRules.passToRound2(state: EuchreState): EuchreState {
    var s = state
    repeat(PLAYER_COUNT) { s = act(s, EuchreAction.Pass) }
    check(s.phase == EuchrePhase.BIDDING_ROUND_2)
    return s
}

/** The first seed at or after [from] whose fresh deal satisfies [predicate]. */
fun EuchreRules.findSeed(from: Long = 0, limit: Long = 500_000, predicate: (EuchreState) -> Boolean): Long {
    var seed = from
    while (seed < from + limit) {
        if (predicate(newGame(seed))) return seed
        seed++
    }
    error("No seed in [$from, ${from + limit}) satisfies the predicate")
}

/**
 * Plays a whole match by choosing uniformly random legal actions for every seat. Every step
 * validates that the chosen action came from the view's own legalActions, so a full run doubles
 * as a legality sweep.
 */
fun EuchreRules.randomMatch(seed: Long, actionSeed: Long = seed, maxSteps: Int = 100_000): EuchreState {
    val random = Random(actionSeed)
    var state = newGame(seed)
    var steps = 0
    while (!isTerminal(state)) {
        check(steps++ < maxSteps) { "Match did not terminate within $maxSteps steps" }
        val actor = checkNotNull(currentActor(state))
        val legal = view(state, actor).legalActions
        check(legal.isNotEmpty()) { "Actor $actor has no legal actions in ${state.phase}" }
        state = apply(state, actor, legal.random(random))
    }
    return state
}

fun allSeats(): List<Seat> = (0 until PLAYER_COUNT).map(::Seat)
