package jp.developer.bbee.featuredemo

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dagger.hilt.android.AndroidEntryPoint
import jp.developer.bbee.featuredemo.navigation.AuthenticatedRoute
import jp.developer.bbee.featuredemo.navigation.BiometricAuthRoute
import jp.developer.bbee.featuredemo.navigation.DetailRoute
import jp.developer.bbee.featuredemo.navigation.HomeRoute
import jp.developer.bbee.featuredemo.ui.authenticated.AuthenticatedScreen
import jp.developer.bbee.featuredemo.ui.biometric.BiometricAuthScreen
import jp.developer.bbee.featuredemo.ui.detail.DetailScreen
import jp.developer.bbee.featuredemo.ui.home.HomeScreen
import jp.developer.bbee.featuredemo.ui.theme.FeatureDemoTheme

// BiometricPrompt(androidx.biometric 安定版)が FragmentActivity を要求するため
// ComponentActivity ではなく FragmentActivity を継承する
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FeatureDemoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavDisplay(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun AppNavDisplay(modifier: Modifier = Modifier) {
    val backStack = rememberNavBackStack(HomeRoute)

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
        },
    )
}
