package jp.developer.bbee.featuredemo.debug

import androidx.compose.runtime.Composable
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * release ビルド用の [DebugFeature]。何もしない。
 * デバッグ画面と常駐通知サービスの実装は `src/debug` にしか存在しないため、
 * release APK には含まれない。
 */
@Singleton
class ReleaseDebugFeature @Inject constructor() : DebugFeature {

    override val isEnabled: Boolean = false

    @Composable
    override fun DebugStartupEffect() = Unit

    @Composable
    override fun DebugScreen(onBack: () -> Unit) = Unit
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DebugFeatureModule {

    @Binds
    abstract fun bindDebugFeature(impl: ReleaseDebugFeature): DebugFeature
}
