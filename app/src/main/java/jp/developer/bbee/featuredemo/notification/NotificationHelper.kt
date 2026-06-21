package jp.developer.bbee.featuredemo.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import jp.developer.bbee.featuredemo.MainActivity
import jp.developer.bbee.featuredemo.R

/**
 * 3 種類のデモ通知の生成・投稿をまとめたヘルパー。
 *
 * 画面(ViewModel)とアクション受信用の [NotificationActionReceiver] の双方から呼ばれるため、
 * 状態を持たない `object` として定数と投稿ロジックを集約する。
 */
object NotificationHelper {

    const val CHANNEL_ID = "demo_messages"
    private const val CHANNEL_NAME = "デモ通知"

    const val NOTIFICATION_ID_SIMPLE = 1001
    const val NOTIFICATION_ID_OPEN_SCREEN = 1002
    const val NOTIFICATION_ID_CHOICES = 1003

    // 通知タップ時に開く画面を MainActivity へ伝えるための Intent エクストラ
    const val EXTRA_DESTINATION = "extra_destination"
    const val DESTINATION_NOTIFICATION = "notification_demo"

    // 選択肢アクションを受け取るためのブロードキャスト定義
    const val ACTION_UPDATE_STATUS = "jp.developer.bbee.featuredemo.action.UPDATE_STATUS"
    const val EXTRA_STATUS = "extra_status"

    /**
     * 通知チャンネルを作成する。同一 ID で繰り返し呼んでも安全(既存なら更新)。
     * Application の起動時に一度呼んでおけば十分。
     */
    fun createChannel(context: Context) {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_DEFAULT,
        )
            .setName(CHANNEL_NAME)
            .setDescription("通知機能デモ用のチャンネル")
            .build()
        NotificationManagerCompat.from(context).createNotificationChannel(channel)
    }

    /** 1. 普通のメッセージ通知 */
    fun showSimpleMessage(context: Context) {
        val notification = baseBuilder(context)
            .setContentTitle("新着メッセージ")
            .setContentText("これは普通のメッセージ通知です。")
            .setAutoCancel(true)
            .build()
        notify(context, NOTIFICATION_ID_SIMPLE, notification)
    }

    /** 2. タップするとアプリの通知デモ画面を開く通知 */
    fun showOpenScreen(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_DESTINATION, DESTINATION_NOTIFICATION)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_OPEN_SCREEN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = baseBuilder(context)
            .setContentTitle("画面を開く通知")
            .setContentText("タップすると通知デモ画面を開きます。")
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        notify(context, NOTIFICATION_ID_OPEN_SCREEN, notification)
    }

    /** 3. 選択肢付き通知。選んだ内容でステータスを更新する */
    fun showChoices(context: Context) {
        val notification = baseBuilder(context)
            .setContentTitle("承認リクエスト")
            .setContentText("この依頼を承認しますか?")
            .addAction(0, "承認", statusActionIntent(context, TaskStatus.APPROVED))
            .addAction(0, "却下", statusActionIntent(context, TaskStatus.REJECTED))
            .setAutoCancel(false)
            .build()
        notify(context, NOTIFICATION_ID_CHOICES, notification)
    }

    /** 選択肢が選ばれた後、選択結果を反映した通知へ更新する(receiver から呼ばれる) */
    fun showStatusUpdated(context: Context, status: TaskStatus) {
        val notification = baseBuilder(context)
            .setContentTitle("承認リクエスト")
            .setContentText("ステータスを「${status.label}」に更新しました。")
            .setAutoCancel(true)
            .build()
        notify(context, NOTIFICATION_ID_CHOICES, notification)
    }

    private fun statusActionIntent(context: Context, status: TaskStatus): PendingIntent {
        val intent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_UPDATE_STATUS
            putExtra(EXTRA_STATUS, status.name)
        }
        return PendingIntent.getBroadcast(
            context,
            // ステータスごとに別の PendingIntent になるよう requestCode を分ける
            status.ordinal,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun baseBuilder(context: Context): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

    private fun notify(context: Context, id: Int, notification: android.app.Notification) {
        // Android 13(TIRAMISU)以降は POST_NOTIFICATIONS 権限がないと通知は表示されない。
        // 権限が無い場合は黙って無視する(画面側で権限取得を促す)。
        // 12 以下では実行時権限が不要なため、チェックせずそのまま投稿する。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
