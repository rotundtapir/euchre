// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.monetization.Monetization
import io.github.rotundtapir.cardkit.ui.SoundEffect
import io.github.rotundtapir.cardkit.ui.deal.DealAnimationState
import io.github.rotundtapir.cardkit.ui.deal.DealingHandRow
import io.github.rotundtapir.cardkit.ui.deal.FlyingDealCard
import io.github.rotundtapir.cardkit.ui.deal.dealTimings
import io.github.rotundtapir.cardkit.ui.deal.runDealAnimation
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.HAND_SIZE
import io.github.rotundtapir.euchre.transitions
import kotlinx.coroutines.flow.first

/**
 * The table. Everything the player sees during a game: the score bar, the trump line, the three
 * opponents, the felt, and whichever action panel the engine is currently waiting on.
 *
 * Pacing is signal-driven: the deal animation, the completed-trick hold and the hand-result dialog
 * each raise a signal the ViewModel's bots wait for. Every one of those mechanisms is inert at
 * [AnimationSpeed.OFF] — the connected and e2e suites depend on it.
 */
@Composable
fun GameScreen(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    settings: SettingsControls,
    monetization: Monetization,
    onAction: (EuchreAction) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onResultDismiss: (Int) -> Unit = {},
    onDealAnimationFinish: (Int) -> Unit = {},
    onTrickAcknowledge: (Int, Int) -> Unit = { _, _ -> },
    soundHook: ((SoundEffect) -> Unit)? = null,
) {
    val animationSpeed = settings.animationSpeed
    var sortHand by rememberSaveable { mutableStateOf(settings.sortByDefault) }
    var showSettings by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    // Highest hand number whose result dialog has been dismissed — the NEXT hand's shuffle waits
    // for it, so nothing moves behind the dialog while the player reads it.
    var resultAckedHand by remember { mutableIntStateOf(0) }
    // Whether the FINAL hand's result dialog has been dismissed. resultAckedHand can't tell:
    // between hands the dialog shows under the NEXT hand's number. Keyed on winner so it resets if
    // this composable survives into another game.
    var finalResultAcked by remember(view.winner) { mutableStateOf(false) }

    val dealState = remember { DealAnimationState() }
    dealState.soundHook = soundHook
    val deal = rememberDealGate(view, animationSpeed, dealState, onDealAnimationFinish) { handNumber ->
        snapshotFlow { resultAckedHand }.first { it >= handNumber }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { dealState.overlayOrigin = it.positionInRoot() },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 12.dp),
            ) {
                ScoreBar(
                    view = view,
                    onOpenSettings = { showSettings = true },
                    onMenu = { showLeaveConfirm = true },
                )
                TrumpLine(view, botNames)
                Spacer(Modifier.height(12.dp))
                OpponentsRow(view, botNames, dealState)
                TrickArea(
                    view = view,
                    botNames = botNames,
                    animationSpeed = animationSpeed,
                    dealState = dealState,
                    modifier = Modifier.weight(1f),
                    holdTricks = settings.holdTricks,
                    onTrickAcknowledge = onTrickAcknowledge,
                )
                when {
                    dealState.dealing -> DealingHandRow(
                        cards = if (sortHand) {
                            remember(view.hand, view.trump) { sortedForDisplay(view.hand, view.trump) }
                        } else {
                            view.hand
                        },
                        state = dealState,
                        humanSeat = view.seat,
                        timings = dealTimings(animationSpeed),
                    )
                    // A fresh hand whose shuffle is still held behind the result dialog: keep the
                    // new cards and the bidding buttons off screen until the deal actually runs.
                    animationSpeed != AnimationSpeed.OFF && view.handNumber > deal.dealtHand ->
                        Box(Modifier.fillMaxWidth())
                    else -> ActionArea(
                        view = view,
                        botNames = botNames,
                        sortHand = sortHand,
                        onToggleSort = { sortHand = !sortHand },
                        onAction = onAction,
                    )
                }
                Spacer(Modifier.height(8.dp))
                monetization.BannerSlot(Modifier.fillMaxWidth())
            }
            // The packet currently in flight from the deck to a pile, drawn above everything.
            FlyingDealCard(dealState)
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            inGame = true,
            monetization = monetization,
            onDismiss = { showSettings = false },
        )
    }
    if (showLeaveConfirm) {
        LeaveGameDialog(onConfirm = onExit, onDismiss = { showLeaveConfirm = false })
    }

    // At game end the final hand's breakdown shows first; the score sheet follows once dismissed.
    if (view.winner != null && finalResultAcked) {
        GameOverDialog(
            view = view,
            botNames = botNames,
            // The game's only interstitial moment: once per finished game, on the way out (a no-op
            // that exits immediately in FOSS/web builds, when ads are removed, or before consent).
            onBackToMenu = { monetization.maybeShowInterstitial(onDismissed = onExit) },
        )
    }

    HandResultDialog(
        view = view,
        botNames = botNames,
        onDismiss = {
            resultAckedHand = view.handNumber
            onResultDismiss(view.handNumber)
            if (view.winner != null) finalResultAcked = true
        },
    )
}

