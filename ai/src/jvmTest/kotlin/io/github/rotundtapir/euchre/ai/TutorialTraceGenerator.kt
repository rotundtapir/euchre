// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.JokerRole
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.SuitedCard
import io.github.rotundtapir.cardkit.core.TrickEvaluator
import io.github.rotundtapir.cardkit.core.TrickPlay
import io.github.rotundtapir.euchre.engine.EUCHRE_SEATS
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchreHandResult
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.HAND_SIZE
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT
import io.github.rotundtapir.euchre.engine.partnerOf
import java.io.File
import kotlin.random.Random
import kotlin.test.Test
import org.junit.jupiter.api.Disabled

/**
 * THROWAWAY GENERATOR — a tool, not a gate. It is how the four scripted lessons in
 * `shared/src/commonMain/kotlin/io/github/rotundtapir/euchre/ui/tutorial/` were produced, and how
 * a replacement seed would be re-picked if an engine or bot change ever invalidated one.
 *
 * Each lesson is a single hand on a pinned seed, replayed through exactly the wiring
 * `EuchreViewModel.newGame` uses: `EuchreRules()` with every house rule off, bots at the other
 * three seats as `StrategyPlayer(EuchreBot(false), Random(seed + i))`, bot names from
 * `BOT_NAMES.shuffled(Random(seed))`, and a per-lesson first dealer. The human seat is driven by a
 * per-lesson stand-in policy (see [humanAction]); whatever line it produces is transcribed into
 * that lesson's step list by hand, and `TutorialScriptTest` then gates the transcription.
 *
 * [search] scans a seed range for candidates satisfying each lesson's acceptance predicate and
 * prints the best few; [generate] re-renders the four SHIPPED seeds to
 * `build/tutorial-trace-<lesson>.txt` so the prose can be checked against the real trace.
 *
 * Re-enable (remove @Disabled) and run with:
 *   ./gradlew :ai:jvmTest --tests "*TutorialTraceGenerator*"
 */
@Disabled("one-shot generator for the tutorial lesson scripts; not a regression test")
class TutorialTraceGenerator {

    /**
     * Must mirror `EuchreViewModel.BOT_NAMES` exactly. Copied rather than imported because `:ai`
     * must never depend on `:shared` (the same split 500's generator lives with); the drift gate
     * over in `:shared` asserts every bot name a lesson's prose uses against the real pool.
     */
    private val botNamesPool = listOf(
        "Ada", "Bruno", "Cleo", "Dara", "Enzo", "Fen", "Greta", "Hugo",
        "Iris", "Jonas", "Kira", "Lars", "Mira", "Nils", "Olive", "Pia",
    )

    /** The seeds the four lessons ship with. Re-render them with [generate]. */
    private val shipped = mapOf(
        Lesson.BASICS to 3929L,
        Lesson.BIDDING to 1L,
        Lesson.ALONE to 830L,
        Lesson.DEFENSE to 938L,
    )

    @Test
    fun generate() {
        for ((lesson, seed) in shipped) {
            val trace = trace(lesson, seed) ?: error("${lesson.id}: seed $seed no longer qualifies")
            val text = render(trace)
            println(text)
            File("build/tutorial-trace-${lesson.id}.txt").apply { parentFile.mkdirs() }.writeText(text)
        }
    }

    @Test
    fun search() {
        val report = buildString {
            for (lesson in Lesson.entries) {
                val hits = (0L until SEARCH_LIMIT).mapNotNull { trace(lesson, it) }
                appendLine("### ${lesson.id}: ${hits.size} candidates in [0, $SEARCH_LIMIT)")
                appendLine("### seeds: " + hits.sortedByDescending { it.score }.take(20).joinToString {
                    "${it.seed}(${it.score})"
                })
                hits.sortedByDescending { it.score }.take(CANDIDATES_SHOWN).forEach { appendLine(render(it)) }
            }
        }
        println(report)
        File("build/tutorial-trace-search.txt").apply { parentFile.mkdirs() }.writeText(report)
    }

    // --- Lessons ---------------------------------------------------------------------------------

    /**
     * The four lessons, each with the seat that deals its hand. Seat 0 is always the human, seat 2
     * their partner, seats 1 and 3 the opponents.
     */
    enum class Lesson(val id: String, val dealer: Seat) {
        /** A bot makes trump in round 1 before the human's turn to bid; the human just plays. */
        BASICS("basics", Seat(1)),

        /** The human deals, everyone passes round 1, and the human names trump in round 2. */
        BIDDING("bidding", Seat(0)),

        /** The human deals, picks the up-card up alone, buries one, and marches. */
        ALONE("alone", Seat(0)),

        /** An opponent makes trump on a stretch; the human's side euchres them. */
        DEFENSE("defense", Seat(3)),
    }

