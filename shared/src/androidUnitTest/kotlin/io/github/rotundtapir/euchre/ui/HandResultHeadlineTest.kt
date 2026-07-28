// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import io.github.rotundtapir.cardkit.core.Seat
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.euchre.engine.EuchreHandResult
import io.github.rotundtapir.euchre.engine.HAND_SIZE
import io.github.rotundtapir.euchre.engine.Makers
import kotlin.test.Test
import kotlin.test.assertEquals

/** The one-line verdict on the hand-result dialog's banner. */
class HandResultHeadlineTest {

    private fun result(tricks: Int, alone: Boolean = false, loneDefender: Seat? = null): EuchreHandResult {
        val makers = Makers(
            maker = Seat(0),
            trump = Suit.SPADES,
            orderedUp = true,
            alone = alone,
            loneDefender = loneDefender,
        )
        val made = tricks >= 3
        val points = when {
            tricks == HAND_SIZE && alone -> 4
            tricks == HAND_SIZE -> 2
            made -> 1
            loneDefender != null -> 4
            else -> 2
        }
        return EuchreHandResult(
            makers = makers,
            makerTricks = tricks,
            made = made,
            teamDeltas = mapOf((if (made) 0 else 1) to points),
        )
    }

    @Test
    fun `each outcome reads as its points and its reason`() {
        assertEquals("+1 — made it", handResultHeadline(result(tricks = 3)))
        assertEquals("+1 — made it", handResultHeadline(result(tricks = 4)))
        assertEquals("+2 — march!", handResultHeadline(result(tricks = 5)))
        assertEquals("+4 — alone march", handResultHeadline(result(tricks = 5, alone = true)))
        assertEquals("+2 — euchred!", handResultHeadline(result(tricks = 2)))
        assertEquals(
            "+4 — euchred alone!",
            handResultHeadline(result(tricks = 1, alone = true, loneDefender = Seat(1))),
        )
    }
}
