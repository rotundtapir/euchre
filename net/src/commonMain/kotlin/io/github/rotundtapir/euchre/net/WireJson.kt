// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.net

import io.github.rotundtapir.cardkit.net.gameWireModule
import io.github.rotundtapir.cardkit.net.wireJson
import io.github.rotundtapir.euchre.engine.EuchreAction
import io.github.rotundtapir.euchre.engine.EuchrePlayerView

/**
 * The single JSON configuration used on both ends of Euchre's wire, kept identical client- and
 * server-side so a frame encoded by one decodes on the other.
 *
 * The configuration itself — unknown keys ignored, unknown wire enums coerced to their `UNKNOWN`
 * member, fields at their default omitted, `"type"` pinned as the discriminator — lives in
 * `cardkit-net`'s [wireJson]. What Euchre adds is the registration of its own payload types.
 * Registering exactly one action/view/config instantiation is what makes this `Json` speak Euchre and
 * nothing else.
 */
val WireJson = wireJson(
    gameWireModule(
        actionSerializer = EuchreAction.serializer(),
        viewSerializer = EuchrePlayerView.serializer(),
        configSerializer = LobbyConfig.serializer(),
        createLobbyClass = CreateLobby::class,
        createLobbySerializer = CreateLobby.serializer(),
    ),
)
