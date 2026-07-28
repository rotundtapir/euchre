// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.TrickPlay
import io.github.rotundtapir.cardkit.ui.CardAspectRatio
import io.github.rotundtapir.cardkit.ui.CardBack
import io.github.rotundtapir.cardkit.ui.PlayingCard
import io.github.rotundtapir.cardkit.ui.SettingsIcon
import io.github.rotundtapir.cardkit.ui.SuitText
import io.github.rotundtapir.cardkit.ui.cardFaceShape
import io.github.rotundtapir.cardkit.ui.clickableWhen
import io.github.rotundtapir.cardkit.ui.deal.DealAnimationState
import io.github.rotundtapir.cardkit.ui.deal.OpponentPile
import io.github.rotundtapir.cardkit.ui.deal.ShufflingDeck
import io.github.rotundtapir.cardkit.ui.deal.dealAnchor
import io.github.rotundtapir.cardkit.ui.felt.CardSurfaceWhite
import io.github.rotundtapir.cardkit.ui.felt.NeutralInkOnCardSurface
import io.github.rotundtapir.cardkit.ui.felt.OnBackgroundIconButton
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialAnchors
import io.github.rotundtapir.cardkit.ui.tutorial.tutorialTarget
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT
import io.github.rotundtapir.euchre.engine.TRICKS_PER_HAND
import io.github.rotundtapir.euchre.engine.WINNING_SCORE
import io.github.rotundtapir.euchre.engine.dealOrder
import io.github.rotundtapir.euchre.engine.teamTricks
import io.github.rotundtapir.euchre.ui.tutorial.TRICK_ANCHOR

/**
 * The top bar: both teams' match scores (Euchre plays to [WINNING_SCORE]) with this hand's trick
 * counts beside them, then the settings cog and the leave-game menu.
 */
@Composable
fun ScoreBar(
    view: EuchrePlayerView,
    onOpenSettings: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val myTeam = view.myTeam
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamScore("Us", view, myTeam)
        TeamScore("Them", view, view.opponentTeam)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OnBackgroundIconButton(
                imageVector = SettingsIcon,
                contentDescription = "Settings",
                onClick = onOpenSettings,
                modifier = Modifier.testTag("gameSettingsButton"),
            )
            TextButton(
                onClick = onMenu,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.testTag("menuButton"),
            ) { Text("Menu") }
        }
    }
}

/** One side of the score bar: match points over the tricks that side has taken this hand. */
@Composable
private fun TeamScore(label: String, view: EuchrePlayerView, team: Int) {
    val tricks = teamTricks(view.tricksWon, team)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$label: ${view.scores[team] ?: 0}/$WINNING_SCORE", fontWeight = FontWeight.Bold)
        Text(
            "tricks $tricks/$TRICKS_PER_HAND",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
        )
    }
}

/**
 * The one-line summary of where the hand stands: which suit is on offer during the auction, and
 * once trump is made, who made it and whether they are playing alone.
 */
@Composable
fun TrumpLine(view: EuchrePlayerView, botNames: Map<Seat, String>, modifier: Modifier = Modifier) {
    val makers = view.makers
    val upcardName = view.upcardSuit?.symbol ?: view.upcard?.label ?: ""
    val text = when {
        makers != null -> buildString {
            append("Trump: ${makers.trump.symbol} · maker: ${seatLabel(view.seat, botNames, makers.maker)}")
            if (makers.alone) append(" (alone)")
            makers.loneDefender?.let { append(" · ${seatLabel(view.seat, botNames, it)} defends alone") }
        }
        view.phase == EuchrePhase.BIDDING_ROUND_2 -> "Bidding — $upcardName turned down"
        view.phase == EuchrePhase.BIDDING_ROUND_1 || view.phase == EuchrePhase.FARMERS ->
            "Bidding — $upcardName turned up"
        else -> ""
    }
    if (text.isBlank()) {
        // Keep the row's height stable between phases so the felt below never jumps.
        Text("", modifier = modifier)
    } else {
        // A card-white pill: the line carries suit symbols whose black glyphs sink into the felt.
        Surface(
            shape = RoundedCornerShape(50),
            color = CardSurfaceWhite,
            contentColor = NeutralInkOnCardSurface,
            modifier = modifier,
        ) {
            SuitText(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
        }
    }
}

/**
 * The three seats across the table, clockwise from the local player — so the partner lands opposite
 * (in the middle) and the two opponents flank it, matching the turn order shown on the felt.
 */
@Composable
fun OpponentsRow(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    dealState: DealAnimationState,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        for (offset in 1 until PLAYER_COUNT) {
            OpponentStatus(
                view = view,
                botNames = botNames,
                seat = Seat((view.seat.index + offset) % PLAYER_COUNT),
                dealState = dealState,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OpponentStatus(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    seat: Seat,
    dealState: DealAnimationState,
    modifier: Modifier = Modifier,
) {
    // A seat is only "sitting out" once the hand's active set is known; before then everyone plays.
    val sittingOut = view.activeSeats.isNotEmpty() && seat !in view.activeSeats
    val isPartner = view.isMyTeam(seat)
    val nameColor = teamColor(view, seat)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            seatLabel(view.seat, botNames, seat) + if (seat == view.dealer) " (D)" else "",
            color = nameColor,
            fontWeight = if (view.toAct == seat || isPartner) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (isPartner) {
            Text("(partner)", style = MaterialTheme.typography.labelSmall, color = nameColor)
        }
        if (sittingOut) {
            Text("(sitting out)", style = MaterialTheme.typography.labelSmall)
        } else {
            OpponentPile(seat = seat, state = dealState, width = 44.dp, handSize = view.handSizes[seat] ?: 0)
        }
        Text("tricks: ${view.tricksWon[seat] ?: 0}", style = MaterialTheme.typography.labelSmall)
        BiddingStatus(view, seat)
    }
}

/** What this seat did in the auction, kept on screen through the bidding phases. */
@Composable
private fun BiddingStatus(view: EuchrePlayerView, seat: Seat) {
    if (view.makers != null || view.phase == EuchrePhase.COMPLETE) return
    val last = view.biddingHistory.lastOrNull { it.first == seat }?.second
    SuitText(
        when (last) {
            null -> "—"
            is EuchreAction.Pass -> "passed"
            is EuchreAction.OrderUp -> "ordered up"
            is EuchreAction.CallTrump -> "called ${last.suit.symbol}"
            is EuchreAction.CallFarmers -> "swapped 3"
            is EuchreAction.DeclineFarmers -> "stood pat"
            else -> "—"
        },
        style = MaterialTheme.typography.labelSmall,
    )
}

/**
 * The felt: the table centre where the deal, the up-card and the current trick live.
 *
 * With "Hold completed tricks" on, a finished trick stays put until tapped away; the tap raises the
 * pacing signal that releases the next leader. Like all pacing this is inert at
 * [AnimationSpeed.OFF].
 */
@Composable
fun TrickArea(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    animationSpeed: AnimationSpeed,
    dealState: DealAnimationState,
    modifier: Modifier = Modifier,
    holdTricks: Boolean = false,
    onTrickAcknowledge: (Int, Int) -> Unit = { _, _ -> },
    // The lesson bubble gives its own "tap the trick" instruction, so the felt's hint would just
    // say it twice.
    hideTapHint: Boolean = false,
    anchors: TutorialAnchors? = null,
) {
    val holdingTrick = holdTricks &&
        animationSpeed != AnimationSpeed.OFF &&
        !dealState.dealing &&
        view.hasClosedTrick()
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .background(Color(0x22000000), RoundedCornerShape(16.dp))
            .tutorialTarget(anchors, TRICK_ANCHOR)
            .clickableWhen(holdingTrick) { onTrickAcknowledge(view.handNumber, view.trickNumber) },
        contentAlignment = Alignment.Center,
    ) {
        // Size the cards from the felt itself so a full trick (with its name labels) always fits,
        // but empty space is used rather than wasted.
        val slots = view.activeSeats.size.coerceAtLeast(2)
        val byWidth = (maxWidth - 16.dp - 8.dp * (slots - 1)) / slots
        val byHeight = (maxHeight - 40.dp) / CardAspectRatio
        val cardWidth = minOf(byWidth, byHeight).coerceIn(56.dp, 96.dp)
        when {
            dealState.dealing -> DealFelt(dealState, cardWidth)
            view.phase == EuchrePhase.PLAY ->
                PlayFelt(view, botNames, cardWidth, holdingTrick && !hideTapHint)
            else -> UpCardSpot(view, dealState, cardWidth)
        }
    }
}

/** Shuffle/deal stage: the deck the packets fly from, beside the up-card slot they end at. */
@Composable
private fun DealFelt(dealState: DealAnimationState, cardWidth: Dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        ShufflingDeck(dealState)
        Box(
            modifier = Modifier
                .size(cardWidth, cardWidth * CardAspectRatio)
                .dealAnchor(dealState, UpcardTarget),
        ) {
            if (dealState.countFor(UpcardTarget) > 0) CardBack(width = cardWidth)
        }
    }
}

/**
 * The auction (and the discard/defend beats that follow it): the remaining stock face down with the
 * turn card face up on top of it while it is on offer. Refused by all four, the card goes back
 * under the stock face down — the caption then names its suit, which is what round 2 still cares
 * about, rather than showing a card nobody can take.
 */
@Composable
private fun UpCardSpot(view: EuchrePlayerView, dealState: DealAnimationState, cardWidth: Dp) {
    val upcard = view.upcard
    val faceUp = view.phase == EuchrePhase.BIDDING_ROUND_1 || view.phase == EuchrePhase.FARMERS
    val showFace = upcard != null && faceUp && !view.upcardTaken
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            // The stock the turn card sits on; also the anchor the deal's last packet flies to. It
            // is nudged out from behind the face-up card, and sits square once nothing is on it.
            Box(Modifier.peekingOut(showFace).dealAnchor(dealState, UpcardTarget)) {
                CardBack(width = cardWidth)
            }
            if (showFace) PlayingCard(checkNotNull(upcard), width = cardWidth)
        }
        Spacer(Modifier.height(6.dp))
        SuitText(upCardCaption(view, showFace), style = MaterialTheme.typography.labelMedium)
    }
}

