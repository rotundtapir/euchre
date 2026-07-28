// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui.tutorial

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.leftBowerSuit
import io.github.rotundtapir.cardkit.core.of
import io.github.rotundtapir.cardkit.ui.PlayingCard
import io.github.rotundtapir.cardkit.ui.SuitText
import io.github.rotundtapir.cardkit.ui.felt.CardSurfaceWhite
import io.github.rotundtapir.cardkit.ui.felt.InkOnCardSurface
import io.github.rotundtapir.cardkit.ui.tutorial.BubbleLayout
import io.github.rotundtapir.cardkit.ui.tutorial.NarrateEffect
import io.github.rotundtapir.cardkit.ui.tutorial.NarrationState
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialAnchors
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialScriptState
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.ui.hasClosedTrick
import io.github.rotundtapir.euchre.ui.seatLabel
import kotlin.math.roundToInt

/**
 * One lesson in progress: which lesson, how far through its script the player is, and the two ways
 * out (taking the next scripted action, and finishing the lesson for good).
 */
@Stable
class EuchreTutorialSession(
    val lesson: TutorialLesson,
    stepIndex: Int,
    onAdvance: () -> Unit,
    val onFinish: () -> Unit,
) {
    /** The generic cardkit script cursor over this lesson's steps. */
    val script: EuchreTutorialState = TutorialScriptState(lesson.steps, stepIndex, onAdvance)

    /** The pending human decision, or null once the lesson's hand is over. */
    val step: EuchreTutorialStep? get() = script.step
}

/**
 * The lesson guidance as a speech bubble anchored to whatever needs interacting with next: it
 * floats just above the scripted button or card with a tail pointing down at it, or over the felt
 * with the tail pointing up while a completed trick is explained.
 *
 * While the bots act there is deliberately NO bubble (500 learned this): a permanent "watch the
 * table" box adds noise without teaching anything.
 */
@Composable
internal fun TutorialBubble(
    session: EuchreTutorialSession,
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    anchors: TutorialAnchors,
    overlayOrigin: Offset,
    narration: NarrationState? = null,
) {
    val step = session.step
    val isHumanDecision = view.isMyTurn && when (step) {
        is EuchreTutorialStep.Round1Step -> view.phase == EuchrePhase.BIDDING_ROUND_1
        is EuchreTutorialStep.Round2Step -> view.phase == EuchrePhase.BIDDING_ROUND_2
        is EuchreTutorialStep.DiscardStep -> view.phase == EuchrePhase.DEALER_DISCARD
        is EuchreTutorialStep.PlayStep -> view.phase == EuchrePhase.PLAY
        null -> false
    }
    // A completed trick held on the felt (a lesson forces the hold on): explain what happened. Once
    // a trick closes, view.trickNumber IS that trick's number, so it keys the lesson's notes.
    val closedTrick = view.lastTrick?.takeIf { view.hasClosedTrick() }
    val text = when {
        step == null -> session.lesson.handDone
        isHumanDecision -> step.advice
        closedTrick != null -> session.lesson.trickNotes[view.trickNumber]
            ?: "${seatLabel(view.seat, botNames, closedTrick.winner)} won the trick; tap it to continue."
        else -> return
    }
    val target = anchors[targetKey(step, isHumanDecision)]
        ?: anchors[HAND_ANCHOR]
        ?: anchors[TRICK_ANCHOR]
        ?: return
    val tailDown = isHumanDecision
    // Never sit on top of the controls the advice is telling the player to use: the bubble stops
    // above the action panel even when its target (a card in the fan) is below that.
    val panelTop = anchors[PANEL_ANCHOR]?.let { it.top - overlayOrigin.y }
    // Trump is not made yet at a bidding step, so fall back to the suit on offer — which is exactly
    // the suit the advice is reasoning about.
    val bowerSuit = view.trump ?: view.upcardSuit
    // No clips ship in v0.1.0; narration is null and the resolver is the seam audio drops into.
    NarrateEffect(narration, text) { null }

    BubbleLayout(
        target = target,
        overlayOrigin = overlayOrigin,
        tailDown = tailDown,
        maxWidth = 520.dp,
        yPlacement = { local, height, gap ->
            if (tailDown) {
                bubbleTopAbove(local.top, panelTop, height, gap)
            } else {
                (local.bottom - height - gap).roundToInt()
            }
        },
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = CardSurfaceWhite,
            contentColor = InkOnCardSurface,
            shadowElevation = 8.dp,
            modifier = Modifier.testTag("tutorialAdvice"),
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    session.lesson.title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                SuitText(text, fontSize = 17.sp, lineHeight = 23.sp)
                if (isHumanDecision && step?.showBowerOrder == true && bowerSuit != null) {
                    Spacer(Modifier.height(8.dp))
                    BowerOrderRow(bowerSuit)
                }
            }
        }
    }
}

