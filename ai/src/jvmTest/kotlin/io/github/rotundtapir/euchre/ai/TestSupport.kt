// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.EuchreState
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT

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
