// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import androidx.compose.runtime.Immutable
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.euchre.EuchreHouseRules

/**
 * The settings dialog's plumbing, bundled: each setting's current value plus its write-through
 * callback. Built once in `EuchreApp` and passed as a unit to [HomeScreen], [BotSetupScreen],
 * [GameScreen] and [SettingsDialog], so adding a setting touches one construction site instead of
 * a parameter list per screen.
 *
 * The four house rules travel as one [EuchreHouseRules] value (exactly what `newGame` takes) with a
 * single write-through callback, so a new toggle costs one field on that value, not two here.
 */
@Immutable
data class SettingsControls(
    val animationSpeed: AnimationSpeed,
    val onCycleAnimationSpeed: () -> Unit,
    val sortByDefault: Boolean,
    val onSetSortByDefault: (Boolean) -> Unit,
    val holdTricks: Boolean,
    val onSetHoldTricks: (Boolean) -> Unit,
    val soundVolume: Float,
    val onSetSoundVolume: (Float) -> Unit,
    val botSkill: BotSkill,
    val onSetBotSkill: (BotSkill) -> Unit,
    val houseRules: EuchreHouseRules,
    val onSetHouseRules: (EuchreHouseRules) -> Unit,
    // Online play: the server to connect to and the display name other players see. Both live here
    // because the settings dialog edits them and the online screens read them.
    val serverUrl: String,
    val onSetServerUrl: (String) -> Unit,
    val playerName: String,
    val onSetPlayerName: (String) -> Unit,
)
