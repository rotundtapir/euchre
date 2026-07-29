// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.net.InetSocketAddress
import java.net.Socket

/**
 * On-device tests of the online flow against a real, host-run game server — the Android counterpart
 * to the web's `online.spec.ts`: entry → create a lobby → the server's LobbyState renders → Start
 * fills the empty seats with bots and a real game begins. Plus the invite link's *share* side, which
 * only exists on Android (an `ACTION_SEND` chooser rather than a clipboard copy).
 *
 * Requires a server on the host: `DEV_MODE=true ./gradlew :server:run` — the emulator reaches it at
 * `ws://10.0.2.2:8080`. Without one these skip via [assumeTrue] rather than fail, so the rest of the
 * connected suite still runs on a plain emulator and CI stays green without a server step.
 *
 * Name-ordered: the share test opens the system share sheet, which is system UI a Compose test
 * cannot reliably dismiss, so it must run after the game-flow test.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OnlineFlowTest {

    companion object {
        /** The host machine as the emulator sees it. */
        private const val SERVER_HOST = "10.0.2.2"
        private const val SERVER_PORT = 8080
        private const val SERVER_URL = "ws://$SERVER_HOST:$SERVER_PORT"
        private const val REACH_TIMEOUT_MS = 2_000
        private const val STEP_TIMEOUT_MS = 30_000L
        private const val JOIN_CODE_LENGTH = 4
    }

    @get:Rule
    val rule = AndroidComposeTestRule(
        activityRule = ActivityScenarioRule<MainActivity>(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ANIMATION_SPEED, "OFF")
                .putExtra(MainActivity.EXTRA_SOUND_VOLUME, 0f)
                .putExtra(MainActivity.EXTRA_SERVER_URL, SERVER_URL)
                // Prefills the display name (the mirror of the web's ?playerName=). The name gates
                // the create/join buttons, so without it the test cannot leave the entry screen —
                // and typing into the Compose canvas is what these overrides exist to avoid.
                .putExtra(MainActivity.EXTRA_PLAYER_NAME, "Tester"),
        ),
        activityProvider = { scenarioRule ->
            var activity: MainActivity? = null
            scenarioRule.scenario.onActivity { activity = it }
            activity!!
        },
    )

    @Before
    fun requireLocalServer() {
        val reachable = runCatching {
            Socket().use { it.connect(InetSocketAddress(SERVER_HOST, SERVER_PORT), REACH_TIMEOUT_MS) }
        }.isSuccess
        assumeTrue(
            "No game server at $SERVER_URL — start one on the host: DEV_MODE=true ./gradlew :server:run",
            reachable,
        )
    }

    private fun waitForTag(tag: String) = rule.waitUntil(STEP_TIMEOUT_MS) {
        rule.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    }

    /** Enter online mode and create a lobby, returning the join code the server assigned. */
    private fun createLobby(): String {
        rule.onNodeWithText("Play with friends").performClick()
        // Enabled only once the prefilled name has reached the field.
        rule.waitUntil(STEP_TIMEOUT_MS) {
            rule.onAllNodes(hasTestTag("createLobby") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("createLobby").performClick()
        rule.onNodeWithTag("confirmCreate").performClick()
        // The server replied with a LobbyState: the join code is on screen.
        waitForTag("lobbyCode")
        return rule.onNodeWithTag("lobbyCode")
            .fetchSemanticsNode().config[SemanticsProperties.Text].first().text
    }

    @Test
    fun test1_createLobby_startGame_dealsARealHandAgainstBots() {
        val code = createLobby()
        assertEquals("join code '$code' should be 4 characters", JOIN_CODE_LENGTH, code.length)
        // The lobby column scrolls on short screens; bring Start into the viewport before tapping.
        rule.onNodeWithTag("startGame").performScrollTo().performClick()
        // The three empty seats became bots and the deal ran. Asserted via the emote button, which
        // only the online game screen has — so its presence means we are in a *networked* game, not
        // merely that some game screen appeared. Deliberately not "wait for a bid prompt": euchre's
        // auction can end before it reaches the human (a bot orders up), so no particular prompt is
        // guaranteed — the same trap the web spec fell into.
        waitForTag("emoteButton")
    }

    @Test
    fun test2_shareInviteLink_firesASendChooserCarryingTheJoinUrl() {
        Intents.init()
        try {
            val code = createLobby()
            rule.onNodeWithTag("shareInvite").performClick()
            intended(
                allOf(
                    hasAction(Intent.ACTION_CHOOSER),
                    hasExtra(
                        equalTo(Intent.EXTRA_INTENT),
                        allOf(
                            hasAction(Intent.ACTION_SEND),
                            hasExtra(equalTo(Intent.EXTRA_TEXT), containsString("?joinCode=$code")),
                        ),
                    ),
                ),
            )
        } finally {
            Intents.release()
            // Close the system share sheet the tap opened so it doesn't linger over later suites.
            InstrumentationRegistry.getInstrumentation().uiAutomation
                .performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        }
    }
}
