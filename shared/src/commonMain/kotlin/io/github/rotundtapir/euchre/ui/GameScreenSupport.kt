// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.ui.deal.DealPacket
import io.github.rotundtapir.cardkit.ui.deal.DealTarget
import io.github.rotundtapir.cardkit.ui.felt.OpponentTeamColors
import io.github.rotundtapir.cardkit.ui.felt.PartnerHighlight
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.dealOrder

/** The felt anchor key of the up-card / kitty stub the deal's last packet flies to. */
const val UPCARD_ANCHOR = "upcard"

/** The centre pile the deal schedule turns the up-card onto. */
val UpcardTarget = DealTarget.Center(UPCARD_ANCHOR)

/**
 * How a seat is named on screen: "You" for [humanSeat], otherwise its bot's name. Takes the seat
 * rather than the view so the end-of-game dialogs, which outlive any single view, can use it too.
 */
fun seatLabel(humanSeat: Seat, botNames: Map<Seat, String>, seat: Seat): String =
    if (seat == humanSeat) "You" else botNames[seat] ?: "Seat ${seat.index}"

/**
 * The colour a [seat]'s name is drawn in: your own side in amber, the opposing side in the first
 * of cardkit's felt-readable opponent hues. Euchre only ever has two teams, so the palette can
 * never run out.
 */
fun teamColor(view: EuchrePlayerView, seat: Seat): Color =
    if (view.isMyTeam(seat)) PartnerHighlight else OpponentTeamColors.first()

/** Clickable only while [enabled] — a factory, since conditional `.then` chains crash AGP lint. */
fun Modifier.tappableWhen(enabled: Boolean, onTap: () -> Unit): Modifier =
    if (enabled) this.clickable(onClick = onTap) else this

/**
 * The trick evaluator a UI should rank cards with. The Joker is admitted as the highest trump
 * unconditionally: with the Benny house rule off it is not in the deck at all, so the role can
 * never change a real hand's order, and this keeps the helper free of a house-rule parameter the
 * redacted view does not carry.
 */
private fun displayEvaluator(trump: Suit?): TrickEvaluator =
    TrickEvaluator(trumpSuit = trump, jokerRole = JokerRole.HIGHEST_TRUMP)

/**
 * Hand order for display: trumps first (Benny, right bower, left bower, then A K Q 10 9), then the
 * remaining suits in alternating colours, each strongest first. Bower-aware — the left bower sorts
 * into the trump block, not its printed suit, which is the whole point of offering the toggle.
 */
/**
 * The human's hand as it should be drawn, memoized: the sort's inputs only change on a new hand, a
 * card played, or trump being made, but the enclosing screen recomposes on every engine transition.
 */
@Composable
fun rememberDisplayHand(view: EuchrePlayerView, sorted: Boolean): List<Card> =
    if (sorted) remember(view.hand, view.trump) { sortedForDisplay(view.hand, view.trump) } else view.hand

/**
 * True when a completed trick is sitting on the felt waiting to be acknowledged, as far as the
 * *view* can tell. Presentation decides whether it is actually held there (the hold setting, the
 * animation speed, whether a deal is running) — this is the half both the felt and the tutorial
 * bubble must agree on.
 */
fun EuchrePlayerView.hasClosedTrick(): Boolean =
    phase == EuchrePhase.PLAY && currentTrick.isEmpty() && lastTrick != null && !isMyTurn

fun sortedForDisplay(hand: List<Card>, trump: Suit?): List<Card> {
    val eval = displayEvaluator(trump)
    val suitOrder = listOf(Suit.SPADES, Suit.HEARTS, Suit.CLUBS, Suit.DIAMONDS)
    return hand.sortedWith(
        compareBy(
            { card -> if (eval.isTrump(card)) 0 else 1 + suitOrder.indexOf(eval.effectiveSuit(card)) },
            { card -> -eval.strength(card, eval.effectiveSuit(card)) },
        ),
    )
}

/**
 * Euchre's deal, as the animation's packet schedule.
 *
 * Real Euchre deals five each in two passes of mixed packets. The convention chosen here (and kept
 * consistent, since the animation is purely cosmetic — the engine has already dealt): starting at
 * the dealer's left, the first pass gives 3-2-3-2 and the second pass each seat's complement
 * 2-3-2-3, so every seat ends on five. The top of the remaining stock is then turned up onto the
 * felt as the up-card.
 */
fun euchreDealSchedule(dealer: Seat): List<DealPacket> {
    val order = dealOrder(dealer)
    fun pass(firstPacket: Int) = order.mapIndexed { i, seat ->
        DealPacket(DealTarget.SeatPile(seat), if (i % 2 == 0) firstPacket else FULL_PACKET + SHORT_PACKET - firstPacket)
    }
    return pass(FULL_PACKET) + pass(SHORT_PACKET) + DealPacket(UpcardTarget, 1)
}

private const val FULL_PACKET = 3
private const val SHORT_PACKET = 2
