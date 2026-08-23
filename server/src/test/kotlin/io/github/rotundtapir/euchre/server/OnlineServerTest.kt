// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.server

import io.github.rotundtapir.cardkit.net.Hello
import io.github.rotundtapir.cardkit.net.JoinLobby
import io.github.rotundtapir.cardkit.net.Platform
import io.github.rotundtapir.cardkit.net.RoomPhase
import io.github.rotundtapir.cardkit.net.SetReady
import io.github.rotundtapir.cardkit.net.StartGame
import io.github.rotundtapir.cardkit.net.UpdateRequired
import io.github.rotundtapir.cardkit.net.Welcome
import io.github.rotundtapir.cardkit.server.GameServer
import io.github.rotundtapir.cardkit.server.ServerConfig
import io.github.rotundtapir.cardkit.server.gameServerModule
import io.github.rotundtapir.euchre.engine.PLAYER_COUNT
import io.github.rotundtapir.euchre.engine.WINNING_SCORE
import io.github.rotundtapir.euchre.net.CreateLobby
import io.github.rotundtapir.euchre.net.LobbyState
import io.github.rotundtapir.euchre.net.PROTOCOL_VERSION
import io.github.rotundtapir.euchre.net.ViewUpdate
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/**
 * Euchre's online server over a real WebSocket, hosted by `testApplication`. The room machinery is
 * cardkit's and tested there against a toy game; what these cover is that *Euchre* is wired into it
 * correctly — a real four-hand game reaches a real scoreline, the house rules reach the engine, and
 * a dropped player's seat comes back.
 */
class OnlineServerTest {

    private fun ApplicationTestBuilder.startServer(config: ServerConfig): CoroutineScope {
        // A dedicated pool per server, shut down with the scope, so a test that ends mid-game leaves
        // no coroutines on the shared Dispatchers.Default to slow later ones down.
        val executor = Executors.newFixedThreadPool(4)
        val job = SupervisorJob()
        job.invokeOnCompletion { executor.shutdownNow() }
        val scope = CoroutineScope(job + executor.asCoroutineDispatcher())
        val server = GameServer(config, scope, EuchreDescriptor)
        application { gameServerModule(server, config) }
        return scope
    }

    private fun devConfig(overrides: ServerConfig.() -> ServerConfig = { this }): ServerConfig =
        ServerConfig(devMode = true, allowedOrigins = listOf("*"), turnTimeoutMillisOverride = 5000).overrides()

