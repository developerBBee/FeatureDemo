package jp.developer.bbee.featuredemo.debug

import androidx.compose.runtime.Composable

/**
 * デバッグ専用機能(デバッグ画面・常駐通知)への入口。
 *
 * 実装はビルドバリアントごとのソースセットで差し替える:
 * - `src/debug`  … [DebugFeature] の実装(デバッグ画面と常駐フォアグラウンドサービス)
 * - `src/release`… 何もしない実装
 *
 * これによりデバッグ用のコードとサービス宣言は release APK に一切含まれない。
 */
interface DebugFeature {

    /** デバッグ機能が有効か。release ビルドでは false */
    val isEnabled: Boolean

    /**
     * アプリ起動時に一度だけ呼ばれる副作用。
     * デバッグビルドでは通知権限の要求と常駐通知サービスの起動を行う。
     */
    @Composable
    fun DebugStartupEffect()

    /** デバッグ画面本体。release ビルドでは何も描画しない */
    @Composable
    fun DebugScreen(onBack: () -> Unit)
}
