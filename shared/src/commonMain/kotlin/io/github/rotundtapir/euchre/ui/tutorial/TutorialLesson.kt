// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui.tutorial

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.ui.tutorial.NarrationLine
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialPage
import io.github.rotundtapir.cardkit.ui.tutorial.TutorialScriptState
import io.github.rotundtapir.cardkit.ui.tutorial.cardSpeechText
import io.github.rotundtapir.euchre.EuchreHouseRules

/**
 * The interactive "How to play": FOUR scripted lessons, each one real hand on a fixed seed replayed
 * through the normal [io.github.rotundtapir.euchre.EuchreViewModel] wiring, with a guidance bubble
 * explaining every human decision and ONLY the scripted action enabled — so the hand always follows
 * the script.
 *
 * Every lesson's trace was produced by `ai/src/jvmTest/.../TutorialTraceGenerator.kt` (kept,
 * @Disabled, as the tool that picked the seeds) and is gated by `TutorialScriptTest`: if an engine
 * or bot change alters a seed's trace, that test fails and the lesson's script AND prose must be
 * regenerated together.
 */

/** One human decision in a scripted lesson, with the advice shown before it is taken. */
sealed interface EuchreTutorialStep {
    val advice: String

    /** Show the trump pecking order (right bower, left bower, ace…) under the advice. */
    val showBowerOrder: Boolean get() = false

    /**
     * Round 1: order the up-card's suit up (the dealer picks it up), or pass. In the tutorial the
     * "Go alone" box must be ticked to match [alone] before the order-up button arms — ticking it
     * is the lesson.
     */
    data class Round1Step(
        val orderUp: Boolean,
        val alone: Boolean = false,
        override val advice: String,
        override val showBowerOrder: Boolean = false,
    ) : EuchreTutorialStep

    /** Round 2: name [call] as trump, or pass when it is null. */
    data class Round2Step(
        val call: Suit?,
        val alone: Boolean = false,
        override val advice: String,
        override val showBowerOrder: Boolean = false,
    ) : EuchreTutorialStep

    /** The dealer's bury after picking the up-card up: only [card] is selectable. */
    data class DiscardStep(val card: Card, override val advice: String) : EuchreTutorialStep

    /** A trick: only [card] is playable. */
    data class PlayStep(
        val card: Card,
        override val advice: String,
        override val showBowerOrder: Boolean = false,
    ) : EuchreTutorialStep
}

/**
 * One lesson: a pinned deal plus every word said about it.
 *
 * [id] is frozen — it keys the per-lesson completion flag in settings AND is the stem of every
 * narration clip name for the lesson, so renaming one orphans both. [seed] and [dealer] pin the
 * hand; [pinnedRules] pins the house rules it was scripted under (lessons ignore the player's
 * settings, or a toggle would change the script out from under them).
 */
@Suppress("LongParameterList") // a lesson IS its content: splitting the prose off only hides it
class TutorialLesson(
    val id: String,
    val ordinal: Int,
    val title: String,
    val subtitle: String,
    val seed: Long,
    val dealer: Seat,
    val prologue: List<TutorialPage>,
    val steps: List<EuchreTutorialStep>,
    val trickNotes: Map<Int, String>,
    val handDone: String,
    val epilogue: List<TutorialPage>,
    val completion: String,
    val pinnedRules: EuchreHouseRules = TUTORIAL_HOUSE_RULES,
)

/**
 * Every lesson is scripted with all four house rules OFF: the scripts teach base Euchre, and a
 * toggle the player happened to leave on would deal a different hand (Benny) or change the legal
 * actions at a prompt (stick the dealer).
 */
val TUTORIAL_HOUSE_RULES = EuchreHouseRules(
    stickTheDealer = false,
    defendAlone = false,
    bennyEnabled = false,
    farmersHand = false,
)

/**
 * The four lessons in teaching order. Their [TutorialLesson.id]s are frozen narration key stems.
 *
 * Built from functions, not from top-level `val`s in the content files: cross-file top-level
 * initialization order is not guaranteed on Kotlin/Wasm, and a null lesson here would be a
 * spectacularly confusing crash.
 */
val tutorialLessons: List<TutorialLesson> =
    listOf(basicsLesson(), biddingLesson(), aloneLesson(), defenseLesson())

/** The lesson [id] identifies, or null. */
fun tutorialLesson(id: String): TutorialLesson? = tutorialLessons.firstOrNull { it.id == id }

/** The live script state a lesson hands the game screen. */
typealias EuchreTutorialState = TutorialScriptState<EuchreTutorialStep>

// --- Narration ----------------------------------------------------------------------------------

/**
 * Every display text the tutorial can show, paired with the stable id of its (future) narration
 * clip: `"{lessonId}-primer-N"`, `"-step-N"`, `"-trick-N"`, `"-epilogue-N"`, `"-completion"`,
 * `"-hand-done"`. No audio ships in v0.1.0 — the ids exist so clips can be generated and dropped in
 * without touching a single line of script. `TutorialNarrationTest` gates them: ids unique, and
 * display texts unique across ALL lessons (the lookup below is BY TEXT, so a duplicate would
 * silently narrate the wrong clip).
 */
val tutorialNarrationSources: List<Pair<String, String>> = buildList {
    for (lesson in tutorialLessons) {
        lesson.prologue.forEachIndexed { i, page -> add("${lesson.id}-primer-${i + 1}" to page.body) }
        lesson.steps.forEachIndexed { i, step -> add("${lesson.id}-step-${i + 1}" to step.advice) }
        lesson.trickNotes.entries.sortedBy { it.key }
            .forEach { (n, note) -> add("${lesson.id}-trick-$n" to note) }
        lesson.epilogue.forEachIndexed { i, page -> add("${lesson.id}-epilogue-${i + 1}" to page.body) }
        add("${lesson.id}-completion" to lesson.completion)
        add("${lesson.id}-hand-done" to lesson.handDone)
    }
}

private val narrationIdByDisplay: Map<String, String> =
    tutorialNarrationSources.associate { (id, display) -> display to id }

/**
 * The narration clip id for a display text the tutorial is showing, or null for dynamic texts (the
 * bubble's interpolated fallbacks) that will have no pre-generated clip.
 */
fun narrationIdFor(displayText: String): String? = narrationIdByDisplay[displayText]

/**
 * Phrase-level speech fixes where a synthesizer stumbles on the written form. RULE (inherited from
 * cardkit): a substitution may only change how the SAME words are rendered — never insert words the
 * screen does not show, or the voice audibly diverges for anyone reading along.
 */
private val EUCHRE_SPEECH_SUBSTITUTIONS = listOf(
    // "9, 10, J, Q, K, A" as a bare list draws a stumble on the run of numerals.
    "9, 10, J, Q, K, A" to "nine, ten, jack, queen, king, ace",
)

/**
 * Every narration line as a synthesizer should speak it. Nothing consumes this yet; it is the
 * hand-off point for the clip generator when tutorial audio lands.
 */
val tutorialNarration: List<NarrationLine> = tutorialNarrationSources.map { (id, display) ->
    NarrationLine(id, cardSpeechText(display, EUCHRE_SPEECH_SUBSTITUTIONS))
}

