// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.euchre.ai.EuchreBot
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePhase
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * [EuchreViewModel] driven exactly as the UI drives it: the human (seat 0) plays from
 * [EuchreViewModel.humanView] through the action funnels, and the pacing acknowledgements stand in
 * for taps. `viewModelScope` runs on a [StandardTestDispatcher] so the gates' `delay` /
 * `withTimeoutOrNull` advance in virtual time.
 */
class EuchreViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** The same heuristic the bots use, so the human's side of the game is deterministic too. */
    private val decider = EuchreBot()

    private fun submitHumanTurn(vm: EuchreViewModel, view: EuchrePlayerView, salt: Long) {
        vm.submitHumanAction(decider.decide(view, Random(salt)))
    }

    private fun offGame(
        seed: Long,
        houseRules: EuchreHouseRules = EuchreHouseRules(),
        holdTricks: Boolean = false,
    ): EuchreViewModel = EuchreViewModel().apply {
        setAnimationSpeed(AnimationSpeed.OFF)
        setHoldTricks(holdTricks)
        newGame(seed = seed, houseRules = houseRules, botSkill = BotSkill.STANDARD)
    }

    /**
     * Drives a game at [AnimationSpeed.OFF] to completion (or until it stalls), playing the human
     * and acknowledging each scored hand — the minimum the UI must do to keep the game moving. At
     * OFF every pacing mechanism is inert except the hand-result gate, which is why the
     * acknowledgement is mandatory. [onView] sees every view the human is offered.
     */
    private fun TestScope.playToCompletion(
        vm: EuchreViewModel,
        seed: Long,
        ackResults: Boolean = true,
        onView: (EuchrePlayerView) -> Unit = {},
    ): EuchrePlayerView {
        var ackedHand = 0
        var guard = 0
        while (guard++ < STEP_LIMIT) {
            advanceUntilIdle()
            val view = vm.humanView.value ?: break
            if (view.winner != null) return view
            if (ackResults && view.lastHandResult != null && view.handNumber > ackedHand) {
                ackedHand = view.handNumber
                vm.acknowledgeHandResult(view.handNumber)
                continue
            }
            if (view.isMyTurn) {
                onView(view)
                submitHumanTurn(vm, view, seed + view.handNumber * 100L + view.trickNumber)
                continue
            }
            break // not our turn, nothing new, not terminal → blocked on an un-raised signal
        }
        return vm.humanView.value ?: error("game produced no view")
    }

    @Test
    fun `a full match against standard bots at OFF reaches a winner`() = runTest(dispatcher) {
        val vm = offGame(seed = 2024L)
        val end = playToCompletion(vm, seed = 2024L)
        assertNotNull(end.winner, "the match should reach a winner")
        assertEquals(EuchrePhase.COMPLETE, end.phase)
        assertTrue((end.scores[end.winner] ?: 0) >= 10, "the winner must actually be at 10 or more")
    }

    @Test
    fun `all pacing is inert at OFF even with holdTricks on`() = runTest(dispatcher) {
        // The invariant the whole connected/e2e suite relies on: at OFF, holdTricks (and every
        // other signal we never raise here) must not stall the game.
        val vm = offGame(seed = 55L, holdTricks = true)
        assertNotNull(playToCompletion(vm, seed = 55L).winner, "holdTricks must be a no-op at OFF")
    }

    @Test
    fun `newGame twice with the same seed produces identical views`() = runTest(dispatcher) {
        fun run(): List<EuchrePlayerView> {
            val seen = mutableListOf<EuchrePlayerView>()
            val vm = offGame(seed = 7L)
            playToCompletion(vm, seed = 7L) { seen += it }
            seen += vm.humanView.value!!
            return seen
        }
        val a = run()
        val b = run()
        assertTrue(a.size > 20, "a full match should offer the human many turns")
        assertEquals(a, b, "same seed → identical view sequence")
    }

    @Test
    fun `bot names are distinct and stable per seed`() = runTest(dispatcher) {
        val vm = offGame(seed = 42L)
        advanceUntilIdle()
        val first = vm.botNames
        assertEquals(3, first.size, "four seats → three bots")
        assertEquals(3, first.values.toSet().size, "bot names must be distinct")
        vm.newGame(seed = 42L)
        advanceUntilIdle()
        assertEquals(first, vm.botNames, "same seed → same names")
    }

    @Test
    fun `the next hand waits until the previous hand's result is acknowledged`() = runTest(dispatcher) {
        val vm = offGame(seed = 2024L)
        // Drive WITHOUT acknowledging: the game must stall at the top of hand 2, because its first
        // bidder gates on handResultAcked — the one gate that stays active at OFF.
        val stalled = playToCompletion(vm, seed = 2024L, ackResults = false)
        assertNotNull(stalled.lastHandResult, "hand 1 should have been scored")
        assertNull(stalled.winner)
        assertTrue(stalled.biddingHistory.isEmpty(), "the next hand's auction must be blocked pending the ack")

        vm.acknowledgeHandResult(stalled.handNumber)
        assertNotNull(playToCompletion(vm, seed = 2024L).winner, "acknowledging releases the match")
    }

    @Test
    fun `stick the dealer removes the dealer's Pass from the human's round-two prompt`() =
        runTest(dispatcher) {
            assertEquals(
                false,
                stuckDealerMayPass(stickTheDealer = true),
                "a stuck dealer must never be offered Pass",
            )
            assertEquals(
                true,
                stuckDealerMayPass(stickTheDealer = false),
                "with the rule off the dealer may still throw the hand in",
            )
        }

    /**
     * Plays matches until the human reaches round-two bidding as the dealer, and reports whether
     * Pass was offered there. Scans several seeds because that position only comes up when all four
     * seats pass a hand the human deals. Null if no seed produced one (which would itself be a bug
     * worth failing on).
     */
    private fun TestScope.stuckDealerMayPass(stickTheDealer: Boolean): Boolean? {
        val rules = EuchreHouseRules(stickTheDealer = stickTheDealer)
        for (seed in 1L..SEED_SCAN) {
            var mayPass: Boolean? = null
            val vm = offGame(seed = seed, houseRules = rules)
            playToCompletion(vm, seed = seed) { view ->
                if (mayPass == null &&
                    view.phase == EuchrePhase.BIDDING_ROUND_2 &&
                    view.seat == view.dealer
                ) {
                    mayPass = view.legalActions.any { it is EuchreAction.Pass }
                }
            }
            mayPass?.let { return it }
        }
        return null
    }

    @Test
    fun `house rules that are off never surface their phases or actions`() = runTest(dispatcher) {
        val vm = offGame(
            seed = 99L,
            houseRules = EuchreHouseRules(defendAlone = false, bennyEnabled = false, farmersHand = false),
        )
        playToCompletion(vm, seed = 99L) { view ->
            assertFalse(
                view.phase == EuchrePhase.DEFEND_ALONE || view.phase == EuchrePhase.FARMERS,
                "a disabled house rule must never reach the human",
            )
            assertTrue(
                view.legalActions.none {
                    it is EuchreAction.DefendAlone || it is EuchreAction.CallFarmers
                },
                "a disabled house rule's actions must be absent from legalActions",
            )
        }
    }

    private companion object {
        /** Enough steps for a full match; a stalled game exits early instead of spinning. */
        const val STEP_LIMIT = 5000

        /** How many seeds to try when hunting for a specific (uncommon) bidding position. */
        const val SEED_SCAN = 40L
    }
}
