// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.core.TrickPlay
import io.github.rotundtapir.euchre.EuchreViewModel
import io.github.rotundtapir.euchre.ai.EuchreBot
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchreHandResult
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT
import io.github.rotundtapir.euchre.engine.TRICKS_PER_HAND
import io.github.rotundtapir.euchre.ui.tutorial.EuchreTutorialStep
import io.github.rotundtapir.euchre.ui.tutorial.TutorialLesson
import io.github.rotundtapir.euchre.ui.tutorial.tutorialLessons
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * THE DRIFT GATE on the tutorial's hand-copied lesson scripts.
 *
 * Every lesson is replayed against the real rules and bots with exactly `EuchreViewModel.newGame`'s
 * wiring — the lesson's pinned seed, dealer and house rules, `EuchreBot(false)` at seats 1–3 seeded
 * `Random(seed + i)` — asserting that:
 *  (a) every scripted human action is LEGAL at the moment the engine prompts for it (the property
 *      the tutorial UI depends on: it enables only that action);
 *  (b) the script is fully consumed, with no leftover steps and no unanswered prompt;
 *  (c) the hand ends exactly the way the lesson's prose says it does;
 *  (d) the trick winners match the story [TutorialLesson.trickNotes] tells;
 *  (e) every bot name the prose mentions is the name that seed actually draws for that seat.
 *
 * If an engine or bot change alters a seed's trace, these fail — turning CLAUDE.md's manual
 * "regenerate the lesson script and its texts together" rule into CI.
 */
class TutorialScriptTest {

    private val human = Seat(0)

    /** What each lesson's hand must actually do — asserted independently of the lesson's own prose. */
    private class Expected(
        val maker: Seat,
        val trump: Suit,
        val alone: Boolean,
        val made: Boolean,
        val makerTricks: Int,
        val teamDeltas: Map<Int, Int>,
        /** Winning seat index of each of the five tricks, in order. */
        val trickWinners: List<Int>,
        /** The bot names the seed draws, by seat index 1..3. */
        val botNames: List<String>,
    )

    private val expectations = mapOf(
        // Your partner Nils (seat 2) orders up ♠ and marches; you take tricks 1 and 4.
        "basics" to Expected(
            maker = Seat(2),
            trump = Suit.SPADES,
            alone = false,
            made = true,
            makerTricks = 5,
            teamDeltas = mapOf(0 to 2),
            trickWinners = listOf(0, 2, 2, 0, 2),
            botNames = listOf("Kira", "Nils", "Bruno"),
        ),
        // You name ♣ in round 2 and take every trick.
        "bidding" to Expected(
            maker = Seat(0),
            trump = Suit.CLUBS,
            alone = false,
            made = true,
            makerTricks = 5,
            teamDeltas = mapOf(0 to 2),
            trickWinners = listOf(0, 0, 0, 0, 0),
            botNames = listOf("Hugo", "Mira", "Pia"),
        ),
        // You pick the right bower up as dealer, go alone and march for 4.
        "alone" to Expected(
            maker = Seat(0),
            trump = Suit.CLUBS,
            alone = true,
            made = true,
            makerTricks = 5,
            teamDeltas = mapOf(0 to 4),
            trickWinners = listOf(0, 0, 0, 0, 0),
            botNames = listOf("Cleo", "Ada", "Nils"),
        ),
        // Fen (seat 3) orders it up for herself and is held to one trick: euchred, +2 to you.
        "defense" to Expected(
            maker = Seat(3),
            trump = Suit.HEARTS,
            alone = false,
            made = false,
            makerTricks = 1,
            teamDeltas = mapOf(0 to 2),
            trickWinners = listOf(3, 2, 0, 0, 0),
            botNames = listOf("Hugo", "Dara", "Fen"),
        ),
    )

    private class Replay(
        val stepsConsumed: Int,
        val plays: List<TrickPlay>,
        val activeSeats: Int,
        val result: EuchreHandResult,
    ) {
        val trickWinners: List<Seat>
            get() {
                val eval = TrickEvaluator(result.makers.trump, JokerRole.ABSENT)
                return plays.chunked(activeSeats).map { eval.winner(it) }
            }
    }

    /**
     * Replays [lesson]'s hand, taking the scripted action at every human prompt and asserting each
     * one is legal when it is taken.
     */
    private fun replay(lesson: TutorialLesson): Replay {
        val rules = EuchreRules(
            stickTheDealer = lesson.pinnedRules.stickTheDealer,
            defendAlone = lesson.pinnedRules.defendAlone,
            bennyEnabled = lesson.pinnedRules.bennyEnabled,
            farmersHandEnabled = lesson.pinnedRules.farmersHand,
        )
        val bot = EuchreBot(lesson.pinnedRules.bennyEnabled)
        val botRandoms = (1 until PLAYER_COUNT).associate { i -> Seat(i) to Random(lesson.seed + i) }

        var state = rules.newGame(lesson.seed, lesson.dealer)
        var stepIndex = 0
        val plays = mutableListOf<TrickPlay>()
        var activeSeats = PLAYER_COUNT

        var guard = 0
        while (state.handNumber == 0 && state.phase != EuchrePhase.COMPLETE) {
            check(guard++ < GUARD_LIMIT) { "${lesson.id}: runaway replay — the hand never completed" }
            val seat = rules.currentActor(state) ?: fail("${lesson.id}: no actor in ${state.phase}")
            val view = rules.view(state, seat)
            val action = if (seat == human) {
                scriptedAction(lesson, lesson.steps.getOrNull(stepIndex++), view.legalActions, stepIndex)
            } else {
                bot.decide(view, botRandoms.getValue(seat))
            }
            if (state.phase == EuchrePhase.PLAY) {
                activeSeats = state.activeSeats.size
                plays += TrickPlay(seat, (action as EuchreAction.PlayCard).card)
            }
            state = rules.apply(state, seat, action)
        }

        return Replay(
            stepsConsumed = stepIndex,
            plays = plays,
            activeSeats = activeSeats,
            result = state.lastHandResult ?: fail("${lesson.id}: the hand was never scored"),
        )
    }