/** How far the deal animation has got, as the screen's layout needs to know it. */
private class DealProgress(val dealtHand: Int)

/**
 * Runs the shuffle-and-deal animation once per new hand and reports which hands have been dealt on
 * screen. Cards fly from a centre deck to each seat in Euchre's two-pass packet order, then the
 * turn card lands on the felt, then the human's five flip face up. [awaitResultAck] holds the
 * shuffle until the previous hand's result dialog is gone.
 *
 * At [AnimationSpeed.OFF] none of this runs — the deal state stays DONE and `dealtHand` keeps up
 * with the view, so the hand area is never blanked.
 */
@Composable
private fun rememberDealGate(
    view: EuchrePlayerView,
    animationSpeed: AnimationSpeed,
    dealState: DealAnimationState,
    onDealAnimationFinish: (Int) -> Unit,
    awaitResultAck: suspend (Int) -> Unit,
): DealProgress {
    // rememberUpdatedState so a recomposition that changes the callback identity doesn't leave the
    // running effect holding a stale one.
    val currentFinish by rememberUpdatedState(onDealAnimationFinish)
    val currentAwaitAck by rememberUpdatedState(awaitResultAck)
    // Saveable so an activity recreation doesn't replay a deal that already ran.
    var lastAnimatedHand by rememberSaveable { mutableIntStateOf(0) }
    // Highest hand whose deal has finished showing (or was skipped). While view.handNumber is
    // beyond this the hand area renders nothing, so a hand held behind the result dialog cannot
    // flash its cards before the shuffle.
    var dealtHand by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(view.handNumber) {
        if (animationSpeed == AnimationSpeed.OFF) return@LaunchedEffect
        if (view.handNumber == lastAnimatedHand) {
            // Recreation mid-hand: the deal was already shown (or abandoned) — never leave the hand
            // area blanked waiting for an animation that won't replay.
            dealtHand = maxOf(dealtHand, view.handNumber)
            return@LaunchedEffect
        }
        lastAnimatedHand = view.handNumber
        // Only animate a genuine hand start: a view first seen mid-hand must not replay the deal,
        // just release the pacing signal so play proceeds.
        // Borrow the pacing gates' own hand-start predicate so the two can never drift apart.
        if (!view.transitions.isHandStart || view.currentTrick.isNotEmpty()) {
            dealtHand = maxOf(dealtHand, view.handNumber)
            currentFinish(view.handNumber)
            return@LaunchedEffect
        }
        if (view.lastHandResult != null && view.winner == null) currentAwaitAck(view.handNumber)
        runDealAnimation(
            state = dealState,
            schedule = euchreDealSchedule(view.dealer),
            timings = dealTimings(animationSpeed),
            handSize = HAND_SIZE,
        )
        dealtHand = maxOf(dealtHand, view.handNumber)
        // Release the first bidder: the ViewModel waits on this signal, not a timer, so slow
        // devices can't open the auction mid-deal.
        currentFinish(view.handNumber)
    }
    return DealProgress(dealtHand)
}
