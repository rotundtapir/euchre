// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ai

import io.github.rotundtapir.cardkit.core.Player
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import kotlin.coroutines.CoroutineContext
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Adapts [EuchreAdvancedBot] to the [Player] seam, running each decision on [dispatcher].
 *
 * On the JVM/Android the default [Dispatchers.Default] moves the search off the caller's (usually
 * main) thread; on wasmJs it is the same single-threaded event loop and the bot's own cooperative
 * yields are what keep the UI alive. The seeded [random] mirrors
 * [io.github.rotundtapir.cardkit.core.StrategyPlayer]'s per-seat convention.
 */
class EuchreAdvancedBotPlayer(
    private val bot: EuchreAdvancedBot,
    private val random: Random,
    private val dispatcher: CoroutineContext = Dispatchers.Default,
) : Player<EuchrePlayerView, EuchreAction> {
    override suspend fun decide(view: EuchrePlayerView): EuchreAction =
        withContext(dispatcher) { bot.decide(view, random) }
}
