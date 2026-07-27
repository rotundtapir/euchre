// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.GameRules
import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.SuitedCard
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.core.TrickPlay
import io.github.rotundtapir.cardkit.core.deal
import io.github.rotundtapir.cardkit.core.nextSeat
import io.github.rotundtapir.cardkit.core.playOrder
import io.github.rotundtapir.cardkit.core.shuffleWith
import io.github.rotundtapir.cardkit.core.teamOf
import io.github.rotundtapir.cardkit.core.teammatesOf
import kotlin.random.Random

/** The next hand's seed, derived deterministically so one seed drives a whole match. */
fun nextSeed(seed: Long): Long = Random(seed).nextLong()

/**
 * The rules of 4-player Euchre as a pure state machine. House rules are constructor toggles; when
 * a toggle is off, its actions never appear in [legalActions].
 *
 * Standard flow per hand: deal 5 to each seat, turn the top of the kitty up; round 1 — each seat
 * from the dealer's left may order the up-card's suit up (the dealer then picks it up and buries a
 * card) or pass; round 2 (after four passes) — each seat may name any other suit, or pass for a
 * throw-in redeal ([stickTheDealer] instead forces the dealer to call). The maker may go alone,
 * sitting their partner out. First active seat left of the dealer leads trick one.
 */
class EuchreRules(
    val stickTheDealer: Boolean = false,
    val defendAlone: Boolean = false,
    val bennyEnabled: Boolean = false,
    val farmersHandEnabled: Boolean = false,
) : GameRules<EuchreState, EuchreAction, EuchrePlayerView> {

    fun newGame(seed: Long, firstDealer: Seat = Seat(0)): EuchreState =
        dealHand(seed, firstDealer, handNumber = 0, scores = mapOf(0 to 0, 1 to 0))

    override fun currentActor(state: EuchreState): Seat? = when (state.phase) {
        EuchrePhase.COMPLETE -> null
        EuchrePhase.PLAY -> playerToAct(state)
        EuchrePhase.DEALER_DISCARD -> state.dealer
        else -> state.bidding.toAct
    }

    override fun isTerminal(state: EuchreState): Boolean = state.phase == EuchrePhase.COMPLETE

    override fun view(state: EuchreState, seat: Seat): EuchrePlayerView = EuchrePlayerView(
        seat = seat,
        phase = state.phase,
        handNumber = state.handNumber,
        hand = state.hands[seat].orEmpty(),
        handSizes = state.hands.mapValues { it.value.size },
        dealer = state.dealer,
        scores = state.scores,
        toAct = currentActor(state),
        upcard = state.upcard,
        upcardSuit = state.upcardSuit,
        biddingHistory = state.bidding.history.map { (actor, action) ->
            // A farmers swap's cards are buried face-down: visible only to the seat that swapped.
            if (action is EuchreAction.CallFarmers && actor != seat) {
                actor to EuchreAction.CallFarmers(emptyList())
            } else {
                actor to action
            }
        },
        makers = state.makers,
        activeSeats = state.activeSeats,
        leader = state.leader,
        currentTrick = state.currentTrick,
        ledSuit = state.ledSuit,
        trickNumber = state.trickNumber,
        tricksWon = state.tricksWon,
        lastTrick = state.lastTrick,
        legalActions = legalActions(state, seat),
        lastHandResult = state.lastHandResult,
        handResults = state.handResults,
        winner = state.winner,
    )

    override fun legalActions(state: EuchreState, seat: Seat): List<EuchreAction> {
        if (currentActor(state) != seat) return emptyList()
        return when (state.phase) {
            EuchrePhase.FARMERS -> farmersActions(state, seat)
            EuchrePhase.BIDDING_ROUND_1 ->
                if (state.upcard is Joker) bennyForcedCalls() else round1Actions()
            EuchrePhase.BIDDING_ROUND_2 -> round2Actions(state, seat)
            EuchrePhase.DEALER_DISCARD ->
                state.hands.getValue(seat).map { EuchreAction.DealerDiscard(it) }
            EuchrePhase.DEFEND_ALONE -> listOf(EuchreAction.DefendAlone, EuchreAction.DeclineDefend)
            EuchrePhase.PLAY -> legalPlays(state, seat).map { EuchreAction.PlayCard(it) }
            EuchrePhase.COMPLETE -> emptyList()
        }
    }

    override fun apply(state: EuchreState, seat: Seat, action: EuchreAction): EuchreState {
        check(currentActor(state) == seat) { "Not $seat's turn in ${state.phase}" }
        return when (action) {
            is EuchreAction.Pass -> applyPass(state, seat)
            is EuchreAction.OrderUp -> applyOrderUp(state, seat, action)
            is EuchreAction.CallTrump -> applyCallTrump(state, seat, action)
            is EuchreAction.DealerDiscard -> applyDealerDiscard(state, seat, action)
            is EuchreAction.DefendAlone, is EuchreAction.DeclineDefend -> applyDefendChoice(state, seat, action)
            is EuchreAction.CallFarmers, is EuchreAction.DeclineFarmers -> applyFarmers(state, seat, action)
            is EuchreAction.PlayCard -> applyPlay(state, seat, action)
        }
    }

    // --- Dealing -------------------------------------------------------------------------------

    private fun dealHand(
        seed: Long,
        dealer: Seat,
        handNumber: Int,
        scores: Map<Int, Int>,
        handResults: List<EuchreHandResult> = emptyList(),
        lastHandResult: EuchreHandResult? = null,
    ): EuchreState {
        val dealt = deal(euchreDeck(bennyEnabled).shuffleWith(Random(seed)), PLAYER_COUNT, HAND_SIZE)
        val hands = dealt.hands.mapIndexed { i, hand -> Seat(i) to hand }.toMap()
        val upcard = dealt.leftover.first()
        val base = EuchreState(
            rngSeed = seed,
            handNumber = handNumber,
            dealer = dealer,
            phase = EuchrePhase.BIDDING_ROUND_1,
            hands = hands,
            upcard = upcard,
            upcardSuit = (upcard as? SuitedCard)?.suit,
            kitty = dealt.leftover.drop(1),
            bidding = EuchreBiddingState(toAct = nextSeat(dealer, PLAYER_COUNT)),
            scores = scores,
            handResults = handResults,
            lastHandResult = lastHandResult,
        )
        val farmer = firstFarmer(base)
        return when {
            // A turned-up Benny Joker forces the dealer to name trump and take it up — this
            // preempts farmer's hand (the deal never reaches normal round-1 bidding).
            upcard is Joker -> base.copy(bidding = base.bidding.copy(toAct = dealer))
            farmer != null -> base.copy(
                phase = EuchrePhase.FARMERS,
                bidding = base.bidding.copy(toAct = farmer),
            )
            else -> base
        }
    }

    // --- Farmer's hand -------------------------------------------------------------------------

    private fun qualifiesForFarmers(hand: List<Card>): Boolean =
        hand.isNotEmpty() && hand.all { it is SuitedCard && (it.rank == Rank.NINE || it.rank == Rank.TEN) }

    /** Qualifying seats, from the dealer's left, that have not yet called or declined. */
    private fun firstFarmer(state: EuchreState): Seat? {
        if (!farmersHandEnabled) return null
        val acted = state.bidding.history
            .filter { it.second is EuchreAction.CallFarmers || it.second is EuchreAction.DeclineFarmers }
            .map { it.first }
            .toSet()
        return playOrder(nextSeat(state.dealer, PLAYER_COUNT), (0 until PLAYER_COUNT).map(::Seat))
            .firstOrNull { it !in acted && qualifiesForFarmers(state.hands.getValue(it)) }
    }

    private fun farmersActions(state: EuchreState, seat: Seat): List<EuchreAction> {
        val hand = state.hands.getValue(seat)
        val swaps = threeSubsets(hand).map { EuchreAction.CallFarmers(it) }
        return swaps + EuchreAction.DeclineFarmers
    }

    private fun threeSubsets(cards: List<Card>): List<List<Card>> =
        cards.indices.flatMap { i ->
            (i + 1 until cards.size).flatMap { j ->
                (j + 1 until cards.size).map { k -> listOf(cards[i], cards[j], cards[k]) }
            }
        }

    private fun applyFarmers(state: EuchreState, seat: Seat, action: EuchreAction): EuchreState {
        check(state.phase == EuchrePhase.FARMERS) { "No farmer's hand decision in ${state.phase}" }
        check(qualifiesForFarmers(state.hands.getValue(seat))) { "$seat does not qualify for farmer's hand" }
        val swapped = when (action) {
            is EuchreAction.DeclineFarmers -> state
            is EuchreAction.CallFarmers -> {
                val hand = state.hands.getValue(seat)
                require(action.discards.size == FARMERS_SWAP_SIZE) { "Swap exactly $FARMERS_SWAP_SIZE cards" }
                require(action.discards.toSet().size == FARMERS_SWAP_SIZE) { "Duplicate swap cards" }
                require(hand.containsAll(action.discards)) { "Swapped cards must be in hand" }
                check(state.kitty.size >= FARMERS_SWAP_SIZE) { "Kitty too small to swap" }
                val incoming = state.kitty.takeLast(FARMERS_SWAP_SIZE)
                state.copy(
                    hands = state.hands + (seat to (hand - action.discards.toSet() + incoming)),
                    kitty = state.kitty.dropLast(FARMERS_SWAP_SIZE) + action.discards,
                )
            }
            else -> error("Unexpected farmers action $action")
        }
        val recorded = swapped.copy(
            bidding = swapped.bidding.copy(history = swapped.bidding.history + (seat to action)),
        )
        val next = firstFarmer(recorded)
        return if (next != null) {
            recorded.copy(bidding = recorded.bidding.copy(toAct = next))
        } else {
            recorded.copy(
                phase = EuchrePhase.BIDDING_ROUND_1,
                bidding = recorded.bidding.copy(toAct = nextSeat(recorded.dealer, PLAYER_COUNT)),
            )
        }
    }

    // --- Bidding -------------------------------------------------------------------------------

    private fun round1Actions(): List<EuchreAction> = listOf(
        EuchreAction.Pass,
        EuchreAction.OrderUp(alone = false),
        EuchreAction.OrderUp(alone = true),
    )

    /** The dealer's forced call when the Benny Joker is turned up: name any suit, no pass. */
    private fun bennyForcedCalls(): List<EuchreAction> = Suit.entries.flatMap {
        listOf(EuchreAction.CallTrump(it, alone = false), EuchreAction.CallTrump(it, alone = true))
    }

    private fun round2Actions(state: EuchreState, seat: Seat): List<EuchreAction> {
        val callable = Suit.entries.filter { it != state.upcardSuit }.flatMap {
            listOf(EuchreAction.CallTrump(it, alone = false), EuchreAction.CallTrump(it, alone = true))
        }
        val mayPass = !(stickTheDealer && seat == state.dealer)
        return if (mayPass) callable + EuchreAction.Pass else callable
    }

    private fun applyPass(state: EuchreState, seat: Seat): EuchreState {
        val recorded = record(state, seat, EuchreAction.Pass)
        return when (state.phase) {
            EuchrePhase.BIDDING_ROUND_1 -> {
                check(state.upcard !is Joker) { "The dealer must call when the Joker is turned up" }
                if (seat == state.dealer) {
                    // All four passed: turn the up-card down and open round 2.
                    recorded.copy(
                        phase = EuchrePhase.BIDDING_ROUND_2,
                        upcard = null,
                        kitty = recorded.kitty + state.upcard!!,
                        bidding = recorded.bidding.copy(toAct = nextSeat(state.dealer, PLAYER_COUNT)),
                    )
                } else {
                    advanceBidder(recorded, seat)
                }
            }
            EuchrePhase.BIDDING_ROUND_2 -> {
                if (seat == state.dealer) {
                    check(!stickTheDealer) { "Stuck dealer cannot pass" }
                    // Thrown in: redeal under the next dealer, no score change.
                    dealHand(
                        seed = nextSeed(state.rngSeed),
                        dealer = nextSeat(state.dealer, PLAYER_COUNT),
                        handNumber = state.handNumber + 1,
                        scores = state.scores,
                        handResults = state.handResults,
                        lastHandResult = state.lastHandResult,
                    )
                } else {
                    advanceBidder(recorded, seat)
                }
            }
            else -> error("Cannot pass in ${state.phase}")
        }
    }

    private fun applyOrderUp(state: EuchreState, seat: Seat, action: EuchreAction.OrderUp): EuchreState {
        check(state.phase == EuchrePhase.BIDDING_ROUND_1) { "Order up only in round 1" }
        val upcard = checkNotNull(state.upcard) { "No up-card to order" }
        check(upcard !is Joker) { "The Joker up-card is called via CallTrump" }
        val makers = Makers(
            maker = seat,
            trump = checkNotNull(state.upcardSuit),
            orderedUp = true,
            alone = action.alone,
        )
        val recorded = record(state, seat, action)
        val dealerSitsOut = action.alone && state.dealer != seat && partnerOf(seat) == state.dealer
        return if (dealerSitsOut) {
            // The dealer's hand is dead: the up-card is never picked up and nothing is buried.
            afterTrumpMade(
                recorded.copy(upcard = null, kitty = recorded.kitty + upcard, makers = makers),
            )
        } else {
            recorded.copy(
                phase = EuchrePhase.DEALER_DISCARD,
                makers = makers,
                upcard = null,
                hands = recorded.hands + (state.dealer to (recorded.hands.getValue(state.dealer) + upcard)),
                bidding = recorded.bidding.copy(toAct = state.dealer),
            )
        }
    }

    private fun applyCallTrump(state: EuchreState, seat: Seat, action: EuchreAction.CallTrump): EuchreState {
        val recorded = record(state, seat, action)
        return when (state.phase) {
            EuchrePhase.BIDDING_ROUND_1 -> {
                // Only legal as the dealer's forced call on a turned-up Benny Joker.
                check(state.upcard is Joker && seat == state.dealer) { "Round 1 trump is made by ordering up" }
                recorded.copy(
                    phase = EuchrePhase.DEALER_DISCARD,
                    makers = Makers(seat, action.suit, orderedUp = true, alone = action.alone),
                    upcard = null,
                    hands = recorded.hands + (seat to (recorded.hands.getValue(seat) + Joker)),
                    bidding = recorded.bidding.copy(toAct = seat),
                )
            }
            EuchrePhase.BIDDING_ROUND_2 -> {
                check(action.suit != state.upcardSuit) { "The turned-down suit may not be named" }
                afterTrumpMade(
                    recorded.copy(makers = Makers(seat, action.suit, orderedUp = false, alone = action.alone)),
                )
            }
            else -> error("Cannot call trump in ${state.phase}")
        }
    }

    private fun applyDealerDiscard(
        state: EuchreState,
        seat: Seat,
        action: EuchreAction.DealerDiscard,
    ): EuchreState {
        check(state.phase == EuchrePhase.DEALER_DISCARD) { "No discard due" }
        val hand = state.hands.getValue(seat)
        require(action.card in hand) { "Discard must be in hand" }
        return afterTrumpMade(
            state.copy(
                hands = state.hands + (seat to (hand - action.card)),
                kitty = state.kitty + action.card,
            ),
        )
    }

    // --- Defend alone --------------------------------------------------------------------------

    private fun defenders(makers: Makers): List<Seat> =
        (0 until PLAYER_COUNT).map(::Seat).filter { teamOf(it, TEAM_COUNT) != makers.makerTeam }

    private fun afterTrumpMade(state: EuchreState): EuchreState {
        val makers = checkNotNull(state.makers)
        if (defendAlone && makers.alone) {
            val first = playOrder(nextSeat(state.dealer, PLAYER_COUNT), (0 until PLAYER_COUNT).map(::Seat))
                .first { it in defenders(makers) }
            return state.copy(
                phase = EuchrePhase.DEFEND_ALONE,
                bidding = state.bidding.copy(toAct = first),
            )
        }
        return startPlay(state)
    }

    private fun applyDefendChoice(state: EuchreState, seat: Seat, action: EuchreAction): EuchreState {
        check(state.phase == EuchrePhase.DEFEND_ALONE) { "No defend-alone decision in ${state.phase}" }
        val makers = checkNotNull(state.makers)
        val recorded = record(state, seat, action)
        if (action is EuchreAction.DefendAlone) {
            return startPlay(recorded.copy(makers = makers.copy(loneDefender = seat)))
        }
        val asked = recorded.bidding.history
            .filter { it.second is EuchreAction.DefendAlone || it.second is EuchreAction.DeclineDefend }
            .map { it.first }
            .toSet()
        val remaining = defenders(makers).filter { it !in asked }
        return if (remaining.isEmpty()) {
            startPlay(recorded)
        } else {
            recorded.copy(bidding = recorded.bidding.copy(toAct = remaining.first()))
        }
    }

    // --- Play ----------------------------------------------------------------------------------

    private fun evaluator(state: EuchreState): TrickEvaluator = TrickEvaluator(
        trumpSuit = checkNotNull(state.makers).trump,
        jokerRole = if (bennyEnabled) JokerRole.HIGHEST_TRUMP else JokerRole.ABSENT,
    )

    private fun startPlay(state: EuchreState): EuchreState {
        val makers = checkNotNull(state.makers)
        val sittingOut = buildSet {
            if (makers.alone) add(partnerOf(makers.maker))
            makers.loneDefender?.let { lone -> addAll(defenders(makers).filter { it != lone }) }
        }
        val active = (0 until PLAYER_COUNT).map(::Seat).filter { it !in sittingOut }
        val leader = playOrder(nextSeat(state.dealer, PLAYER_COUNT), (0 until PLAYER_COUNT).map(::Seat))
            .first { it in active }
        return state.copy(
            phase = EuchrePhase.PLAY,
            activeSeats = active,
            leader = leader,
            currentTrick = emptyList(),
            ledSuit = null,
            trickNumber = 0,
            tricksWon = active.associateWith { 0 },
        )
    }

    private fun playerToAct(state: EuchreState): Seat =
        playOrder(checkNotNull(state.leader), state.activeSeats)[state.currentTrick.size]

    private fun legalPlays(state: EuchreState, seat: Seat): List<Card> {
        val hand = state.hands[seat].orEmpty()
        if (state.currentTrick.isEmpty()) return hand
        return evaluator(state).legalFollows(hand, state.ledSuit)
    }

    private fun applyPlay(state: EuchreState, seat: Seat, action: EuchreAction.PlayCard): EuchreState {
        check(state.phase == EuchrePhase.PLAY) { "No card play in ${state.phase}" }
        require(action.card in legalPlays(state, seat)) { "Illegal play ${action.card.code}" }
        val eval = evaluator(state)
        val play = TrickPlay(seat, action.card)
        val trick = state.currentTrick + play
        val played = state.copy(
            hands = state.hands + (seat to (state.hands.getValue(seat) - action.card)),
            currentTrick = trick,
            ledSuit = if (state.currentTrick.isEmpty()) eval.ledSuitOf(play) else state.ledSuit,
        )
        if (trick.size < state.activeSeats.size) return played
        return completeTrick(played, trick, eval)
    }

    private fun completeTrick(state: EuchreState, trick: List<TrickPlay>, eval: TrickEvaluator): EuchreState {
        val trickWinner = eval.winner(trick)
        val tricksWon = state.tricksWon + (trickWinner to (state.tricksWon.getValue(trickWinner) + 1))
        val swept = state.copy(
            currentTrick = emptyList(),
            ledSuit = null,
            leader = trickWinner,
            trickNumber = state.trickNumber + 1,
            tricksWon = tricksWon,
            lastTrick = CompletedTrick(trick, trickWinner),
        )
        if (swept.trickNumber < TRICKS_PER_HAND) return swept
        return scoreHand(swept)
    }

    private fun scoreHand(state: EuchreState): EuchreState {
        val result = scoreEuchreHand(checkNotNull(state.makers), state.tricksWon)
        val scores = state.scores.mapValues { (team, score) -> score + (result.teamDeltas[team] ?: 0) }
        val matchWinner = determineEuchreWinner(scores)
        return if (matchWinner != null) {
            state.copy(
                phase = EuchrePhase.COMPLETE,
                scores = scores,
                lastHandResult = result,
                handResults = state.handResults + result,
                winner = matchWinner,
            )
        } else {
            dealHand(
                seed = nextSeed(state.rngSeed),
                dealer = nextSeat(state.dealer, PLAYER_COUNT),
                handNumber = state.handNumber + 1,
                scores = scores,
                handResults = state.handResults + result,
                lastHandResult = result,
            )
        }
    }

    // --- Shared helpers ------------------------------------------------------------------------

    private fun partnerOf(seat: Seat): Seat = teammatesOf(seat, PLAYER_COUNT, TEAM_COUNT).first()

    private fun record(state: EuchreState, seat: Seat, action: EuchreAction): EuchreState =
        state.copy(bidding = state.bidding.copy(history = state.bidding.history + (seat to action)))

    private fun advanceBidder(state: EuchreState, seat: Seat): EuchreState =
        state.copy(bidding = state.bidding.copy(toAct = nextSeat(seat, PLAYER_COUNT)))
}
