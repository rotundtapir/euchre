// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.net

import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.SuitedCard
import io.github.rotundtapir.cardkit.net.ClientMessage
import io.github.rotundtapir.cardkit.net.Hello
import io.github.rotundtapir.cardkit.net.LobbyState
import io.github.rotundtapir.cardkit.net.Platform
import io.github.rotundtapir.cardkit.net.RoomPhase
import io.github.rotundtapir.cardkit.net.SeatInfo
import io.github.rotundtapir.cardkit.net.ServerMessage
import io.github.rotundtapir.cardkit.net.ViewUpdate
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePlayerView
import io.github.rotundtapir.euchre.engine.EuchreRules
import kotlinx.serialization.SerializationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins Euchre's wire format from the very first release. These goldens are a compatibility contract:
 * if a change to a message shape, a `@SerialName`, or the [WireJson] config alters the emitted JSON,
 * this test fails loudly rather than shipping a silent protocol break.
 *
 * All nine action shapes are pinned individually, because those serial names are the vocabulary a
 * released client speaks and there is no second chance to choose them.
 */
class ProtocolGoldenTest {

    private val jackOfSpades = SuitedCard(Rank.JACK, Suit.SPADES)

    private fun sampleView(): EuchrePlayerView =
        EuchreRules().let { it.view(it.newGame(7L), Seat(0)) }

    private inline fun <reified T : ClientMessage> roundTripClient(msg: T) {
        val json = WireJson.encodeToString<ClientMessage>(msg)
        assertEquals(msg, WireJson.decodeFromString<ClientMessage>(json), "client round-trip: $json")
    }

    private inline fun <reified T : ServerMessage> roundTripServer(msg: T) {
        val json = WireJson.encodeToString<ServerMessage>(msg)
        assertEquals(msg, WireJson.decodeFromString<ServerMessage>(json), "server round-trip: $json")
    }

    @Test
    fun `create-lobby carries the house rules and omits them at their defaults`() {
        assertEquals(
            """{"type":"lobby.create","displayName":"Alice"}""",
            WireJson.encodeToString<ClientMessage>(CreateLobby("Alice")),
            "defaults must stay omitted: that is what makes adding an optional field non-breaking",
        )
        assertEquals(
            """{"type":"lobby.create","displayName":"Alice","stickTheDealer":true,"defendAlone":true,""" +
                """"bennyEnabled":true,"farmersHand":true,"turnTimeoutSeconds":60,""" +
                """"idleDisbandMinutes":30,"seed":42}""",
            WireJson.encodeToString<ClientMessage>(
                CreateLobby(
                    "Alice",
                    stickTheDealer = true,
                    defendAlone = true,
                    bennyEnabled = true,
                    farmersHand = true,
                    turnTimeoutSeconds = 60,
                    idleDisbandMinutes = 30,
                    seed = 42L,
                ),
            ),
        )
    }

