// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Joker
import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.SuitedCard
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckEuchreTest {

    @Test
    fun `standard deck is the 24 cards nine through ace`() {
        val deck = euchreDeck()
        assertEquals(24, deck.size)
        assertEquals(24, deck.toSet().size)
        val expectedRanks = setOf(Rank.NINE, Rank.TEN, Rank.JACK, Rank.QUEEN, Rank.KING, Rank.ACE)
        assertTrue(deck.all { it is SuitedCard && it.rank in expectedRanks })
        assertEquals(4, deck.filterIsInstance<SuitedCard>().groupBy { it.suit }.size)
        assertTrue(deck.filterIsInstance<SuitedCard>().groupBy { it.suit }.values.all { it.size == 6 })
    }

    @Test
    fun `benny deck adds the joker`() {
        val deck = euchreDeck(benny = true)
        assertEquals(25, deck.size)
        assertTrue(Joker in deck)
    }
}
