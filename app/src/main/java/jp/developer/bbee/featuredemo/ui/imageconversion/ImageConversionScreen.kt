package jp.developer.bbee.featuredemo.ui.imageconversion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.developer.bbee.featuredemo.image.ConversionFormat
import jp.developer.bbee.featuredemo.image.ImageConverter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class SourceImageInfo(
    val displayName: String?,
    val mimeType: String?,
    val width: Int,
    val height: Int,
)

data class ImageConversionUiState(
    val sourceBitmap: Bitmap? = null,
    val sourceInfo: SourceImageInfo? = null,
    val selectedFormat: ConversionFormat = ConversionFormat.PNG,
    val isConverting: Boolean = false,
    val resultMessage: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class ImageConversionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageConversionUiState())
    val uiState: StateFlow<ImageConversionUiState> = _uiState.asStateFlow()

    private var loadImageJob: Job? = null

    fun onImageSelected(uri: Uri) {
        // 短時間に連続して画像を選択した場合に、先に開始した読み込みが後から完了して
        // 新しい選択結果を上書きしないよう、進行中の読み込みをキャンセルする
        loadImageJob?.cancel()
        loadImageJob = viewModelScope.launch {
            _uiState.update { it.copy(resultMessage = null, isError = false) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val bitmap = loadBitmap(uri)
                    val (displayName, mimeType) = querySourceInfo(uri)
                    bitmap to SourceImageInfo(displayName, mimeType, bitmap.width, bitmap.height)
                }
            }.onSuccess { (bitmap, info) ->
                _uiState.update { it.copy(sourceBitmap = bitmap, sourceInfo = info) }
            }.onFailure { e ->
                // キャンセルはエラーではないため表示せず、コルーチンの規約どおり再送出する
                if (e is CancellationException) throw e
                _uiState.update {
                    it.copy(resultMessage = "画像の読み込みに失敗しました: ${e.message}", isError = true)
                }
            }
        }
    }

    fun onFormatSelected(format: ConversionFormat) {
        _uiState.update { it.copy(selectedFormat = format) }
    }

    /** 保存ダイアログ(CreateDocument)に渡すデフォルトのファイル名。 */
    fun suggestedFileName(): String {
        val state = _uiState.value
        val baseName = state.sourceInfo?.displayName
            ?.substringBeforeLast('.')
            ?.takeIf { it.isNotBlank() }
            ?: "converted_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}"
        return "$baseName.${state.selectedFormat.extension}"
    }

    fun onSaveTargetSelected(target: Uri) {
        val state = _uiState.value
        val bitmap = state.sourceBitmap ?: return
        val format = state.selectedFormat
        viewModelScope.launch {
            _uiState.update { it.copy(isConverting = true, resultMessage = null, isError = false) }
            runCatching {
                // 変換 (CPU バウンド) と保存 (IO バウンド) でディスパッチャを分ける
                val bytes = withContext(Dispatchers.Default) {
                    ImageConverter.convert(context, bitmap, format)
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(target)?.use { it.write(bytes) }
                        ?: throw IllegalStateException("出力先を開けませんでした")
                }
                bytes.size
            }.onSuccess { size ->
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        resultMessage = "${format.displayName} 形式で保存しました (${formatBytes(size)})",
                    )
                }
            }.onFailure { e ->
                // 変換失敗時は CreateDocument で作成された空ファイルを削除しておく
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, target) }
                _uiState.update {
                    it.copy(
                        isConverting = false,
                        resultMessage = "変換に失敗しました: ${e.message}",
                        isError = true,
                    )
                }
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                // getPixels でピクセルを読み出せるようソフトウェア割り当てにする
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSampleSize(
                    sampleSizeFor(maxOf(info.size.width, info.size.height))
                )
            }
        } else {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, boundsOptions)
            }
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(maxOf(boundsOptions.outWidth, boundsOptions.outHeight))
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: throw IllegalStateException("画像を開けませんでした")
        }

    /** OOM を避けるため、長辺が [MAX_DIMENSION] 以下になる 2 のべき乗のサンプルサイズを返す。 */
    private fun sampleSizeFor(maxDimension: Int): Int {
        var sampleSize = 1
        while (maxDimension / sampleSize > MAX_DIMENSION) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun querySourceInfo(uri: Uri): Pair<String?, String?> {
        val displayName = context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        return displayName to context.contentResolver.getType(uri)
    }

    private fun formatBytes(size: Int): String = when {
        size >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", size / (1 shl 20).toDouble())
        size >= 1 shl 10 -> String.format(Locale.US, "%.1f KB", size / (1 shl 10).toDouble())
        else -> "$size B"
    }

    private companion object {
        const val MAX_DIMENSION = 4096
    }
}

@Composable
fun ImageConversionScreen(
    modifier: Modifier = Modifier,
    viewModel: ImageConversionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::onImageSelected) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(uiState.selectedFormat.mimeType)
    ) { uri -> uri?.let(viewModel::onSaveTargetSelected) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Button(
            onClick = {
                pickImageLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            // 変換中に画像を選び直すと、プレビューと実際に保存される画像
            // (変換開始時にキャプチャしたもの) が食い違うため無効化する
            enabled = !uiState.isConverting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("画像を選択")
        }

        uiState.sourceBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "選択した画像",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp),
                contentScale = ContentScale.Fit,
            )
        }

        uiState.sourceInfo?.let { info ->
            Column {
                Text(
                    text = info.displayName ?: "(名称不明)",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = "${info.mimeType ?: "形式不明"} / ${info.width}×${info.height}px",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (uiState.sourceBitmap != null) {
            Text(
                text = "変換先の形式",
                style = MaterialTheme.typography.titleMedium,
            )
            Column {
                ConversionFormat.entries.forEach { format ->
                    FormatRow(
                        format = format,
                        selected = format == uiState.selectedFormat,
                        enabled = format.isSupported && !uiState.isConverting,
                        onSelect = { viewModel.onFormatSelected(format) },
                    )
                }
            }
            Button(
                onClick = { createDocumentLauncher.launch(viewModel.suggestedFileName()) },
                enabled = !uiState.isConverting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isConverting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("変換して保存")
                }
            }
        }

        uiState.resultMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiState.isError) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
    }
}

@Composable
private fun FormatRow(
    format: ConversionFormat,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            enabled = enabled,
        )
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = format.displayName,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = format.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
