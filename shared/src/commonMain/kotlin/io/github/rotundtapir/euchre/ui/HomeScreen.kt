// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import io.github.rotundtapir.cardkit.ui.SettingsIcon
import io.github.rotundtapir.cardkit.ui.felt.CardSurfaceWhite
import io.github.rotundtapir.cardkit.ui.felt.InkOnCardSurface
import io.github.rotundtapir.cardkit.ui.felt.OnBackgroundIconButton
import io.github.rotundtapir.cardkit.ui.felt.OnBackgroundOutlinedButton
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.cardkit.ui.settings.SectionHeader
import io.github.rotundtapir.cardkit.ui.settings.SwitchRow

/** The title screen: play, learn, or open settings. */
@Composable
fun HomeScreen(
    monetization: Monetization,
    settings: SettingsControls,
    onPlayWithBots: () -> Unit,
    /** Where "How to play" goes: the interactive tutorial's lesson picker. */
    onHowToPlay: () -> Unit,
    modifier: Modifier = Modifier,
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
                    onClick = onHowToPlay,
                    modifier = Modifier.testTag("walkthroughButton"),
                ) { Text("How to play") }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Play with bots", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(24.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
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
                HOUSE_RULE_ROWS.forEach { row ->
                    SwitchRow(
                        label = row.label,
                        checked = row.read(rules),
                        onCheckedChange = { settings.onSetHouseRules(row.write(rules, it)) },
                        switchModifier = Modifier.testTag("setup:${row.tag}"),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onStart,
                colors = primaryButtonColors(),
                modifier = Modifier.testTag("startBotGame"),
            ) { Text("Play", fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(16.dp))
            TextButton(
                onClick = onBack,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onBackground),
                modifier = Modifier.testTag("botSetupBack"),
            ) { Text("Back") }
        }
    }
}

/** The filled call-to-action styling: card white with the fixed dark-green ink (never theme primary). */
@Composable
private fun primaryButtonColors() = ButtonDefaults.buttonColors(
    containerColor = CardSurfaceWhite,
    contentColor = InkOnCardSurface,
)