    @Test
    fun `a solo player plus three bots plays a full game to a real scoreline`() = testApplication {
        val scope = startServer(devConfig())
        val client = createClient { install(WebSockets) }
        try {
            client.webSocket("/ws") {
                sendMsg(Hello(PROTOCOL_VERSION, "0.2.0", Platform.ANDROID))
                waitFor<Welcome>()
                sendMsg(CreateLobby("Alice", seed = 42))
                val lobby = waitFor<LobbyState>()
                assertEquals(PLAYER_COUNT, lobby.seats.size, "a euchre table is always four seats")
                sendMsg(StartGame)

                val playing = waitForLobby { it.phase == RoomPhase.PLAYING }
                val bots = playing.seats.filter { it.isBot }
                assertEquals(3, bots.size, "the three empty seats become bots")
                assertTrue(bots.all { it.name.endsWith("(bot)") }, "bots are labelled: ${bots.map { it.name }}")

                val over = withTimeout(TEST_TIMEOUT_MS) { playWithBotUntilGameOver() }
                assertTrue(over.winnerTeam in 0..1, "a team must have won: ${over.winnerTeam}")
                assertTrue(
                    over.scores.getValue(over.winnerTeam) >= WINNING_SCORE,
                    "the winner must have reached $WINNING_SCORE: ${over.scores}",
                )
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `the lobby's house rules reach the engine`() = testApplication {
        val scope = startServer(devConfig())
        val client = createClient { install(WebSockets) }
        try {
            client.webSocket("/ws") {
                sendMsg(Hello(PROTOCOL_VERSION, "0.2.0", Platform.WEB))
                waitFor<Welcome>()
                sendMsg(CreateLobby("Alice", bennyEnabled = true, farmersHand = true, seed = 7))
                val lobby = waitFor<LobbyState>()
                // Echoed back so every client renders the same setup.
                assertTrue(lobby.config.bennyEnabled)
                assertTrue(lobby.config.farmersHand)
                sendMsg(StartGame)
                waitForLobby { it.phase == RoomPhase.PLAYING }

                // Benny adds the joker, so the deck is 25 cards and one of them can be the up-card.
                // The observable proof the flag reached the rules is that the game deals and plays at
                // all under them — a mismatched ruleset would throw on the first illegal action.
                val over = withTimeout(TEST_TIMEOUT_MS) { playWithBotUntilGameOver(benny = true) }
                assertTrue(over.winnerTeam in 0..1)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `two humans join by code and finish a game together`() = testApplication {
        val scope = startServer(devConfig())
        val client = createClient { install(WebSockets) }
        try {
            val code = CompletableDeferred<String>()
            coroutineScope {
                launch {
                    client.webSocket("/ws") {
                        sendMsg(Hello(PROTOCOL_VERSION, "0.2.0", Platform.WEB))
                        waitFor<Welcome>()
                        sendMsg(JoinLobby(code.await(), "Bob"))
                        waitFor<LobbyState>()
                        sendMsg(SetReady(true))
                        withTimeout(TEST_TIMEOUT_MS) { playWithBotUntilGameOver(seed = 2) }
                    }
                }
                client.webSocket("/ws") {
                    sendMsg(Hello(PROTOCOL_VERSION, "0.2.0", Platform.ANDROID))
                    waitFor<Welcome>()
                    sendMsg(CreateLobby("Alice", seed = 99))
                    code.complete(waitFor<LobbyState>().joinCode)
                    // Codes are case-insensitive; the joiner used it verbatim, so just wait for them.
                    waitForLobby { lobby ->
                        val humans = lobby.seats.filter { !it.isBot && it.connected }
                        humans.size == 2 && humans.all { it.ready || it.seat == lobby.creatorSeat }
                    }
                    sendMsg(StartGame)
                    val over = withTimeout(TEST_TIMEOUT_MS) { playWithBotUntilGameOver(seed = 1) }
                    assertTrue(over.winnerTeam in 0..1)
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a dropped player reclaims their seat with their session token`() = testApplication {
        val scope = startServer(devConfig())
        val client = createClient { install(WebSockets) }
        try {
            var token: String? = null
            var seat = -1
            client.webSocket("/ws") {
                sendMsg(Hello(PROTOCOL_VERSION, "0.2.0", Platform.WEB))
                token = waitFor<Welcome>().sessionToken
                sendMsg(CreateLobby("Alice", seed = 5))
                seat = assertNotNull(waitFor<LobbyState>().yourSeat).index
                sendMsg(StartGame)
                waitForLobby { it.phase == RoomPhase.PLAYING }
                waitFor<ViewUpdate>() // underway; now drop the socket abruptly
            }
            client.webSocket("/ws") {
                sendMsg(Hello(PROTOCOL_VERSION, "0.2.0", Platform.WEB, sessionToken = token))
                val welcome = waitFor<Welcome>()
                val resumed = assertNotNull(welcome.resumed, "expected to resume into the live game")
                assertEquals(RoomPhase.PLAYING, resumed.phase)
                val lobby = waitFor<LobbyState>()
                assertEquals(seat, assertNotNull(lobby.yourSeat).index, "the same seat must come back")
                // And it is playable: finish the game from the reclaimed seat.
                val over = withTimeout(TEST_TIMEOUT_MS) { playWithBotUntilGameOver(seed = 3) }
                assertTrue(over.winnerTeam in 0..1)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `a client on an older protocol is told to update`() = testApplication {
        val scope = startServer(devConfig { copy(minAppVersion = "0.9.0") })
        val client = createClient { install(WebSockets) }
        try {
            client.webSocket("/ws") {
                sendMsg(Hello(PROTOCOL_VERSION, "0.2.0", Platform.ANDROID))
                assertEquals("0.9.0", waitFor<UpdateRequired>().minAppVersion)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `health names euchre as the responder and reports its live room counts`() = testApplication {
        val scope = startServer(devConfig())
        try {
            // The "game" field is why this is pinned by exact bytes rather than parsed loosely: both
            // suites' preconditions ask "is a server answering FOR MY GAME", so a euchre server that
            // stopped identifying itself would let 500's tests mistake it for theirs — the failure
            // that cost three false red runs before the field existed.
            assertEquals(
                """{"status":"ok","game":"euchre","rooms":0,"activeGames":0,"draining":false}""",
                client.get("/health").bodyAsText(),
            )
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `metrics are namespaced to euchre so two game servers never collide`() = testApplication {
        val scope = startServer(devConfig())
        try {
            val body = client.get("/metrics").bodyAsText()
            assertTrue(body.contains("euchre_connections_total"), body.take(200))
            assertTrue(body.contains("euchre_rooms_active"), body.take(200))
            assertTrue(!body.contains("fivehundred"), "the other game's prefix must not appear")
        } finally {
            scope.cancel()
        }
    }

    private companion object {
        // Generous: a full euchre game to 10 is many hands, and CI runners are slow.
        const val TEST_TIMEOUT_MS = 120_000L
    }
}
