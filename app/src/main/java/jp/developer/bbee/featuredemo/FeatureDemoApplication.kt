package jp.developer.bbee.featuredemo

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import jp.developer.bbee.featuredemo.notification.NotificationHelper

@HiltAndroidApp
class FeatureDemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 通知チャンネルは起動時に一度作成しておく(同一 ID なら再作成は no-op)
        NotificationHelper.createChannel(this)
    }
}
