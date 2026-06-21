package jp.developer.bbee.featuredemo.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 選択肢付き通知(承認/却下)のアクションを受け取り、
 * 共有ステータス([NotificationStatusRepository])を更新したうえで
 * 通知を選択結果の表示に差し替える。
 *
 * BroadcastReceiver への `@AndroidEntryPoint` フィールド注入は、Hilt Gradle プラグインの
 * バイトコード変換と Kotlin の組み合わせで `super.onReceive` を呼べない制約があるため、
 * `@EntryPoint` + [EntryPointAccessors] で Singleton リポジトリを取得する方式にしている。
 * 取得されるインスタンスは ViewModel が注入で得るものと同一の Singleton。
 * AndroidManifest への登録が必要。
 */
class NotificationActionReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ReceiverEntryPoint {
        fun statusRepository(): NotificationStatusRepository
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != NotificationHelper.ACTION_UPDATE_STATUS) return

        val repository = EntryPointAccessors
            .fromApplication(context.applicationContext, ReceiverEntryPoint::class.java)
            .statusRepository()

        val status = TaskStatus.fromName(intent.getStringExtra(NotificationHelper.EXTRA_STATUS))
        repository.update(status)
        NotificationHelper.showStatusUpdated(context, status)
    }
}