    @Test
    fun `every action's serial name and shape is pinned`() {
        fun action(stateVersion: Int, action: EuchreAction) =
            WireJson.encodeToString<ClientMessage>(SubmitAction(stateVersion, action))

        assertEquals(
            """{"type":"game.action","stateVersion":1,"action":{"type":"pass"}}""",
            action(1, EuchreAction.Pass),
        )
        assertEquals(
            """{"type":"game.action","stateVersion":2,"action":{"type":"orderUp"}}""",
            action(2, EuchreAction.OrderUp()),
        )
        assertEquals(
            """{"type":"game.action","stateVersion":3,"action":{"type":"orderUp","alone":true}}""",
            action(3, EuchreAction.OrderUp(alone = true)),
        )
        assertEquals(
            """{"type":"game.action","stateVersion":4,"action":{"type":"callTrump","suit":"HEARTS"}}""",
            action(4, EuchreAction.CallTrump(Suit.HEARTS)),
        )
        assertEquals(
            """{"type":"game.action","stateVersion":5,"action":{"type":"callTrump","suit":"HEARTS","alone":true}}""",
            action(5, EuchreAction.CallTrump(Suit.HEARTS, alone = true)),
        )
        // A card carries a fully-qualified discriminator, because cardkit's Card hierarchy has no
        // @SerialName. Verbose, but it is what the released 500 app already speaks, so giving those
        // types short names in cardkit would be a breaking change for that game — not a tidy-up.
        assertEquals(
            """{"type":"game.action","stateVersion":6,"action":{"type":"dealerDiscard","card":""" +
                """{"type":"io.github.rotundtapir.cardkit.core.SuitedCard","rank":"JACK","suit":"SPADES"}}}""",
            action(6, EuchreAction.DealerDiscard(jackOfSpades)),
        )
        assertEquals(
            """{"type":"game.action","stateVersion":7,"action":{"type":"defendAlone"}}""",
            action(7, EuchreAction.DefendAlone),
        )
        assertEquals(
            """{"type":"game.action","stateVersion":8,"action":{"type":"declineDefend"}}""",
            action(8, EuchreAction.DeclineDefend),
        )
        assertEquals(
            """{"type":"game.action","stateVersion":9,"action":{"type":"callFarmers","discards":[""" +
                """{"type":"io.github.rotundtapir.cardkit.core.SuitedCard","rank":"JACK","suit":"SPADES"}]}}""",
            action(9, EuchreAction.CallFarmers(listOf(jackOfSpades))),
        )
        assertEquals(
            """{"type":"game.action","stateVersion":10,"action":{"type":"declineFarmers"}}""",
            action(10, EuchreAction.DeclineFarmers),
        )
        assertEquals(
            """{"type":"game.action","stateVersion":11,"action":{"type":"playCard","card":""" +
                """{"type":"io.github.rotundtapir.cardkit.core.SuitedCard","rank":"JACK","suit":"SPADES"}}}""",
            action(11, EuchreAction.PlayCard(jackOfSpades)),
        )
    }

    @Test
    fun `a lobby snapshot is pinned`() {
        assertEquals(
            """{"type":"lobby.state","joinCode":"AB12","gameId":"g-1","config":{},""" +
                """"seats":[{"seat":0,"name":"Alice","isBot":false,"ready":true,"connected":true}],""" +
                """"creatorSeat":0,"yourSeat":0,"phase":"lobby"}""",
            WireJson.encodeToString<ServerMessage>(
                LobbyState(
                    joinCode = "AB12",
                    gameId = "g-1",
                    config = LobbyConfig(),
                    seats = listOf(SeatInfo(Seat(0), "Alice", isBot = false, ready = true, connected = true)),
                    creatorSeat = Seat(0),
                    yourSeat = Seat(0),
                    phase = RoomPhase.LOBBY,
                ),
            ),
        )
    }

    @Test
    fun `a view update never carries hidden information`() {
        // The redaction contract, asserted on the wire rather than on the object: the kitty and other
        // players' hands must not appear as fields at all, so there is nothing to leak even by
        // accident. `hand` is this seat's own five cards; everyone else is a count.
        val encoded = WireJson.encodeToString<ServerMessage>(ViewUpdate(1, sampleView(), 30_000))
        assertFalse(encoded.contains("kitty"), "the kitty must not be on the wire: $encoded")
        assertFalse(encoded.contains("hands"), "other seats' hands must not be on the wire: $encoded")
        assertTrue(encoded.contains(""""handSizes":{"0":5,"1":5,"2":5,"3":5}"""), encoded)
        assertTrue(encoded.startsWith("""{"type":"game.view","stateVersion":1,"view":{"seat":0,"""), encoded)
        assertTrue(encoded.endsWith(""","turnRemainingMillis":30000}"""), encoded)
        // The up-card IS public for the whole hand, by design — an AI needs it to exclude a
        // turned-down card, and every client can see it on the table anyway.
        assertTrue(encoded.contains(""""upcard":{"type":"io.github.rotundtapir.cardkit.core.SuitedCard"""), encoded)
        // Seats are bare ints, and Seat map keys are stringified ints.
        assertTrue(encoded.contains(""""dealer":0"""), encoded)
        assertTrue(encoded.contains(""""scores":{"0":0,"1":0}"""), encoded)
    }

