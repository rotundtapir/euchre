// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.web

import io.github.rotundtapir.euchre.online.SessionTokenStore
import kotlinx.browser.window

/**
 * Keeps the online session token in the tab's `sessionStorage`, so a page reload — which tears down
 * the whole wasm instance and its WebSocket — can still reclaim the seat it held.
 *
 * Deliberately `sessionStorage` and not `localStorage`: the token is a bearer credential for one
 * seat, and the server evicts the older socket when a second one presents the same token. Two tabs
 * sharing a token through `localStorage` would therefore evict each other in an endless reconnect
 * loop. Per-tab storage means each tab is its own player, which is what opening a second tab looks
 * like it should do anyway.
 */
class SessionStorageTokenStore(private val key: String = "euchre.sessionToken") : SessionTokenStore {

    override suspend fun load(): String? = runCatching { window.sessionStorage.getItem(key) }.getOrNull()

    override suspend fun save(token: String?) {
        // Storage can throw (private-mode quotas, a blocked origin). A token that cannot be stored
        // costs this tab its resume, never the game it is playing.
        runCatching {
            if (token == null) window.sessionStorage.removeItem(key) else window.sessionStorage.setItem(key, token)
        }
    }
}