/** Nudged out from behind the card it sits under; square when it stands alone. */
private fun Modifier.peekingOut(behindACard: Boolean): Modifier =
    if (behindACard) padding(start = 10.dp, top = 10.dp) else this

/**
 * What the stock is doing right now. Once the turn card is refused by all four it goes back under
 * the stock face down, as at a table — so the caption carries the only thing still in play about
 * it: its suit, the one suit round 2 may not name.
 */
private fun upCardCaption(view: EuchrePlayerView, showFace: Boolean): String = when {
    showFace -> "turn card"
    view.upcardTaken -> "Up-card taken"
    view.phase == EuchrePhase.BIDDING_ROUND_2 ->
        view.upcardSuit?.let { "${it.symbol} can't be named" } ?: "turned down"
    else -> "turned down"
}

/** Play stage: the trick in progress, or the completed trick still being shown. */
@Composable
private fun PlayFelt(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    cardWidth: Dp,
    showTapHint: Boolean,
) {
    AnimatedContent(
        targetState = view,
        contentKey = { it.currentTrick.size to it.trickNumber },
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "trickArea",
    ) { v ->
        val lastTrick = v.lastTrick
        when {
            v.currentTrick.isNotEmpty() -> TrickPlaysRow(v, botNames, v.currentTrick, cardWidth)
            lastTrick != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TrickPlaysRow(v, botNames, lastTrick.plays, cardWidth)
                Spacer(Modifier.height(4.dp))
                Text("${seatLabel(v.seat, botNames, lastTrick.winner)} won the trick")
                if (showTapHint) {
                    Text(
                        "tap to continue",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    )
                }
            }
            else -> Text(if (v.isMyTurn) "You lead" else "Waiting for the first card…")
        }
    }
}

/**
 * The trick in its final geometry from the first card: one slot per ACTIVE seat in play order —
 * three for a lone hand, two when a lone defender is out too — with the still-to-play seats as
 * faint placeholders, so nothing shifts as later cards land.
 */
@Composable
private fun TrickPlaysRow(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    plays: List<TrickPlay>,
    cardWidth: Dp,
) {
    val played = plays.map { it.seat }.toSet()
    val order = dealOrder(view.dealer).filter { it in view.activeSeats }
    val upcoming = order.filter { it !in played }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        plays.forEach { play ->
            TrickSlot(view, botNames, play.seat, play.card, cardWidth)
        }
        upcoming.forEach { seat ->
            TrickSlot(view, botNames, seat, card = null, cardWidth = cardWidth)
        }
    }
}

/** One seat's place in the trick: its card if played, otherwise a card-sized outline. */
@Composable
private fun TrickSlot(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    seat: Seat,
    card: Card?,
    cardWidth: Dp,
) {
    val isMyTeam = view.isMyTeam(seat)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (card != null) {
            PlayingCard(card, width = cardWidth)
        } else {
            // Mirrors PlayingCard's geometry so the slot reserves exactly the space the card takes.
            Box(
                Modifier
                    .size(cardWidth, cardWidth * CardAspectRatio)
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f),
                        cardFaceShape(cardWidth),
                    ),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            seatLabel(view.seat, botNames, seat),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = cardWidth + NAME_OVERHANG),
            color = teamColor(view, seat).copy(alpha = if (card == null) DIMMED else 1f),
            fontWeight = if (isMyTeam && card != null) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

/** How far a played card's name label may overhang its card before it ellipsizes. */
private val NAME_OVERHANG = 16.dp

/** Alpha for a seat that has yet to play to the current trick. */
private const val DIMMED = 0.45f
