// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickPlay
import io.github.rotundtapir.cardkit.core.teamOf
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Phases of a match. Between hands the engine deals again and returns to [FARMERS] or
 * [BIDDING_ROUND_1]. [FARMERS] and [DEFEND_ALONE] only occur under their house-rule toggles.
 */
enum class EuchrePhase { FARMERS, BIDDING_ROUND_1, BIDDING_ROUND_2, DEALER_DISCARD, DEFEND_ALONE, PLAY, COMPLETE }

/** Who made trump and under what terms. */
@Serializable
data class Makers(
    val maker: Seat,
    val trump: Suit,
    /** True when trump was made in round 1 (the up-card was ordered up / picked up). */
    val orderedUp: Boolean,
    val alone: Boolean = false,
    /** Set when a defender elects to defend alone against a lone maker (house rule). */
    val loneDefender: Seat? = null,
) {
    val makerTeam: Int get() = teamOf(maker, TEAM_COUNT)
}

/**
 * The pre-play decision log and whose turn it is. [history] spans the farmers, bidding and
 * defend-alone phases in order — public information a UI or determinizer can replay.
 */
@Serializable
data class EuchreBiddingState(
    val history: List<Pair<Seat, EuchreAction>> = emptyList(),
    val toAct: Seat,
)

/** The most recently completed trick, kept so UIs can show it (and its winner) between tricks. */
@Serializable
data class CompletedTrick(val plays: List<TrickPlay>, val winner: Seat)

/** The scored outcome of one completed hand. */
@Serializable
data class EuchreHandResult(
    val makers: Makers,
    /** Tricks taken by the maker's team. */
    val makerTricks: Int,
    /** True when the makers took at least three tricks. */
    val made: Boolean,
    /** Points awarded, per team index (exactly one team scores in Euchre). */
    val teamDeltas: Map<Int, Int>,
)

/**
 * The full, authoritative state of a match — a pure value the reducer maps to the next state.
 * Everything is serializable so a server can snapshot and restore games.
 *
 * Hidden information: [hands] (except one's own) and [kitty]. The [upcard] is public. [kitty]
 * accumulates every face-down card that leaves play: the undealt cards, a turned-down up-card,
 * the dealer's discard, and farmers'-hand swaps — the reducer only reads its *bottom* cards for
 * farmers swaps, so AI-sampled worlds may leave it empty in later phases.
 */
@Serializable
data class EuchreState(
    /** Seeds this hand's shuffle; evolves via [nextSeed] each deal, so one seed drives a match. */
    val rngSeed: Long,
    val handNumber: Int,
    val dealer: Seat,
    val phase: EuchrePhase,
    val hands: Map<Seat, List<Card>>,
    /** The face-up turn card during round-1 bidding; null once picked up or turned down. */
    val upcard: Card? = null,
    /**
     * The up-card's suit, remembered after pickup/turn-down: round 2 may not name it, and UIs
     * show it. Null when the Benny Joker was turned up (it has no suit).
     */
    val upcardSuit: Suit? = null,
    val kitty: List<Card> = emptyList(),
    val bidding: EuchreBiddingState,
    val makers: Makers? = null,
    /** Seats playing this hand: all four minus anyone sitting out for a lone call. */
    val activeSeats: List<Seat> = emptyList(),
    val leader: Seat? = null,
    val currentTrick: List<TrickPlay> = emptyList(),
    val ledSuit: Suit? = null,
    /** Completed tricks this hand. */
    val trickNumber: Int = 0,
    val tricksWon: Map<Seat, Int> = emptyMap(),
    val lastTrick: CompletedTrick? = null,
    val scores: Map<Int, Int> = mapOf(0 to 0, 1 to 0),
    val lastHandResult: EuchreHandResult? = null,
    val handResults: List<EuchreHandResult> = emptyList(),
    /** The winning team index once a team reaches [WINNING_SCORE]. */
    val winner: Int? = null,
)

/**
 * A player's move. Serial names are the stable wire vocabulary for future online play — never
 * rename them. Every action is enumerable, so views carry one `legalActions` list.
 */
@Serializable
sealed interface EuchreAction {
    /** Decline to order up (round 1) or to name trump (round 2). */
    @Serializable
    @SerialName("pass")
    data object Pass : EuchreAction

    /** Round 1: order the up-card's suit as trump (the dealer picks the card up). */
    @Serializable
    @SerialName("orderUp")
    data class OrderUp(val alone: Boolean = false) : EuchreAction

    /**
     * Round 2: name trump (any suit but the turned-down one). Also the dealer's forced call when
     * the Benny Joker is turned up.
     */
    @Serializable
    @SerialName("callTrump")
    data class CallTrump(val suit: Suit, val alone: Boolean = false) : EuchreAction

    /** The dealer buries one card after picking up the up-card. */
    @Serializable
    @SerialName("dealerDiscard")
    data class DealerDiscard(val card: Card) : EuchreAction

    /** Defend alone against a lone maker (house rule): the other defender sits out. */
    @Serializable
    @SerialName("defendAlone")
    data object DefendAlone : EuchreAction

    @Serializable
    @SerialName("declineDefend")
    data object DeclineDefend : EuchreAction

    /** Farmer's hand (house rule): swap exactly three cards with the bottom of the kitty. */
    @Serializable
    @SerialName("callFarmers")
    data class CallFarmers(val discards: List<Card>) : EuchreAction

    @Serializable
    @SerialName("declineFarmers")
    data object DeclineFarmers : EuchreAction

    @Serializable
    @SerialName("playCard")
    data class PlayCard(val card: Card) : EuchreAction
}

/**
 * What one seat may see: their own hand plus public information. Never expose other hands or the
 * kitty's contents. Carries everything a determinizer needs to reconstruct a playable state, which
 * is also what makes the game networkable later.
 */
@Serializable
data class EuchrePlayerView(
    val seat: Seat,
    val phase: EuchrePhase,
    val handNumber: Int,
    val hand: List<Card>,
    val handSizes: Map<Seat, Int>,
    val dealer: Seat,
    val scores: Map<Int, Int>,
    val toAct: Seat? = null,
    val upcard: Card? = null,
    val upcardSuit: Suit? = null,
    val biddingHistory: List<Pair<Seat, EuchreAction>> = emptyList(),
    val makers: Makers? = null,
    val activeSeats: List<Seat> = emptyList(),
    val leader: Seat? = null,
    val currentTrick: List<TrickPlay> = emptyList(),
    val ledSuit: Suit? = null,
    val trickNumber: Int = 0,
    val tricksWon: Map<Seat, Int> = emptyMap(),
    val lastTrick: CompletedTrick? = null,
    /** The actions this seat may take right now (empty when it is not their turn). */
    val legalActions: List<EuchreAction> = emptyList(),
    val lastHandResult: EuchreHandResult? = null,
    val handResults: List<EuchreHandResult> = emptyList(),
    val winner: Int? = null,
) {
    val trump: Suit? get() = makers?.trump
    val myTeam: Int get() = teamOf(seat, TEAM_COUNT)
    val isMyTurn: Boolean get() = toAct == seat
}
