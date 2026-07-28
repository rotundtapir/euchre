// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.ui.CardHand
import io.github.rotundtapir.cardkit.ui.SuitText
import io.github.rotundtapir.cardkit.ui.felt.CardSurfaceWhite
import io.github.rotundtapir.cardkit.ui.felt.InkOnCardSurface
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialAnchors
import io.github.rotundtapir.cardkit.ui.tutorial.tutorialTarget
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.FARMERS_SWAP_SIZE
import io.github.rotundtapir.euchre.ui.tutorial.ACTION_ANCHOR
import io.github.rotundtapir.euchre.ui.tutorial.EuchreTutorialSession
import io.github.rotundtapir.euchre.ui.tutorial.EuchreTutorialStep
import io.github.rotundtapir.euchre.ui.tutorial.HAND_ANCHOR
import io.github.rotundtapir.euchre.ui.tutorial.cardAnchor

/**
 * Everything below the felt: the prompt for whatever the human owes the engine right now, plus
 * their hand. Which panel shows is driven by the phase and by `view.legalActions` — a house rule
 * that is off simply has no actions, so its panel never appears.
 */
@Composable
fun ActionArea(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    sortHand: Boolean,
    onToggleSort: () -> Unit,
    onAction: (EuchreAction) -> Unit,
    modifier: Modifier = Modifier,
    // Non-null during a tutorial lesson: only the scripted action is enabled, and taking it
    // advances the script. [anchors] records where that action sits so the bubble can point at it.
    tutorial: EuchreTutorialSession? = null,
    anchors: TutorialAnchors? = null,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        Text(
            "You — tricks: ${view.tricksWon[view.seat] ?: 0}" + if (view.seat == view.dealer) " · dealer" else "",
            fontWeight = if (view.isMyTurn) FontWeight.Bold else FontWeight.Normal,
        )
        Spacer(Modifier.height(4.dp))
        val hand = HandParams(view, sortHand, onToggleSort, tutorial, anchors)
        when {
            !view.isMyTurn -> {
                Text(view.toAct?.let { "Waiting for ${seatLabel(view.seat, botNames, it)}…" } ?: "")
                Spacer(Modifier.height(4.dp))
                HumanHand(hand, playable = { false }, dimUnplayable = false, onClick = {})
            }
            view.phase == EuchrePhase.FARMERS -> FarmersHandPrompt(hand, onAction)
            view.phase == EuchrePhase.DEALER_DISCARD -> DealerDiscardPanel(hand, onAction)
            view.phase == EuchrePhase.DEFEND_ALONE -> DefendAlonePanel(hand, onAction)
            view.phase == EuchrePhase.PLAY -> PlayPanel(hand, onAction)
            // Round 1 offers OrderUp; the dealer's forced call on a turned-up Benny does not, and
            // falls through to the suit picker like round 2.
            view.legalActions.any { it is EuchreAction.OrderUp } -> BiddingRound1Panel(view, hand, onAction)
            else -> BiddingRound2Panel(view, hand, onAction)
        }
    }
}

/**
 * Round 1: order the turn card's suit up, or pass. The dealer's button says "Pick it up" — for them
 * ordering up means taking the card into hand and burying one.
 */
