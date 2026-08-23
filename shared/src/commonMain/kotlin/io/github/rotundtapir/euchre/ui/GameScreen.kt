// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.monetization.Monetization
import io.github.rotundtapir.cardkit.net.Emote
import io.github.rotundtapir.cardkit.net.EmoteReceived
import io.github.rotundtapir.cardkit.ui.CardAspectRatio
import io.github.rotundtapir.cardkit.ui.SoundEffect
import io.github.rotundtapir.cardkit.ui.deal.DealAnimationState
import io.github.rotundtapir.cardkit.ui.deal.DealingHandRow
import io.github.rotundtapir.cardkit.ui.deal.FlyingDealCard
import io.github.rotundtapir.cardkit.ui.deal.dealTimings
import io.github.rotundtapir.cardkit.ui.deal.runDealAnimation
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialAnchors
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialPage
import io.github.rotundtapir.cardkit.ui.tutorial.NarrationState
import io.github.rotundtapir.cardkit.ui.tutorial.NarrationToggle
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialPagesDialog
import io.github.rotundtapir.cardkit.ui.tutorial.tutorialTarget
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.HAND_SIZE
import io.github.rotundtapir.euchre.transitions
import io.github.rotundtapir.euchre.ui.tutorial.EuchreTutorialSession
import io.github.rotundtapir.euchre.ui.tutorial.TutorialBubble
import io.github.rotundtapir.euchre.ui.tutorial.narrationUriFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
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
    // Whether a completed trick waits to be tapped away. Decided by the caller, because a tutorial
    // lesson forces it on and the ViewModel's pacing gates must be told the same thing.
    holdTricks: Boolean = false,
    onResultDismiss: (Int) -> Unit = {},
    onDealAnimationFinish: (Int) -> Unit = {},
    onTrickAcknowledge: (Int, Int) -> Unit = { _, _ -> },
    soundHook: ((SoundEffect) -> Unit)? = null,
    // Non-null while a tutorial lesson is running: the board is the same, but only the scripted
    // action is enabled, completed tricks are always held, and the lesson's own dialogs bookend it.
    tutorial: EuchreTutorialSession? = null,
    // Online games override the leave-confirm body — leaving hands the seat to a bot, not a loss.
    leaveConfirmText: String? = null,
    // The server's turn clock, online only: milliseconds left for whoever is to act.
    turnRemainingMillis: Long? = null,
    // Non-null in an online game: adds the emote control to the top bar and shows incoming emotes.
    online: OnlineGameControls? = null,
    /** Null in previews and tests that don't wire audio; the tutorial is then silent. */
    narration: NarrationState? = null,
) {
    val animationSpeed = settings.animationSpeed
    var sortHand by rememberSaveable { mutableStateOf(settings.sortByDefault) }
    var showSettings by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    // Highest hand number whose result dialog has been dismissed — the NEXT hand's shuffle waits
    // for it, so nothing moves behind the dialog while the player reads it.
    var resultAckedHand by remember { mutableIntStateOf(0) }
    // How many SCORED hands have been acknowledged. Not the same thing as the hand number: a hand
    // thrown in (everyone passes twice, no stick-the-dealer) scores nothing, so it carries the
    // previous hand's result forward while handResults stays put.
    var ackedResults by remember { mutableIntStateOf(0) }
    // Whether the FINAL hand's result dialog has been dismissed. resultAckedHand can't tell:
    // between hands the dialog shows under the NEXT hand's number. Keyed on winner so it resets if
    // this composable survives into another game.
    var finalResultAcked by remember(view.winner) { mutableStateOf(false) }
    // Set once the lesson's hand has been scored and its result dialog dismissed.
    var lessonComplete by rememberSaveable { mutableStateOf(false) }
    // Screen rects of the lesson's interaction targets (a button, a card, the felt), for the bubble.
    val tutorialAnchors = if (tutorial != null) remember { TutorialAnchors() } else null
    // The most recent incoming emote, shown briefly as a speech bubble pointing at its sender;
    // seatAnchors records each seat's on-screen position for the bubble to point at.
    val seatAnchors = if (online != null) remember { TutorialAnchors() } else null
    var latestEmote by remember { mutableStateOf<EmoteReceived?>(null) }
    if (online != null) {
        LaunchedEffect(online) {
            online.incomingEmotes.collect { received ->
                latestEmote = received
                delay(EMOTE_TOAST_MILLIS)
                latestEmote = null
            }
        }
    }

    val density = LocalDensity.current

    val dealState = remember { DealAnimationState() }
    dealState.soundHook = soundHook
    // Only a hand whose result dialog is still to be read holds up the next shuffle. A thrown-in
    // hand carries the previous hand's result but shows no dialog for it, so waiting on one would
    // leave the hand area blank for good.
    val resultPending = view.handResults.size > ackedResults && view.winner == null
    val dealtHand = rememberDealGate(view, animationSpeed, dealState, resultPending, onDealAnimationFinish) { hand ->
        snapshotFlow { resultAckedHand }.first { it >= hand }
    }

    // True once this hand has actually been dealt on screen. NOT the same as "no animation running":
    // between GameScreen composing and its deal effect starting there is a gap in which nothing is
    // animating and nothing has been dealt either. Showing the settled table in that gap is what
    // made starting a game flicker — a finished deal for a frame, then a rewind into the shuffle.
    val dealShown = animationSpeed == AnimationSpeed.OFF || view.handNumber <= dealtHand

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { dealState.overlayOrigin = it.positionInRoot() },
        ) {
            // Size the chrome from the height actually available, the way TrickArea already sizes
            // the felt's cards. A tall phone is unchanged; a short window compacts the opponents'
            // row and shrinks the fan rather than letting it fall off the bottom.
            val shortScreen = maxHeight < SHORT_SCREEN_HEIGHT
            // Short AND wider than tall — a phone in landscape, or most browser windows. The
            // opponents move into a side column, handing their whole height back to the felt and
            // the fan. Compacting vertically is not enough: the portrait stack does not fit in a
            // ~390dp-tall viewport at any card size.
            val sideBySide = shortScreen && maxWidth > maxHeight
            val handFraction = if (sideBySide) HAND_HEIGHT_FRACTION_WIDE else HAND_HEIGHT_FRACTION
            val handCardWidth = (maxHeight * handFraction / CardAspectRatio)
                .coerceIn(MIN_HAND_CARD_WIDTH, HAND_CARD_WIDTH)
            // The tallest the hand area has had to be. Keyed on the viewport height because the
            // floor only ever grows: without the key, a floor measured in a tall window would
            // survive a resize to a short one and squeeze the very felt it exists to protect.
            // Android recreates on rotation and would reset it anyway; the web resizes in place and
            // would not — and the web is why this change exists.
            var handAreaFloor by remember(maxHeight) { mutableStateOf(0.dp) }
            // The felt and the player's own half: one definition used by both arrangements, so a
            // change to either cannot silently apply in only one orientation.
            val board: @Composable ColumnScope.() -> Unit = {
                TrickArea(
                    view = view,
                    botNames = botNames,
                    animationSpeed = animationSpeed,
                    dealState = dealState,
                    // The felt may shrink, but never to nothing: a trick has to stay readable once
                    // everything else has taken its share.
                    modifier = Modifier.weight(1f).heightIn(min = MIN_FELT_HEIGHT),
                    holdTricks = holdTricks,
                    onTrickAcknowledge = onTrickAcknowledge,
                    // A lesson's bubble carries the "tap the trick" instruction itself.
                    hideTapHint = tutorial != null,
                    dealShown = dealShown,
                    anchors = tutorialAnchors,
                )
                // The hand area's three states are different heights — the dealing row, the blank
                // held behind a result dialog, and whichever action panel the phase calls for (the
                // bidding ones carry a go-alone toggle the others don't). Since the felt above
                // takes whatever height is left, every one of those changes used to move the felt
                // and rescale every card on it. So the area keeps the tallest height it has needed
                // so far: it grows at most a few times early in a game and then holds still. A
                // measured floor rather than a hard-coded one, so it follows the device's text
                // size and the player's display scaling.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = handAreaFloor)
                        .onSizeChanged { size ->
                            val height = with(density) { size.height.toDp() }
                            if (height > handAreaFloor) handAreaFloor = height
                        },
                    // Anchored to the BOTTOM of the reserved area, so the slack opens above the
                    // panel rather than under it: the hand fan is the last element of every panel,
                    // so bottom-aligning is what keeps the cards themselves still when a prompt
                    // gains or loses a row. (Top-aligning held the felt but slid the hand 96dp up
                    // as bidding ended — caught by TableLayoutStabilityTest on its first run.)
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    when {
                        dealState.dealing -> DealingHandRow(
                            cards = rememberDisplayHand(view, sortHand),
                            state = dealState,
                            humanSeat = view.seat,
                            timings = dealTimings(animationSpeed),
                            // Matches the fan it becomes, or the row would overflow a short screen
                            // and then jump size the moment the deal finished.
                            cardWidth = handCardWidth,
                        )
                        // A fresh hand whose shuffle has not run yet — held behind a result dialog,
                        // or simply not started: keep the cards and the buttons off screen.
                        !dealShown -> Box(Modifier.fillMaxWidth())
                        // Wrapped so the local player's own seat has a rect too: an emote from us
                        // (or a substituted bot in our seat) points at the hand, not at nothing.
                        else -> Box(Modifier.fillMaxWidth().tutorialTarget(seatAnchors, seatAnchor(view.seat))) {
                            ActionArea(
                                view = view,
                                botNames = botNames,
                                sortHand = sortHand,
                                onToggleSort = { sortHand = !sortHand },
                                onAction = onAction,
                                tutorial = tutorial,
                                anchors = tutorialAnchors,
                                cardWidth = handCardWidth,
                            )
                        }
                    }
                }
            }
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
                    trailing = when {
                        online != null -> ({ OnlineBarControls(online, turnRemainingMillis) })
                        narration != null -> ({ NarrationToggle(narration, compact = true) })
                        else -> null
                    },
                )
                if (sideBySide) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.width(SIDE_PANEL_WIDTH)) {
                            // The turn card is only public once the deal has turned it over.
                            TrumpLine(view, botNames, upcardRevealed = dealShown)
                            Spacer(Modifier.height(8.dp))
                            OpponentsColumn(view, botNames, dealState, dealShown, anchors = seatAnchors)
                        }
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) { board() }
                    }
                } else {
                    // The turn card is only public once the deal has actually turned it over.
                    TrumpLine(view, botNames, upcardRevealed = dealShown)
                    Spacer(Modifier.height(12.dp))
                    OpponentsRow(
                        view, botNames, dealState, dealShown,
                        anchors = seatAnchors, compact = shortScreen,
                    )
                    board()
                }
                Spacer(Modifier.height(8.dp))
                monetization.BannerSlot(Modifier.fillMaxWidth())
            }
            // Incoming emote, as a speech bubble pointing at its sender's seat.
            val emote = latestEmote
            if (emote != null && seatAnchors != null) {
                seatAnchors[seatAnchor(emote.seat)]?.let { rect ->
                    EmoteBubble(
                        target = rect,
                        overlayOrigin = dealState.overlayOrigin,
                        text = "${seatLabel(view.seat, botNames, emote.seat)}: ${emoteLabel(emote.emote)}",
                        tailDown = emote.seat == view.seat,
                    )
                }
            }
            // The packet currently in flight from the deck to a pile, drawn above everything.
            FlyingDealCard(dealState)
            if (tutorial != null && tutorialAnchors != null && !dealState.dealing) {
                TutorialBubble(
                    tutorial, view, botNames, tutorialAnchors, dealState.overlayOrigin,
                    narration = narration,
                )
            }
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
        LeaveGameDialog(
            onConfirm = onExit,
            onDismiss = { showLeaveConfirm = false },
            body = leaveConfirmText,
        )
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
            if (tutorial != null) {
                // The lesson's result is deliberately never acknowledged: the next hand's shuffle
                // waits on a signal that now never comes, so the finished board stays put behind
                // the epilogue instead of dealing on.
                lessonComplete = true
            } else {
                ackedResults = view.handResults.size
                resultAckedHand = view.handNumber
                onResultDismiss(view.handNumber)
                if (view.winner != null) finalResultAcked = true
            }
        },
    )

    if (tutorial != null && lessonComplete) {
        TutorialPagesDialog(
            pages = tutorial.lesson.epilogue + TutorialPage("Lesson complete", tutorial.lesson.completion),
            narration = narration,
            narrationUriFor = ::narrationUriFor,
            nextTag = "tutorialEpilogueNext",
            finishLabel = "Next",
            finishTag = "tutorialCompleteContinue",
            onFinish = tutorial.onFinish,
            lastPageTag = "tutorialComplete",
            uniformBodyHeight = true,
        )
    }
}

