// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui

import io.github.rotundtapir.euchre.ui.tutorial.narrationIdFor
import io.github.rotundtapir.euchre.ui.tutorial.tutorialLessons
import io.github.rotundtapir.euchre.ui.tutorial.tutorialNarration
import io.github.rotundtapir.euchre.ui.tutorial.tutorialNarrationSources
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The tutorial's narration keys. No audio ships in v0.1.0, but the ids are the future clip
 * filenames, so they are frozen now — and the id lookup is BY DISPLAY TEXT, which only works while
 * no two texts anywhere in the four lessons are identical.
 */
class TutorialNarrationTest {

    @Test
    fun `every narration id is unique and stems from its lesson`() {
        val ids = tutorialNarrationSources.map { it.first }
        assertEquals(ids.size, ids.distinct().size, "duplicate narration ids: ${duplicates(ids)}")
        val stems = tutorialLessons.map { it.id }
        ids.forEach { id ->
            assertTrue(stems.any { id.startsWith("$it-") }, "narration id '$id' has no lesson stem")
        }
    }

    @Test
    fun `every lesson contributes the full key set`() {
        val ids = tutorialNarrationSources.map { it.first }.toSet()
        for (lesson in tutorialLessons) {
            lesson.prologue.indices.forEach { assertTrue("${lesson.id}-primer-${it + 1}" in ids) }
            lesson.steps.indices.forEach { assertTrue("${lesson.id}-step-${it + 1}" in ids) }
            lesson.trickNotes.keys.forEach { assertTrue("${lesson.id}-trick-$it" in ids) }
            lesson.epilogue.indices.forEach { assertTrue("${lesson.id}-epilogue-${it + 1}" in ids) }
            assertTrue("${lesson.id}-completion" in ids)
            assertTrue("${lesson.id}-hand-done" in ids)
        }
    }

    @Test
    fun `display texts are unique across all four lessons`() {
        // The narration lookup is keyed by the text on screen: two identical texts in different
        // lessons would silently narrate one of them with the other's clip.
        val texts = tutorialNarrationSources.map { it.second }
        assertEquals(texts.size, texts.distinct().size, "duplicate tutorial texts: ${duplicates(texts)}")
        texts.forEach { text -> assertNotNull(narrationIdFor(text), "no id resolves for: $text") }
    }

    @Test
    fun `spoken text expands card notation and never shouts`() {
        val basicsStep = tutorialLessons.first { it.id == "basics" }.steps.first().advice
        val spoken = tutorialNarration.first { it.text.isNotBlank() && it.id == "basics-step-1" }.text
        assertTrue("J♣" in basicsStep, "the fixture step should carry card notation")
        assertTrue("jack of clubs" in spoken, "card notation must be spoken in words: $spoken")
        assertTrue("♣" !in spoken, "no suit glyph may survive into the spoken text: $spoken")
        assertTrue("LEFT BOWER" !in spoken, "shouted emphasis must be folded to lowercase")
    }

    private fun <T> duplicates(values: List<T>): List<T> =
        values.groupBy { it }.filterValues { it.size > 1 }.keys.toList()
}
