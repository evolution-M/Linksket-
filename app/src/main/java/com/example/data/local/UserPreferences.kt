package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemePreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "linkstash_prefs")

data class AppSettings(
    val defaultFolderId: Long = 1L,
    val isGridView: Boolean = true,
    val wifiOnlyDownloads: Boolean = false,
    val autoExportToGallery: Boolean = false,
    val themePreference: ThemePreference = ThemePreference.SYSTEM
)

class UserPreferences(private val context: Context) {

    private object Keys {
        val DEFAULT_FOLDER_ID = longPreferencesKey("default_folder_id")
        val IS_GRID_VIEW = booleanPreferencesKey("is_grid_view")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
        val AUTO_EXPORT = booleanPreferencesKey("auto_export_to_gallery")
        val THEME_PREF = stringPreferencesKey("theme_preference")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME_PREF] ?: ThemePreference.SYSTEM.name
        AppSettings(
            defaultFolderId = prefs[Keys.DEFAULT_FOLDER_ID] ?: 1L,
            isGridView = prefs[Keys.IS_GRID_VIEW] ?: true,
            wifiOnlyDownloads = prefs[Keys.WIFI_ONLY] ?: false,
            autoExportToGallery = prefs[Keys.AUTO_EXPORT] ?: false,
            themePreference = runCatching { ThemePreference.valueOf(themeStr) }
                .getOrDefault(ThemePreference.SYSTEM)
        )
    }

    suspend fun setDefaultFolderId(folderId: Long) {
        context.dataStore.edit { it[Keys.DEFAULT_FOLDER_ID] = folderId }
    }

    suspend fun setGridView(isGrid: Boolean) {
        context.dataStore.edit { it[Keys.IS_GRID_VIEW] = isGrid }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = wifiOnly }
    }

    suspend fun setAutoExport(autoExport: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_EXPORT] = autoExport }
    }

    suspend fun setThemePreference(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME_PREF] = theme.name }
    }
}