    // --- Simulation ------------------------------------------------------------------------------

    private data class Trick(val plays: List<TrickPlay>, val winner: Seat)

    /** One human decision as the simulation took it, with the state that framed it. */
    private data class Decision(
        val phase: EuchrePhase,
        val hand: List<Card>,
        val legal: List<EuchreAction>,
        val taken: EuchreAction,
    )

    private data class Trace(
        val lesson: Lesson,
        val seed: Long,
        val botNames: Map<Seat, String>,
        val hands: Map<Seat, List<Card>>,
        val upcard: Card?,
        val bidding: List<Pair<Seat, EuchreAction>>,
        val decisions: List<Decision>,
        val trump: Suit,
        val tricks: List<Trick>,
        val result: EuchreHandResult,
        val score: Int = 0,
    ) {
        val dealer: Seat get() = lesson.dealer
        val eval: TrickEvaluator get() = TrickEvaluator(trump, JokerRole.ABSENT)
        val humanPlays: List<Card>
            get() = decisions.filter { it.phase == EuchrePhase.PLAY }
                .map { (it.taken as EuchreAction.PlayCard).card }
    }

    /** Simulates [lesson]'s hand on [seed], or null if the seed does not satisfy its predicate. */
    private fun trace(lesson: Lesson, seed: Long): Trace? {
        val rules = EuchreRules()
        val bot = EuchreBot(false)
        val botRandoms = (1 until PLAYER_COUNT).associate { i -> Seat(i) to Random(seed + i) }
        val humanRandom = Random(seed)

        var state = rules.newGame(seed, lesson.dealer)
        val openingHands = state.hands
        val upcard = state.upcard
        val decisions = mutableListOf<Decision>()
        val plays = mutableListOf<TrickPlay>()
        var seatsPerTrick = PLAYER_COUNT
        var biddingLog = state.bidding.history

        var guard = 0
        while (state.handNumber == 0 && state.phase != EuchrePhase.COMPLETE) {
            if (guard++ > GUARD_LIMIT) return null
            val seat = rules.currentActor(state) ?: return null
            val view = rules.view(state, seat)
            val action = if (seat == HUMAN) {
                humanAction(lesson, view, bot, humanRandom)?.also {
                    decisions += Decision(view.phase, view.hand, view.legalActions, it)
                } ?: return null
            } else {
                bot.decide(view, botRandoms.getValue(seat))
            }
            if (state.phase == EuchrePhase.PLAY) {
                plays += TrickPlay(seat, (action as EuchreAction.PlayCard).card)
            } else {
                // Frozen at the last pre-play state: from here on the log no longer grows.
                biddingLog = state.bidding.history + (seat to action)
                seatsPerTrick = state.activeSeats.size.takeIf { it > 0 } ?: seatsPerTrick
            }
            state = rules.apply(state, seat, action)
            if (state.phase == EuchrePhase.PLAY) seatsPerTrick = state.activeSeats.size
        }

        // handNumber advanced: the hand was either scored or (round 2 passed out) thrown in.
        val result = state.lastHandResult ?: return null
        if (plays.size != seatsPerTrick * HAND_SIZE) return null
        val eval = TrickEvaluator(result.makers.trump, JokerRole.ABSENT)
        val tricks = plays.chunked(seatsPerTrick).map { Trick(it, eval.winner(it)) }

        val trace = Trace(
            lesson = lesson,
            seed = seed,
            botNames = botNames(seed),
            hands = openingHands,
            upcard = upcard,
            bidding = biddingLog,
            decisions = decisions,
            trump = result.makers.trump,
            tricks = tricks,
            result = result,
        )
        return trace.takeIf { accepts(it) }?.copy(score = score(trace))
    }

    /**
     * The human seat's stand-in policy per lesson. Returning null abandons the seed: the engine
     * asked the human for a decision this lesson's shape does not allow.
     */
    private fun humanAction(
        lesson: Lesson,
        view: EuchrePlayerView,
        bot: EuchreBot,
        random: Random,
    ): EuchreAction? = when (view.phase) {
        EuchrePhase.PLAY -> bot.decide(view, random)
        EuchrePhase.DEALER_DISCARD -> if (lesson == Lesson.ALONE) bot.decide(view, random) else null
        EuchrePhase.BIDDING_ROUND_1 -> when (lesson) {
            // Lesson 1 must be pure play: a bot has to make trump before the human's turn.
            Lesson.BASICS -> null
            // A scripted pass has to be a pass the bot itself would make, or the lesson would be
            // teaching a bad bid; anything else abandons the seed.
            Lesson.BIDDING, Lesson.DEFENSE -> bot.decide(view, random).takeIf { it is EuchreAction.Pass }
            Lesson.ALONE -> EuchreAction.OrderUp(alone = true)
        }
        EuchrePhase.BIDDING_ROUND_2 -> when (lesson) {
            Lesson.BIDDING -> bestRound2Call(view, bot)
            Lesson.DEFENSE -> bot.decide(view, random).takeIf { it is EuchreAction.Pass }
            else -> null
        }
        else -> null
    }

