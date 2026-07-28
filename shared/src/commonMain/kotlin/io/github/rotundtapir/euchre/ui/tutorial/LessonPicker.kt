// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.ui.tutorial

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.rotundtapir.cardkit.ui.felt.InkOnCardSurface
import io.github.rotundtapir.cardkit.ui.tutorial.CardFaceDialog
import io.github.rotundtapir.cardkit.ui.tutorial.ReaderTextButton
import io.github.rotundtapir.cardkit.ui.tutorial.ReaderTitle

/**
 * "How to play": the four lessons, in order, with a tick beside the ones already finished and the
 * next one to take picked out. The written rules stay one tap away — some players want the
 * reference, not the walkthrough.
 */
@Composable
fun LessonPickerDialog(
    lessonsDone: Set<String>,
    onSelect: (TutorialLesson) -> Unit,
    onReadRules: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The next lesson to take: the first unfinished one, or nothing once they are all done.
    val next = tutorialLessons.firstOrNull { it.id !in lessonsDone }
    CardFaceDialog(onDismissRequest = onDismiss, modifier = modifier, testTag = "lessonPicker") {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 22.dp, bottom = 8.dp),
        ) {
            ReaderTitle("How to play")
            Spacer(Modifier.height(6.dp))
            Text(
                "Four short lessons. Each one deals the same fixed hand every time and walks you " +
                    "through it — only the right move is enabled, so you cannot go wrong.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(14.dp))
            tutorialLessons.forEach { lesson ->
                LessonRow(
                    lesson = lesson,
                    done = lesson.id in lessonsDone,
                    isNext = lesson.id == next?.id,
                    onSelect = { onSelect(lesson) },
                )
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = InkOnCardSurface.copy(alpha = 0.2f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                ReaderTextButton(
                    "Read the rules",
                    onClick = onReadRules,
                    modifier = Modifier.testTag("readRules"),
                )
                Spacer(Modifier.weight(1f))
                ReaderTextButton(
                    "Close",
                    onClick = onDismiss,
                    emphasized = true,
                    modifier = Modifier.testTag("lessonPickerClose"),
                )
            }
        }
    }
}

/** One lesson in the picker: its number and title, what it teaches, and whether it is finished. */
@Composable
private fun LessonRow(
    lesson: TutorialLesson,
    done: Boolean,
    isNext: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isNext) InkOnCardSurface.copy(alpha = 0.08f) else Color.Transparent,
        contentColor = InkOnCardSurface,
        border = BorderStroke(1.dp, InkOnCardSurface.copy(alpha = if (isNext) 0.7f else 0.25f)),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .testTag("lesson:${lesson.id}"),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                "${lesson.ordinal}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    lesson.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isNext) FontWeight.Bold else FontWeight.Medium,
                )
                Text(lesson.subtitle, style = MaterialTheme.typography.bodySmall)
            }
            // Plain glyph, no emoji: the wasm canvas only carries the bundled symbol subset.
            Text(
                when {
                    done -> "✓"
                    isNext -> "Next"
                    else -> ""
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