@Composable
private fun BiddingRound1Panel(view: EuchrePlayerView, hand: HandParams, onAction: (EuchreAction) -> Unit) {
    val step = hand.tutorial?.step as? EuchreTutorialStep.Round1Step
    var alone by remember(view.handNumber) { mutableStateOf(false) }
    var acted by remember(view) { mutableStateOf(false) }
    // In a lesson only the scripted button arms — and an order-up scripted "alone" stays disabled
    // until the box is actually ticked, because ticking it IS the lesson.
    val passEnabled = !acted && (hand.tutorial == null || step?.orderUp == false)
    val orderEnabled = !acted &&
        (hand.tutorial == null || (step != null && step.orderUp && alone == step.alone))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        SuitText("Order up ${view.upcardSuit?.symbol ?: view.upcard?.label.orEmpty()}?", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeltActionButton(
                label = "Pass",
                tag = "bid:pass",
                enabled = passEnabled,
                onClick = {
                    acted = true
                    onAction(EuchreAction.Pass)
                    hand.tutorial?.script?.onAdvance?.invoke()
                },
                modifier = hand.anchorFor(step?.orderUp == false),
            )
            FeltActionButton(
                label = if (view.seat == view.dealer) "Pick it up" else "Order it up",
                tag = "bid:orderUp",
                enabled = orderEnabled,
                emphasized = true,
                onClick = {
                    acted = true
                    onAction(EuchreAction.OrderUp(alone))
                    hand.tutorial?.script?.onAdvance?.invoke()
                },
                modifier = hand.anchorFor(step?.orderUp == true),
            )
        }
        AloneToggle(alone) { alone = it }
        HumanHand(hand, playable = { false }, dimUnplayable = false, onClick = {})
    }
}

/**
 * Round 2: name any suit but the turned-down one. Pass is offered only when the view actually
 * carries it — with "stick the dealer" on, the dealer has no Pass and the button is absent.
 */
@Composable
private fun BiddingRound2Panel(view: EuchrePlayerView, hand: HandParams, onAction: (EuchreAction) -> Unit) {
    val step = hand.tutorial?.step as? EuchreTutorialStep.Round2Step
    var alone by remember(view.handNumber) { mutableStateOf(false) }
    var acted by remember(view) { mutableStateOf(false) }
    val suits = view.legalActions.filterIsInstance<EuchreAction.CallTrump>().map { it.suit }.distinct()
    val mayPass = view.legalActions.any { it is EuchreAction.Pass }
    val passEnabled = !acted && (hand.tutorial == null || (step != null && step.call == null))
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(if (mayPass) "Name trump, or pass" else "You must name trump", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        ) {
            if (mayPass) {
                FeltActionButton(
                    label = "Pass",
                    tag = "bid:pass",
                    enabled = passEnabled,
                    onClick = {
                        acted = true
                        onAction(EuchreAction.Pass)
                        hand.tutorial?.script?.onAdvance?.invoke()
                    },
                    modifier = hand.anchorFor(step != null && step.call == null),
                )
            }
            suits.forEach { suit ->
                val scripted = step != null && step.call == suit
                val armed = step != null && step.call == suit && alone == step.alone
                FeltActionButton(
                    label = suit.symbol,
                    tag = "bid:trump:${suit.name}",
                    enabled = !acted && (hand.tutorial == null || armed),
                    emphasized = true,
                    onClick = {
                        acted = true
                        onAction(EuchreAction.CallTrump(suit, alone))
                        hand.tutorial?.script?.onAdvance?.invoke()
                    },
                    modifier = hand.anchorFor(scripted),
                )
            }
        }
        AloneToggle(alone) { alone = it }
        HumanHand(hand, playable = { false }, dimUnplayable = false, onClick = {})
    }
}

/** The house-rule prompt a lone maker's opponents get: double the stake, or play it normally. */
@Composable
private fun DefendAlonePanel(hand: HandParams, onAction: (EuchreAction) -> Unit) {
    var acted by remember(hand.view) { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Defend alone? Your partner would sit out.", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeltActionButton(
                label = "No, play together",
                tag = "defend:decline",
                enabled = !acted,
                onClick = {
                    acted = true
                    onAction(EuchreAction.DeclineDefend)
                },
            )
            FeltActionButton(
                label = "Defend alone",
                tag = "defend:alone",
                enabled = !acted,
                emphasized = true,
                onClick = {
                    acted = true
                    onAction(EuchreAction.DefendAlone)
                },
            )
        }
        HumanHand(hand, playable = { false }, dimUnplayable = false, onClick = {})
    }
}