/**
 * The anchor the bubble's tail points at for the pending step.
 *
 * A step that names a card points at that card, wherever the fan currently has it — sorting the
 * hand moves it, and the anchor moves with it. Only the bidding steps point at a button.
 *
 * The discard used to fall through to [ACTION_ANCHOR] with the rest, but its panel anchors no
 * button: the tail was left aiming at whatever last recorded that key — the previous step's bid
 * button, gone from the screen by then — so it pointed at empty felt near the middle of the table
 * instead of at the card the text was naming.
 */
internal fun targetKey(step: EuchreTutorialStep?, isHumanDecision: Boolean): String = when {
    !isHumanDecision -> TRICK_ANCHOR
    step is EuchreTutorialStep.PlayStep -> cardAnchor(step.card.code)
    step is EuchreTutorialStep.DiscardStep -> cardAnchor(step.card.code)
    else -> ACTION_ANCHOR
}

/**
 * Where the top of a downward-pointing bubble goes: clear of its [targetTop], and clear of the
 * action panel's top edge too when there is one. Pointing at a card in the fan would otherwise put
 * the bubble over the prompt and the Discard button — the very controls the text asks for.
 */
internal fun bubbleTopAbove(targetTop: Float, panelTop: Float?, bubbleHeight: Int, gap: Int): Int {
    val ceiling = if (panelTop != null) minOf(targetTop, panelTop) else targetTop
    return (ceiling - bubbleHeight - gap).roundToInt()
}

/** The action panel below the felt: the bubble stays above it rather than covering its buttons. */
const val PANEL_ANCHOR = "panel"

/** The felt: where the bubble sits while a completed trick is being explained. */
const val TRICK_ANCHOR = "trick"

/** The single enabled button of the current action panel. */
const val ACTION_ANCHOR = "action"

/** The whole hand fan — the fallback anchor when the scripted card is off screen. */
const val HAND_ANCHOR = "hand"

/** The anchor key of one card in the human's fan. */
fun cardAnchor(code: String): String = "card:$code"

/**
 * The trump pecking order for a lesson's bower moments, drawn for the suit actually in play: right
 * bower, left bower, then the ace and king of trumps.
 */
@Composable
private fun BowerOrderRow(trump: Suit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.fillMaxWidth()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlayingCard(Rank.JACK of trump, width = BowerCardWidth)
            Text(">", fontWeight = FontWeight.Bold)
            PlayingCard(Rank.JACK of leftBowerSuit(trump), width = BowerCardWidth)
            Text(">", fontWeight = FontWeight.Bold)
            PlayingCard(Rank.ACE of trump, width = BowerCardWidth)
            Text(">", fontWeight = FontWeight.Bold)
            PlayingCard(Rank.KING of trump, width = BowerCardWidth)
        }
        Spacer(Modifier.height(2.dp))
        SuitText("Trump order with ${trump.symbol} trump", style = MaterialTheme.typography.labelSmall)
    }
}

private val BowerCardWidth = 40.dp