/**
 * Runs the shuffle-and-deal animation once per new hand and returns the highest hand number whose
 * deal has finished showing (or was skipped) — while the view is beyond it the hand area renders
 * nothing, so a hand held behind the result dialog cannot flash its cards before the shuffle. Cards fly from a centre deck to each seat in Euchre's two-pass packet order, then the
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
    resultPending: Boolean,
    onDealAnimationFinish: (Int) -> Unit,
    awaitResultAck: suspend (Int) -> Unit,
): Int {
    // rememberUpdatedState so a recomposition that changes the callback identity doesn't leave the
    // running effect holding a stale one.
    val currentFinish by rememberUpdatedState(onDealAnimationFinish)
    val currentAwaitAck by rememberUpdatedState(awaitResultAck)
    val currentResultPending by rememberUpdatedState(resultPending)
    // Saveable so an activity recreation doesn't replay a deal that already ran. NO_HAND, not 0:
    // hands are numbered from zero, so a zero sentinel reads as "hand 0 has already been dealt" and
    // the first deal of a game — the one every player sees — is skipped as if it were a recreation.
    var lastAnimatedHand by rememberSaveable { mutableIntStateOf(NO_HAND) }
    var dealtHand by rememberSaveable { mutableIntStateOf(NO_HAND) }
    LaunchedEffect(view.handNumber) {
        if (animationSpeed == AnimationSpeed.OFF) {
            // A hand dealt at OFF is already fully on screen — record it as dealt, or switching
            // animations back on mid-hand blanks the hand and the ActionArea for the rest of the
            // hand: `dealShown` sees handNumber > dealtHand and nothing ever advances it.
            dealtHand = maxOf(dealtHand, view.handNumber)
            lastAnimatedHand = view.handNumber
            return@LaunchedEffect
        }
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
        if (currentResultPending) currentAwaitAck(view.handNumber)
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
    return dealtHand
}

/** The online-game hooks GameScreen needs: incoming emotes to show, and a way to send one. */
@Immutable
class OnlineGameControls(
    val incomingEmotes: SharedFlow<EmoteReceived>,
    val onSendEmote: (Emote) -> Unit,
)

