package jp.developer.bbee.featuredemo.ui.textscanner

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.text.Text as MlKitText
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetectedTextLine(
    val text: String,
    val bounds: Rect?,
)

@HiltViewModel
class TextScannerViewModel @Inject constructor() : ViewModel() {

    private val _textLines = MutableStateFlow<List<DetectedTextLine>>(emptyList())
    val textLines: StateFlow<List<DetectedTextLine>> = _textLines.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private var lastDetectedAt = 0L

    fun onTextRecognized(result: MlKitText) {
        val now = SystemClock.elapsedRealtime()
        val lines = result.textBlocks.flatMap { it.lines }
        if (lines.isEmpty()) {
            // 検出が一瞬途切れたときのちらつきを抑えるため、少しの間は直前の結果を残す
            if (now - lastDetectedAt > HOLD_DURATION_MS) {
                _textLines.value = emptyList()
                _recognizedText.value = ""
            }
            return
        }
        lastDetectedAt = now
        _textLines.value = lines.map { line ->
            DetectedTextLine(text = line.text, bounds = line.boundingBox)
        }
        _recognizedText.value = result.text
    }

    private companion object {
        const val HOLD_DURATION_MS = 1_000L
    }
}

@Composable
fun TextScannerScreen(
    modifier: Modifier = Modifier,
    viewModel: TextScannerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        TextScannerContent(viewModel = viewModel, modifier = modifier)
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("文字の読み取りにはカメラ権限が必要です")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("カメラ権限を許可する")
            }
        }
    }
}

@Composable
private fun TextScannerContent(
    viewModel: TextScannerViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val textLines by viewModel.textLines.collectAsStateWithLifecycle()
    val recognizedText by viewModel.recognizedText.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()

    // 既定のオプションはラテン文字(英数字)向け。日本語等を読み取る場合は
    // 対応するオプション(例: JapaneseTextRecognizerOptions)に切り替える
    val textRecognizer = remember { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                // COORDINATE_SYSTEM_VIEW_REFERENCED により boundingBox が
                // PreviewView 座標系へ自動変換され、そのままオーバーレイ描画に使える
                MlKitAnalyzer(
                    listOf(textRecognizer),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    ContextCompat.getMainExecutor(context),
                ) { result ->
                    result.getValue(textRecognizer)?.let(viewModel::onTextRecognized)
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose {
            cameraController.unbind()
            textRecognizer.close()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply { controller = cameraController }
                },
                modifier = Modifier.fillMaxSize(),
            )
            TextOverlay(textLines = textLines)
        }
        RecognizedTextPanel(
            recognizedText = recognizedText,
            onCopyClick = {
                coroutineScope.launch {
                    clipboard.setClipEntry(
                        ClipEntry(ClipData.newPlainText("recognized_text", recognizedText))
                    )
                }
            },
        )
    }
}

@Composable
private fun TextOverlay(
    textLines: List<DetectedTextLine>,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        textLines.forEach { line ->
            val rect = line.bounds ?: return@forEach
            drawRect(
                color = Color.Green,
                topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                size = Size(rect.width().toFloat(), rect.height().toFloat()),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

@Composable
private fun RecognizedTextPanel(
    recognizedText: String,
    onCopyClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = recognizedText.ifEmpty { "カメラを文字に向けてください" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp, max = 120.dp)
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .padding(8.dp),
        )
        Button(
            onClick = onCopyClick,
            enabled = recognizedText.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("読み取った文字をコピー")
        }
    }
}
