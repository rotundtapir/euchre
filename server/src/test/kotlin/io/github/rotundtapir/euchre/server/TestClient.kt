// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.server

import io.github.rotundtapir.cardkit.net.ClientMessage
import io.github.rotundtapir.cardkit.net.ErrorCode
import io.github.rotundtapir.cardkit.net.ErrorMessage
import io.github.rotundtapir.cardkit.net.GameOver
import io.github.rotundtapir.cardkit.net.ServerMessage
import io.github.rotundtapir.euchre.ai.EuchreBot
import io.github.rotundtapir.euchre.net.AnyViewUpdate
import io.github.rotundtapir.euchre.net.LobbyState
import io.github.rotundtapir.euchre.net.SubmitAction
import io.github.rotundtapir.euchre.net.WireJson
import io.github.rotundtapir.euchre.net.forEuchre
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlin.random.Random

/** Encode and send a client message over the test WebSocket. */
suspend fun DefaultClientWebSocketSession.sendMsg(message: ClientMessage) {
    send(Frame.Text(WireJson.encodeToString<ClientMessage>(message)))
}

/** Receive and decode the next server message, skipping non-text frames. */
suspend fun DefaultClientWebSocketSession.nextMsg(): ServerMessage {
    while (true) {
        val frame = incoming.receive()
        if (frame is Frame.Text) return WireJson.decodeFromString<ServerMessage>(frame.readText())
    }
}

/** Receive until a message of type [T] arrives. */
suspend inline fun <reified T : ServerMessage> DefaultClientWebSocketSession.waitFor(): T {
    while (true) {
        val message = nextMsg()
        if (message is T) return message
    }
}

/** Receive lobby snapshots until one satisfies [predicate]. */
suspend fun DefaultClientWebSocketSession.waitForLobby(predicate: (LobbyState) -> Boolean): LobbyState {
    while (true) {
        val lobby = waitFor<LobbyState>()
        if (predicate(lobby)) return lobby
    }
}

/**
 * Play this seat with [EuchreBot] until [GameOver], returning it. Any view update where it is our
 * turn is answered with a bot move echoing that update's state version.
 */
suspend fun DefaultClientWebSocketSession.playWithBotUntilGameOver(
    seed: Long = 1L,
    benny: Boolean = false,
): GameOver {
    val bot = EuchreBot(benny = benny)
    val rng = Random(seed)
    while (true) {
        when (val message = nextMsg()) {
            is GameOver -> return message
            is AnyViewUpdate -> {
                val update = message.forEuchre()
                if (update.view.isMyTurn) {
                    sendMsg(SubmitAction(update.stateVersion, bot.decide(update.view, rng)))
                }
            }
            // A STALE_ACTION can legitimately occur if a slow runner let the turn timeout fire and the
            // server's bot already played: our late move is simply obsolete, not a failure. The next
            // view update carries the true state. Any other error is a real problem.
            is ErrorMessage ->
                if (message.code != ErrorCode.STALE_ACTION) error("server rejected a move mid-game: $message")
            else -> Unit
        }
    }
}
