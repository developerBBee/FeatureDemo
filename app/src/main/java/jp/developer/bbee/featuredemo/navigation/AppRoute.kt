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
