package jp.developer.bbee.featuredemo.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/** アプリ設定用の Preferences DataStore を指す修飾子 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppSettingsPreferences

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    @AppSettingsPreferences
    fun provideAppSettingsPreferences(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // DataStore は同一ファイルに対して複数インスタンスを作れない(実行時例外)。
        // 生成はこのモジュールに集約し、利用側は必ず注入で受け取ること
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        produceFile = { context.preferencesDataStoreFile(AppSettingsDataStore.FILE_NAME) },
    )

    /**
     * デバッグ画面が「アプリ内の全 Preferences DataStore」を一覧できるようにするための登録。
     * DataStore を追加したら、ここにも `@IntoMap` のエントリーを 1 つ足すこと。
     */
    @Provides
    @IntoMap
    @StringKey(AppSettingsDataStore.FILE_NAME)
    fun provideAppSettingsIntoMap(
        @AppSettingsPreferences dataStore: DataStore<Preferences>,
    ): DataStore<Preferences> = dataStore
}
