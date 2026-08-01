package jp.developer.bbee.featuredemo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import jp.developer.bbee.featuredemo.data.datastore.AppSettingsDataStore
import jp.developer.bbee.featuredemo.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class FeatureDemoApplication : Application() {

    // Hilt の field injection は super.onCreate() の中で完了するため、
    // onCreate() 内(super 呼び出し後)からは安全に参照できる
    @Inject lateinit var appSettingsDataStore: AppSettingsDataStore

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // 通知チャンネルは起動時に一度作成しておく(同一 ID なら再作成は no-op)
        NotificationHelper.createChannel(this)
        applicationScope.launch { appSettingsDataStore.recordLaunch() }
    }
}
