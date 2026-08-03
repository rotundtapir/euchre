// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import io.github.rotundtapir.cardkit.ui.felt.OnBackgroundIconButton
import io.github.rotundtapir.cardkit.ui.felt.OnBackgroundOutlinedButton
import io.github.rotundtapir.cardkit.ui.felt.cardSurfaceButtonColors

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

/** The filled call-to-action styling: cardkit's card-white pill with its fixed dark-green ink. */
@Composable
private fun primaryButtonColors() = cardSurfaceButtonColors()
