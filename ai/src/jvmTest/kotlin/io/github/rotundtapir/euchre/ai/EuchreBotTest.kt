// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.of
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.WINNING_SCORE
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class EuchreBotTest {

    private fun playMatch(rules: EuchreRules, seed: Long): io.github.rotundtapir.euchre.engine.EuchreState {
        val bot = EuchreBot(rules.bennyEnabled)
        val random = Random(seed)
        var state = rules.newGame(seed)
        var steps = 0
        while (!rules.isTerminal(state)) {
            check(steps++ < 100_000) { "Match did not terminate" }
            val actor = checkNotNull(rules.currentActor(state))
            val view = rules.view(state, actor)
            val action = bot.decide(view, random)
            assertTrue(action in view.legalActions, "illegal $action in ${state.phase}")
            state = rules.apply(state, actor, action)
        }
        return state
    }

    @Test
    fun `plays full legal matches at every toggle combination`() {
        val combos = listOf(
            EuchreRules(),
            EuchreRules(stickTheDealer = true),
            EuchreRules(defendAlone = true),
            EuchreRules(bennyEnabled = true),
            EuchreRules(farmersHandEnabled = true),
            EuchreRules(stickTheDealer = true, defendAlone = true, bennyEnabled = true, farmersHandEnabled = true),
        )
        combos.forEachIndexed { i, rules ->
            val end = playMatch(rules, seed = 1000L + i)
            val winner = assertNotNull(end.winner, "combo $i")
            assertTrue(end.scores.getValue(winner) >= WINNING_SCORE)
        }
    }

    @Test
    fun `bot matches are deterministic per seed`() {
        val rules = EuchreRules(stickTheDealer = true)
        assertEquals(playMatch(rules, 77), playMatch(rules, 77))
    }

    // --- Estimation and bidding ------------------------------------------------------------------

    private val bot = EuchreBot()

    private fun round1View(
        hand: List<io.github.rotundtapir.cardkit.core.Card>,
        seat: Int = 1,
        dealer: Int = 0,
        upcard: io.github.rotundtapir.cardkit.core.Card = Rank.NINE of Suit.SPADES,
    ) = EuchrePlayerView(
        seat = Seat(seat),
        phase = EuchrePhase.BIDDING_ROUND_1,
        handNumber = 0,
        hand = hand,
        handSizes = (0..3).associate { Seat(it) to 5 },
        dealer = Seat(dealer),
        scores = mapOf(0 to 0, 1 to 0),
        toAct = Seat(seat),
        upcard = upcard,
        upcardSuit = (upcard as? io.github.rotundtapir.cardkit.core.SuitedCard)?.suit,
        legalActions = listOf(EuchreAction.Pass, EuchreAction.OrderUp(false), EuchreAction.OrderUp(true)),
    )

    private val layDown = listOf(
        Rank.JACK of Suit.SPADES, // right bower
        Rank.JACK of Suit.CLUBS, // left bower
        Rank.ACE of Suit.SPADES,
        Rank.KING of Suit.SPADES,
        Rank.ACE of Suit.HEARTS,
    )

    private val junk = listOf(
        Rank.NINE of Suit.HEARTS,
        Rank.TEN of Suit.DIAMONDS,
        Rank.QUEEN of Suit.CLUBS,
        Rank.NINE of Suit.DIAMONDS,
        Rank.TEN of Suit.HEARTS,
    )

    @Test
    fun `trick estimation separates a lay-down from junk`() {
        assertTrue(bot.estimateTricks(layDown, Suit.SPADES) >= 4.0)
        assertTrue(bot.estimateTricks(junk, Suit.SPADES) < 2.0)
    }

    @Test
    fun `orders up a strong hand and goes alone on a lay-down`() {
        val action = bot.decide(round1View(layDown), Random(1))
        assertIs<EuchreAction.OrderUp>(action)
        assertTrue(action.alone)
    }

    @Test
    fun `passes on junk`() {
        assertEquals(EuchreAction.Pass, bot.decide(round1View(junk), Random(1)))
    }

    @Test
    fun `a stuck dealer calls their best suit even with junk`() {
        val calls = Suit.entries.filter { it != Suit.SPADES }.flatMap {
            listOf(EuchreAction.CallTrump(it, false), EuchreAction.CallTrump(it, true))
        }
        val view = round1View(junk, seat = 0).copy(
            phase = EuchrePhase.BIDDING_ROUND_2,
            upcard = Rank.NINE of Suit.SPADES,
            toAct = Seat(0),
            legalActions = calls, // no Pass: stick the dealer
        )
        val action = bot.decide(view, Random(1))
        assertIs<EuchreAction.CallTrump>(action)
    }

    @Test
    fun `defends alone only with real tricks`() {
        val makers = io.github.rotundtapir.euchre.engine.Makers(
            maker = Seat(1),
            trump = Suit.SPADES,
            orderedUp = true,
            alone = true,
        )
        val base = round1View(layDown, seat = 2).copy(
            phase = EuchrePhase.DEFEND_ALONE,
            makers = makers,
            toAct = Seat(2),
            legalActions = listOf(EuchreAction.DefendAlone, EuchreAction.DeclineDefend),
        )
        assertEquals(EuchreAction.DefendAlone, bot.decide(base, Random(1)))
        assertEquals(EuchreAction.DeclineDefend, bot.decide(base.copy(hand = junk), Random(1)))
    }

    @Test
    fun `farmers keeps the best same-suit pair`() {
        val hand = listOf(
            Rank.NINE of Suit.HEARTS,
            Rank.TEN of Suit.HEARTS,
            Rank.NINE of Suit.CLUBS,
            Rank.NINE of Suit.DIAMONDS,
            Rank.TEN of Suit.SPADES,
        )
        val view = round1View(hand).copy(
            phase = EuchrePhase.FARMERS,
            legalActions = listOf(EuchreAction.DeclineFarmers), // bot ignores; swap always preferred
        )
        val action = bot.decide(view, Random(1))
        assertIs<EuchreAction.CallFarmers>(action)
        val kept = hand - action.discards.toSet()
        assertEquals(setOf(Rank.NINE of Suit.HEARTS, Rank.TEN of Suit.HEARTS), kept.toSet())
    }

    @Test
    fun `dealer discard prefers creating a void and never a singleton ace`() {
        val eval = bot.evaluator(Suit.SPADES)
        val hand = listOf(
            Rank.JACK of Suit.SPADES,
            Rank.ACE of Suit.SPADES,
            Rank.KING of Suit.HEARTS,
            Rank.QUEEN of Suit.HEARTS,
            Rank.TEN of Suit.DIAMONDS, // singleton: discard to make a void
            Rank.ACE of Suit.CLUBS, // singleton ace: keep
        )
        assertEquals(Rank.TEN of Suit.DIAMONDS, bot.chooseDiscard(hand, eval))
    }
}
