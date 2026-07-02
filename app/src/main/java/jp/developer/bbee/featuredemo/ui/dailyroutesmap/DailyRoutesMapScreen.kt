package jp.developer.bbee.featuredemo.ui.dailyroutesmap

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import jp.developer.bbee.featuredemo.data.db.LocationPointEntity
import jp.developer.bbee.featuredemo.data.location.LocationRepository
import jp.developer.bbee.featuredemo.service.LocationTrackingService
import jp.developer.bbee.featuredemo.service.TrackingStateHolder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DailyRoutesMapViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationRepository: LocationRepository,
    private val trackingStateHolder: TrackingStateHolder,
) : ViewModel() {

    val isTracking: StateFlow<Boolean> = trackingStateHolder.isTracking

    val availableDates: StateFlow<List<String>> = locationRepository.getAvailableDates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDateIndex = MutableStateFlow(0)

    // selectedDate / isOldestDate / isNewestDate はすべて同じ clamp 済みインデックスから導出し、
    // availableDates が縮んだ場合でも表示と前後ボタンの活性状態が食い違わないようにする
    private val clampedDateIndex: Flow<Int> =
        combine(availableDates, _selectedDateIndex) { dates, idx ->
            idx.coerceIn(0, (dates.size - 1).coerceAtLeast(0))
        }

    val selectedDate: StateFlow<String?> = combine(availableDates, clampedDateIndex) { dates, idx ->
        dates.getOrNull(idx)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val routePoints: StateFlow<List<LocationPointEntity>> = selectedDate
        .flatMapLatest { date ->
            if (date != null) locationRepository.getPointsForDate(date) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOldestDate: StateFlow<Boolean> = combine(availableDates, clampedDateIndex) { dates, idx ->
        idx >= dates.size - 1
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isNewestDate: StateFlow<Boolean> = combine(availableDates, clampedDateIndex) { dates, idx ->
        dates.isEmpty() || idx == 0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun startTracking() = LocationTrackingService.start(context)
    fun stopTracking() = LocationTrackingService.stop(context)

    fun selectOlderDate() {
        val max = (availableDates.value.size - 1).coerceAtLeast(0)
        _selectedDateIndex.value = (_selectedDateIndex.value.coerceIn(0, max) + 1).coerceAtMost(max)
    }

    fun selectNewerDate() {
        val max = (availableDates.value.size - 1).coerceAtLeast(0)
        _selectedDateIndex.value = (_selectedDateIndex.value.coerceIn(0, max) - 1).coerceAtLeast(0)
    }
}

@Composable
fun DailyRoutesMapScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DailyRoutesMapViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val availableDates by viewModel.availableDates.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val routePoints by viewModel.routePoints.collectAsStateWithLifecycle()
    val isOldestDate by viewModel.isOldestDate.collectAsStateWithLifecycle()
    val isNewestDate by viewModel.isNewestDate.collectAsStateWithLifecycle()

    var hasLocationPermission by remember {
        mutableStateOf(hasLocationPermission(context))
    }

    // バックスタックからの復帰時に権限が取り消されている場合を検出する
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasLocationPermission = hasLocationPermission(context)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission =
            permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    val cameraPositionState = rememberCameraPositionState {
        // 東京駅をデフォルト中心
        position = CameraPosition.fromLatLngZoom(LatLng(35.6812362, 139.7671248), 14f)
    }

    LaunchedEffect(routePoints) {
        if (routePoints.size >= 2) {
            val bounds = LatLngBounds.Builder().apply {
                routePoints.forEach { include(LatLng(it.latitude, it.longitude)) }
            }.build()
            cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, 120))
        } else if (routePoints.size == 1) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(routePoints[0].latitude, routePoints[0].longitude), 16f
                )
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!hasLocationPermission) {
            PermissionRequestContent(
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                },
                onBack = onBack,
            )
        } else {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = remember { MapProperties(isMyLocationEnabled = true) },
            ) {
                val latLngs = routePoints.map { LatLng(it.latitude, it.longitude) }
                if (latLngs.size >= 2) {
                    Polyline(
                        points = latLngs,
                        color = RoutePolylineColor,
                        width = 10f,
                    )
                }
                routePoints.firstOrNull()?.let { first ->
                    Marker(
                        state = rememberMarkerState(
                            key = "start_${first.id}",
                            position = LatLng(first.latitude, first.longitude),
                        ),
                        title = "出発地点",
                    )
                }
                if (routePoints.size > 1) {
                    routePoints.lastOrNull()?.let { last ->
                        Marker(
                            state = rememberMarkerState(
                                key = "end_${last.id}",
                                position = LatLng(last.latitude, last.longitude),
                            ),
                            title = "最終地点",
                        )
                    }
                }
            }

            // 戻るボタン
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "戻る",
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }

            // 日付セレクタ
            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp),
                elevation = CardDefaults.cardElevation(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) {
                    IconButton(
                        onClick = viewModel::selectOlderDate,
                        enabled = !isOldestDate,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前の日")
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    ) {
                        Text(
                            text = selectedDate ?: "データなし",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = if (availableDates.isEmpty()) "未記録"
                                   else "${routePoints.size}地点 / ${availableDates.size}日分",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(
                        onClick = viewModel::selectNewerDate,
                        enabled = !isNewestDate,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "次の日")
                    }
                }
            }

            // 記録ボタン
            ExtendedFloatingActionButton(
                onClick = { if (isTracking) viewModel.stopTracking() else viewModel.startTracking() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                containerColor = if (isTracking) MaterialTheme.colorScheme.errorContainer
                                 else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isTracking) MaterialTheme.colorScheme.onErrorContainer
                               else MaterialTheme.colorScheme.onPrimaryContainer,
                icon = {},
                text = { Text(if (isTracking) "■ 記録停止" else "● 記録開始") },
            )
        }
    }
}

@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("位置情報の権限が必要です", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "経路を記録・表示するには位置情報の許可が必要です。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequestPermission) {
            Text("権限を許可する")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onBack) {
            Text("戻る")
        }
    }
}

// Material Blue 700
private val RoutePolylineColor = Color(0xFF1976D2)

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED
