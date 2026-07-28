// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import io.github.rotundtapir.cardkit.ui.pacing.TableTransitions
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView

/**
 * Projects a [EuchrePlayerView] onto cardkit-ui's pacing/sound seam.
 *
 * A data class (not an anonymous object) so Compose effect keying follows view equality.
 */
data class EuchreTransitions(private val view: EuchrePlayerView) : TableTransitions {
    override val handNumber: Int get() = view.handNumber.asGateHand()
    override val trickNumber: Int get() = view.trickNumber
    override val trickCardCount: Int get() = view.currentTrick.size
    override val handResultCount: Int get() = view.handResults.size

    /**
     * True only at the very top of a hand, before anyone has acted — the deal gate must re-arm per
     * hand and then stay disarmed. A fresh deal opens in round-1 bidding (or the farmer's-hand
     * prompt that precedes it) with an empty decision log; the first action of any kind ends it.
     */
    override val isHandStart: Boolean
        get() = view.biddingHistory.isEmpty() &&
            (view.phase == EuchrePhase.BIDDING_ROUND_1 || view.phase == EuchrePhase.FARMERS)

    override val awaitingHandResultAck: Boolean
        get() = view.lastHandResult != null && view.winner == null
}

/** The pacing/sound projection of this view. */
val EuchrePlayerView.transitions: EuchreTransitions get() = EuchreTransitions(this)

/**
 * An engine hand number as cardkit's gates count them.
 *
 * [TableTransitions.handNumber] is documented as 1-based, and the gates lean on that: their signal
 * state starts at zero and each wait is `signal >= handNumber`. Euchre's engine numbers hands from
 * zero, so passing one straight through makes the very first hand's wait read as already satisfied
 * — the deal gate becomes a no-op and the bots open the auction while the cards are still in the
 * air. Every signal raised for the gates goes through here, so both sides count the same way.
 */
internal fun Int.asGateHand(): Int = this + 1
