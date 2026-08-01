package jp.developer.bbee.featuredemo.debug.ui

import android.database.Cursor
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.developer.bbee.featuredemo.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** DataStore 1 ファイル分のダンプ */
data class DataStoreDump(
    val name: String,
    val entries: List<PreferenceEntry>,
    val error: String? = null,
)

data class PreferenceEntry(val key: String, val value: String)

/** DB のテーブル 1 つ分のダンプ */
data class TableDump(
    val name: String,
    val rowCount: Int,
    val columns: List<String>,
    val rows: List<List<String>>,
    val truncated: Boolean,
)

data class DebugUiState(
    val isLoading: Boolean = true,
    val dataStores: List<DataStoreDump> = emptyList(),
    val databaseName: String? = null,
    val databaseVersion: Int = 0,
    val tables: List<TableDump> = emptyList(),
    val error: String? = null,
)

/**
 * デバッグ画面のデータ収集。
 *
 * - DataStore は Hilt のマルチバインディングで登録された全 Preferences DataStore を読む。
 *   (同一ファイルに対して DataStore インスタンスを作り直すと実行時例外になるため、
 *    ファイルを直接開かず、アプリが保持しているインスタンスを注入して読む)
 * - Database は sqlite_master からテーブル一覧を取り、各テーブルを汎用的にダンプする。
 */
@HiltViewModel
class DebugViewModel @Inject constructor(
    private val database: AppDatabase,
    private val preferenceDataStores: Map<String, @JvmSuppressWildcards DataStore<Preferences>>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DebugUiState())
    val uiState: StateFlow<DebugUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val dataStores = readDataStores()
            val state = withContext(Dispatchers.IO) {
                runCatching { readDatabase() }.fold(
                    onSuccess = { it },
                    onFailure = { e -> DebugUiState(error = e.toString()) },
                )
            }
            _uiState.value = state.copy(isLoading = false, dataStores = dataStores)
        }
    }

    private suspend fun readDataStores(): List<DataStoreDump> =
        preferenceDataStores.entries
            .sortedBy { it.key }
            .map { (name, store) ->
                runCatching {
                    val entries = store.data.first().asMap()
                        .map { (key, value) -> PreferenceEntry(key.name, formatValue(value)) }
                        .sortedBy { it.key }
                    DataStoreDump(name = name, entries = entries)
                }.getOrElse { e ->
                    DataStoreDump(name = name, entries = emptyList(), error = e.toString())
                }
            }

    private fun formatValue(value: Any?): String = when (value) {
        null -> "null"
        is Set<*> -> value.joinToString(prefix = "[", postfix = "]")
        else -> value.toString()
    }

    private fun readDatabase(): DebugUiState {
        val db = database.openHelper.readableDatabase
        val tables = tableNames(db).map { table -> dumpTable(db, table) }
        return DebugUiState(
            databaseName = database.openHelper.databaseName,
            databaseVersion = db.version,
            tables = tables,
        )
    }

    private fun tableNames(db: SupportSQLiteDatabase): List<String> =
        db.query(
            """
            SELECT name FROM sqlite_master
            WHERE type = 'table'
              AND name NOT LIKE 'sqlite_%'
              AND name NOT LIKE 'android_metadata'
              AND name NOT LIKE 'room_master_table'
            ORDER BY name
            """.trimIndent()
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.getString(0))
                }
            }
        }

    private fun dumpTable(db: SupportSQLiteDatabase, table: String): TableDump {
        // table 名は sqlite_master から取得した実在のテーブル名のみ
        val quoted = "`" + table.replace("`", "``") + "`"
        val rowCount = db.query("SELECT COUNT(*) FROM $quoted").use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
        return db.query("SELECT * FROM $quoted LIMIT $MAX_ROWS").use { cursor ->
            val columns = cursor.columnNames.toList()
            val rows = buildList {
                while (cursor.moveToNext()) {
                    add(columns.indices.map { index -> cellValue(cursor, index) })
                }
            }
            TableDump(
                name = table,
                rowCount = rowCount,
                columns = columns,
                rows = rows,
                truncated = rowCount > rows.size,
            )
        }
    }

    private fun cellValue(cursor: Cursor, index: Int): String = when (cursor.getType(index)) {
        Cursor.FIELD_TYPE_NULL -> "null"
        Cursor.FIELD_TYPE_BLOB -> "<blob ${cursor.getBlob(index).size} bytes>"
        else -> cursor.getString(index) ?: "null"
    }

    private companion object {
        // 全件読むとメモリと描画コストが大きいため、テーブルごとに先頭のみ表示する
        const val MAX_ROWS = 100
    }
}
