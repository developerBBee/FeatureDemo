package jp.developer.bbee.featuredemo.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object HomeRoute : NavKey

@Serializable
data class DetailRoute(val id: String) : NavKey

@Serializable
data object BiometricAuthRoute : NavKey

@Serializable
data object AuthenticatedRoute : NavKey

@Serializable
data object IntentLauncherRoute : NavKey

@Serializable
data object BarcodeScannerRoute : NavKey

@Serializable
data object TextScannerRoute : NavKey

@Serializable
data object FaceDetectionRoute : NavKey

@Serializable
data object NotificationRoute : NavKey

@Serializable
data object DailyRoutesMapRoute : NavKey

@Serializable
data object ImageConversionRoute : NavKey

// デバッグビルドでのみ到達する画面。ルート定義自体は rememberNavBackStack の
// シリアライズ対象として main に置き、画面の実装は src/debug 側で差し替える
@Serializable
data object DebugRoute : NavKey
