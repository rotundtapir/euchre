// SPDX-License-Identifier: GPL-3.0-or-later WITH LicenseRef-cardkit-ads-exception
package io.github.rotundtapir.euchre

import android.content.Context
import io.github.rotundtapir.cardkit.ui.settings.DataStoreKeyValueStore

/** The DataStore file backing the app's preferences. Renaming it orphans existing settings. */
private const val SETTINGS_STORE_NAME = "euchre_settings"

/** The app's settings, persisted with Jetpack DataStore. */
fun androidSettingsRepository(context: Context): SettingsRepository =
    KeyValueSettingsRepository(DataStoreKeyValueStore(context, SETTINGS_STORE_NAME))