    @Test
    fun `the bidding history's Pair encoding is pinned`() {
        // biddingHistory is List<Pair<Seat, EuchreAction>>, which kotlinx encodes as
        // {"first":…,"second":…}. That is an implicit shape rather than a named one, so pin it: a
        // later change to a named record would be a silent protocol break.
        val view = sampleView().copy(biddingHistory = listOf(Seat(1) to EuchreAction.Pass))
        val encoded = WireJson.encodeToString<ServerMessage>(ViewUpdate(2, view))
        assertTrue(
            encoded.contains(""""biddingHistory":[{"first":1,"second":{"type":"pass"}}]"""),
            "bidding history shape changed: $encoded",
        )
    }

    @Test
    fun `an absent turn timer is omitted rather than sent as a value`() {
        val encoded = WireJson.encodeToString<ServerMessage>(ViewUpdate(3, sampleView()))
        assertFalse(encoded.contains("turnRemainingMillis"), encoded)
    }

    @Test
    fun `every message type round-trips`() {
        roundTripClient(Hello(PROTOCOL_VERSION, "0.1.2", Platform.ANDROID))
        roundTripClient(CreateLobby("Alice", stickTheDealer = true, seed = 1L))
        roundTripClient(SubmitAction(1, EuchreAction.Pass))
        roundTripClient(SubmitAction(2, EuchreAction.CallTrump(Suit.CLUBS, alone = true)))
        roundTripClient(SubmitAction(3, EuchreAction.CallFarmers(listOf(jackOfSpades))))
        roundTripClient(SubmitAction(4, EuchreAction.PlayCard(jackOfSpades)))
        roundTripServer(ViewUpdate(1, sampleView(), turnRemainingMillis = 30_000))
        roundTripServer(ViewUpdate(2, sampleView()))
        roundTripServer(
            LobbyState(
                joinCode = "AB12",
                gameId = "g-1",
                config = LobbyConfig(stickTheDealer = true, farmersHand = true),
                seats = listOf(SeatInfo(Seat(0), "Alice", isBot = false, ready = true, connected = true)),
                creatorSeat = Seat(0),
                yourSeat = Seat(0),
                phase = RoomPhase.PLAYING,
            ),
        )
    }

    @Test
    fun `unknown fields are ignored for forward compatibility`() {
        val encoded = """{"type":"lobby.create","displayName":"Alice","futureHouseRule":true}"""
        assertEquals(CreateLobby("Alice"), WireJson.decodeFromString<ClientMessage>(encoded))
    }

    @Test
    fun `an unregistered message type fails to decode rather than being silently accepted`() {
        // The server treats a decode failure as MALFORMED; that must stay true.
        assertFailsWith<SerializationException> {
            WireJson.decodeFromString<ClientMessage>("""{"type":"lobby.telepathy","wish":"euchre them"}""")
        }
    }

    @Test
    fun `an unknown phase fails to decode, which is why a new phase bumps the protocol version`() {
        // Deliberate: EuchrePhase has no UNKNOWN sink, because a phase this build has never heard of
        // means the rules changed and the hand cannot be rendered. Documented in Protocol.kt.
        val encoded = WireJson.encodeToString<ServerMessage>(ViewUpdate(1, sampleView()))
            .replace(""""phase":"BIDDING_ROUND_1"""", """"phase":"BIDDING_ROUND_3"""")
        assertFailsWith<SerializationException> {
            WireJson.decodeFromString<ServerMessage>(encoded)
        }
    }
}
