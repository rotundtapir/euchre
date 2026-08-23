// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.rotundtapir.cardkit.monetization.Monetization
import io.github.rotundtapir.cardkit.ui.tutorial.NarrationState
import io.github.rotundtapir.cardkit.ui.tutorial.NarrationToggle
import io.github.rotundtapir.cardkit.ui.SettingsIcon
import io.github.rotundtapir.cardkit.ui.felt.OnBackgroundIconButton
import io.github.rotundtapir.cardkit.ui.felt.OnBackgroundOutlinedButton
import io.github.rotundtapir.cardkit.ui.felt.cardSurfaceButtonColors
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.cardkit.ui.settings.SectionHeader
import io.github.rotundtapir.cardkit.ui.settings.SwitchRow

/** The title screen: play, learn, or open settings. */
@Composable
fun HomeScreen(
    monetization: Monetization,
    settings: SettingsControls,
    onPlayWithBots: () -> Unit,
    /** Enters online mode: play a real four-hander with friends over a lobby code. */
    onPlayOnline: () -> Unit,
    /** Where "How to play" goes: the interactive tutorial's lesson picker. */
    onHowToPlay: () -> Unit,
    modifier: Modifier = Modifier,
    /** Null in previews and tests that don't wire audio; the mute is then simply absent. */
    narration: NarrationState? = null,
) {
    var showSettings by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
            OnBackgroundIconButton(
                imageVector = SettingsIcon,
                contentDescription = "Settings",
                onClick = { showSettings = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("settingsButton"),
            )
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text("Euchre", fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text("North American rules · first to 10", fontSize = 16.sp)
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onPlayWithBots,
                    colors = primaryButtonColors(),
                    modifier = Modifier.testTag("playWithBotsButton"),
                ) { Text("Play with bots", fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(16.dp))
                OnBackgroundOutlinedButton(
                    onClick = onPlayOnline,
                    modifier = Modifier.testTag("playOnlineButton"),
                ) { Text("Play with friends") }
                Spacer(Modifier.height(16.dp))
                OnBackgroundOutlinedButton(
                    onClick = onHowToPlay,
                    modifier = Modifier.testTag("walkthroughButton"),
                    // The ♪ warns that the tutorial speaks aloud — no surprise audio. It drops
                    // when narration is muted, and the toggle below flips it back any time.
                ) { Text(if (narration?.enabled == true) "How to play ♪" else "How to play") }
                if (narration != null) NarrationToggle(narration)
            }
        }
    }

    if (showSettings) {
        SettingsDialog(
            settings = settings,
            inGame = false,
            monetization = monetization,
            onDismiss = { showSettings = false },
        )
    }
}

/**
 * The setup screen reached from "Play with bots": choose the opponents' strength and the house
 * rules this game runs under, then deal. These are the same persisted settings the cog edits —
 * surfaced here because they are exactly the choices that only apply to a game about to start.
 */
@Composable
fun BotSetupScreen(
    settings: SettingsControls,
    onStart: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rules = settings.houseRules
    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Two columns when the screen is short and wide (a landscape phone, a small browser
            // window). Stacked, the five options are taller than the viewport, and a canvas app
            // draws no scrollbar — so the overflow is silent: the list simply appears to end. There
            // is ample width at those sizes, and spending it makes scrolling unnecessary rather
            // than merely possible.
            val twoColumns = maxWidth > maxHeight && maxHeight < SETUP_SHORT_HEIGHT
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Only the options scroll; Play and Back are pinned below. On a short screen (a
            // landscape phone, a small browser window) the option list is taller than the viewport,
            // and a canvas app draws no scrollbar — so a scrolling Play button is not merely below
            // the fold, it is below the fold with nothing on screen suggesting it exists.
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                // Top, not Center: centred inside a scroll viewport, an over-long list renders with
                // empty space beneath it, which reads as "that is all the options" while three are
                // still hidden below. Top-aligned, the list runs to the bottom edge and the clipped
                // row is itself the cue that it continues.
            ) {
            // The title is the first thing to go when height is scarce: this screen is reached by
            // tapping "Play with bots" and has a Back button, so it is the most redundant 57dp on
            // it — and buying two option rows with it is what lets the whole list fit.
            if (!twoColumns) {
                Text("Play with bots", fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = if (twoColumns) 900.dp else 420.dp),
            ) {
                SectionHeader("Opponents")
                SwitchRow(
                    label = "Advanced AI",
                    checked = settings.botSkill == BotSkill.ADVANCED,
                    onCheckedChange = { on ->
                        settings.onSetBotSkill(if (on) BotSkill.ADVANCED else BotSkill.STANDARD)
                    },
                    switchModifier = Modifier.testTag("setupAdvancedAi"),
                )
                SectionHeader("House rules")
                // The same four switches the settings dialog shows, from one definition — but
                // enabled here, since this screen precedes the game they apply to.
                if (twoColumns) {
                    val split = (HOUSE_RULE_ROWS.size + 1) / 2
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        for (column in listOf(HOUSE_RULE_ROWS.take(split), HOUSE_RULE_ROWS.drop(split))) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                for (row in column) {
                                    SwitchRow(
                                        label = row.label,
                                        checked = row.read(rules),
                                        onCheckedChange = { settings.onSetHouseRules(row.write(rules, it)) },
                                        switchModifier = Modifier.testTag("setup:${row.tag}"),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    for (row in HOUSE_RULE_ROWS) {
                        SwitchRow(
                            label = row.label,
                            checked = row.read(rules),
                            onCheckedChange = { settings.onSetHouseRules(row.write(rules, it)) },
                            switchModifier = Modifier.testTag("setup:${row.tag}"),
                        )
                    }
                }
            }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onStart,
                colors = primaryButtonColors(),
                modifier = Modifier.testTag("startBotGame"),
            ) { Text("Play", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.testTag("botSetupBack"),
            ) { Text("Back") }
        }
        }
    }
}

/** The filled call-to-action styling: cardkit's card-white pill with its fixed dark-green ink. */
@Composable
private fun primaryButtonColors() = cardSurfaceButtonColors()

/** Below this height the bot-setup screen lays its options out in two columns; see BotSetupScreen. */
private val SETUP_SHORT_HEIGHT = 600.dp
