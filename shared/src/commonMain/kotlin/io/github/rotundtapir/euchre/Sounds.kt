// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import androidx.compose.runtime.Composable
import io.github.rotundtapir.cardkit.ui.SoundEffect
import io.github.rotundtapir.cardkit.ui.pacing.rememberTableSoundEffects
import io.github.rotundtapir.cardkit.ui.pacing.soundEffectsFor
import io.github.rotundtapir.euchre.engine.EuchrePlayerView

/**
 * The game's sound wiring: cardkit's state-driven table triggers, fed this game's view through the
 * [EuchreTransitions] projection. Returns the shared play function the dealing animation's
 * `soundHook` also uses (shuffle / card-slide effects fire imperatively from there).
 *
 * A null [view] is a no-op, and at `volume == 0f` no audio resource is ever touched — the
 * instrumented suites rely on that (native playback crashes on the `-no-audio` emulator).
 */
@Composable
fun rememberEuchreSoundEffects(view: EuchrePlayerView?, volume: Float): (SoundEffect) -> Unit =
    rememberTableSoundEffects(view?.transitions, volume)

/**
 * The effects a [prev]-to-[next] view transition triggers — cardkit's pure rule set applied to this
 * game's projection. Exposed (rather than inlined at the call site) so the mapping from Euchre
 * views to effects is unit-testable without a composition.
 */
fun euchreSoundEffectsFor(prev: EuchrePlayerView?, next: EuchrePlayerView?): List<SoundEffect> =
    soundEffectsFor(prev?.transitions, next?.transitions)
