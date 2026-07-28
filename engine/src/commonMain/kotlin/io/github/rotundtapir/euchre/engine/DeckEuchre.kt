// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.core.buildDeck
import io.github.rotundtapir.cardkit.core.extendedRanks
import io.github.rotundtapir.cardkit.core.nextSeat
import io.github.rotundtapir.cardkit.core.playOrder
import io.github.rotundtapir.cardkit.core.rangeTo
import io.github.rotundtapir.cardkit.core.teamOf
import io.github.rotundtapir.cardkit.core.teammatesOf

/** Players at the table — v0.1.0 is 4-player fixed partnerships only (seats 0&2 vs 1&3). */
const val PLAYER_COUNT = 4

/** Teams: seat index modulo 2. */
const val TEAM_COUNT = 2

const val HAND_SIZE = 5
const val TRICKS_PER_HAND = 5

/** First team to reach this score wins the match. */
const val WINNING_SCORE = 10

/** Cards a farmer's-hand call swaps with the bottom of the kitty. */
const val FARMERS_SWAP_SIZE = 3

/**
 * The Euchre deck: 9, 10, J, Q, K, A of every suit (24 cards), plus the Joker when the Benny
 * house rule is on (25). (`Rank.NINE..Rank.ACE` includes the extended 11–13 ranks used by other
 * cardkit games, hence the subtraction.)
 */
fun euchreDeck(benny: Boolean = false): List<Card> = buildDeck {
    allSuits((Rank.NINE..Rank.ACE) - extendedRanks)
    if (benny) joker()
}

/** Every seat at the table, ascending — the universe the engine and its consumers iterate. */
val EUCHRE_SEATS: List<Seat> = (0 until PLAYER_COUNT).map(::Seat)

/** The seats in the order they act this hand: from the dealer's left, clockwise. */
fun dealOrder(dealer: Seat): List<Seat> = playOrder(nextSeat(dealer, PLAYER_COUNT), EUCHRE_SEATS)

/** [seat]'s partner: with fixed 4-player partnerships there is exactly one. */
fun partnerOf(seat: Seat): Seat = teammatesOf(seat, PLAYER_COUNT, TEAM_COUNT).first()

/** The other side. Euchre has exactly two teams, so "not ours" is a single team. */
fun opposingTeam(team: Int): Int = TEAM_COUNT - 1 - team

/** Tricks taken by [team] this hand, from the per-seat tally. */
fun teamTricks(tricksWon: Map<Seat, Int>, team: Int): Int =
    tricksWon.entries.filter { teamOf(it.key, TEAM_COUNT) == team }.sumOf { it.value }

/**
 * The trick evaluator for a Euchre hand. The Benny house rule is the only thing that changes card
 * ranking, so every consumer — engine, bots, determinizer — derives its evaluator here rather than
 * restating the mapping.
 */
fun euchreEvaluator(trump: Suit?, benny: Boolean): TrickEvaluator =
    TrickEvaluator(trump, if (benny) JokerRole.HIGHEST_TRUMP else JokerRole.ABSENT)
