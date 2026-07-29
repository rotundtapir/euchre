// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.server

import io.github.rotundtapir.cardkit.server.GameServer
import io.github.rotundtapir.cardkit.server.ServerConfig
import io.github.rotundtapir.cardkit.server.gameServerModule
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("main")

/**
 * Euchre's server binary: compose `cardkit-server` with [EuchreDescriptor] and listen. Everything
 * else — rooms, lobbies, seat hosting, reconnect, snapshots, anti-abuse, `/health`, `/metrics`,
 * `/admin/drain` — is generic and lives in cardkit-server, shared with the 500 server running
 * alongside this one on the same box.
 */
fun main() {
    val config = ServerConfig.fromEnv(defaults = EUCHRE_DEFAULTS)
    val scope = CoroutineScope(SupervisorJob())
    val server = GameServer(config, scope, EuchreDescriptor)
    server.restoreRooms() // before the listener: a reconnect must find its restored room
    server.startMaintenance()
    log.info(
        "Starting {} server on port {} (devMode={}, dataDir={})",
        EuchreDescriptor.gameName,
        config.port,
        config.devMode,
        config.dataDir ?: "-",
    )
    embeddedServer(CIO, port = config.port) {
        gameServerModule(server, config)
    }.start(wait = true)
}

/**
 * Euchre's own identity, which the generic config knows nothing about. Every field stays overridable
 * by an environment variable, so the container remains configured entirely by env vars.
 *
 * [ServerConfig.minAppVersion] is the first release with online play: anything older has no online
 * code at all, so there is no build that can usefully be told to update below this.
 */
internal val EUCHRE_DEFAULTS = ServerConfig(
    allowedOrigins = listOf("https://rotundtapir.github.io"),
    minAppVersion = "0.2.0",
    serverVersion = "0.2.0",
)
