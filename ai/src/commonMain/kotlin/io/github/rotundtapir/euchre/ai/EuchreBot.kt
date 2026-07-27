// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Strategy
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.SuitedCard
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.core.teamOf
import io.github.rotundtapir.cardkit.core.teammatesOf
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT
import io.github.rotundtapir.euchre.engine.TEAM_COUNT
import kotlin.random.Random

/**
 * A heuristic AI opponent for Euchre. Deliberately simple but sound: it never makes an illegal
 * move and plays sensible (if not expert) Euchre. The [Strategy] interface keeps it swappable for
 * [EuchreAdvancedBot] without touching call sites, and it doubles as that bot's rollout policy —
 * keep it fast and strictly deterministic.
 *
 * Behaviour:
 *  - **Round 1** estimates makeable tricks in the up-card's suit with a positional adjustment
 *    (ordering strengthens the dealer's hand); the dealer evaluates their post-pickup hand.
 *  - **Round 2** names the best-estimating callable suit past a threshold; a stuck dealer calls
 *    their best suit regardless.
 *  - **Alone** on near-lay-down hands holding the top trump; **defend alone** only with real
 *    defensive tricks.
 *  - **Play** wins tricks as cheaply as possible, lets a winning partner be, dumps low otherwise;
 *    as the making team it leads trump from the top, defending it cashes side aces first.
 */
class EuchreBot(private val benny: Boolean = false) : Strategy<EuchrePlayerView, EuchreAction> {

    override fun decide(view: EuchrePlayerView, random: Random): EuchreAction = when (view.phase) {
        EuchrePhase.FARMERS -> decideFarmers(view)
        EuchrePhase.BIDDING_ROUND_1 -> decideRound1(view)
        EuchrePhase.BIDDING_ROUND_2 -> decideRound2(view)
        EuchrePhase.DEALER_DISCARD ->
            EuchreAction.DealerDiscard(chooseDiscard(view.hand, evaluator(checkNotNull(view.trump))))
        EuchrePhase.DEFEND_ALONE -> decideDefend(view)
        EuchrePhase.PLAY -> EuchreAction.PlayCard(choosePlay(view))
        EuchrePhase.COMPLETE -> error("No action at COMPLETE")
    }

    fun evaluator(trump: Suit): TrickEvaluator =
        TrickEvaluator(trump, if (benny) JokerRole.HIGHEST_TRUMP else JokerRole.ABSENT)

    // --- Trick estimation ------------------------------------------------------------------------

    /**
     * Expected tricks for this [hand] with [trump]: sure trump winners near full value, small
     * trumps as length, side aces at a ruff discount, and a ruffing chance for a void with spare
     * trumps. A 5-trick scale: >= ~2.6 is a call, >= ~4.2 is lone territory.
     */
    fun estimateTricks(hand: List<Card>, trump: Suit): Double {
        val eval = evaluator(trump)
        val trumps = hand.filter { eval.isTrump(it) }
        var tricks = trumps.sumOf { trumpValue(it, eval) }
        val side = hand.filterIsInstance<SuitedCard>().filterNot { eval.isTrump(it) }.groupBy { it.suit }
        for (suit in Suit.entries.filter { it != trump }) {
            val inSuit = side[suit].orEmpty()
            if (inSuit.any { it.rank == Rank.ACE }) tricks += 0.6 // strong but ruffable
            if (inSuit.isEmpty() && trumps.size >= 2) tricks += 0.3 // void + spare trumps: a ruff
        }
        return tricks
    }

    private fun trumpValue(card: Card, eval: TrickEvaluator): Double = when {
        card is Joker -> 1.0
        eval.isRightBower(card) -> 1.0
        eval.isLeftBower(card) -> 0.9
        card is SuitedCard && card.rank == Rank.ACE -> 0.9
        card is SuitedCard && card.rank == Rank.KING -> 0.6
        card is SuitedCard && card.rank == Rank.QUEEN -> 0.4
        else -> 0.3 // small trumps win as length once trump is drawn
    }