/** After picking the turn card up the dealer holds six and buries one. */
@Composable
private fun DealerDiscardPanel(hand: HandParams, onAction: (EuchreAction) -> Unit) {
    val step = hand.tutorial?.step as? EuchreTutorialStep.DiscardStep
    CardSelectionPanel(
        hand = hand,
        prompt = "Pick it up — bury one card",
        confirmLabel = "Discard",
        confirmTag = "discardButton",
        requiredCount = 1,
        onConfirm = { cards ->
            onAction(EuchreAction.DealerDiscard(cards.single()))
            hand.tutorial?.script?.onAdvance?.invoke()
        },
        selectable = if (hand.tutorial == null) null else setOfNotNull(step?.card),
    )
}

/**
 * Farmer's hand (house rule): a hand of nothing but nines and tens may swap three cards for the
 * bottom three of the kitty, before the auction opens.
 */
@Composable
private fun FarmersHandPrompt(hand: HandParams, onAction: (EuchreAction) -> Unit) {
    CardSelectionPanel(
        hand = hand,
        prompt = "Farmer's hand — swap $FARMERS_SWAP_SIZE cards?",
        confirmLabel = "Swap $FARMERS_SWAP_SIZE",
        confirmTag = "farmersSwap",
        requiredCount = FARMERS_SWAP_SIZE,
        onConfirm = { cards -> onAction(EuchreAction.CallFarmers(cards)) },
        secondary = { enabled, act ->
            FeltActionButton(
                label = "Keep my hand",
                tag = "farmersDecline",
                enabled = enabled,
                onClick = {
                    act()
                    onAction(EuchreAction.DeclineFarmers)
                },
            )
        },
    )
}

/** The human's turn to play: legal cards are tappable, the rest dimmed. */
@Composable
private fun PlayPanel(hand: HandParams, onAction: (EuchreAction) -> Unit) {
    val playable = remember(hand.view) {
        hand.view.legalActions.filterIsInstance<EuchreAction.PlayCard>().map { it.card }.toSet()
    }
    val scripted = (hand.tutorial?.step as? EuchreTutorialStep.PlayStep)?.card
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Your turn — tap a card to play")
        Spacer(Modifier.height(4.dp))
        HumanHand(
            hand = hand,
            playable = { it in playable && (hand.tutorial == null || it == scripted) },
            onClick = { card ->
                onAction(EuchreAction.PlayCard(card))
                hand.tutorial?.script?.onAdvance?.invoke()
            },
        )
    }
}

/**
 * The shared "choose exactly N of your cards, then confirm" panel behind the dealer's discard and
 * the farmer's-hand swap. [secondary] adds an extra button beside the confirm (the swap's decline),
 * receiving the enabled flag and the same double-tap latch the confirm uses.
 */
@Composable
private fun CardSelectionPanel(
    hand: HandParams,
    prompt: String,
    confirmLabel: String,
    confirmTag: String,
    requiredCount: Int,
    onConfirm: (List<Card>) -> Unit,
    secondary: (@Composable (Boolean, () -> Unit) -> Unit)? = null,
    // Tutorial constraint: when non-null, only these cards may be selected.
    selectable: Set<Card>? = null,
) {
    var selected by remember(hand.view.hand) { mutableStateOf(emptySet<Card>()) }
    var acted by remember(hand.view) { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("$prompt (${selected.size}/$requiredCount selected)", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            secondary?.invoke(!acted) { acted = true }
            Button(
                onClick = {
                    acted = true
                    onConfirm(selected.toList())
                },
                enabled = !acted && selected.size == requiredCount,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CardSurfaceWhite,
                    contentColor = InkOnCardSurface,
                ),
                modifier = Modifier.testTag(confirmTag),
            ) { Text(confirmLabel) }
        }
        Spacer(Modifier.height(4.dp))
        HumanHand(
            hand = hand,
            playable = { selectable == null || it in selectable },
            selected = selected,
            onClick = { card ->
                selected = when {
                    card in selected -> selected - card
                    selected.size < requiredCount -> selected + card
                    else -> selected
                }
            },
        )
    }
}

/** The go-alone opt-in shared by both bidding rounds: your partner sits the hand out. */
@Composable
private fun AloneToggle(alone: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = alone,
            onCheckedChange = onChange,
            // The M3 defaults draw the box in `primary` — green on green. Pin it to onBackground.
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.onBackground,
                uncheckedColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                checkmarkColor = MaterialTheme.colorScheme.background,
            ),
            modifier = Modifier.testTag("aloneToggle"),
        )
        Text("Go alone", style = MaterialTheme.typography.labelLarge)
    }
}

