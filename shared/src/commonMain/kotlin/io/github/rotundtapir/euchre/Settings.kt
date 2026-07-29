// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import androidx.compose.runtime.Stable
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.cardkit.ui.settings.KeyValueStore
import io.github.rotundtapir.cardkit.ui.settings.booleanSetting
import io.github.rotundtapir.cardkit.ui.settings.enumSetting
import io.github.rotundtapir.cardkit.ui.settings.floatSetting
import io.github.rotundtapir.cardkit.ui.settings.stringSetting
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Storage keys. These are persisted strings — renaming one silently resets that setting for every
 * existing install, so treat them as frozen.
 */
object SettingsKeys {
    const val ANIMATION_SPEED = "animation_speed"
    const val SORT_HAND_BY_DEFAULT = "sort_hand_by_default"
    const val HOLD_TRICKS = "hold_tricks"
    const val SOUND_VOLUME = "sound_volume"
    const val BOT_SKILL = "bot_skill"
    const val STICK_THE_DEALER = "stick_the_dealer"
    const val DEFEND_ALONE = "defend_alone"
    const val BENNY_ENABLED = "benny_enabled"
    const val FARMERS_HAND = "farmers_hand"
    const val SERVER_URL = "server_url"
    const val PLAYER_NAME = "player_name"

    /** Per-lesson completion flag: `"${LESSON_DONE_PREFIX}$lessonId"`. */
    const val LESSON_DONE_PREFIX = "lesson_done_"
}

object SettingsDefaults {
    val ANIMATION_SPEED = AnimationSpeed.NORMAL
    const val SORT_HAND_BY_DEFAULT = false
    const val HOLD_TRICKS = false
    const val SOUND_VOLUME = 0.7f
    val BOT_SKILL = BotSkill.STANDARD

    /** On by default: a hand where everyone passes twice is a dull throw-in. */
    const val STICK_THE_DEALER = true
    const val DEFEND_ALONE = false
    const val BENNY_ENABLED = false
    const val FARMERS_HAND = false

    /** The official online server. Overridable in settings so a self-host or a dev box can be used. */
    const val SERVER_URL = "wss://euchre.29022617.xyz"

    /** Empty until the player names themselves on the online entry screen. */
    const val PLAYER_NAME = ""
}

/**
 * The app's persisted preferences. Platform backends implement [KeyValueStore] (DataStore on
 * Android, localStorage on web) and this repository maps the game's keys onto it.
 *
 * [Stable] so the composables that bundle its callbacks can be skipped: the flows are `val`s and
 * every mutation goes through a suspend setter, which is exactly the contract.
 */
@Stable
interface SettingsRepository {
    val animationSpeed: Flow<AnimationSpeed>
    val sortHandByDefault: Flow<Boolean>
    val holdTricks: Flow<Boolean>
    val soundVolume: Flow<Float>
    val botSkill: Flow<BotSkill>
    val stickTheDealer: Flow<Boolean>
    val defendAlone: Flow<Boolean>
    val bennyEnabled: Flow<Boolean>
    val farmersHand: Flow<Boolean>

    /** The online game server URL (`wss://…`); [SettingsDefaults.SERVER_URL] when unset. */
    val serverUrl: Flow<String>

    /** The player's chosen display name for online games; [SettingsDefaults.PLAYER_NAME] when unset. */
    val playerName: Flow<String>
    fun lessonDone(lessonId: String): Flow<Boolean>

    suspend fun setAnimationSpeed(value: AnimationSpeed)
    suspend fun setSortHandByDefault(value: Boolean)
    suspend fun setHoldTricks(value: Boolean)
    suspend fun setSoundVolume(value: Float)
    suspend fun setBotSkill(value: BotSkill)
    suspend fun setStickTheDealer(value: Boolean)
    suspend fun setDefendAlone(value: Boolean)
    suspend fun setBennyEnabled(value: Boolean)
    suspend fun setFarmersHand(value: Boolean)
    suspend fun setServerUrl(value: String)
    suspend fun setPlayerName(value: String)
    suspend fun setLessonDone(lessonId: String, value: Boolean)
}

/**
 * The whole repository over one [KeyValueStore]; both platforms share it, differing only in the
 * store they hand in.
 */