/** The online top-bar cluster: the turn clock while one is running, then the emote picker. */
@Composable
private fun OnlineBarControls(controls: OnlineGameControls, turnRemainingMillis: Long?) {
    turnRemainingMillis?.let { remaining ->
        val seconds = remaining / MILLIS_PER_SECOND
        Text(
            "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.testTag("turnClock"),
        )
    }
    OnlineEmoteButton(controls)
}

/** The emote picker in the top bar: a small button opening a dropdown of the canned phrases. */
@Composable
private fun OnlineEmoteButton(controls: OnlineGameControls) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { open = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.testTag("emoteButton"),
        ) { Text("Emote") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            EMOTE_OPTIONS.forEach { (emote, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        controls.onSendEmote(emote)
                        open = false
                    },
                    modifier = Modifier.testTag("emote:${emote.name}"),
                )
            }
        }
    }
}

private fun emoteLabel(emote: Emote): String =
    EMOTE_OPTIONS.firstOrNull { it.first == emote }?.second ?: emote.name

private val EMOTE_OPTIONS = listOf(
    Emote.WELL_PLAYED to "Well played",
    Emote.NICE_HAND to "Nice hand",
    Emote.OOPS to "Oops",
    Emote.THINKING to "Hmm",
    Emote.HURRY_UP to "Hurry up",
    Emote.GOOD_GAME to "Good game",
)

private const val EMOTE_TOAST_MILLIS = 2500L
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60L

/** Before any hand has been animated. Below every real hand number, which start at zero. */
private const val NO_HAND = -1

/**
 * Below this height the game screen switches to its compact chrome. Chosen just under the shortest
 * portrait phone viewport and well above a landscape phone's, so rotating is what trips it rather
 * than merely owning a small device.
 */
private val SHORT_SCREEN_HEIGHT = 600.dp

/** Share of the screen's height the fan may take, before the floor below applies. */
private const val HAND_HEIGHT_FRACTION = 0.21f

/** Side by side the opponents no longer compete for height, so the fan can have more of it. */
private const val HAND_HEIGHT_FRACTION_WIDE = 0.30f

/** The felt's floor — below this a trick stops being readable, so other things give way first. */
private val MIN_FELT_HEIGHT = 96.dp

/** Width of the landscape side panel: enough for a seat name plus its one-line status. */
private val SIDE_PANEL_WIDTH = 190.dp

/** The fan never shrinks past this: smaller and the pips stop being readable at arm's length. */
private val MIN_HAND_CARD_WIDTH = 48.dp
