// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat

/** Tricks the makers must take to make their contract. */
const val TRICKS_TO_MAKE = 3

/**
 * How a hand ended, as the one classification both the score and any description of it derive from.
 * Ordered from the makers' best result to their worst.
 */
enum class EuchreOutcome(val points: Int) {
    /** All five tricks, alone: the maximum. */
    LONE_MARCH(4),

    /** All five tricks. */
    MARCH(2),

    /** Three or four tricks: the contract, made. */
    MADE(1),

    /** Set by a lone defender (house rule). */
    EUCHRED_ALONE(4),

    /** Set: the defenders score. */
    EUCHRED(2),
    ;

    /** True when the makers' team is the one that scores [points]. */
    val made: Boolean get() = this == LONE_MARCH || this == MARCH || this == MADE
}

/**
 * Classifies a completed hand: makers taking 3–4 tricks make it; all five is a march, doubled when
 * alone; falling short is a euchre, worth double again to a successful lone defender (house rule).
 */
fun euchreOutcome(makers: Makers, makerTricks: Int): EuchreOutcome = when {
    makerTricks == TRICKS_PER_HAND -> if (makers.alone) EuchreOutcome.LONE_MARCH else EuchreOutcome.MARCH
    makerTricks >= TRICKS_TO_MAKE -> EuchreOutcome.MADE
    makers.loneDefender != null -> EuchreOutcome.EUCHRED_ALONE
    else -> EuchreOutcome.EUCHRED
}

/** Scores a completed hand. Exactly one team scores. */
fun scoreEuchreHand(makers: Makers, tricksWon: Map<Seat, Int>): EuchreHandResult {
    val makerTricks = teamTricks(tricksWon, makers.makerTeam)
    val outcome = euchreOutcome(makers, makerTricks)
    val scoringTeam = if (outcome.made) makers.makerTeam else opposingTeam(makers.makerTeam)
    return EuchreHandResult(
        makers = makers,
        makerTricks = makerTricks,
        made = outcome.made,
        teamDeltas = mapOf(scoringTeam to outcome.points),
    )
}

/** The winning team index once [scores] reach [WINNING_SCORE], else null. */
fun determineEuchreWinner(scores: Map<Int, Int>): Int? =
    scores.entries.filter { it.value >= WINNING_SCORE }.maxByOrNull { it.value }?.key
