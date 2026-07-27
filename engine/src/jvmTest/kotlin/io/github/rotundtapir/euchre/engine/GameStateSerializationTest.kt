// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * The engine state is the future wire/snapshot format: every phase must survive a JSON round
 * trip unchanged, and action serial names are pinned.
 */
class GameStateSerializationTest {
    private val json = Json

    private fun roundTrip(state: EuchreState): EuchreState =
        json.decodeFromString<EuchreState>(json.encodeToString(EuchreState.serializer(), state))

    @Test
    fun `every phase of a full match round-trips`() {
        val rules = EuchreRules(
            stickTheDealer = true,
            defendAlone = true,
            bennyEnabled = true,
            farmersHandEnabled = true,
        )
        val random = Random(5)
        val phasesSeen = mutableSetOf<EuchrePhase>()
        var state = rules.newGame(5)
        var steps = 0
        while (!rules.isTerminal(state) && steps++ < 20_000) {
            phasesSeen += state.phase
            assertEquals(state, roundTrip(state))
            val actor = rules.currentActor(state)!!
            state = rules.apply(state, actor, rules.view(state, actor).legalActions.random(random))
        }
        assertEquals(state, roundTrip(state))
        phasesSeen += state.phase
        assertTrue(EuchrePhase.PLAY in phasesSeen && EuchrePhase.COMPLETE in phasesSeen)
    }

    @Test
    fun `farmers and defend-alone states round-trip`() {
        val rules = EuchreRules(farmersHandEnabled = true, defendAlone = true)
        val farmers = rules.newGame(rules.findSeed { it.phase == EuchrePhase.FARMERS })
        assertEquals(farmers, roundTrip(farmers))

        val ordered = rules.act(rules.newGame(1, Seat(0)), EuchreAction.OrderUp(alone = true))
        val defend = rules.act(ordered, EuchreAction.DealerDiscard(ordered.hands.getValue(Seat(0)).first()))
        assertEquals(EuchrePhase.DEFEND_ALONE, defend.phase)
        assertEquals(defend, roundTrip(defend))
    }

    @Test
    fun `action serial names are the pinned wire vocabulary`() {
        fun name(action: EuchreAction): String =
            json.encodeToString(EuchreAction.serializer(), action).substringAfter("\"type\":\"").substringBefore("\"")
        assertEquals("pass", name(EuchreAction.Pass))
        assertEquals("orderUp", name(EuchreAction.OrderUp(alone = true)))
        assertEquals("callTrump", name(EuchreAction.CallTrump(io.github.rotundtapir.cardkit.core.Suit.HEARTS)))
        assertEquals("dealerDiscard", name(EuchreAction.DealerDiscard(euchreDeck().first())))
        assertEquals("defendAlone", name(EuchreAction.DefendAlone))
        assertEquals("declineDefend", name(EuchreAction.DeclineDefend))
        assertEquals("callFarmers", name(EuchreAction.CallFarmers(euchreDeck().take(3))))
        assertEquals("declineFarmers", name(EuchreAction.DeclineFarmers))
        assertEquals("playCard", name(EuchreAction.PlayCard(euchreDeck().first())))
    }

    @Test
    fun `views round-trip too`() {
        val rules = EuchreRules()
        val state = rules.newGame(9)
        val view = rules.view(state, Seat(1))
        assertEquals(
            view,
            json.decodeFromString<EuchrePlayerView>(json.encodeToString(EuchrePlayerView.serializer(), view)),
        )
    }
}