    private fun loneWorthy(hand: List<Card>, trump: Suit, estimate: Double): Boolean {
        val eval = evaluator(trump)
        return estimate >= LONE_THRESHOLD && hand.any { eval.isRightBower(it) || (it is Joker && benny) }
    }

    // --- Bidding ---------------------------------------------------------------------------------

    private fun decideRound1(view: EuchrePlayerView): EuchreAction {
        if (view.upcard is Joker) return bennyForcedCall(view)
        val trump = checkNotNull(view.upcardSuit)
        if (view.seat == view.dealer) {
            val kept = afterBestDiscard(view.hand + checkNotNull(view.upcard), trump)
            val estimate = estimateTricks(kept, trump)
            return if (estimate >= ORDER_THRESHOLD) {
                EuchreAction.OrderUp(alone = loneWorthy(kept, trump, estimate))
            } else {
                EuchreAction.Pass
            }
        }
        val estimate = estimateTricks(view.hand, trump)
        val dealerIsPartner = view.dealer in teammatesOf(view.seat, PLAYER_COUNT, TEAM_COUNT)
        // Ordering up hands the dealer a trump: cheaper when the dealer is our partner.
        val threshold = ORDER_THRESHOLD + if (dealerIsPartner) -POSITIONAL_ADJUST else POSITIONAL_ADJUST
        return if (estimate >= threshold) {
            EuchreAction.OrderUp(alone = loneWorthy(view.hand, trump, estimate))
        } else {
            EuchreAction.Pass
        }
    }

    /** The dealer's forced call on a turned-up Benny Joker: best suit for the post-pickup hand. */
    private fun bennyForcedCall(view: EuchrePlayerView): EuchreAction {
        val withJoker = view.hand + Joker
        val best = Suit.entries.maxBy { estimateTricks(afterBestDiscard(withJoker, it), it) }
        val kept = afterBestDiscard(withJoker, best)
        val estimate = estimateTricks(kept, best)
        return EuchreAction.CallTrump(best, alone = loneWorthy(kept, best, estimate))
    }

    private fun decideRound2(view: EuchrePlayerView): EuchreAction {
        val callable = view.legalActions.filterIsInstance<EuchreAction.CallTrump>().map { it.suit }.distinct()
        val best = callable.maxByOrNull { estimateTricks(view.hand, it) } ?: return EuchreAction.Pass
        val estimate = estimateTricks(view.hand, best)
        val mustCall = view.legalActions.none { it is EuchreAction.Pass } // stuck dealer
        return if (estimate >= CALL_THRESHOLD || mustCall) {
            EuchreAction.CallTrump(best, alone = loneWorthy(view.hand, best, estimate))
        } else {
            EuchreAction.Pass
        }
    }

    private fun decideDefend(view: EuchrePlayerView): EuchreAction {
        val trump = checkNotNull(view.trump)
        return if (estimateTricks(view.hand, trump) >= DEFEND_ALONE_THRESHOLD) {
            EuchreAction.DefendAlone
        } else {
            EuchreAction.DeclineDefend
        }
    }

    // --- Farmer's hand ---------------------------------------------------------------------------

    /** Always swap — an all-nines-and-tens hand is near-worthless. Keep the best (same-suit) pair. */
    private fun decideFarmers(view: EuchrePlayerView): EuchreAction {
        val hand = view.hand.filterIsInstance<SuitedCard>()
        var keep = hand.take(2)
        var bestScore = -1
        for (i in hand.indices) {
            for (j in i + 1 until hand.size) {
                val score = hand[i].rank.ordinal + hand[j].rank.ordinal +
                    if (hand[i].suit == hand[j].suit) SAME_SUIT_BONUS else 0
                if (score > bestScore) {
                    bestScore = score
                    keep = listOf(hand[i], hand[j])
                }
            }
        }
        return EuchreAction.CallFarmers(view.hand.filterNot { it in keep })
    }

