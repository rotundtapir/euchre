// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.engine

import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScoringTest {

    private fun makers(maker: Int = 0, alone: Boolean = false, loneDefender: Int? = null) = Makers(
        maker = Seat(maker),
        trump = Suit.SPADES,
        orderedUp = true,
        alone = alone,
        loneDefender = loneDefender?.let(::Seat),
    )

    private fun tricks(vararg bySeat: Pair<Int, Int>) = bySeat.associate { (s, n) -> Seat(s) to n }

    @Test
    fun `three or four tricks scores one point`() {
        val result = scoreEuchreHand(makers(), tricks(0 to 2, 1 to 1, 2 to 1, 3 to 1))
        assertTrue(result.made)
        assertEquals(3, result.makerTricks)
        assertEquals(mapOf(0 to 1), result.teamDeltas)

        val four = scoreEuchreHand(makers(), tricks(0 to 3, 2 to 1, 1 to 1, 3 to 0))
        assertEquals(mapOf(0 to 1), four.teamDeltas)
    }

    @Test
    fun `a march scores two`() {
        val result = scoreEuchreHand(makers(), tricks(0 to 3, 2 to 2, 1 to 0, 3 to 0))
        assertEquals(5, result.makerTricks)
        assertEquals(mapOf(0 to 2), result.teamDeltas)
    }

    @Test
    fun `a lone march scores four`() {
        val result = scoreEuchreHand(makers(alone = true), tricks(0 to 5, 1 to 0, 3 to 0))
        assertEquals(mapOf(0 to 4), result.teamDeltas)
    }

    @Test
    fun `a lone hand short of a march still scores normally`() {
        val result = scoreEuchreHand(makers(alone = true), tricks(0 to 4, 1 to 1, 3 to 0))
        assertEquals(mapOf(0 to 1), result.teamDeltas)
        val march = scoreEuchreHand(makers(alone = false), tricks(0 to 4, 2 to 1, 1 to 0, 3 to 0))
        assertEquals(mapOf(0 to 2), march.teamDeltas)
    }

    @Test
    fun `euchred makers give the defenders two`() {
        val result = scoreEuchreHand(makers(maker = 1), tricks(0 to 2, 2 to 1, 1 to 1, 3 to 1))
        assertFalse(result.made)
        assertEquals(2, result.makerTricks)
        assertEquals(mapOf(0 to 2), result.teamDeltas)
    }

    @Test
    fun `a successful lone defense scores four`() {
        val result = scoreEuchreHand(
            makers(maker = 1, alone = true, loneDefender = 0),
            tricks(0 to 3, 1 to 2),
        )
        assertEquals(mapOf(0 to 4), result.teamDeltas)
    }

    @Test
    fun `a failed lone defense scores the makers normally`() {
        val result = scoreEuchreHand(
            makers(maker = 1, alone = true, loneDefender = 0),
            tricks(0 to 0, 1 to 5),
        )
        assertEquals(mapOf(1 to 4), result.teamDeltas) // lone march
    }

    @Test
    fun `winner is the first team at ten`() {
        assertNull(determineEuchreWinner(mapOf(0 to 9, 1 to 8)))
        assertEquals(0, determineEuchreWinner(mapOf(0 to 10, 1 to 8)))
        assertEquals(1, determineEuchreWinner(mapOf(0 to 9, 1 to 11)))
    }
}
