package jp.developer.bbee.featuredemo.ui.biometric

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class BiometricAuthViewModel @Inject constructor() : ViewModel() {

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
        _message.value = when (errorCode) {
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED,
                -> "認証がキャンセルされました"

            else -> "認証エラー($errorCode): $errString"
        }
    }

    fun onAuthenticationFailed() {
        _message.value = "生体情報を認識できませんでした。もう一度お試しください"
    }
}

@Composable
fun BiometricAuthScreen(
    onAuthenticated: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BiometricAuthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as FragmentActivity
    val message by viewModel.message.collectAsStateWithLifecycle()

    var authStatus by remember {
        mutableIntStateOf(BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG))
    }

    // 認証成功時のコールバックは BiometricPrompt 生成後に変わり得るため最新を参照する
    val currentOnAuthenticated by rememberUpdatedState(onAuthenticated)
    val biometricPrompt = remember(activity) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(context),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    currentOnAuthenticated()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    viewModel.onAuthenticationError(errorCode, errString)
                }

                override fun onAuthenticationFailed() {
                    viewModel.onAuthenticationFailed()
                }
            },
        )
    }
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder()
            .setTitle("生体認証")
            .setSubtitle("登録済みの生体情報で認証してください")
            .setAllowedAuthenticators(BIOMETRIC_STRONG)
            // DEVICE_CREDENTIAL を許可しない場合はネガティブボタンが必須
            .setNegativeButtonText("キャンセル")
            .build()
    }

    // 生体情報の登録画面から戻ってきたら利用可否を再判定する
    val enrollLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        authStatus = BiometricManager.from(context).canAuthenticate(BIOMETRIC_STRONG)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "生体認証デモ",
            style = MaterialTheme.typography.headlineMedium,
        )
        when (authStatus) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                Text("端末に登録済みの生体情報で認証します")
                Button(onClick = dropUnlessResumed { biometricPrompt.authenticate(promptInfo) }) {
                    Text("認証する")
                }
            }

            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> {
                Text("生体情報が未登録です。端末設定から登録してください")
                Button(onClick = dropUnlessResumed { enrollLauncher.launch(enrollIntent()) }) {
                    Text("生体情報を登録する")
                }
            }

            else -> {
                Text(
                    text = unavailableMessage(authStatus),
                    textAlign = TextAlign.Center,
                )
            }
        }
        message?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun enrollIntent(): Intent = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
        Intent(Settings.ACTION_BIOMETRIC_ENROLL).putExtra(
            Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
            BIOMETRIC_STRONG,
        )

    Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ->
        Intent(Settings.ACTION_FINGERPRINT_ENROLL)

    else -> Intent(Settings.ACTION_SECURITY_SETTINGS)
}

private fun unavailableMessage(status: Int): String = when (status) {
    BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "この端末は生体認証に対応していません"
    BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "生体認証ハードウェアが現在利用できません"
    BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED -> "セキュリティアップデートが必要です"
    else -> "生体認証を利用できません(status=$status)"
}
