package jp.developer.bbee.featuredemo.ui.facedetection

import android.Manifest
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class DetectedFace(
    val bounds: Rect,
    val trackingId: Int?,
    val smilingProbability: Float?,
    val leftEyeOpenProbability: Float?,
    val rightEyeOpenProbability: Float?,
)

@HiltViewModel
class FaceDetectionViewModel @Inject constructor() : ViewModel() {

    private val _faces = MutableStateFlow<List<DetectedFace>>(emptyList())
    val faces: StateFlow<List<DetectedFace>> = _faces.asStateFlow()

    private var lastDetectedAt = 0L

    fun onFacesDetected(detected: List<Face>) {
        val now = SystemClock.elapsedRealtime()
        if (detected.isEmpty()) {
            if (now - lastDetectedAt > HOLD_DURATION_MS) {
                _faces.value = emptyList()
            }
            return
        }
        lastDetectedAt = now
        _faces.value = detected.map { face ->
            DetectedFace(
                bounds = face.boundingBox,
                trackingId = face.trackingId,
                smilingProbability = face.smilingProbability,
                leftEyeOpenProbability = face.leftEyeOpenProbability,
                rightEyeOpenProbability = face.rightEyeOpenProbability,
            )
        }
    }

    private companion object {
        const val HOLD_DURATION_MS = 500L
    }
}

@Composable
fun FaceDetectionScreen(
    modifier: Modifier = Modifier,
    viewModel: FaceDetectionViewModel = hiltViewModel(),
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
        FaceDetectionContent(viewModel = viewModel, modifier = modifier)
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        ) {
            Text("顔検出にはカメラ権限が必要です")
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("カメラ権限を許可する")
            }
        }
    }
}

@Composable
private fun FaceDetectionContent(
    viewModel: FaceDetectionViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val faces by viewModel.faces.collectAsStateWithLifecycle()

    val faceDetectorOptions = remember {
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .enableTracking()
            .build()
    }
    val faceDetector = remember { FaceDetection.getClient(faceDetectorOptions) }
    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setImageAnalysisAnalyzer(
                ContextCompat.getMainExecutor(context),
                // COORDINATE_SYSTEM_VIEW_REFERENCED により boundingBox が
                // PreviewView 座標系へ自動変換され、そのままオーバーレイ描画に使える
                MlKitAnalyzer(
                    listOf(faceDetector),
                    ImageAnalysis.COORDINATE_SYSTEM_VIEW_REFERENCED,
                    ContextCompat.getMainExecutor(context),
                ) { result ->
                    viewModel.onFacesDetected(result.getValue(faceDetector).orEmpty())
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
        onDispose { cameraController.unbind() }
    }

    DisposableEffect(Unit) {
        onDispose { faceDetector.close() }
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
            FaceOverlay(faces = faces)
        }
        FaceInfoPanel(faces = faces)
    }
}

@Composable
private fun FaceOverlay(
    faces: List<DetectedFace>,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            faces.forEach { face ->
                val rect = face.bounds
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(rect.left.toFloat(), rect.top.toFloat()),
                    size = Size(rect.width().toFloat(), rect.height().toFloat()),
                    style = Stroke(width = 3.dp.toPx()),
                )
            }
        }
        faces.forEach { face ->
            val rect = face.bounds
            val labelLines = buildList {
                face.trackingId?.let { add("ID: $it") }
                face.smilingProbability?.let { add("笑顔: ${(it * 100).toInt()}%") }
                val leftEye = face.leftEyeOpenProbability?.let { (it * 100).toInt() }
                val rightEye = face.rightEyeOpenProbability?.let { (it * 100).toInt() }
                if (leftEye != null && rightEye != null) {
                    add("左目: ${leftEye}% 右目: ${rightEye}%")
                }
            }
            if (labelLines.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .offset { IntOffset(rect.left, rect.bottom + 4) }
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    labelLines.forEach { line ->
                        Text(
                            text = line,
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceInfoPanel(
    faces: List<DetectedFace>,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (faces.isEmpty()) "カメラを顔に向けてください" else "検出した顔: ${faces.size}人",
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
