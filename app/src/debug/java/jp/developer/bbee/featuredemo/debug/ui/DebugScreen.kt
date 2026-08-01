package jp.developer.bbee.featuredemo.debug.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.developer.bbee.featuredemo.debug.DebugNotificationService
import jp.developer.bbee.featuredemo.ui.icons.arrowBack
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * デバッグビルド専用の確認画面。
 * DataStore と Room データベースの中身をそのまま表示する。
 */
@Composable
fun DebugScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DebugViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // 再読み込みのタイミングで常駐通知の表示状態も取り直す
    val isNotificationRunning = remember(uiState) { DebugNotificationService.isRunning(context) }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(imageVector = arrowBack, contentDescription = "戻る")
            }
            Text(
                text = "デバッグ画面",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::refresh) {
                Text("再読み込み")
            }
        }

        if (uiState.isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            uiState.error?.let { error ->
                item(key = "error") {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            item(key = "notification") {
                NotificationSection(
                    isRunning = isNotificationRunning,
                    onRestart = { DebugNotificationService.start(context) },
                )
            }

            item(key = "datastore_header") { SectionHeader("DataStore") }
            if (uiState.dataStores.isEmpty() && !uiState.isLoading) {
                item(key = "datastore_empty") { EmptyText("登録された DataStore はありません") }
            }
            for (store in uiState.dataStores) {
                item(key = "datastore_${store.name}") { DataStoreCard(store) }
            }

            item(key = "database_header") {
                SectionHeader(
                    title = "Database",
                    subtitle = uiState.databaseName?.let { "$it (version ${uiState.databaseVersion})" },
                )
            }
            if (uiState.tables.isEmpty() && !uiState.isLoading) {
                item(key = "database_empty") { EmptyText("テーブルがありません") }
            }
            for (table in uiState.tables) {
                item(key = "table_${table.name}") { TableCard(table) }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        subtitle?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NotificationSection(isRunning: Boolean, onRestart: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "常駐通知: " + if (isRunning) "表示中" else "非表示",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "デバッグビルドのみ。通知をタップするとこの画面を開きます",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRestart) {
                Text("常駐通知を再表示")
            }
        }
    }
}

@Composable
private fun DataStoreCard(dump: DataStoreDump) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = dump.name, style = MaterialTheme.typography.titleSmall)
            when {
                dump.error != null -> Text(
                    text = dump.error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )

                dump.entries.isEmpty() -> EmptyText("(空)")

                else -> dump.entries.forEach { entry ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(text = entry.key, style = MaterialTheme.typography.labelSmall)
                    MonospaceText(entry.value)
                    // 時刻らしきキーは人間が読める形式も併記する
                    formatEpochMillisOrNull(entry.key, entry.value)?.let { formatted ->
                        Text(
                            text = formatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCard(dump: TableDump) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "${dump.name} (${dump.rowCount} 件)",
                style = MaterialTheme.typography.titleSmall,
            )
            if (dump.truncated) {
                Text(
                    text = "先頭 ${dump.rows.size} 件のみ表示",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            if (dump.rows.isEmpty()) {
                EmptyText("(空)")
            } else {
                // 列がはみ出す場合に備え、カード内をまとめて横スクロールさせて
                // ヘッダーと各行の桁を揃える
                Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    MonospaceText(
                        text = dump.columns.joinToString(" | "),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    dump.rows.forEach { row ->
                        MonospaceText(row.joinToString(" | "))
                    }
                }
            }
        }
    }
}

@Composable
private fun MonospaceText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = color,
    )
}

// "..._at" / "timestamp" のようなキーで、値がエポックミリ秒として妥当なら日時に変換する
private fun formatEpochMillisOrNull(key: String, value: String): String? {
    val looksLikeTime = key.endsWith("_at") || key.contains("time")
    if (!looksLikeTime) return null
    val millis = value.toLongOrNull()?.takeIf { it > 0L } ?: return null
    return runCatching {
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
    }.getOrNull()
}
