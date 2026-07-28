// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.graphics.scale
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rotundtapir.euchre.ui.FELT_TAG
import io.github.rotundtapir.euchre.ui.HAND_CARD_TAG_PREFIX
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Films the tutorial's opening — the primer's "Deal" through the scripted hand's first moments —
 * one PNG per frame, for the host-side harness in `scripts/frame-signatures.py` to judge.
 *
 * NOT an assertion suite. It captures; the predicates that decide whether the motion is broken
 * (something vanishing with no fade, something teleporting, something that should move standing
 * still) run on the host, where numpy is. Tagged `@Ignore`-free but harmless: it only writes files.
 *
 * Two things make the frames worth trusting. The clock is taken off wall time
 * ([AndroidComposeTestRule.mainClock] with `autoAdvance = false`), so frames land at exact
 * multiples of a frame interval rather than wherever the emulator's software renderer happened to
 * be — and, more importantly, so capture is *possible at all*: with the clock running, every
 * Compose interaction auto-waits for idle, and "idle" means no animation in flight, so a
 * mid-animation screenshot cannot be taken. Real time is nudged along beside it because the deal
 * animation's own pacing uses `delay`, which the virtual clock does not drive.
 *
 * Runs at [ANIMATION_SPEED] rather than OFF, which is the whole point: OFF short-circuits the code
 * under inspection. That makes this the one suite here that is *about* motion, so CI skips it
 * (`notClass=` on the android-e2e job in `.github/workflows/ci.yml`) and it is driven by hand when
 * a motion bug needs pinning down:
 *
 * ```
 * ./gradlew :app:assembleFossDebug :app:assembleFossDebugAndroidTest
 * adb install -r -g app/build/outputs/apk/foss/debug/app-foss-debug.apk
 * adb install -r -g app/build/outputs/apk/androidTest/foss/debug/app-foss-debug-androidTest.apk
 * adb shell am instrument -w -e class io.github.rotundtapir.euchre.TutorialFrameCapture \
 *   io.github.rotundtapir.euchre.test/androidx.test.runner.AndroidJUnitRunner
 * adb pull /sdcard/Android/data/io.github.rotundtapir.euchre/files/frames .
 * python3 scripts/frame-signatures.py frames
 * ```
 *
 * `am instrument` rather than the Gradle task deliberately: connectedAndroidTest uninstalls the app
 * when it finishes, and the frames live in the app's own external files dir, so they go with it.
 */
@RunWith(AndroidJUnit4::class)
class TutorialFrameCapture {

