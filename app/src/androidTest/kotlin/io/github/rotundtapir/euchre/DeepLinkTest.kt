// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device test of the Android invite-link deep link: launching [MainActivity] with the App Links
 * VIEW intent (`https://rotundtapir.github.io/euchre/?joinCode=…`) must route straight into online
 * mode on the Join screen with the code prefilled.
 *
 * This is the Android-only seam — `intent.data` parsing in [MainActivity] → `joinCodeOverride` → the
 * online ViewModel's join entry — that neither the shared unit tests nor the web e2e can cover. Note
 * it needs no game server: the Join screen renders from the link alone, whether or not the background
 * connection ever succeeds.
 */
@RunWith(AndroidJUnit4::class)
class DeepLinkTest {

    @get:Rule
    val rule = AndroidComposeTestRule(
        activityRule = ActivityScenarioRule<MainActivity>(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://rotundtapir.github.io/euchre/?joinCode=ABCD"),
                ApplicationProvider.getApplicationContext(),
                MainActivity::class.java,
            )
                .putExtra(MainActivity.EXTRA_ANIMATION_SPEED, "OFF")
                .putExtra(MainActivity.EXTRA_SOUND_VOLUME, 0f)
                // A dead port on purpose: entering online mode must never dial the real server from
                // a test, and the Join screen does not wait on the connection to render.
                .putExtra(MainActivity.EXTRA_SERVER_URL, "ws://10.0.2.2:59999")
                .putExtra(MainActivity.EXTRA_PLAYER_NAME, "Tester"),
        ),
        activityProvider = { scenarioRule ->
            var activity: MainActivity? = null
            scenarioRule.scenario.onActivity { activity = it }
            activity!!
        },
    )

    @Test
    fun deepLinkOpensJoinScreenWithCodePrefilled() {
        // The switch to online/join happens in a LaunchedEffect once composed — wait for it.
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithText("Join a game").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Join a game").assertIsDisplayed()
        rule.onNodeWithTag("joinCode").assertTextContains("ABCD")
        rule.onNodeWithTag("confirmJoin").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT_MS = 20_000L
    }
}