    /** Round 2: name the suit this hand estimates best in, if it is worth calling at all. */
    private fun bestRound2Call(view: EuchrePlayerView, bot: EuchreBot): EuchreAction? {
        val callable = view.legalActions.filterIsInstance<EuchreAction.CallTrump>()
            .filterNot { it.alone }
            .map { it.suit }
        val best = callable.maxByOrNull { bot.estimateTricks(view.hand, it) } ?: return null
        if (bot.estimateTricks(view.hand, best) < ROUND2_CALL_THRESHOLD) return null
        return EuchreAction.CallTrump(best, alone = false)
    }

    // --- Acceptance ------------------------------------------------------------------------------

    /** The per-lesson predicate: what the hand must actually DO to be teachable. */
    private fun accepts(t: Trace): Boolean = when (t.lesson) {
        Lesson.BASICS -> acceptsBasics(t)
        Lesson.BIDDING -> acceptsBidding(t)
        Lesson.ALONE -> acceptsAlone(t)
        Lesson.DEFENSE -> acceptsDefense(t)
    }

    /**
     * Lesson 1: a bot makes trump in round 1 (so the human never bids, and never discards — the
     * dealer is a bot), both bowers are played during the hand, the human holds one of them, and
     * at least one trick is a genuine follow-suit squeeze.
     */
    private fun acceptsBasics(t: Trace): Boolean {
        val makers = t.result.makers
        if (makers.maker == HUMAN || makers.alone || !makers.orderedUp) return false
        if (t.decisions.size != HAND_SIZE || t.decisions.any { it.phase != EuchrePhase.PLAY }) return false
        val played = t.tricks.flatMap { it.plays }.map { it.card }
        if (played.none { t.eval.isRightBower(it) } || played.none { t.eval.isLeftBower(it) }) return false
        if (t.humanPlays.none { t.eval.isRightBower(it) || t.eval.isLeftBower(it) }) return false
        return t.decisions.any { it.legal.size in 1 until it.hand.size }
    }

    /**
     * Lesson 2: the human deals, all three bots pass twice, the human names trump in round 2 (never
     * the turned-down suit, by construction) and makes it.
     */
    private fun acceptsBidding(t: Trace): Boolean {
        val makers = t.result.makers
        if (makers.maker != HUMAN || makers.alone || makers.orderedUp) return false
        if (t.bidding.count { it.second is EuchreAction.Pass } != PASSES_BEFORE_ROUND2_CALL) return false
        if (t.decisions.count { it.phase != EuchrePhase.PLAY } != 2) return false
        return t.result.made
    }

    /** Lesson 3: the human deals, picks up alone after three passes, buries one, and marches. */
    private fun acceptsAlone(t: Trace): Boolean {
        val makers = t.result.makers
        if (makers.maker != HUMAN || !makers.alone || !makers.orderedUp) return false
        if (t.decisions.none { it.phase == EuchrePhase.DEALER_DISCARD }) return false
        return t.result.made && t.result.makerTricks == HAND_SIZE
    }

    /** Lesson 4: an opponent makes trump (the human passing along the way) and is euchred. */
    private fun acceptsDefense(t: Trace): Boolean {
        val makers = t.result.makers
        if (makers.makerTeam == HUMAN_TEAM || makers.alone || makers.loneDefender != null) return false
        val bids = t.decisions.filter { it.phase != EuchrePhase.PLAY }
        if (bids.any { it.taken !is EuchreAction.Pass }) return false
        if (t.decisions.size - bids.size != HAND_SIZE) return false
        return !t.result.made && t.result.makerTricks <= EUCHRE_CEILING
    }

    /** Preference between accepted seeds — bigger is better, purely for picking the nicest hand. */
    private fun score(t: Trace): Int = when (t.lesson) {
        Lesson.BASICS -> scoreBasics(t)
        Lesson.BIDDING -> scoreBidding(t)
        Lesson.ALONE -> scoreAlone(t)
        Lesson.DEFENSE -> (EUCHRE_CEILING - t.result.makerTricks) * 2 + t.tricks.count { it.winner == HUMAN }
    }

