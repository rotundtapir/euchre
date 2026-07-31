// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import io.github.rotundtapir.euchre.ui.tutorial.tutorialNarration
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The drift gate between the tutorial's on-screen words and its pre-generated voice clips.
 *
 * `scripts/generate-narration.sh` synthesizes one MP3 per [tutorialNarration] line and records each
 * line's SHA-256 in the manifest beside the clips. If a tutorial text changes without regenerating,
 * this fails — the voice must always say what the screen shows, and a stale clip is worse than
 * silence because it is confidently wrong.
 *
 * Skipped wholesale until the first clips land, so the build stays green while the tutorial ships
 * silent. Once a manifest exists the gate is live and cannot be turned off by accident.
 */
class NarrationManifestTest {

    private val narrationDir = moduleFile("src/commonMain/composeResources/files/narration")

    @Test
    fun `every narration line has a current clip and manifest entry - else regenerate`() {
        val manifestFile = File(narrationDir, "manifest.txt")
        if (!manifestFile.exists()) return // no audio generated yet — see the class doc
        val manifest = manifestFile.readLines()
            .filterNot { it.startsWith("#") || it.isBlank() }
            .associate { line -> line.substringBefore(' ') to line.substringAfter(' ') }
        for (line in tutorialNarration) {
            val recorded = manifest[line.id]
                ?: fail("Narration line '${line.id}' has no manifest entry — run scripts/generate-narration.sh")
            assertEquals(
                recorded,
                sha256(line.text),
                "Narration text '${line.id}' changed after its audio was generated — " +
                    "run scripts/generate-narration.sh and commit the new clips",
            )
            assertTrue(
                File(narrationDir, "${line.id}.mp3").exists(),
                "Missing clip ${line.id}.mp3 — run scripts/generate-narration.sh",
            )
        }
        assertEquals(
            tutorialNarration.map { it.id }.toSet(),
            manifest.keys,
            "Manifest lists clips for lines that no longer exist — run scripts/generate-narration.sh",
        )
    }

    /**
     * Not a test of behaviour: writes the current narration texts where the generation script reads
     * them. Running it is how the script extracts the texts without parsing Kotlin — and it is why
     * the texts can be dumped on a machine with the Android toolchain and rendered on a different
     * one with a GPU.
     *
     * JSON Lines rather than the TSV 500 uses, because **euchre's tutorial prose contains paragraph
     * breaks**: 12 of these 68 texts span multiple lines, so a line-oriented format silently
     * mis-parses them — the first TSV dump turned 68 entries into 98 "rows". One JSON object per
     * line survives any character in the text, and the reader never has to guess where an entry
     * ends. (500's texts happen to be single-line, which is why its TSV has never had to cope.)
     */
    @Test
    fun `dump narration texts for the generation script`() {
        val out = moduleFile("build/narration-texts.jsonl")
        out.parentFile.mkdirs()
        out.writeText(
            tutorialNarration.joinToString("\n") { line ->
                Json.encodeToString(
                    JsonObject.serializer(),
                    JsonObject(mapOf("id" to JsonPrimitive(line.id), "text" to JsonPrimitive(line.text))),
                )
            } + "\n",
        )
        assertTrue(tutorialNarration.isNotEmpty())
    }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray())
            .joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }

    /** Resolves a path inside the `shared` module whether the test runs from it or from the root. */
    private fun moduleFile(relative: String): File {
        val cwd = File(System.getProperty("user.dir"))
        val direct = File(cwd, relative)
        if (direct.exists() || cwd.name == "shared") return direct
        return File(cwd, "shared/$relative")
    }
}
