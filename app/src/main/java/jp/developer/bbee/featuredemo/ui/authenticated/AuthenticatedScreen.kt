package jp.developer.bbee.featuredemo.ui.authenticated

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.dropUnlessResumed
import dagger.hilt.android.lifecycle.HiltViewModel
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@HiltViewModel
class AuthenticatedViewModel @Inject constructor() : ViewModel() {

    // ViewModel は NavEntry にスコープされるため、この画面がバックスタックに
    // 積まれている間だけ認証時刻が保持される
    val authenticatedAt: String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date())
}

@Composable
fun AuthenticatedScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthenticatedViewModel = hiltViewModel(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = "認証に成功しました",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text("認証日時: ${viewModel.authenticatedAt}")
        Button(onClick = dropUnlessResumed(block = onBack)) {
            Text("ホームに戻る")
        }
    }
}
