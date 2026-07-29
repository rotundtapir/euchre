// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.online

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.core.TrickPlay
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.EUCHRE_SEATS

/**
 * Purely visual projections of the human's own move, shown instantly before the server confirms
 * (see [OnlineGameSession.applyOptimistic]). They deliberately do NOT try to reproduce the engine —
 * only the immediate feedback (the card leaves the hand and lands on the felt, the decision joins
 * the auction log, it stops being our turn). The authoritative server view that arrives a moment
 * later replaces this entirely, so anything not modelled here (trick winners, whose turn is truly
 * next, scoring) is corrected then.
 */

/**
 * Best-effort next seat, only used to make it read as "not your turn" until the server view lands.
 *
 * Walks the *active* seats rather than simply counting one seat round: a lone hand sits a player
 * out, and naming them as to-act would show a seat that is not even holding cards. [activeSeats] is
 * empty until trump is made, and the whole table bids, so the auction falls back to all four.
 */
private fun EuchrePlayerView.nextActor(): Seat {
    val order = activeSeats.ifEmpty { EUCHRE_SEATS }
    val here = order.indexOf(seat)
    return if (here < 0) seat else order[(here + 1) % order.size]
}

/**
 * The evaluator the led suit is derived with. The Joker is admitted as the highest trump
 * unconditionally: with the Benny house rule off it is not in the deck at all, so the role cannot
 * change a real hand — and the redacted view does not carry the house rules to switch on.
 */
private fun EuchrePlayerView.displayEvaluator(): TrickEvaluator =
    TrickEvaluator(trumpSuit = trump, jokerRole = JokerRole.HIGHEST_TRUMP)

fun EuchrePlayerView.withOptimisticPlay(card: Card): EuchrePlayerView {
    val play = TrickPlay(seat, card)
    return copy(
        hand = hand - card,
        currentTrick = currentTrick + play,
        // Leading sets the suit others must follow; the *effective* suit, so a left bower led reads
        // as trump exactly as the engine records it.
        ledSuit = ledSuit ?: displayEvaluator().ledSuitOf(play),
        legalActions = emptyList(),
        toAct = nextActor(),
    )
}

/**
 * A bidding decision — a pass, an order-up, a trump call, a defend-alone answer, a declined
 * farmer's hand: everything whose only visible effect is another line in the auction log.
 */
fun EuchrePlayerView.withOptimisticBid(action: EuchreAction): EuchrePlayerView = copy(
    biddingHistory = biddingHistory + (seat to action),
    legalActions = emptyList(),
    toAct = nextActor(),
)

/** The dealer burying a card after picking the up-card up: it simply leaves the hand. */
fun EuchrePlayerView.withOptimisticDiscard(card: Card): EuchrePlayerView = copy(
    hand = hand - card,
    legalActions = emptyList(),
)

/**
 * Calling a farmer's hand projects nothing but the end of our turn: the three cards swapped in come
 * from the bottom of the kitty, which is hidden information this client cannot predict. Guessing
 * would show cards that are not in the hand — so the felt simply waits for the server's view.
 */
fun EuchrePlayerView.withOptimisticFarmers(): EuchrePlayerView = copy(legalActions = emptyList())
