package jp.developer.bbee.featuredemo

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import jp.developer.bbee.featuredemo.debug.DebugFeature
import jp.developer.bbee.featuredemo.navigation.AuthenticatedRoute
import jp.developer.bbee.featuredemo.navigation.BiometricAuthRoute
import jp.developer.bbee.featuredemo.navigation.DebugRoute
import jp.developer.bbee.featuredemo.navigation.DetailRoute
import jp.developer.bbee.featuredemo.navigation.HomeRoute
import jp.developer.bbee.featuredemo.navigation.BarcodeScannerRoute
import jp.developer.bbee.featuredemo.navigation.IntentLauncherRoute
import jp.developer.bbee.featuredemo.navigation.FaceDetectionRoute
import jp.developer.bbee.featuredemo.navigation.DailyRoutesMapRoute
import jp.developer.bbee.featuredemo.navigation.ImageConversionRoute
import jp.developer.bbee.featuredemo.navigation.NotificationRoute
import jp.developer.bbee.featuredemo.navigation.TextScannerRoute
import jp.developer.bbee.featuredemo.notification.NotificationHelper
import jp.developer.bbee.featuredemo.ui.dailyroutesmap.DailyRoutesMapScreen
import jp.developer.bbee.featuredemo.ui.authenticated.AuthenticatedScreen
import jp.developer.bbee.featuredemo.ui.barcodescanner.BarcodeScannerScreen
import jp.developer.bbee.featuredemo.ui.biometric.BiometricAuthScreen
import jp.developer.bbee.featuredemo.ui.detail.DetailScreen
import jp.developer.bbee.featuredemo.ui.home.HomeScreen
import jp.developer.bbee.featuredemo.ui.intentlauncher.IntentLauncherScreen
import jp.developer.bbee.featuredemo.ui.facedetection.FaceDetectionScreen
import jp.developer.bbee.featuredemo.ui.imageconversion.ImageConversionScreen
import jp.developer.bbee.featuredemo.ui.notification.NotificationDemoScreen
import jp.developer.bbee.featuredemo.ui.textscanner.TextScannerScreen
import jp.developer.bbee.featuredemo.ui.theme.FeatureDemoTheme
import javax.inject.Inject

// BiometricPrompt(androidx.biometric 安定版)が FragmentActivity を要求するため
// ComponentActivity ではなく FragmentActivity を継承する
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    // ビルドバリアントごとに実装が差し替わる(release では何もしない実装が注入される)
    @Inject lateinit var debugFeature: DebugFeature

    // 通知タップで開く画面を表す state。onNewIntent で更新し、Compose 側で消費する。
    // 同じ画面を再タップしても再発火させるため、消費後に null へ戻す前提で扱う。
    private val pendingDestination = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingDestination.value = intent?.getStringExtra(NotificationHelper.EXTRA_DESTINATION)
        enableEdgeToEdge()
        setContent {
            FeatureDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavDisplay(
                        pendingDestination = pendingDestination,
                        debugFeature = debugFeature,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDestination.value = intent.getStringExtra(NotificationHelper.EXTRA_DESTINATION)
    }
}

@Composable
private fun AppNavDisplay(
    pendingDestination: MutableState<String?>,
    debugFeature: DebugFeature,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(HomeRoute)
    val destination by pendingDestination

    // デバッグビルドでは常駐通知サービスを起動する(release では何も起きない)
    debugFeature.DebugStartupEffect()

    // 通知からの遷移指示を処理する。処理後に null へ戻すことで再タップにも反応する。
    LaunchedEffect(destination) {
        when (destination) {
            NotificationHelper.DESTINATION_NOTIFICATION ->
                if (backStack.lastOrNull() != NotificationRoute) {
                    backStack.add(NotificationRoute)
                }

            NotificationHelper.DESTINATION_DEBUG ->
                if (debugFeature.isEnabled && backStack.lastOrNull() != DebugRoute) {
                    backStack.add(DebugRoute)
                }
        }
        if (destination != null) {
            pendingDestination.value = null
        }
    }

    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { backStack.removeLastOrNull() },
        // ViewModelStoreNavEntryDecorator により各 NavEntry が ViewModelStoreOwner を担い、
        // hiltViewModel() の生存期間が NavEntry(バックスタック上のエントリー)に
        // スコープされる。エントリーがポップされると ViewModel は clear される
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        entryProvider = entryProvider {
            entry<HomeRoute> {
                HomeScreen(
                    onItemClick = { id -> backStack.add(DetailRoute(id)) },
                    onBiometricDemoClick = { backStack.add(BiometricAuthRoute) },
                    onIntentLauncherDemoClick = { backStack.add(IntentLauncherRoute) },
                    onBarcodeScannerDemoClick = { backStack.add(BarcodeScannerRoute) },
                    onTextScannerDemoClick = { backStack.add(TextScannerRoute) },
                    onFaceDetectionDemoClick = { backStack.add(FaceDetectionRoute) },
                    onNotificationDemoClick = { backStack.add(NotificationRoute) },
                    onDailyRoutesMapDemoClick = { backStack.add(DailyRoutesMapRoute) },
                    onImageConversionDemoClick = { backStack.add(ImageConversionRoute) },
                )
            }
            entry<DetailRoute> { route ->
                DetailScreen(
                    route = route,
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<BiometricAuthRoute> {
                BiometricAuthScreen(
                    onAuthenticated = {
                        // 認証画面を認証後画面で置き換え、戻る操作で認証画面に
                        // 戻らないようにする
                        backStack.removeLastOrNull()
                        backStack.add(AuthenticatedRoute)
                    },
                )
            }
            entry<AuthenticatedRoute> {
                AuthenticatedScreen(
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<IntentLauncherRoute> {
                IntentLauncherScreen()
            }
            entry<BarcodeScannerRoute> {
                BarcodeScannerScreen()
            }
            entry<TextScannerRoute> {
                TextScannerScreen()
            }
            entry<FaceDetectionRoute> {
                FaceDetectionScreen()
            }
            entry<NotificationRoute> {
                NotificationDemoScreen()
            }
            entry<DailyRoutesMapRoute> {
                DailyRoutesMapScreen(
                    onBack = { backStack.removeLastOrNull() },
                )
            }
            entry<ImageConversionRoute> {
                ImageConversionScreen()
            }
            entry<DebugRoute> {
                debugFeature.DebugScreen(
                    onBack = { backStack.removeLastOrNull() },
                )
            }
        },
    )
}