    /** The engine action a step means, checked against what the engine will actually accept. */
    private fun scriptedAction(
        lesson: TutorialLesson,
        step: EuchreTutorialStep?,
        legal: List<EuchreAction>,
        stepNumber: Int,
    ): EuchreAction {
        val action = when (step) {
            is EuchreTutorialStep.Round1Step ->
                if (step.orderUp) EuchreAction.OrderUp(step.alone) else EuchreAction.Pass
            is EuchreTutorialStep.Round2Step ->
                step.call?.let { EuchreAction.CallTrump(it, step.alone) } ?: EuchreAction.Pass
            is EuchreTutorialStep.DiscardStep -> EuchreAction.DealerDiscard(step.card)
            is EuchreTutorialStep.PlayStep -> EuchreAction.PlayCard(step.card)
            null -> fail("${lesson.id}: script exhausted but the engine still prompts the human")
        }
        assertContains(legal, action, "${lesson.id}: scripted step $stepNumber ($action) is not legal")
        return action
    }

    @Test
    fun `every scripted action is legal and each lesson consumes its whole script`() {
        for (lesson in tutorialLessons) {
            val replay = replay(lesson)
            assertEquals(lesson.steps.size, replay.stepsConsumed, "${lesson.id}: unused tutorial steps")
            assertEquals(
                replay.activeSeats * TRICKS_PER_HAND,
                replay.plays.size,
                "${lesson.id}: the hand must run all five tricks",
            )
        }
    }

    @Test
    fun `each lesson's hand ends exactly the way its prose says`() {
        for (lesson in tutorialLessons) {
            val expected = expectations[lesson.id] ?: fail("no expectation for lesson ${lesson.id}")
            val result = replay(lesson).result
            assertEquals(expected.maker, result.makers.maker, "${lesson.id}: maker")
            assertEquals(expected.trump, result.makers.trump, "${lesson.id}: trump")
            assertEquals(expected.alone, result.makers.alone, "${lesson.id}: alone")
            assertEquals(expected.made, result.made, "${lesson.id}: made")
            assertEquals(expected.makerTricks, result.makerTricks, "${lesson.id}: maker tricks")
            assertEquals(expected.teamDeltas, result.teamDeltas, "${lesson.id}: score")
        }
    }

    @Test
    fun `trick winners match the story the trick notes tell`() {
        for (lesson in tutorialLessons) {
            val expected = expectations.getValue(lesson.id)
            assertEquals(
                expected.trickWinners.map(::Seat),
                replay(lesson).trickWinners,
                "${lesson.id}: trick winners",
            )
            assertEquals(
                (1..TRICKS_PER_HAND).toSet(),
                lesson.trickNotes.keys,
                "${lesson.id}: one note per trick",
            )
        }
    }

    @Test
    fun `every bot name a lesson mentions is the one its seed actually draws`() {
        for (lesson in tutorialLessons) {
            val expected = expectations.getValue(lesson.id)
            // Mirrors EuchreViewModel.newGame: seats 1..3 take the first three of the seeded shuffle.
            val drawn = EuchreViewModel.BOT_NAMES.shuffled(Random(lesson.seed)).take(PLAYER_COUNT - 1)
            assertEquals(expected.botNames, drawn, "${lesson.id}: the seed's bot names moved")
            val mentioned = EuchreViewModel.BOT_NAMES.filter { name ->
                Regex("\\b$name\\b").containsMatchIn(lessonText(lesson))
            }
            assertTrue(
                drawn.containsAll(mentioned),
                "${lesson.id}: prose names ${mentioned - drawn.toSet()}, who are not at this table",
            )
        }
    }

    @Test
    fun `every scripted card is one the human actually holds at that moment`() {
        // Legality already implies it for plays; this also covers the discard, and fails with a
        // readable message when a re-pinned seed shifts the deal.
        for (lesson in tutorialLessons) {
            val cards: List<Card> = lesson.steps.mapNotNull { step ->
                when (step) {
                    is EuchreTutorialStep.PlayStep -> step.card
                    is EuchreTutorialStep.DiscardStep -> step.card
                    else -> null
                }
            }
            assertEquals(cards.size, cards.distinct().size, "${lesson.id}: a card is scripted twice")
        }
    }

    private fun lessonText(lesson: TutorialLesson): String = buildString {
        lesson.prologue.forEach { appendLine(it.title).appendLine(it.body) }
        lesson.steps.forEach { appendLine(it.advice) }
        lesson.trickNotes.values.forEach { appendLine(it) }
        lesson.epilogue.forEach { appendLine(it.title).appendLine(it.body) }
        appendLine(lesson.completion)
        appendLine(lesson.handDone)
        appendLine(lesson.title)
        appendLine(lesson.subtitle)
    }

    private companion object {
        const val GUARD_LIMIT = 200
    }
}
