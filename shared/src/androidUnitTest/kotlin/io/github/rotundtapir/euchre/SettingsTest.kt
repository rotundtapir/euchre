// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.cardkit.ui.settings.InMemoryKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

/**
 * [KeyValueSettingsRepository] over an in-memory store: every setting must fall back to its
 * documented default when nothing is stored, and round-trip once written.
 */
class KeyValueSettingsRepositoryTest {

    @Test
    fun `an empty store yields every documented default`() = runTest {
        val repo = KeyValueSettingsRepository(InMemoryKeyValueStore())
        assertEquals(SettingsDefaults.ANIMATION_SPEED, repo.animationSpeed.first())
        assertEquals(SettingsDefaults.SORT_HAND_BY_DEFAULT, repo.sortHandByDefault.first())
        assertEquals(SettingsDefaults.HOLD_TRICKS, repo.holdTricks.first())
        assertEquals(SettingsDefaults.SOUND_VOLUME, repo.soundVolume.first())
        assertEquals(SettingsDefaults.BOT_SKILL, repo.botSkill.first())
        assertEquals(SettingsDefaults.DEFEND_ALONE, repo.defendAlone.first())
        assertEquals(SettingsDefaults.BENNY_ENABLED, repo.bennyEnabled.first())
        assertEquals(SettingsDefaults.FARMERS_HAND, repo.farmersHand.first())
        assertFalse(repo.lessonDone("basics").first())
    }

    @Test
    fun `stick the dealer defaults to on`() = runTest {
        // A hand where everyone passes twice is a dull throw-in, so this one starts enabled — and
        // the default is load-bearing for the bidding UI (the dealer's Pass button disappears).
        assertTrue(SettingsDefaults.STICK_THE_DEALER)
        assertTrue(KeyValueSettingsRepository(InMemoryKeyValueStore()).stickTheDealer.first())
    }

    @Test
    fun `every setting round-trips through the repository`() = runTest {
        val repo = KeyValueSettingsRepository(InMemoryKeyValueStore())
        repo.setAnimationSpeed(AnimationSpeed.FAST)
        repo.setSortHandByDefault(true)
        repo.setHoldTricks(true)
        repo.setSoundVolume(0.25f)
        repo.setBotSkill(BotSkill.ADVANCED)
        repo.setStickTheDealer(false)
        repo.setDefendAlone(true)
        repo.setBennyEnabled(true)
        repo.setFarmersHand(true)
        repo.setLessonDone("bidding", true)

        assertEquals(AnimationSpeed.FAST, repo.animationSpeed.first())
        assertTrue(repo.sortHandByDefault.first())
        assertTrue(repo.holdTricks.first())
        assertEquals(0.25f, repo.soundVolume.first())
        assertEquals(BotSkill.ADVANCED, repo.botSkill.first())
        assertFalse(repo.stickTheDealer.first())
        assertTrue(repo.defendAlone.first())
        assertTrue(repo.bennyEnabled.first())
        assertTrue(repo.farmersHand.first())
        assertTrue(repo.lessonDone("bidding").first())
        assertFalse(repo.lessonDone("defense").first(), "lesson flags are per-lesson, not global")
    }

    @Test
    fun `enums persist by exact entry name under their frozen keys`() = runTest {
        // The stored strings are the wire format shared with existing installs: renaming a key or
        // an entry silently resets that setting for everyone.
        val store = InMemoryKeyValueStore()
        val repo = KeyValueSettingsRepository(store)
        repo.setAnimationSpeed(AnimationSpeed.SLOW)
        repo.setBotSkill(BotSkill.ADVANCED)
        assertEquals("SLOW", store.string(SettingsKeys.ANIMATION_SPEED).first())
        assertEquals("ADVANCED", store.string(SettingsKeys.BOT_SKILL).first())
    }

    @Test
    fun `an unrecognised stored enum degrades to the default instead of crashing`() = runTest {
        val store = InMemoryKeyValueStore()
        store.putString(SettingsKeys.ANIMATION_SPEED, "TURBO")
        store.putString(SettingsKeys.BOT_SKILL, "GRANDMASTER")
        val repo = KeyValueSettingsRepository(store)
        assertEquals(SettingsDefaults.ANIMATION_SPEED, repo.animationSpeed.first())
        assertEquals(SettingsDefaults.BOT_SKILL, repo.botSkill.first())
    }

    @Test
    fun `the default house-rule set matches the stored defaults`() {
        // EuchreHouseRules() is what a game starts with before settings load; drift here would mean
        // the first frame of a game ran under different rules than the user's saved ones.
        val rules = EuchreHouseRules()
        assertEquals(SettingsDefaults.STICK_THE_DEALER, rules.stickTheDealer)
        assertEquals(SettingsDefaults.DEFEND_ALONE, rules.defendAlone)
        assertEquals(SettingsDefaults.BENNY_ENABLED, rules.bennyEnabled)
        assertEquals(SettingsDefaults.FARMERS_HAND, rules.farmersHand)
    }
}
