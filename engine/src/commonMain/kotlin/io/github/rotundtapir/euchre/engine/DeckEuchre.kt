// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.buildDeck
import io.github.rotundtapir.cardkit.core.extendedRanks
import io.github.rotundtapir.cardkit.core.rangeTo

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