class KeyValueSettingsRepository(private val store: KeyValueStore) : SettingsRepository {
    override val animationSpeed: Flow<AnimationSpeed> =
        store.enumSetting(SettingsKeys.ANIMATION_SPEED, SettingsDefaults.ANIMATION_SPEED, AnimationSpeed::fromName)
    override val sortHandByDefault: Flow<Boolean> =
        store.booleanSetting(SettingsKeys.SORT_HAND_BY_DEFAULT, SettingsDefaults.SORT_HAND_BY_DEFAULT)
    override val holdTricks: Flow<Boolean> =
        store.booleanSetting(SettingsKeys.HOLD_TRICKS, SettingsDefaults.HOLD_TRICKS)
    override val soundVolume: Flow<Float> =
        store.floatSetting(SettingsKeys.SOUND_VOLUME, SettingsDefaults.SOUND_VOLUME)
    override val botSkill: Flow<BotSkill> =
        store.enumSetting(SettingsKeys.BOT_SKILL, SettingsDefaults.BOT_SKILL, BotSkill::fromName)
    override val stickTheDealer: Flow<Boolean> =
        store.booleanSetting(SettingsKeys.STICK_THE_DEALER, SettingsDefaults.STICK_THE_DEALER)
    override val defendAlone: Flow<Boolean> =
        store.booleanSetting(SettingsKeys.DEFEND_ALONE, SettingsDefaults.DEFEND_ALONE)
    override val bennyEnabled: Flow<Boolean> =
        store.booleanSetting(SettingsKeys.BENNY_ENABLED, SettingsDefaults.BENNY_ENABLED)
    override val farmersHand: Flow<Boolean> =
        store.booleanSetting(SettingsKeys.FARMERS_HAND, SettingsDefaults.FARMERS_HAND)

    // Blank falls back to the official server: a cleared field must not leave online play pointed at
    // an address that can never connect.
    override val serverUrl: Flow<String> = store.string(SettingsKeys.SERVER_URL)
        .map { stored -> stored?.takeIf { it.isNotBlank() } ?: SettingsDefaults.SERVER_URL }
    override val playerName: Flow<String> =
        store.stringSetting(SettingsKeys.PLAYER_NAME, SettingsDefaults.PLAYER_NAME)

    override fun lessonDone(lessonId: String): Flow<Boolean> =
        store.booleanSetting(SettingsKeys.LESSON_DONE_PREFIX + lessonId, false)

    override suspend fun setAnimationSpeed(value: AnimationSpeed) =
        store.putString(SettingsKeys.ANIMATION_SPEED, value.name)

    override suspend fun setSortHandByDefault(value: Boolean) =
        store.putBoolean(SettingsKeys.SORT_HAND_BY_DEFAULT, value)

    override suspend fun setHoldTricks(value: Boolean) = store.putBoolean(SettingsKeys.HOLD_TRICKS, value)

    override suspend fun setSoundVolume(value: Float) = store.putFloat(SettingsKeys.SOUND_VOLUME, value)

    override suspend fun setBotSkill(value: BotSkill) = store.putString(SettingsKeys.BOT_SKILL, value.name)

    override suspend fun setStickTheDealer(value: Boolean) =
        store.putBoolean(SettingsKeys.STICK_THE_DEALER, value)

    override suspend fun setDefendAlone(value: Boolean) = store.putBoolean(SettingsKeys.DEFEND_ALONE, value)

    override suspend fun setBennyEnabled(value: Boolean) = store.putBoolean(SettingsKeys.BENNY_ENABLED, value)

    override suspend fun setFarmersHand(value: Boolean) = store.putBoolean(SettingsKeys.FARMERS_HAND, value)

    override suspend fun setServerUrl(value: String) = store.putString(SettingsKeys.SERVER_URL, value.trim())

    override suspend fun setPlayerName(value: String) = store.putString(SettingsKeys.PLAYER_NAME, value)

    override suspend fun setLessonDone(lessonId: String, value: Boolean) =
        store.putBoolean(SettingsKeys.LESSON_DONE_PREFIX + lessonId, value)
}

/** The house-rule set a game is played under — the engine's constructor toggles, as one value. */
data class EuchreHouseRules(
    val stickTheDealer: Boolean = SettingsDefaults.STICK_THE_DEALER,
    val defendAlone: Boolean = SettingsDefaults.DEFEND_ALONE,
    val bennyEnabled: Boolean = SettingsDefaults.BENNY_ENABLED,
    val farmersHand: Boolean = SettingsDefaults.FARMERS_HAND,
)
