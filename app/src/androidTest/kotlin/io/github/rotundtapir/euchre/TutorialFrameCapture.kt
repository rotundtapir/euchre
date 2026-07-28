// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.content.Intent
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.core.graphics.scale
import androidx.compose.ui.graphics.asAndroidBitmap
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
 * under inspection. That makes this the one suite here that is *about* motion, so it is excluded
 * from the CI run (see the `-Pandroid.testInstrumentationRunnerArguments.notClass` in
 * `scripts/capture-tutorial-frames.sh`) and driven by hand when a motion bug needs pinning down.
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
        val outDir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "frames",
        ).apply {
            deleteRecursively()
            mkdirs()
        }

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
        var previous = SystemClock.uptimeMillis()
        repeat(FRAMES) { i ->
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

    private fun tagPresent(tag: String): Boolean =
        rule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true)
            .fetchSemanticsNodes().isNotEmpty()

    private companion object {
        /** SLOW stretches every stage, so a frame interval buys more of the motion. */
        const val ANIMATION_SPEED = "SLOW"
        /** However fast captures come; ~10-20fps in practice, which SLOW is stretched enough for. */
        const val FRAMES = 200
        const val TIMEOUT_MS = 20_000L
    }
}