    // --- Dealer discard --------------------------------------------------------------------------

    /** The weakest non-trump card, preferring to complete a void (never a singleton ace). */
    fun chooseDiscard(hand: List<Card>, eval: TrickEvaluator): Card {
        val nonTrump = hand.filterIsInstance<SuitedCard>().filterNot { eval.isTrump(it) }
        if (nonTrump.isEmpty()) return hand.minBy { rawStrength(it, eval) }
        val singletons = nonTrump.groupBy { it.suit }.values
            .filter { it.size == 1 }
            .flatten()
            .filterNot { it.rank == Rank.ACE }
        val candidates = singletons.ifEmpty { nonTrump }
        return candidates.minBy { rawStrength(it, eval) }
    }

    private fun afterBestDiscard(hand: List<Card>, trump: Suit): List<Card> {
        val eval = evaluator(trump)
        return hand - chooseDiscard(hand, eval)
    }

    // --- Play ------------------------------------------------------------------------------------

    /** The card this bot plays given the current [view]. Always one of the view's legal plays. */
    fun choosePlay(view: EuchrePlayerView): Card {
        val eval = evaluator(checkNotNull(view.trump))
        val legal = view.legalActions.filterIsInstance<EuchreAction.PlayCard>().map { it.card }
        check(legal.isNotEmpty()) { "No legal plays available" }
        if (view.currentTrick.isEmpty()) return lead(view, eval, legal)

        val best = view.currentTrick.maxBy { eval.strength(it.card, view.ledSuit) }
        val partnerWinning = best.seat in teammatesOf(view.seat, PLAYER_COUNT, TEAM_COUNT)
        val bestStrength = eval.strength(best.card, view.ledSuit)
        val winners = legal.filter { eval.strength(it, view.ledSuit) > bestStrength }
        return when {
            partnerWinning -> legal.minBy { rawStrength(it, eval) } // let the partner take it
            winners.isNotEmpty() -> winners.minBy { rawStrength(it, eval) } // win as cheaply as possible
            else -> legal.minBy { rawStrength(it, eval) } // can't win: dump the lowest
        }
    }

    private fun lead(view: EuchrePlayerView, eval: TrickEvaluator, legal: List<Card>): Card {
        val makers = checkNotNull(view.makers)
        val trumps = legal.filter { eval.isTrump(it) }
        if (makers.makerTeam == view.myTeam && trumps.isNotEmpty() && shouldDrawTrump(view, eval)) {
            return trumps.maxBy { eval.strength(it, null) } // draw trump from the top
        }
        val sideAces = legal.filter { !eval.isTrump(it) && it is SuitedCard && it.rank == Rank.ACE }
        if (sideAces.isNotEmpty()) return sideAces.first()
        val nonTrump = legal.filterNot { eval.isTrump(it) }
        return nonTrump.ifEmpty { legal }.maxBy { rawStrength(it, eval) }
    }

    /** Draw trump while holding the top trump or trump length. */
    private fun shouldDrawTrump(view: EuchrePlayerView, eval: TrickEvaluator): Boolean {
        val myTrumps = view.hand.filter { eval.isTrump(it) }
        return myTrumps.size >= 2 || myTrumps.any { eval.isRightBower(it) || it is Joker }
    }

    /** A context-free strength for keep/dump decisions: trumps rank above all side cards. */
    internal fun rawStrength(card: Card, eval: TrickEvaluator): Int =
        if (eval.isTrump(card)) eval.strength(card, null)
        else (card as? SuitedCard)?.rank?.ordinal ?: 0

    private companion object {
        const val ORDER_THRESHOLD = 2.6
        const val POSITIONAL_ADJUST = 0.4
        const val CALL_THRESHOLD = 2.8
        const val LONE_THRESHOLD = 4.2
        const val DEFEND_ALONE_THRESHOLD = 3.0
        const val SAME_SUIT_BONUS = 3
    }
}