/** An outlined button legible on the felt; [emphasized] fills it card-white for the primary choice. */
@Composable
private fun FeltActionButton(
    label: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val onBackground = MaterialTheme.colorScheme.onBackground
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (emphasized) CardSurfaceWhite else Color.Transparent,
            contentColor = if (emphasized) InkOnCardSurface else onBackground,
            disabledContentColor = onBackground.copy(alpha = 0.38f),
        ),
        border = BorderStroke(1.dp, onBackground.copy(alpha = 0.6f)),
        modifier = modifier.testTag(tag),
    ) { SuitText(label) }
}

/** The inputs every hand rendering needs, bundled so each panel takes one parameter, not three. */
private data class HandParams(
    val view: EuchrePlayerView,
    val sortHand: Boolean,
    val onToggleSort: () -> Unit,
    val tutorial: EuchreTutorialSession? = null,
    val anchors: TutorialAnchors? = null,
) {
    /** Records the tutorial's "point here" anchor on this element when it is the scripted one. */
    fun anchorFor(scripted: Boolean): Modifier =
        Modifier.tutorialTarget(if (scripted) anchors else null, ACTION_ANCHOR)
}

/** Fan exposure: each card advances this fraction of a card width, so only that strip is visible. */
private const val HAND_EXPOSURE = 0.5f

/** Tags the human's fan, so tests can scope queries to the cards actually in hand. */
const val HUMAN_HAND_TAG = "humanHand"

/**
 * Test-tag prefix for a card in the human's fan, e.g. `hand:JS`.
 *
 * Deliberately NOT `card:` — cardkit's `PlayingCard` already tags every card it draws
 * `card:<label>`, including the nested image inside each of these very cards, plus the trick, the
 * up-card and `CardArtWarmup`'s off-screen deck. Sharing that prefix made every hand query
 * ambiguous.
 */
const val HAND_CARD_TAG_PREFIX = "hand:"

/** The human's fan, with the sort toggle above it. */
@Composable
private fun HumanHand(
    hand: HandParams,
    playable: (Card) -> Boolean,
    onClick: (Card) -> Unit,
    dimUnplayable: Boolean = true,
    selected: Set<Card> = emptySet(),
) {
    val view = hand.view
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = hand.onToggleSort,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f),
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
            modifier = Modifier.height(30.dp).testTag("sortToggle"),
        ) {
            Text(
                if (hand.sortHand) "Sorted ⇄" else "Deal order ⇄",
                style = MaterialTheme.typography.labelMedium,
            )
        }
        // Memoized: this recomposes on every view emission, but the sort's inputs only change on a
        // new hand, a play, or trump being made.
        val cards = if (hand.sortHand) {
            remember(view.hand, view.trump) { sortedForDisplay(view.hand, view.trump) }
        } else {
            view.hand
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                // Scopes card queries to this hand: cardkit's PlayingCard tags every card it draws
                // `card:<label>`, so an unscoped `card:` search also finds the trick, the up-card
                // and CardArtWarmup's off-screen deck.
                .testTag(HUMAN_HAND_TAG)
                .tutorialTarget(hand.anchors, HAND_ANCHOR),
            contentAlignment = Alignment.Center,
        ) {
            CardHand(
                cards = cards,
                cardWidth = 84.dp,
                exposure = HAND_EXPOSURE,
                playable = playable,
                dimUnplayable = dimUnplayable,
                selected = selected,
                onCardClick = onClick,
                cardModifier = { card ->
                    // Only the exposed left strip of a fanned card is visible, so that is the rect
                    // the tutorial's tail should point at.
                    Modifier
                        .testTag("$HAND_CARD_TAG_PREFIX${card.code}")
                        .tutorialTarget(hand.anchors, cardAnchor(card.code), widthFraction = HAND_EXPOSURE)
                },
            )
        }
    }
}
