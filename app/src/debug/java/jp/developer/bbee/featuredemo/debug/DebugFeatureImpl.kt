package jp.developer.bbee.featuredemo.debug

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jp.developer.bbee.featuredemo.debug.ui.DebugScreen as DebugScreenContent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * デバッグビルド用の [DebugFeature]。
 * 起動時に常駐通知サービスを立ち上げ、デバッグ画面を提供する。
 */
@Singleton
class DebugFeatureImpl @Inject constructor() : DebugFeature {

    override val isEnabled: Boolean = true

    // 画面回転などで起動処理(特に通知権限ダイアログ)を繰り返さないための、
    // プロセス単位のガード
    private var startupHandled = false

    @Composable
    override fun DebugStartupEffect() {
        val context = LocalContext.current

        // API 33 以降は POST_NOTIFICATIONS がないと常駐通知が表示されない。
        // 拒否されてもサービス自体は起動しておく(権限付与後の再起動で表示される)
        val permissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) {
            DebugNotificationService.start(context)
        }

        LaunchedEffect(Unit) {
            if (startupHandled) return@LaunchedEffect
            startupHandled = true
            if (needsNotificationPermission(context)) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                DebugNotificationService.start(context)
            }
        }
    }

    @Composable
    override fun DebugScreen(onBack: () -> Unit) {
        // 同名のメンバー関数と衝突するため、画面本体は別名でインポートして呼び出す
        DebugScreenContent(onBack = onBack)
    }

    private fun needsNotificationPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugFeatureModule {

    @Binds
    abstract fun bindDebugFeature(impl: DebugFeatureImpl): DebugFeature
}
