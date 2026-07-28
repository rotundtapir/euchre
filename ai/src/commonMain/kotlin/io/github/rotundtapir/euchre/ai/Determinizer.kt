// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.cardkit.ai.ConstrainedHandSampler
import io.github.rotundtapir.cardkit.ai.TrickMemory
import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.euchre.engine.EuchreBiddingState
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.EuchreState
import io.github.rotundtapir.euchre.engine.HAND_SIZE
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT
import io.github.rotundtapir.euchre.engine.euchreDeck
import io.github.rotundtapir.euchre.engine.euchreEvaluator
import kotlin.random.Random

/**
 * Accumulates what one bot seat has observed across a hand, beyond what a single view carries
 * (views only expose the current and most recent trick). One instance per bot seat per game.
 */
internal class EuchreSeenTracker(private val benny: Boolean) {
    private var handNumber = -1

    val memory = TrickMemory()

    /** The card this seat itself buried as dealer — hidden from everyone else. */
    var myDiscard: Card? = null
        private set

    /** The cards this seat itself swapped away in a farmer's hand call. */
    var myFarmersDiscards: List<Card> = emptyList()
        private set

    /** Call at the top of every decision with the view being decided on. */
    fun observe(view: EuchrePlayerView) {
        if (view.handNumber != handNumber) {
            handNumber = view.handNumber
            memory.reset()
            myDiscard = null
            myFarmersDiscards = emptyList()
        }
        val trump = view.trump ?: return // nothing on the felt before trump is made
        val eval = euchreEvaluator(trump, benny)
        view.lastTrick?.let { memory.record(it.plays, eval) }
        memory.record(view.currentTrick, eval)
    }

    fun recordMyDiscard(card: Card) {
        myDiscard = card
    }

    fun recordMyFarmers(cards: List<Card>) {
        myFarmersDiscards = cards
    }

    /** Cards whose location this seat knows for certain to be out of the unknown pool. */
    fun knownGone(view: EuchrePlayerView): Set<Card> = buildSet {
        addAll(memory.seenPlays)
        myDiscard?.let(::add)
        addAll(myFarmersDiscards)
        // The turn card is public: face-up, in the dealer's hand, or dead — never in the pool.
        view.upcard?.let(::add)
    }
}

/**
 * Samples full [EuchreState]s consistent with a view plus a tracker's observations, for
 * Monte-Carlo evaluation. Hidden cards are dealt randomly to the other seats (respecting proven
 * voids), a picked-up turn card is credited to the dealer's hidden hand (an approximation — the
 * dealer may in fact have buried it), and the kitty is only materialized in the FARMERS phase,
 * the one phase whose actions read it.
 */
internal class EuchreDeterminizer(private val benny: Boolean) {

    /** The full deck for this table — also the universe for "which cards are still unseen". */
    val deck: List<Card> = euchreDeck(benny)

    private val sampler = ConstrainedHandSampler(deck)
    private val kittySize = deck.size - PLAYER_COUNT * HAND_SIZE - 1

    /**
     * Everything about a decision that every sampled world shares. Built once per decision — a
     * search draws up to a couple of hundred worlds from it, and none of this varies between them.
     */
    class SampleSetup(
        val view: EuchrePlayerView,
        val knownGone: Set<Card>,
        val voids: Map<Seat, Set<Suit>>,
        val handSizes: Map<Seat, Int>,
        val eval: TrickEvaluator?,
        /** True when the picked-up turn card should be credited to the dealer's sampled hand. */
        val dealerGetsUpcard: Boolean,
    )

    /** The invariant half of sampling, for one decision on [view]. */
    fun setup(view: EuchrePlayerView, tracker: EuchreSeenTracker): SampleSetup {
        val upcard = view.upcard
        val dealerGetsUpcard = view.upcardTaken && upcard != null &&
            view.dealer != view.seat && upcard !in tracker.memory.seenPlays &&
            (view.handSizes[view.dealer] ?: 0) > 0
        val handSizes = view.handSizes.toMutableMap()
        if (dealerGetsUpcard) handSizes[view.dealer] = handSizes.getValue(view.dealer) - 1
        return SampleSetup(
            view = view,
            knownGone = tracker.knownGone(view),
            voids = tracker.memory.voids,
            handSizes = handSizes,
            eval = view.trump?.takeIf { view.phase == EuchrePhase.PLAY }?.let { euchreEvaluator(it, benny) },
            dealerGetsUpcard = dealerGetsUpcard,
        )
    }

    /** One sampled world: a state the reducer accepts, agreeing with everything the view shows. */
    fun sample(setup: SampleSetup, random: Random): EuchreState {
        val view = setup.view
        val result = sampler.sample(
            fixedHands = mapOf(view.seat to view.hand),
            handSizes = setup.handSizes,
            knownGone = setup.knownGone,
            voids = setup.voids,
            eval = setup.eval,
            random = random,
        )
        if (setup.dealerGetsUpcard) result.hands.getValue(view.dealer).add(checkNotNull(view.upcard))
        // The reducer only reads the kitty for farmers swaps; leave it empty in other phases.
        val kitty =
            if (view.phase == EuchrePhase.FARMERS) List(kittySize) { result.pool.removeFirst() } else emptyList()
        return reconstruct(view, result.hands, kitty, random)
    }

    /** Copies the public state across verbatim around the sampled [hands] and [kitty]. */
    private fun reconstruct(
        view: EuchrePlayerView,
        hands: Map<Seat, List<Card>>,
        kitty: List<Card>,
        random: Random,
    ): EuchreState = EuchreState(
        // Only consumed if a rollout deals the next hand; drawn from the injected Random so
        // sampled worlds stay reproducible under a fixed seed.
        rngSeed = random.nextLong(),
        handNumber = view.handNumber,
        dealer = view.dealer,
        phase = view.phase,
        hands = hands,
        upcard = view.upcard,
        upcardSuit = view.upcardSuit,
        upcardTaken = view.upcardTaken,
        kitty = kitty,
        bidding = EuchreBiddingState(history = view.biddingHistory, toAct = view.toAct ?: view.seat),
        makers = view.makers,
        activeSeats = view.activeSeats,
        leader = view.leader,
        currentTrick = view.currentTrick,
        ledSuit = view.ledSuit,
        trickNumber = view.trickNumber,
        tricksWon = view.tricksWon,
        lastTrick = view.lastTrick,
        scores = view.scores,
        lastHandResult = view.lastHandResult,
        handResults = view.handResults,
        winner = view.winner,
    )
}
