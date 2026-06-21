package jp.developer.bbee.featuredemo.ui.notification

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.developer.bbee.featuredemo.notification.NotificationHelper
import jp.developer.bbee.featuredemo.notification.NotificationStatusRepository
import jp.developer.bbee.featuredemo.notification.TaskStatus
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NotificationDemoViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statusRepository: NotificationStatusRepository,
) : ViewModel() {

    // 通知のアクションで更新されるステータス。receiver と同じ Singleton を共有する
    val status: StateFlow<TaskStatus> = statusRepository.status

    fun showSimpleMessage() = NotificationHelper.showSimpleMessage(context)

    fun showOpenScreen() = NotificationHelper.showOpenScreen(context)

    fun showChoices() {
        // 選択肢通知を出す前にステータスを初期状態へ戻し、選択の反映を分かりやすくする
        statusRepository.reset()
        NotificationHelper.showChoices(context)
    }

    fun resetStatus() = statusRepository.reset()
}

@Composable
fun NotificationDemoScreen(
    modifier: Modifier = Modifier,
    viewModel: NotificationDemoViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val status by viewModel.status.collectAsStateWithLifecycle()

    var hasPermission by remember {
        mutableStateOf(hasNotificationPermission(context))
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    // 権限が未取得なら通知を出そうとする操作に先立って要求する
    fun ensurePermissionThen(action: () -> Unit) {
        if (hasPermission) {
            action()
        } else {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "通知デモ",
            style = MaterialTheme.typography.headlineMedium,
        )

        if (!hasPermission) {
            Text(
                text = "通知を表示するには通知権限が必要です。ボタンを押すと権限を要求します。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Button(
            onClick = dropUnlessResumed { ensurePermissionThen(viewModel::showSimpleMessage) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("1. メッセージ通知を表示")
        }
        Button(
            onClick = dropUnlessResumed { ensurePermissionThen(viewModel::showOpenScreen) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("2. タップで画面を開く通知を表示")
        }
        Button(
            onClick = dropUnlessResumed { ensurePermissionThen(viewModel::showChoices) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("3. 選択肢付き通知を表示")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        StatusCard(
            status = status,
            onReset = dropUnlessResumed(block = viewModel::resetStatus),
        )
    }
}

@Composable
private fun StatusCard(
    status: TaskStatus,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "選択肢付き通知のステータス",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = status.label,
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = "通知の「承認」「却下」をタップすると、ここの表示が更新されます。",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(onClick = onReset) {
                Text("ステータスをリセット")
            }
        }
    }
}

private fun hasNotificationPermission(context: Context): Boolean {
    // Android 12 以下では実行時権限が不要なため常に許可扱い
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
}
