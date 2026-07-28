// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui.tutorial

import io.github.rotundtapir.cardkit.core.Rank
import io.github.rotundtapir.cardkit.core.Suit
import io.github.rotundtapir.cardkit.core.of
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Where the guidance bubble's tail points.
 *
 * Anchors are recorded by whatever is on screen and read back by key, so a step that asks for a key
 * nothing currently records does not fail — it silently reuses whatever last wrote that key. That is
 * how the discard step ended up pointing at the previous step's bid button. Every step that names a
 * card must therefore point at that card, which is a live anchor wherever the fan puts it.
 */
class TutorialTargetTest {

    private val nine = Rank.NINE of Suit.HEARTS

    @Test
    fun `a step that names a card points at that card`() {
        assertEquals(
            cardAnchor(nine.code),
            targetKey(EuchreTutorialStep.PlayStep(nine, advice = ""), isHumanDecision = true),
        )
        assertEquals(
            cardAnchor(nine.code),
            targetKey(EuchreTutorialStep.DiscardStep(nine, advice = ""), isHumanDecision = true),
            "the discard names a card, so its tail belongs on that card — not on the action panel, " +
                "which anchors no button during a discard",
        )
    }

    @Test
    fun `bidding steps point at the action panel's one live button`() {
        assertEquals(
            ACTION_ANCHOR,
            targetKey(EuchreTutorialStep.Round1Step(orderUp = true, advice = ""), isHumanDecision = true),
        )
        assertEquals(
            ACTION_ANCHOR,
            targetKey(EuchreTutorialStep.Round2Step(call = Suit.SPADES, advice = ""), isHumanDecision = true),
        )
    }

    @Test
    fun `a bubble pointing at a card still clears the action panel`() {
        // The fan sits below the prompt and the Discard button, so a bubble placed only against the
        // card covers both — the controls the advice is asking the player to use.
        val panelTop = 1200f
        val cardTop = 1600f
        val top = bubbleTopAbove(cardTop, panelTop, bubbleHeight = 700, gap = 12)
        assertEquals(1200 - 700 - 12, top, "the bubble stops above the panel, not above the card")
    }

    @Test
    fun `with no panel on screen the bubble sits against its target`() {
        assertEquals(400 - 100 - 12, bubbleTopAbove(400f, null, bubbleHeight = 100, gap = 12))
    }

    @Test
    fun `a target above the panel keeps its own placement`() {
        // A bidding button is inside the panel but above its bottom; the tighter of the two wins.
        assertEquals(900 - 300 - 12, bubbleTopAbove(900f, 1200f, bubbleHeight = 300, gap = 12))
    }

    @Test
    fun `while the bots act the bubble sits over the felt`() {
        assertEquals(TRICK_ANCHOR, targetKey(null, isHumanDecision = false))
        assertEquals(
            TRICK_ANCHOR,
            targetKey(EuchreTutorialStep.PlayStep(nine, advice = ""), isHumanDecision = false),
            "a scripted card the player is not being asked for yet must not drag the tail down to it",
        )
    }
}
