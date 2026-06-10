package jp.developer.bbee.featuredemo.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.developer.bbee.featuredemo.navigation.DetailRoute

@HiltViewModel(assistedFactory = DetailViewModel.Factory::class)
class DetailViewModel @AssistedInject constructor(
    @Assisted val route: DetailRoute,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(route: DetailRoute): DetailViewModel
    }
}

@Composable
fun DetailScreen(
    route: DetailRoute,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    // ViewModelStoreNavEntryDecorator が NavEntry.contentKey をもとに
    // NavEntry ごとの ViewModelStoreOwner を提供するため、route インスタンスごとに
    // 固有の ViewModel が生成され、エントリーがバックスタックから除去されると破棄される
    viewModel: DetailViewModel = hiltViewModel<DetailViewModel, DetailViewModel.Factory>(
        creationCallback = { factory -> factory.create(route) }
    ),
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Detail: ${viewModel.route.id}",
            style = MaterialTheme.typography.headlineMedium,
        )
        Button(onClick = dropUnlessResumed(block = onBack)) {
            Text("Back")
        }
    }
}
