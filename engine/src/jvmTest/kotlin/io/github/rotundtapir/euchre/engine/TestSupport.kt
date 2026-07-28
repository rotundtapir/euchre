// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.testing.driveRandomly
import io.github.rotundtapir.cardkit.testing.firstSeedWhere
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

/** The first seed in [seeds] whose fresh deal satisfies [predicate]. */
fun EuchreRules.findSeed(seeds: LongRange = 0L..500_000L, predicate: (EuchreState) -> Boolean): Long =
    firstSeedWhere(seeds) { seed -> predicate(newGame(seed)) }

/**
 * Plays a whole match with uniformly random legal actions. cardkit-testing's driver asserts on
 * every step that an actor exists, that it has legal actions, and that the chosen action came from
 * that seat's own view — so a full run doubles as a legality sweep.
 */
fun EuchreRules.randomMatch(seed: Long, actionSeed: Long = seed, maxSteps: Int = 100_000): EuchreState =
    driveRandomly(this, newGame(seed), Random(actionSeed), maxSteps)
