package jp.developer.bbee.featuredemo.debug

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import jp.developer.bbee.featuredemo.MainActivity
import jp.developer.bbee.featuredemo.R
import jp.developer.bbee.featuredemo.notification.NotificationHelper

/**
 * デバッグビルド専用の常駐フォアグラウンドサービス。
 * 通知バーに消えない通知を出し、タップするとデバッグ画面へ遷移する。
 *
 * このクラスとマニフェスト宣言は `src/debug` にのみ存在するため、release APK には含まれない。
 */
class DebugNotificationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 通知の「停止」アクションから呼ばれた場合はサービスごと終了する
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        return try {
            startForegroundCompat()
            START_STICKY
        } catch (e: Exception) {
            // フォアグラウンド起動が拒否された場合(バックグラウンド起動制限など)は
            // クラッシュさせずに終了する。デバッグ用途のため復帰は次回起動に任せる
            Log.e(TAG, "startForeground failed; stopping debug notification service", e)
            stopSelf()
            START_NOT_STICKY
        }
    }

    private fun startForegroundCompat() {
        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannelCompat.Builder(
            CHANNEL_ID,
            NotificationManagerCompat.IMPORTANCE_LOW,
        )
            .setName("デバッグツール")
            .setDescription("デバッグビルドの常駐通知")
            .setShowBadge(false)
            .build()
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            putExtra(NotificationHelper.EXTRA_DESTINATION, NotificationHelper.DESTINATION_DEBUG)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            this,
            REQUEST_CODE_OPEN,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stopIntent = PendingIntent.getService(
            this,
            REQUEST_CODE_STOP,
            Intent(this, DebugNotificationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("デバッグメニュー")
            .setContentText("タップでデバッグ画面を開きます")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(contentIntent)
            .addAction(0, "停止", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
    }

    companion object {
        private const val TAG = "DebugNotificationService"
        private const val CHANNEL_ID = "debug_tools"
        private const val NOTIFICATION_ID = 3001
        private const val REQUEST_CODE_OPEN = 3001
        private const val REQUEST_CODE_STOP = 3002
        private const val ACTION_STOP = "jp.developer.bbee.featuredemo.debug.action.STOP"

        /** 常駐通知を開始する。既に起動済みの場合は通知が更新されるだけ */
        fun start(context: Context) {
            val intent = Intent(context, DebugNotificationService::class.java)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                // Android 12 以降はバックグラウンドからのフォアグラウンドサービス起動が
                // 制限される。デバッグ用途なので失敗しても落とさない
                Log.e(TAG, "failed to start debug notification service", e)
            }
        }

        /** 常駐通知を停止する(現状は通知のアクションからのみ使用) */
        fun stop(context: Context) {
            context.stopService(Intent(context, DebugNotificationService::class.java))
        }

        /** 常駐通知が表示中かどうか */
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return false
            return manager.activeNotifications.any { it.id == NOTIFICATION_ID }
        }
    }
}
