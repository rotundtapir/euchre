// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.rotundtapir.cardkit.monetization.Monetization
import io.github.rotundtapir.cardkit.ui.AcknowledgmentsDialog
import io.github.rotundtapir.cardkit.ui.LocalAppConfig
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.cardkit.ui.settings.CycleButtonRow
import io.github.rotundtapir.cardkit.ui.settings.SectionHeader
import io.github.rotundtapir.cardkit.ui.settings.SliderRow
import io.github.rotundtapir.cardkit.ui.settings.SupportSection
import io.github.rotundtapir.cardkit.ui.settings.SwitchRow
import io.github.rotundtapir.euchre.EuchreHouseRules
import io.github.rotundtapir.euchre.SettingsDefaults
import io.github.rotundtapir.euchre.SettingsRepository

/**
 * The settings dialog, opened from the cog on the home screen or in a game. With [inGame] set, the
 * house-rule and bot-skill switches (which can only apply to a new game) are shown but disabled.
 */
@Composable
fun SettingsDialog(
    settings: SettingsControls,
    inGame: Boolean,
    monetization: Monetization,
    onDismiss: () -> Unit,
) {
    // Each nested reader takes over the whole dialog rather than stacking a second window.
    var showAcknowledgments by remember { mutableStateOf(false) }
    if (showAcknowledgments) {
        AcknowledgmentsDialog(onDismiss = { showAcknowledgments = false })
        return
    }
    var showRules by remember { mutableStateOf(false) }
    if (showRules) {
        RulesDialog(houseRules = settings.houseRules, onDismiss = { showRules = false })
        return
    }

    val adsRemoved by monetization.adsRemoved.collectAsState()
    val privacyOptionsRequired by monetization.privacyOptionsRequired.collectAsState()
    val uriHandler = LocalUriHandler.current
    val feedbackUri = LocalAppConfig.current.feedbackUri

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            // The list can exceed the dialog's height on small screens, so scroll the body rather
            // than clipping the lower controls.
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                CycleButtonRow(
                    label = "Animations",
                    value = settings.animationSpeed.label,
                    onClick = settings.onCycleAnimationSpeed,
                    buttonModifier = Modifier.testTag("animationSpeed"),
                )
                SwitchRow(
                    label = "Sort hand by default",
                    checked = settings.sortByDefault,
                    onCheckedChange = settings.onSetSortByDefault,
                    switchModifier = Modifier.testTag("sortDefault"),
                )
                SwitchRow(
                    label = "Hold completed tricks",
                    checked = settings.holdTricks,
                    onCheckedChange = settings.onSetHoldTricks,
                    switchModifier = Modifier.testTag("holdTricks"),
                )
                SliderRow(
                    label = "Sound volume",
                    value = settings.soundVolume,
                    onValueChange = settings.onSetSoundVolume,
                    sliderModifier = Modifier.testTag("volumeSlider"),
                )

                HorizontalDivider()
                HouseRuleSection(settings, inGame)

                HorizontalDivider()
                SectionHeader("Bot opponents (apply to new games)")
                SwitchRow(
                    label = "Advanced AI",
                    checked = settings.botSkill == BotSkill.ADVANCED,
                    onCheckedChange = { on ->
                        settings.onSetBotSkill(if (on) BotSkill.ADVANCED else BotSkill.STANDARD)
                    },
                    enabled = !inGame,
                    labelColor = disabledLabelColor(inGame),
                    switchModifier = Modifier.testTag("advancedAi"),
                )
                Text(
                    "Bots think for up to a few seconds per move. Stronger play.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HorizontalDivider()
                SectionHeader("Online")
                OutlinedTextField(
                    value = settings.serverUrl,
                    onValueChange = settings.onSetServerUrl,
                    label = { Text("Game server") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("serverUrl"),
                )
                if (settings.serverUrl != SettingsDefaults.SERVER_URL) {
                    TextButton(
                        onClick = { settings.onSetServerUrl(SettingsDefaults.SERVER_URL) },
                        modifier = Modifier.testTag("serverUrlReset"),
                    ) { Text("Reset to official server") }
                }

                HorizontalDivider()
                SupportSection(
                    offersRemoveAds = monetization.offersRemoveAds,
                    adsRemoved = adsRemoved,
                    onRemoveAdsOrDonate = { monetization.launchRemoveAdsOrDonate() },
                    privacyOptionsRequired = privacyOptionsRequired,
                    onShowPrivacyOptions = { monetization.showPrivacyOptionsForm() },
                    // FOSS/web: the GitHub issue tracker; Play: a mailto to the developer.
                    onFeedback = { runCatching { uriHandler.openUri(feedbackUri) } },
                    onAcknowledgments = { showAcknowledgments = true },
                ) {
                    OutlinedButton(
                        onClick = { showRules = true },
                        modifier = Modifier.fillMaxWidth().testTag("helpButton"),
                    ) { Text("Help — rules of Euchre") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/** The four engine toggles. In a game they cannot take effect until the next one — so, disabled. */
@Composable
private fun HouseRuleSection(settings: SettingsControls, inGame: Boolean) {
    val rules = settings.houseRules
    val labelColor = disabledLabelColor(inGame)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader("House rules (apply to new games)")
        HOUSE_RULE_ROWS.forEach { row ->
            SwitchRow(
                label = row.label,
                checked = row.read(rules),
                onCheckedChange = { settings.onSetHouseRules(row.write(rules, it)) },
                enabled = !inGame,
                labelColor = labelColor,
                switchModifier = Modifier.testTag(row.tag),
            )
        }
    }
}

/**
 * One house-rule switch: its label, its test tag, how it reads and writes the rule set, and how it
 * is persisted. All four arms together, so a new house rule is one entry here rather than an edit
 * in the dialog, the create-lobby screen and the write-through path.
 */
internal class HouseRuleRow(
    val label: String,
    val tag: String,
    val read: (EuchreHouseRules) -> Boolean,
    val persist: suspend SettingsRepository.(Boolean) -> Unit,
    val write: (EuchreHouseRules, Boolean) -> EuchreHouseRules,
)

/**
 * The four engine toggles as data, so the settings dialog and the online create-lobby screen render
 * the same switches (with their own tag prefixes) from one list instead of two hand-kept copies.
 */
internal val HOUSE_RULE_ROWS = listOf(
    HouseRuleRow(
        "Stick the dealer",
        "stickTheDealer",
        { it.stickTheDealer },
        SettingsRepository::setStickTheDealer,
    ) { r, v -> r.copy(stickTheDealer = v) },
    HouseRuleRow(
        "Defend alone",
        "defendAlone",
        { it.defendAlone },
        SettingsRepository::setDefendAlone,
    ) { r, v -> r.copy(defendAlone = v) },
    HouseRuleRow(
        "Benny (joker)",
        "bennyEnabled",
        { it.bennyEnabled },
        SettingsRepository::setBennyEnabled,
    ) { r, v -> r.copy(bennyEnabled = v) },
    HouseRuleRow(
        "Farmer's hand",
        "farmersHand",
        { it.farmersHand },
        SettingsRepository::setFarmersHand,
    ) { r, v -> r.copy(farmersHand = v) },
)

/** Grey a row's label to match its disabled switch; inherit the ambient colour otherwise. */
@Composable
private fun disabledLabelColor(inGame: Boolean): Color =
    if (inGame) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else Color.Unspecified
