package jp.developer.bbee.featuredemo.ui.intentlauncher

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntentLauncherViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()

    private val _body = MutableStateFlow("")
    val body: StateFlow<String> = _body.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun onTitleChange(value: String) {
        // タイトルは1行制限のため、ペースト等で混入した改行も除去する
        _title.value = value.replace("\n", "")
    }

    fun onBodyChange(value: String) {
        _body.value = value
    }

    fun onAppNotFound() {
        _message.value = "対応するアプリが見つかりませんでした"
    }

    fun suggestedFileName(): String = _title.value.ifBlank { "untitled" } + ".txt"

    fun saveToFile(uri: Uri?) {
        if (uri == null) {
            _message.value = "保存がキャンセルされました"
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                    stream.write(fileContent().toByteArray())
                } ?: error("出力先を開けませんでした")
            }.onSuccess {
                _message.value = "ファイルに保存しました"
            }.onFailure { e ->
                _message.value = "保存に失敗しました: ${e.message}"
            }
        }
    }

    private fun fileContent(): String = buildString {
        appendLine(_title.value)
        append(_body.value)
    }
}

@Composable
fun IntentLauncherScreen(
    modifier: Modifier = Modifier,
    viewModel: IntentLauncherViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val title by viewModel.title.collectAsStateWithLifecycle()
    val body by viewModel.body.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val saveFileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri -> viewModel.saveToFile(uri) }

    fun startActivitySafely(intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            viewModel.onAppNotFound()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "外部アプリ起動デモ",
            style = MaterialTheme.typography.headlineMedium,
        )
        OutlinedTextField(
            value = title,
            onValueChange = viewModel::onTitleChange,
            label = { Text("タイトル(1行)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = body,
            onValueChange = viewModel::onBodyChange,
            label = { Text("本文") },
            minLines = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = dropUnlessResumed { startActivitySafely(emailIntent(title, body)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("メールアプリを起動")
        }
        Button(
            onClick = dropUnlessResumed { startActivitySafely(smsIntent(title, body)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("メッセージアプリを起動")
        }
        Button(
            onClick = dropUnlessResumed { saveFileLauncher.launch(viewModel.suggestedFileName()) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("ファイルとして保存")
        }
        Button(
            onClick = dropUnlessResumed { startActivitySafely(textShareIntent(title, body)) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("共有アプリを選択")
        }
        message?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// mailto: スキームの ACTION_SENDTO を使い、メール作成画面を持つアプリのみを対象にする
private fun emailIntent(title: String, body: String): Intent =
    Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:".toUri()
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, body)
    }

// SMS には件名の概念がないため、タイトルと本文を連結して本文として渡す
private fun smsIntent(title: String, body: String): Intent =
    Intent(Intent.ACTION_SENDTO).apply {
        data = "smsto:".toUri()
        putExtra("sms_body", listOf(title, body).filter { it.isNotBlank() }.joinToString("\n"))
    }

// ACTION_SEND でテキストを受け取れるアプリ一覧をシステムチューザーで表示する
private fun textShareIntent(subject: String, body: String): Intent {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        // LINE 等 EXTRA_TEXT のみ参照するアプリ向けに件名と本文を結合して渡す
        putExtra(Intent.EXTRA_TEXT, listOf(subject, body).filter { it.isNotBlank() }.joinToString("\n"))
        // メールアプリ向けに件名は EXTRA_SUBJECT にも渡す
        if (subject.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
    }
    return Intent.createChooser(sendIntent, "共有")
}
