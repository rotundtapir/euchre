// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.content.Context
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The settings surface against the real DataStore backend: a switch flipped in the dialog must
 * reach storage, be readable through a freshly constructed [SettingsRepository], and still be there
 * after the activity is recreated.
 *
 * House rules are the right subject: unlike animation speed and sound volume they are never
 * overridden by the test fixture's intent extras, so the dialog shows the persisted value directly.
 */
@RunWith(AndroidJUnit4::class)
class SettingsPersistenceTest : EuchreUiTest() {

    private val settings: SettingsRepository
        get() = androidSettingsRepository(ApplicationProvider.getApplicationContext<Context>())

    @After
    fun restoreDefaults() = runBlocking {
        settings.setStickTheDealer(SettingsDefaults.STICK_THE_DEALER)
    }

    @Test
    fun houseRuleToggle_reachesStorage_andSurvivesRecreation() {
        rule.onNodeWithTag("settingsButton").performClick()
        val before = switchIsOn("stickTheDealer") == true

        rule.onNodeWithTag("stickTheDealer").performScrollTo().performClick()
        // The write round-trips through DataStore before the switch recomposes: wait, don't assert.
        rule.waitUntil(STEP_TIMEOUT_MS) { switchIsOn("stickTheDealer") == !before }

        // It really reached storage, not just the composition's local state.
        assertEquals(!before, runBlocking { settings.stickTheDealer.first() })

        rule.onNodeWithText("Done").performClick()
        rule.activityRule.scenario.recreate()

        // A rebuilt activity — new repository instance, new composition — reads the stored value.
        rule.waitUntil(STEP_TIMEOUT_MS) { nodesWithTag("settingsButton").isNotEmpty() }
        rule.onNodeWithTag("settingsButton").performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { switchIsOn("stickTheDealer") == !before }
        rule.onNodeWithText("Done").performClick()
    }

    @Test
    fun theSetupScreenAndTheCogEditTheSameSetting() {
        // The pre-game setup screen surfaces the same persisted house rules as the settings cog, so
        // a change made in one must be visible in the other.
        rule.onNodeWithText("Play with bots").performClick()
        val before = switchIsOn("setup:stickTheDealer") == true
        rule.onNodeWithTag("setup:stickTheDealer").performScrollTo().performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { switchIsOn("setup:stickTheDealer") == !before }

        rule.onNodeWithTag("botSetupBack").performScrollTo().performClick()
        rule.onNodeWithTag("settingsButton").performClick()
        rule.waitUntil(STEP_TIMEOUT_MS) { switchIsOn("stickTheDealer") == !before }
        rule.onNodeWithText("Done").performClick()
    }
}
