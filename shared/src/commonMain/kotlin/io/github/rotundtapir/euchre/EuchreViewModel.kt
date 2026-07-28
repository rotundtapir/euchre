// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.rotundtapir.cardkit.core.Card
import io.github.rotundtapir.cardkit.core.ChannelPlayer
import io.github.rotundtapir.cardkit.core.GameDriver
import io.github.rotundtapir.cardkit.core.Player
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.StrategyPlayer
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.ui.deal.dealTimings
import io.github.rotundtapir.cardkit.ui.pacing.PacingGates
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.euchre.ai.EuchreAdvancedBot
import io.github.rotundtapir.euchre.ai.EuchreAdvancedBotPlayer
import io.github.rotundtapir.euchre.ai.EuchreBot
import io.github.rotundtapir.euchre.ai.EuchreSearchConfig
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.EuchreState
import io.github.rotundtapir.euchre.engine.HAND_SIZE
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Drives one local game against bots.
 *
 * The engine's [GameDriver] runs in [viewModelScope]; every state transition is pushed to
 * [humanView], and the human's turns arrive through a [ChannelPlayer] — the same seam a remote
 * opponent would use when online play lands. Bot turns are paced by signals, never timers (see
 * [PacingGates]), and every pacing mechanism is inert at [AnimationSpeed.OFF] so UI tests run flat
 * out.
 */
class EuchreViewModel : ViewModel() {

    // Rebuilt per game so house rules can change between games; only read via [humanView].
    private lateinit var rules: EuchreRules
    private val humanSeat = Seat(0)
    private val human = ChannelPlayer<EuchrePlayerView, EuchreAction>()
    private val state = MutableStateFlow<EuchreState?>(null)
    private var gameJob: Job? = null

    private val animationSpeed = MutableStateFlow(SettingsDefaults.ANIMATION_SPEED)
    private val holdTricks = MutableStateFlow(SettingsDefaults.HOLD_TRICKS)

    /**
     * Signal-driven bot pacing. The deal-pause estimate only scales the deadlock backstop, so a
     * lost deal-done signal can never wedge the game; Euchre deals five cards, not 500's ten.
     */
    val pacing = PacingGates(animationSpeed, holdTricks) { speed ->
        with(dealTimings(speed)) {
            shuffleMillis + flyBudgetMillis + flipTotalMillis(HAND_SIZE) + PAUSE_SLACK_MILLIS
        }
    }

    /** Bot display names for this game, by seat (seat 0 is the human). */
    var botNames: Map<Seat, String> = emptyMap()
        private set

    val humanView: StateFlow<EuchrePlayerView?> = state
        .map { snapshot -> snapshot?.let { rules.view(it, humanSeat) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setAnimationSpeed(value: AnimationSpeed) {
        animationSpeed.value = value
    }

    fun setHoldTricks(value: Boolean) {
        holdTricks.value = value
    }

    /** Starts a fresh match. Cancels any game already running. */
    fun newGame(
        seed: Long,
        houseRules: EuchreHouseRules = EuchreHouseRules(),
        botSkill: BotSkill = SettingsDefaults.BOT_SKILL,
        aiBudgetMillis: Long? = null,
    ) {
        gameJob?.cancel()
        pacing.reset()
        val gameRules = EuchreRules(
            stickTheDealer = houseRules.stickTheDealer,
            defendAlone = houseRules.defendAlone,
            bennyEnabled = houseRules.bennyEnabled,
            farmersHandEnabled = houseRules.farmersHand,
        )
        rules = gameRules
        val names = BOT_NAMES.shuffled(Random(seed))
        botNames = (1 until PLAYER_COUNT).associate { Seat(it) to names[it - 1] }
        val players = buildMap<Seat, Player<EuchrePlayerView, EuchreAction>> {
            put(humanSeat, human)
            for (i in 1 until PLAYER_COUNT) {
                put(Seat(i), paced(botPlayer(botSkill, gameRules, seed, i, aiBudgetMillis)))
            }
        }
        gameJob = viewModelScope.launch {
            GameDriver(gameRules, players).play(gameRules.newGame(seed)) { snapshot -> state.value = snapshot }
        }
    }

    private fun botPlayer(
        skill: BotSkill,
        gameRules: EuchreRules,
        seed: Long,
        i: Int,
        aiBudgetMillis: Long?,
    ): Player<EuchrePlayerView, EuchreAction> {
        val heuristic = EuchreBot(gameRules.bennyEnabled)
        return when (skill) {
            BotSkill.STANDARD -> StrategyPlayer(heuristic, Random(seed + i))
            BotSkill.ADVANCED -> {
                val budget = aiBudgetMillis?.milliseconds
                val config = if (budget == null) {
                    EuchreSearchConfig()
                } else {
                    EuchreSearchConfig(bidBudget = budget, discardBudget = budget, playBudget = budget)
                }
                EuchreAdvancedBotPlayer(
                    EuchreAdvancedBot(gameRules, config, heuristic),
                    Random(seed + i),
                )
            }
        }
    }

    /** Wraps a bot so its turns are visibly paced by the current animation speed. */
    private fun paced(inner: Player<EuchrePlayerView, EuchreAction>): Player<EuchrePlayerView, EuchreAction> =
        Player { view ->
            pacing.awaitGates(view.transitions)
            delay(pacing.botBeatMillis)
            inner.decide(view)
        }

    // --- Human actions ---------------------------------------------------------------------------

    fun passBid() = submit(EuchreAction.Pass)
    fun orderUp(alone: Boolean = false) = submit(EuchreAction.OrderUp(alone))
    fun callTrump(suit: Suit, alone: Boolean = false) = submit(EuchreAction.CallTrump(suit, alone))
    fun discard(card: Card) = submit(EuchreAction.DealerDiscard(card))
    fun defendAlone() = submit(EuchreAction.DefendAlone)
    fun declineDefend() = submit(EuchreAction.DeclineDefend)
    fun callFarmers(discards: List<Card>) = submit(EuchreAction.CallFarmers(discards))
    fun declineFarmers() = submit(EuchreAction.DeclineFarmers)
    fun playCard(card: Card) = submit(EuchreAction.PlayCard(card))

    // trySubmit drops the action unless the engine is actually waiting, so a double-tap (or a tap
    // racing a state change) can't queue an action that would answer a *later* prompt.
    private fun submit(action: EuchreAction) {
        human.trySubmit(action)
    }

    companion object {
        /** Slack over the measured deal animation before the backstop fires. */
        private const val PAUSE_SLACK_MILLIS = 250L

        internal val BOT_NAMES = listOf(
            "Ada", "Bruno", "Cleo", "Dara", "Enzo", "Fen", "Greta", "Hugo",
            "Iris", "Jonas", "Kira", "Lars", "Mira", "Nils", "Olive", "Pia",
        )
    }
}
