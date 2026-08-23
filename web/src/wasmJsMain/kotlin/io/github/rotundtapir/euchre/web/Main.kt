// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre.web

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.window.ComposeViewport
import io.github.rotundtapir.cardkit.monetization.browser.BrowserMonetization
import io.github.rotundtapir.cardkit.ui.AppConfig
import io.github.rotundtapir.cardkit.ui.AppDistribution
import io.github.rotundtapir.cardkit.ui.AppPlatform
import io.github.rotundtapir.cardkit.ui.CardArtWarmup
import io.github.rotundtapir.cardkit.ui.settings.AnimationSpeed
import io.github.rotundtapir.cardkit.ui.settings.BotSkill
import io.github.rotundtapir.cardkit.ui.settings.LocalStorageKeyValueStore
import io.github.rotundtapir.cardkit.ui.theme.CardkitTheme
import io.github.rotundtapir.euchre.AppReloader
import io.github.rotundtapir.euchre.EuchreApp
import io.github.rotundtapir.euchre.KeyValueSettingsRepository
import io.github.rotundtapir.euchre.ProjectLinks
import io.github.rotundtapir.euchre.engine.euchreDeck
import io.github.rotundtapir.euchre.web.generated.resources.Res
import io.github.rotundtapir.euchre.web.generated.resources.symbol_fallback
import kotlin.random.Random
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.configureWebResources
import org.jetbrains.compose.resources.preloadFont
import io.github.rotundtapir.euchre.online.JoinLink
import org.w3c.dom.url.URLSearchParams

/** localStorage key prefix for this app's settings; every entry is `euchre.<SettingsKeys name>`. */
private const val SETTINGS_PREFIX = "euchre."

/** How long to wait for the fallback font before showing the app regardless. */
private const val FONT_GRACE_MILLIS = 5_000L

/**
 * Browser entry point. URL query parameters mirror MainActivity's test-override intent extras:
 * `?seed=42&animationSpeed=OFF&soundVolume=0` reproduces the instrumentation fixture.
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalResourceApi::class)
fun main() {
    // GitHub Pages serves the app from a repository subpath (…github.io/euchre/), so resource
    // requests must stay relative instead of assuming the site root.
    configureWebResources {
        resourcePathMapping { path -> "./$path" }
    }

    val overrides = urlOverrides()

    ComposeViewport(document.body!!) {
        // The embedded default font lacks the symbols the UI draws (card suits, arrows, the
        // settings gear, check marks) and the web canvas has no system fonts to fall back on, so
        // register a subset of DejaVu Sans (see web/FONT_LICENSE-DejaVu.txt) covering those
        // blocks as a fallback before showing any text.
        val suitFont by preloadFont(Res.font.symbol_fallback)
        val resolver = LocalFontFamilyResolver.current
        var fontsReady by remember { mutableStateOf(false) }
        LaunchedEffect(suitFont) {
            suitFont?.let {
                resolver.preload(FontFamily(it))
                fontsReady = true
            }
        }
        // If the font never arrives (network failure), show the app anyway after a grace period —
        // missing suit glyphs beat a permanently blank page.
        LaunchedEffect(Unit) {
            delay(FONT_GRACE_MILLIS)
            fontsReady = true
        }
        if (!fontsReady) return@ComposeViewport
        // First real frame is about to compose — only now retire the static "loading" placeholder.
        LaunchedEffect(Unit) {
            document.getElementById("loading")?.remove()
        }
        CardkitTheme {
            Box {
                // Web image loading is async: warm the card bitmaps into the resource cache at
                // startup so the first deal doesn't show blank backs/faces while PNGs stream in.
                // Only the cards Euchre can deal — Benny's Joker included, since the warmup runs
                // before a game exists — which is ~780 KB of PNGs not fetched before that deal.
                CardArtWarmup(cards = euchreDeck(benny = true))
                EuchreApp(
                    monetization = remember { BrowserMonetization(ProjectLinks.DONATION_URL) },
                    settings = remember {
                        KeyValueSettingsRepository(LocalStorageKeyValueStore(SETTINGS_PREFIX))
                    },
                    appConfig = AppConfig(
                        feedbackUri = ProjectLinks.ISSUE_TRACKER,
                        version = AppBuildInfo.VERSION,
                        platform = AppPlatform.WEB,
                        flavor = AppDistribution.WEB,
                        commit = AppBuildInfo.COMMIT,
                    ),
                    // A ?seed= parameter pins every new game to it, which is what the e2e suite wants.
                    nextSeed = { overrides.seed ?: Random.nextLong() },
                    animationSpeedOverride = overrides.animationSpeed,
                    soundVolumeOverride = overrides.soundVolume,
                    botSkillOverride = overrides.botSkill,
                    aiBudgetMillisOverride = overrides.aiBudgetMillis,
                    serverUrlOverride = overrides.serverUrl,
                    joinCodeOverride = overrides.joinCode,
                    playerNameOverride = overrides.playerName,
                    // A page reload starts a fresh wasm instance with no memory of its seat; the
                    // token in sessionStorage is the only thing that lets it reclaim one.
                    sessionTokenStore = remember { SessionStorageTokenStore() },
                    linkSharer = remember { BrowserLinkSharer() },
                    // The only real reloader in the app: on web an out-of-date client is fixed by refetching
                    // the page, so the "Update required" dialog offers Reload instead of a store link.
                    appReloader = remember { AppReloader { window.location.reload() } },
                )
            }
        }
    }
}

/**
 * The overrides a URL can carry. All are session-only — none is ever written to localStorage.
 * That matters most for [serverUrl] and [playerName]: a link that could permanently repoint
 * someone's online play would also control the trusted "update required" text they are shown.
 */
private class UrlOverrides(
    val seed: Long?,
    val animationSpeed: AnimationSpeed?,
    val soundVolume: Float?,
    val botSkill: BotSkill?,
    val aiBudgetMillis: Long?,
    val serverUrl: String?,
    val joinCode: String?,
    val playerName: String?,
)

private fun urlOverrides(): UrlOverrides {
    val params = URLSearchParams(window.location.search.toJsString())
    return UrlOverrides(
        seed = params.get("seed")?.toLongOrNull(),
        animationSpeed = AnimationSpeed.fromName(params.get("animationSpeed")),
        soundVolume = params.get("soundVolume")?.toFloatOrNull(),
        botSkill = BotSkill.fromName(params.get("botSkill")),
        aiBudgetMillis = params.get("aiBudgetMs")?.toLongOrNull(),
        serverUrl = params.get("serverUrl")?.takeIf { it.isNotBlank() },
        joinCode = params.get(JoinLink.PARAM)?.let(JoinLink::normalizeCode),
        playerName = params.get("playerName")?.takeIf { it.isNotBlank() },
    )
}
