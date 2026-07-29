// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.server

import io.github.rotundtapir.cardkit.core.GameRules
import io.github.rotundtapir.cardkit.core.Strategy
import io.github.rotundtapir.cardkit.net.CreateLobbyRequest
import io.github.rotundtapir.cardkit.net.GameOver
import io.github.rotundtapir.cardkit.server.GameDescriptor
import io.github.rotundtapir.euchre.ai.EuchreBot
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.EuchreRules
import io.github.rotundtapir.euchre.engine.EuchreState
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT
import io.github.rotundtapir.euchre.net.CreateLobby
import io.github.rotundtapir.euchre.net.LobbyConfig
import io.github.rotundtapir.euchre.net.PROTOCOL_VERSION
import io.github.rotundtapir.euchre.net.WireJson

/**
 * Everything `cardkit-server` needs to host Euchre — the whole of what makes that generic server
 * *this* game. The room actor, seat hosting, lobbies, reconnect, snapshots and anti-abuse are shared
 * with 500 and unchanged by this being Euchre.
 */
object EuchreDescriptor : GameDescriptor<EuchreState, EuchreAction, EuchrePlayerView, LobbyConfig> {

    override val gameName: String = "Euchre"
    override val metricsPrefix: String = "euchre"
    override val protocolVersion: Int = PROTOCOL_VERSION
    override val wireJson = WireJson
    override val stateSerializer = EuchreState.serializer()
    override val configSerializer = LobbyConfig.serializer()

    /**
     * The deterministic heuristic bot, not the Monte-Carlo one: this runs on the server's single
     * shared vCPU for every abandoned seat, and a search budget there would be paid for by every
     * other table on the box. It needs the Benny flag because the joker changes what a legal — and a
     * sensible — move is.
     */
    override fun bot(config: LobbyConfig): Strategy<EuchrePlayerView, EuchreAction> =
        EuchreBot(benny = config.bennyEnabled)

    override fun rulesFor(config: LobbyConfig): GameRules<EuchreState, EuchreAction, EuchrePlayerView> =
        rules(config)

    override fun newGame(config: LobbyConfig, seed: Long): EuchreState = rules(config).newGame(seed)

    /**
     * Euchre plays to [io.github.rotundtapir.euchre.engine.WINNING_SCORE]; the winning *team* is what
     * the wire carries, and both teams' scores come along so a client can show the final line.
     */
    override fun gameOver(state: EuchreState): GameOver = GameOver(state.winner ?: -1, state.scores)

    override fun botRestoreSeed(state: EuchreState): Long = state.rngSeed

    /**
     * A euchre table is always four players in two partnerships, so there is nothing to validate
     * about its shape — only that the request is actually Euchre's. The house rules are booleans and
     * every combination is playable.
     */
    override fun configFrom(request: CreateLobbyRequest): LobbyConfig? {
        val create = request as? CreateLobby ?: return null
        return LobbyConfig(
            stickTheDealer = create.stickTheDealer,
            defendAlone = create.defendAlone,
            bennyEnabled = create.bennyEnabled,
            farmersHand = create.farmersHand,
            turnTimeoutSeconds = create.turnTimeoutSeconds,
            idleDisbandMinutes = create.idleDisbandMinutes,
        )
    }

    override fun playerCount(config: LobbyConfig): Int = PLAYER_COUNT
    override fun turnTimeoutSeconds(config: LobbyConfig): Int = config.turnTimeoutSeconds
    override fun idleDisbandMinutes(config: LobbyConfig): Int = config.idleDisbandMinutes

    override fun withTimeouts(
        config: LobbyConfig,
        turnTimeoutSeconds: Int?,
        idleDisbandMinutes: Int?,
    ): LobbyConfig = config.copy(
        turnTimeoutSeconds = turnTimeoutSeconds ?: config.turnTimeoutSeconds,
        idleDisbandMinutes = idleDisbandMinutes ?: config.idleDisbandMinutes,
    )

    private fun rules(config: LobbyConfig) = EuchreRules(
        stickTheDealer = config.stickTheDealer,
        defendAlone = config.defendAlone,
        bennyEnabled = config.bennyEnabled,
        farmersHandEnabled = config.farmersHand,
    )
}