    @get:Rule
    val rule = AndroidComposeTestRule(
        activityRule = ActivityScenarioRule<MainActivity>(
            Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_ANIMATION_SPEED, ANIMATION_SPEED)
                .putExtra(MainActivity.EXTRA_SOUND_VOLUME, 0f),
        ),
        activityProvider = { scenarioRule ->
            var activity: MainActivity? = null
            scenarioRule.scenario.onActivity { activity = it }
            activity!!
        },
    )

    @Test
    fun filmTheOpeningOfLessonOne() {
        val outDir = framesDir("frames")

        // Get to the primer with the clock still running: none of this is what we are filming.
        rule.onNodeWithTag("walkthroughButton").performClick()
        rule.waitUntil(TIMEOUT_MS) { tagPresent("lessonPicker") }
        rule.onNodeWithTag("lesson:basics").performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { tagPresent("tutorialPrimerNext") }
        while (tagPresent("tutorialPrimerNext")) {
            rule.onNodeWithTag("tutorialPrimerNext").performClick()
            rule.waitForIdle()
        }

        // From here on the clock is ours, so the deal cannot outrun the camera.
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("tutorialPrimerStart").performClick()

        // Advance the animation clock by however long the last capture actually took, so the two
        // clocks stay together. Compose animations run on the virtual clock; the deal's own pacing
        // and the bots' beats run on `delay`, i.e. real time. Advancing a fixed 50ms while a
        // capture costs 400ms lets real time sprint ahead — the first version of this did exactly
        // that, and two bot plays landed inside a single captured step, which reads as a bug in
        // the app when it is a bug in the camera.
        filmFrames(outDir, FRAMES)
    }

    private fun framesDir(name: String): File = File(
        InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
        name,
    ).apply {
        deleteRecursively()
        mkdirs()
    }

    private fun filmFrames(outDir: File, frames: Int) {
        var previous = SystemClock.uptimeMillis()
        repeat(frames) { i ->
            val now = SystemClock.uptimeMillis()
            rule.mainClock.advanceTimeBy((now - previous).coerceAtLeast(1L))
            previous = now
            capture(outDir, i)
        }
    }

    /**
     * Half-size PNGs: encoding a full 1080x2400 frame costs more wall time than the animation
     * being filmed, and the predicates only need to see regions move, appear and vanish.
     */
    private fun capture(dir: File, index: Int) {
        val full = rule.onRoot().captureToImage().asAndroidBitmap()
        val small = full.scale(full.width / 2, full.height / 2)
        File(dir, "frame_%03d.png".format(index)).outputStream().use { out ->
            small.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        small.recycle()
    }

    /** Films from the tap on "Play" to the first frames of a bot game's deal. */
    @Test
    fun filmStartingABotGame() {
        val outDir = framesDir("frames-newgame")
        rule.onNodeWithTag("playWithBotsButton").performClick()
        rule.waitUntil(TIMEOUT_MS) { tagPresent("startBotGame") }

        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag("startBotGame").performClick()
        filmFrames(outDir, NEW_GAME_FRAMES)
    }

    /**
     * Films the way out of a finished lesson — the epilogue's "Next" through to the lesson picker.
     *
     * Screen recording rather than [captureToImage] here, because the picker is a Dialog: it is a
     * separate window, invisible to a capture of the composition root. `screenrecord` sees whatever
     * the display shows, dialogs included, and it runs at the device's own frame rate, so this is a
     * faithful (if wall-clock) film where the deal capture is a deterministic one.
     */
    @Test
    fun filmTheReturnToTheLessonPicker() {
        playLessonOneToItsEpilogue()

        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("screenrecord --time-limit $RECORD_SECONDS $RECORDING")
        Thread.sleep(SPIN_UP_MILLIS) // the recorder takes a moment to actually start writing
        rule.onNodeWithTag("tutorialCompleteContinue").performClick()
        Thread.sleep(RECORD_SECONDS * 1000L) // let the clip run to its own time limit
    }

    /** Plays lesson 1's five scripted tricks and pages its epilogue, leaving "Next" on screen. */
    private fun playLessonOneToItsEpilogue() {
        rule.onNodeWithTag("walkthroughButton").performClick()
        rule.waitUntil(TIMEOUT_MS) { tagPresent("lessonPicker") }
        rule.onNodeWithTag("lesson:basics").performScrollTo().performClick()
        rule.waitUntil(TIMEOUT_MS) { tagPresent("tutorialPrimerNext") }
        while (tagPresent("tutorialPrimerNext")) {
            rule.onNodeWithTag("tutorialPrimerNext").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("tutorialPrimerStart").performClick()

        // Answer whatever the lesson asks until its last page is up. Unlike TutorialFlowTest this
        // runs at SLOW, where the pacing is live: a lesson always holds completed tricks, so every
        // trick waits for a tap on the felt that an OFF-speed run never has to make.
        var guard = 0
        while (!tagPresent("tutorialCompleteContinue") && guard++ < MAX_STEPS) {
            when {
                tagPresent("tutorialEpilogueNext") -> rule.onNodeWithTag("tutorialEpilogueNext").performClick()
                tagPresent("handResultContinue") -> rule.onNodeWithTag("handResultContinue").performClick()
                heldTrick().fetchSemanticsNodes().isNotEmpty() -> heldTrick()[0].performClick()
                playableCards().fetchSemanticsNodes().size == 1 ->
                    playableCards()[0].performScrollTo().performClick()
                else -> Thread.sleep(POLL_MILLIS) // a bot is thinking
            }
            rule.waitForIdle()
        }
        check(tagPresent("tutorialCompleteContinue")) { "lesson never reached its last page" }
    }

    /** The felt, when it is holding a completed trick and waiting to be tapped away. */
    private fun heldTrick() = rule.onAllNodes(hasTestTag(FELT_TAG) and hasClickAction(), useUnmergedTree = true)

    /** The cards the lesson currently allows — exactly one, at every step of lesson 1. */
    private fun playableCards() = rule.onAllNodes(
        hasClickAction() and
            SemanticsMatcher("in-hand card") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(HAND_CARD_TAG_PREFIX) == true
            },
        useUnmergedTree = true,
    )

    private fun tagPresent(tag: String): Boolean =
        rule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private companion object {
        /** SLOW stretches every stage, so a frame interval buys more of the motion. */
        const val ANIMATION_SPEED = "SLOW"
        /** However fast captures come; ~10-20fps in practice, which SLOW is stretched enough for. */
        const val FRAMES = 200
        const val TIMEOUT_MS = 20_000L

        /** Enough to cover the screen swap and the opening of the deal. */
        const val NEW_GAME_FRAMES = 40

        /**
         * Generous: at SLOW the lesson is a ~7s deal, four bot bids a beat apart, then five tricks
         * of bot beats and hold-taps — a couple of minutes of budget, spent only if something hangs.
         */
        const val MAX_STEPS = 400
        const val POLL_MILLIS = 250L

        const val RECORDING = "/sdcard/picker-transition.mp4"
        const val RECORD_SECONDS = 6
        const val SPIN_UP_MILLIS = 1500L
    }
}
