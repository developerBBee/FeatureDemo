package jp.developer.bbee.featuredemo.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * アプリ全体の永続設定を保持する Preferences DataStore のラッパー。
 * 起動回数と最終起動時刻を記録し、デバッグ画面から中身を確認できる。
 */
@Singleton
class AppSettingsDataStore @Inject constructor(
    @AppSettingsPreferences private val dataStore: DataStore<Preferences>,
) {

    val launchCount: Flow<Int> = dataStore.data.map { it[KEY_LAUNCH_COUNT] ?: 0 }

    val lastLaunchedAt: Flow<Long> = dataStore.data.map { it[KEY_LAST_LAUNCHED_AT] ?: 0L }

    /** プロセス起動時に呼ぶ。起動回数をインクリメントし、起動時刻を更新する */
    suspend fun recordLaunch() {
        dataStore.edit { preferences ->
            preferences[KEY_LAUNCH_COUNT] = (preferences[KEY_LAUNCH_COUNT] ?: 0) + 1
            preferences[KEY_LAST_LAUNCHED_AT] = System.currentTimeMillis()
        }
    }

    companion object {
        const val FILE_NAME = "app_settings"

        private val KEY_LAUNCH_COUNT = intPreferencesKey("launch_count")
        private val KEY_LAST_LAUNCHED_AT = longPreferencesKey("last_launched_at")
    }
}
