package jp.developer.bbee.featuredemo.ui.barcodescanner

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.SystemClock
import android.webkit.URLUtil
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DetectedBarcode(
    val text: String,
    val url: String?,
    val bounds: Rect?,
)

@HiltViewModel
class BarcodeScannerViewModel @Inject constructor() : ViewModel() {

    private val _barcodes = MutableStateFlow<List<DetectedBarcode>>(emptyList())
    val barcodes: StateFlow<List<DetectedBarcode>> = _barcodes.asStateFlow()

    private var lastDetectedAt = 0L

    fun onBarcodesDetected(detected: List<Barcode>) {
        val now = SystemClock.elapsedRealtime()
        if (detected.isEmpty()) {
            // 検出が一瞬途切れたときのちらつきを抑えるため、少しの間は直前の結果を残す
            if (now - lastDetectedAt > HOLD_DURATION_MS) {
                _barcodes.value = emptyList()
            }
            return
        }
        lastDetectedAt = now
        _barcodes.value = detected.mapNotNull { barcode ->
            val text = barcode.displayValue ?: barcode.rawValue ?: return@mapNotNull null
            DetectedBarcode(
                text = text,
                url = barcode.url?.url ?: text.takeIf(URLUtil::isNetworkUrl),
                bounds = barcode.boundingBox,
            )
        }
    }

    private companion object {
        const val HOLD_DURATION_MS = 1_000L
    }
}

@Composable
fun BarcodeScannerScreen(
    modifier: Modifier = Modifier,
    viewModel: BarcodeScannerViewModel = hiltViewModel(),
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
        BarcodeScannerContent(viewModel = viewModel, modifier = modifier)
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("バーコードの読み取りにはカメラ権限が必要です")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("カメラ権限を許可する")
            }
        }
    }
}

@Composable
private fun BarcodeScannerContent(
    viewModel: BarcodeScannerViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val barcodes by viewModel.barcodes.collectAsStateWithLifecycle()

    // 既定の BarcodeScanner は全フォーマット対応で、1フレーム内の複数バーコードを検出する
    val barcodeScanner = remember { BarcodeScanning.getClient() }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                // COORDINATE_SYSTEM_VIEW_REFERENCED により boundingBox が
                // PreviewView 座標系へ自動変換され、そのままオーバーレイ描画に使える
                MlKitAnalyzer(
                    listOf(barcodeScanner),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    ContextCompat.getMainExecutor(context),
                ) { result ->
                    viewModel.onBarcodesDetected(result.getValue(barcodeScanner).orEmpty())
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose { cameraController.unbind() }
    }

    // lifecycleOwner の差し替えでは閉じず、この Composable の破棄時にのみ閉じる
    DisposableEffect(Unit) {
        onDispose { barcodeScanner.close() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply { controller = cameraController }
            },
            modifier = Modifier.fillMaxSize(),
        )
        BarcodeOverlay(
            barcodes = barcodes,
            onUrlClick = { url -> openUrl(context, url) },
        )
    }
}

@Composable
private fun BarcodeOverlay(
    barcodes: List<DetectedBarcode>,
    onUrlClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            barcodes.forEach { barcode ->
                val rect = barcode.bounds ?: return@forEach
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                    size = Size(rect.width().toFloat(), rect.height().toFloat()),
                    style = Stroke(width = 2.dp.toPx()),
                )
            }
        }
        barcodes.forEach { barcode ->
            val rect = barcode.bounds ?: return@forEach
            Text(
                text = barcode.text,
                color = if (barcode.url != null) Color(0xFF82B1FF) else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                textDecoration = if (barcode.url != null) TextDecoration.Underline else null,
                modifier = Modifier
                    // 読み取った文字は枠のすぐ下に表示する
                    .offset { IntOffset(rect.left, rect.bottom) }
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .then(
                        barcode.url?.let { url ->
                            Modifier.clickable { onUrlClick(url) }
                        } ?: Modifier
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        // ブラウザーが存在しない場合は何もしない
    }
}
