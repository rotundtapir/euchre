// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.teamOf

private const val POINTS_SINGLE = 1
private const val POINTS_MARCH = 2
private const val POINTS_LONE_MARCH = 4
private const val POINTS_EUCHRE = 2
private const val POINTS_LONE_DEFENSE = 4
private const val TRICKS_TO_MAKE = 3

/**
 * Scores a completed hand: makers taking 3–4 tricks score 1; a march (all five) scores 2, or 4
 * alone; euchred makers give the defenders 2, or 4 to a successful lone defender (house rule).
 * Exactly one team scores.
 */
fun scoreEuchreHand(makers: Makers, tricksWon: Map<Seat, Int>): EuchreHandResult {
    val makerTricks = tricksWon.entries
        .filter { teamOf(it.key, TEAM_COUNT) == makers.makerTeam }
        .sumOf { it.value }
    val made = makerTricks >= TRICKS_TO_MAKE
    val defenderTeam = 1 - makers.makerTeam
    val points = when {
        makerTricks == TRICKS_PER_HAND -> if (makers.alone) POINTS_LONE_MARCH else POINTS_MARCH
        made -> POINTS_SINGLE
        makers.loneDefender != null -> POINTS_LONE_DEFENSE
        else -> POINTS_EUCHRE
    }
    val scoringTeam = if (made) makers.makerTeam else defenderTeam
    return EuchreHandResult(
        makers = makers,
        makerTricks = makerTricks,
        made = made,
        teamDeltas = mapOf(scoringTeam to points),
    )
}

/** The winning team index once [scores] reach [WINNING_SCORE], else null. */
fun determineEuchreWinner(scores: Map<Int, Int>): Int? =
    scores.entries.filter { it.value >= WINNING_SCORE }.maxByOrNull { it.value }?.key
