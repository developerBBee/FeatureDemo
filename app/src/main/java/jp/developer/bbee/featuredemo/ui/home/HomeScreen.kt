package jp.developer.bbee.featuredemo.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
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
class HomeViewModel @Inject constructor() : ViewModel() {

    private val _items = MutableStateFlow((1..20).map { "Item $it" })
    val items: StateFlow<List<String>> = _items.asStateFlow()
}

@Composable
fun HomeScreen(
    onItemClick: (String) -> Unit,
    onBiometricDemoClick: () -> Unit,
    onIntentLauncherDemoClick: () -> Unit,
    onBarcodeScannerDemoClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxSize()) {
        Button(
            onClick = dropUnlessResumed(block = onBiometricDemoClick),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp),
        ) {
            Text("生体認証デモ")
        }
        Button(
            onClick = dropUnlessResumed(block = onIntentLauncherDemoClick),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp),
        ) {
            Text("外部アプリ起動デモ")
        }
        Button(
            onClick = dropUnlessResumed(block = onBarcodeScannerDemoClick),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Text("バーコード読み取りデモ")
        }
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items) { item ->
                ListItem(
                    headlineContent = { Text(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = dropUnlessResumed { onItemClick(item) }),
                )
            }
        }
    }
}
