// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.ui.SuitText
import io.github.rotundtapir.euchre.engine.EuchreHandResult
import io.github.rotundtapir.euchre.engine.EuchreOutcome
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.TRICKS_PER_HAND
import io.github.rotundtapir.euchre.engine.WINNING_SCORE

/**
 * The headline for a scored hand from the winning side's point of view: how many points, and why.
 * Exactly one team scores in Euchre, so a single phrase covers the whole result.
 */
fun handResultHeadline(result: EuchreHandResult): String {
    val points = result.teamDeltas.values.firstOrNull() ?: 0
    // The engine already classified the hand to score it; this only names its five outcomes.
    val reason = when (result.outcome) {
        EuchreOutcome.LONE_MARCH -> "alone march"
        EuchreOutcome.MARCH -> "march!"
        EuchreOutcome.MADE -> "made it"
        EuchreOutcome.EUCHRED_ALONE -> "euchred alone!"
        EuchreOutcome.EUCHRED -> "euchred!"
    }
    return "+$points — $reason"
}

/**
 * The end-of-hand breakdown. Dismissing it raises the hand-result acknowledgement — the next deal's
 * shuffle and the bots both wait on that, so nothing moves behind the dialog while it is read.
 */
@Composable
fun HandResultDialog(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {
    val result = view.lastHandResult ?: return
    // Keyed on the scored-hand COUNT, not the result value: consecutive hands can score
    // structurally identically, and a value key would never reset — the second dialog would never
    // show and the acknowledgement gates would deadlock.
    var dismissed by remember(view.handResults.size) { mutableStateOf(false) }
    if (dismissed) return
    val dismiss = {
        dismissed = true
        onDismiss()
    }

    val makers = result.makers
    val makerName = seatLabel(view.seat, botNames, makers.maker)

    BannerDialog(
        good = (makers.makerTeam == view.myTeam) == result.made,
        onDismissRequest = dismiss,
        bannerPadding = 12.dp,
        modifier = modifier,
        banner = { onHeaderColor ->
            Text(
                handResultHeadline(result),
                style = MaterialTheme.typography.titleLarge,
                color = onHeaderColor,
                textAlign = TextAlign.Center,
            )
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SuitText(
                "$makerName made ${makers.trump.symbol} trump" +
                    if (makers.alone) " and went alone" else "",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "The makers took ${result.makerTricks} of $TRICKS_PER_HAND tricks " +
                    "(three are needed).",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            ScoreDeltaRow("Us", result.teamDeltas[view.myTeam] ?: 0, view.scores[view.myTeam] ?: 0)
            ScoreDeltaRow(
                "Them",
                result.teamDeltas[view.opponentTeam] ?: 0,
                view.scores[view.opponentTeam] ?: 0,
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = dismiss,
                modifier = Modifier.fillMaxWidth().testTag("handResultContinue"),
            ) { Text("Continue") }
        }
    }
}

/**
 * The end-of-hand and end-of-game dialogs' shared shape: a rounded card whose top strip is tinted
 * by whether the news is good for the local player, over a body the caller fills. [banner] receives
 * the content colour to draw on that strip.
 */
@Composable
private fun BannerDialog(
    good: Boolean,
    onDismissRequest: () -> Unit,
    bannerPadding: Dp,
    banner: @Composable ColumnScope.(Color) -> Unit,
    modifier: Modifier = Modifier,
    body: @Composable ColumnScope.() -> Unit,
) {
    val headerColor = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val onHeaderColor = if (good) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onError
    Dialog(onDismissRequest = onDismissRequest) {
        // Never taller than the window. Without this the dialog takes its content's height and the
        // bottom is simply cut off — and the bottom is where every one of these dialogs puts its
        // only button. GameOverDialog is the dangerous case: its onDismissRequest is deliberately
        // empty, so back and outside-tap do nothing, and a clipped "Back to menu" leaves a finished
        // game with no exit but killing the app. A landscape phone (~390dp) is short enough to do
        // it: banner, score sheet and button together exceed that comfortably.
        BoxWithConstraints {
            val maxDialogHeight = maxHeight - DIALOG_WINDOW_MARGIN
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                modifier = modifier.heightIn(max = maxDialogHeight),
            ) {
                Column {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerColor)
                            .padding(horizontal = 24.dp, vertical = bannerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { banner(onHeaderColor) }
                    // The body scrolls rather than the whole dialog: the banner says which dialog
                    // this is and the body's last element is the action, so scrolling the lot would
                    // just move the clipping problem rather than remove it.
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) { body() }
                }
            }
        }
    }
}

/** One side's line in the hand-result table: label left, this hand's delta and the new total right. */
@Composable
private fun ScoreDeltaRow(label: String, delta: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "+$delta",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("$total/$WINNING_SCORE", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/**
 * End-of-game dialog: a win/lose banner, the final totals, and the hand-by-hand sheet built from
 * [EuchrePlayerView.handResults]. Shown only once the final hand's [HandResultDialog] has been
 * dismissed, so the last hand's breakdown is never skipped.
 */
@Composable
fun GameOverDialog(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    onBackToMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val youWon = view.winner == view.myTeam
    val handCount = view.handResults.size

    BannerDialog(
        good = youWon,
        onDismissRequest = {},
        bannerPadding = 16.dp,
        modifier = modifier,
        banner = { onHeaderColor ->
            Text(
                if (youWon) "You win!" else "You lose",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = onHeaderColor,
            )
            Text(
                "${view.scores[view.myTeam] ?: 0} – ${view.scores[view.opponentTeam] ?: 0} " +
                    "after $handCount ${if (handCount == 1) "hand" else "hands"}",
                style = MaterialTheme.typography.labelMedium,
                color = onHeaderColor,
            )
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            ScoreSheetHeader()
            Spacer(Modifier.height(4.dp))
            Column(
                modifier = Modifier
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                view.handResults.forEachIndexed { i, r ->
                    ScoreSheetRow(view, botNames, i, r)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onBackToMenu,
                modifier = Modifier.fillMaxWidth().testTag("backToMenu"),
            ) { Text("Back to menu") }
        }
    }
}

/** Width of each side's delta column in the end-of-game score sheet. */
private val DeltaCellWidth = 48.dp

@Composable
private fun ScoreSheetHeader() {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(
                "Hand",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            listOf("Us", "Them").forEach { label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(DeltaCellWidth).padding(start = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
    }
}

/** One hand of the score sheet: who made what, whether it held, and each side's points. */
@Composable
private fun ScoreSheetRow(
    view: EuchrePlayerView,
    botNames: Map<Seat, String>,
    index: Int,
    result: EuchreHandResult,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${index + 1}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(24.dp),
        )
        SuitText(
            "${result.makers.trump.symbol} · ${seatLabel(view.seat, botNames, result.makers.maker)}" +
                if (result.makers.alone) " (alone)" else "",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(
            if (result.made) "✓" else "✗",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = if (result.made) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        listOf(view.myTeam, view.opponentTeam).forEach { team ->
            Text(
                "+${result.teamDeltas[team] ?: 0}",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (team == view.myTeam) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = TextAlign.End,
                modifier = Modifier.width(DeltaCellWidth),
            )
        }
    }
}

/** "Leave game?" — the current game is not saved, so make the player mean it. */
@Composable
fun LeaveGameDialog(onConfirm: () -> Unit, onDismiss: () -> Unit, body: String? = null) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Leave game?") },
        // An online game overrides the body: leaving hands the seat to a bot, it is not a loss.
        text = { Text(body ?: "The current game will be lost.") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag("confirmLeave")) { Text("Leave") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Breathing room left around a dialog so it reads as a dialog rather than a full screen. Also keeps
 * the scrim visible at the edges, which is the affordance that says the thing behind is still there.
 */
private val DIALOG_WINDOW_MARGIN = 48.dp