    private fun scoreBasics(t: Trace): Int {
        var score = 0
        // The partner making it keeps the very first lesson on the winning side, and a hand holding
        // BOTH bowers tells the whole bower story from one seat.
        if (t.result.makers.maker == PARTNER) score += 3
        if (t.result.made == (t.result.makers.makerTeam == HUMAN_TEAM)) score += 2
        if (t.humanPlays.count { t.eval.isRightBower(it) || t.eval.isLeftBower(it) } == 2) score += 3
        score += t.decisions.count { it.legal.size in 1 until it.hand.size }
        return score + t.tricks.count { it.winner == HUMAN }
    }

    private fun scoreBidding(t: Trace): Int {
        var score = t.result.makerTricks
        if (t.result.makerTricks == HAND_SIZE) score += 2
        // Naming the other colour makes the turned-down restriction vivid rather than incidental.
        if (t.trump.color != (t.upcard as? SuitedCard)?.suit?.color) score += 2
        return score
    }

    private fun scoreAlone(t: Trace): Int {
        val buried = t.decisions.firstOrNull { it.phase == EuchrePhase.DEALER_DISCARD }
            ?.let { (it.taken as EuchreAction.DealerDiscard).card }
        val cleanDiscard = if (buried != null && !t.eval.isTrump(buried)) 3 else 0
        return cleanDiscard + t.humanPlays.count { t.eval.isTrump(it) }
    }

    // --- Rendering -------------------------------------------------------------------------------

    private fun botNames(seed: Long): Map<Seat, String> {
        val names = botNamesPool.shuffled(Random(seed))
        return (1 until PLAYER_COUNT).associate { Seat(it) to names[it - 1] }
    }

    private fun name(t: Trace, seat: Seat) = if (seat == HUMAN) "YOU" else t.botNames.getValue(seat)

    private fun render(t: Trace): String = buildString {
        appendLine("=== ${t.lesson.id.uppercase()} seed=${t.seed} score=${t.score} ===")
        appendLine("Dealer: ${name(t, t.dealer)} (seat ${t.dealer.index}) · up-card ${t.upcard?.label}")
        appendLine("Bots: " + t.botNames.entries.joinToString { "seat ${it.key.index}=${it.value}" })
        EUCHRE_SEATS.forEach { seat ->
            appendLine("  ${name(t, seat)} (seat ${seat.index}): " + t.hands.getValue(seat).joinToString { it.label })
        }
        appendLine("Bidding:")
        t.bidding.forEach { (seat, action) -> appendLine("  ${name(t, seat)}: ${describe(action)}") }
        appendLine("Trump ${t.trump.symbol} · maker ${name(t, t.result.makers.maker)}")
        appendLine("Human decisions:")
        t.decisions.forEachIndexed { i, d ->
            appendLine("  ${i + 1}. [${d.phase}] hand=${d.hand.joinToString(" ") { it.label }}")
            appendLine("       legal=${d.legal.joinToString(" ") { describe(it) }}")
            appendLine("       TAKEN=${describe(d.taken)}")
        }
        appendLine("Tricks:")
        t.tricks.forEachIndexed { i, trick ->
            val text = trick.plays.joinToString { "${name(t, it.seat)}:${it.card.label}" }
            appendLine("  trick ${i + 1}: $text -> ${name(t, trick.winner)}")
        }
        appendLine(
            "Result: made=${t.result.made} makerTricks=${t.result.makerTricks} " +
                "deltas=${t.result.teamDeltas}",
        )
    }

    private fun describe(action: EuchreAction): String = when (action) {
        is EuchreAction.Pass -> "pass"
        is EuchreAction.OrderUp -> "orderUp" + if (action.alone) "(alone)" else ""
        is EuchreAction.CallTrump -> "call ${action.suit.symbol}" + if (action.alone) "(alone)" else ""
        is EuchreAction.DealerDiscard -> "discard ${action.card.label}"
        is EuchreAction.DefendAlone -> "defendAlone"
        is EuchreAction.DeclineDefend -> "declineDefend"
        is EuchreAction.CallFarmers -> "farmersSwap"
        is EuchreAction.DeclineFarmers -> "declineFarmers"
        is EuchreAction.PlayCard -> "play ${action.card.label}"
    }

    private companion object {
        val HUMAN = Seat(0)
        val PARTNER = partnerOf(HUMAN)
        const val HUMAN_TEAM = 0
        const val SEARCH_LIMIT = 4000L
        const val CANDIDATES_SHOWN = 3
        const val GUARD_LIMIT = 200
        /** The bot's own round-2 bar, so the search predicts the bot it will actually run. */
        val ROUND2_CALL_THRESHOLD = EuchreBot.CALL_THRESHOLD

        /** Everyone passes round 1 and all but the caller pass round 2. */
        const val PASSES_BEFORE_ROUND2_CALL = 2 * PLAYER_COUNT - 1
        const val EUCHRE_CEILING = 2
    }
}
