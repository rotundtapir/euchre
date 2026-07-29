// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.net

import io.github.rotundtapir.cardkit.net.CreateLobbyRequest
import io.github.rotundtapir.cardkit.net.DEFAULT_IDLE_DISBAND_MINUTES
import io.github.rotundtapir.cardkit.net.DEFAULT_TURN_TIMEOUT_SECONDS
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Euchre's own half of the wire protocol. Everything game-independent — the envelope, the lobby
 * messages, the wire enums, display-name rules, the WebSocket client — lives in `cardkit-net`; only
 * the parts that *are* Euchre are here.
 *
 * Compatibility strategy is two-layered:
 *  - **Additive JSON evolution is the default.** New optional fields (with defaults) and new values
 *    of the *wire* enums never require a version bump — [WireJson] ignores unknown keys and coerces
 *    unknown enum values to each enum's `UNKNOWN` member.
 *  - **[PROTOCOL_VERSION] bumps only on a breaking change** (a field removal/retype, or a semantic
 *    change). A client outside the server's supported range is told to update.
 *
 * NOTE: the engine enums embedded in [EuchrePlayerView] — [io.github.rotundtapir.euchre.engine
 * .EuchrePhase] and cardkit's `Suit`/`Rank` — deliberately have **no `UNKNOWN` sink**, because a new
 * phase or rank means the rules of the game changed and an old client genuinely cannot render it.
 * Adding a value to any of them is therefore a breaking change and must bump [PROTOCOL_VERSION]; it
 * is not an additive one. Keeping wire sentinels out of the pure engine is the deliberate trade.
 */
const val PROTOCOL_VERSION: Int = 1

/**
 * The negotiated rules for a lobby; echoed back so every client renders the same setup.
 *
 * There is no player or team count on the wire: Euchre is always four players in two partnerships,
 * so a table shape is not something to negotiate. The body is exactly the four house rules, matching
 * `EuchreHouseRules` in the app.
 */
@Serializable
data class LobbyConfig(
    val stickTheDealer: Boolean = false,
    val defendAlone: Boolean = false,
    val bennyEnabled: Boolean = false,
    val farmersHand: Boolean = false,
    val turnTimeoutSeconds: Int = DEFAULT_TURN_TIMEOUT_SECONDS,
    val idleDisbandMinutes: Int = DEFAULT_IDLE_DISBAND_MINUTES,
)

/**
 * Create a new lobby and become its creator, taking a seat. [seed] is honoured only in dev mode.
 *
 * Per-game, because its body is Euchre's house rules. The server routes on the shared
 * [CreateLobbyRequest] interface and converts this to a [LobbyConfig] through `EuchreDescriptor`.
 */
@Serializable
@SerialName("lobby.create")
data class CreateLobby(
    override val displayName: String,
    val stickTheDealer: Boolean = false,
    val defendAlone: Boolean = false,
    val bennyEnabled: Boolean = false,
    val farmersHand: Boolean = false,
    override val turnTimeoutSeconds: Int = DEFAULT_TURN_TIMEOUT_SECONDS,
    override val idleDisbandMinutes: Int = DEFAULT_IDLE_DISBAND_MINUTES,
    override val seed: Long? = null,
) : CreateLobbyRequest

/**
 * Submit a game action. `stateVersion` echoes the prompting [ViewUpdate]; a mismatch means the action
 * is stale (a double-tap or a race) and is rejected without disturbing the game — the network
 * analogue of [io.github.rotundtapir.cardkit.core.ChannelPlayer.trySubmit].
 */
typealias SubmitAction = io.github.rotundtapir.cardkit.net.SubmitAction<EuchreAction>

/**
 * A redacted per-seat view after every applied action (and on connect/reconnect). The view *is* the
 * turn prompt: [EuchrePlayerView.isMyTurn] plus its `legalActions` list tell the client what to
 * offer. `turnRemainingMillis` (never an absolute timestamp — client clocks drift) drives the
 * countdown.
 */
typealias ViewUpdate = io.github.rotundtapir.cardkit.net.ViewUpdate<EuchrePlayerView>

/** Full lobby snapshot, re-broadcast on every change (no deltas — the client never merges state). */
typealias LobbyState = io.github.rotundtapir.cardkit.net.LobbyState<LobbyConfig>

/**
 * The erased forms of the payload-carrying messages, for `is` checks: their type argument is gone at
 * runtime, so `is ViewUpdate` cannot compile while `is AnyViewUpdate` can. Narrow one with
 * [forEuchre] — safe because [WireJson] registers exactly one payload type per message, so anything
 * that decoded here carries Euchre's own.
 */
typealias AnyViewUpdate = io.github.rotundtapir.cardkit.net.ViewUpdate<*>

/** See [AnyViewUpdate]. */
typealias AnyLobbyState = io.github.rotundtapir.cardkit.net.LobbyState<*>

/** See [AnyViewUpdate]. */
typealias AnySubmitAction = io.github.rotundtapir.cardkit.net.SubmitAction<*>

@Suppress("UNCHECKED_CAST")
fun AnyViewUpdate.forEuchre(): ViewUpdate = this as ViewUpdate

@Suppress("UNCHECKED_CAST")
fun AnyLobbyState.forEuchre(): LobbyState = this as LobbyState
