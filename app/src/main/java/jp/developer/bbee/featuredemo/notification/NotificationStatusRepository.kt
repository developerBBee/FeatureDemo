package jp.developer.bbee.featuredemo.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知のアクション(選択肢)で更新されるステータス。
 * [NotificationActionReceiver](BroadcastReceiver)から書き込み、
 * 画面(ViewModel)から読み取るため、両者が同じインスタンスを共有できるよう
 * Hilt の `@Singleton` で保持する。
 */
enum class TaskStatus(val label: String) {
    PENDING("未対応"),
    APPROVED("承認済み"),
    REJECTED("却下"),
    ;

    companion object {
        fun fromName(name: String?): TaskStatus =
            entries.firstOrNull { it.name == name } ?: PENDING
    }
}

@Singleton
class NotificationStatusRepository @Inject constructor() {

    private val _status = MutableStateFlow(TaskStatus.PENDING)
    val status: StateFlow<TaskStatus> = _status.asStateFlow()

    fun update(status: TaskStatus) {
        _status.value = status
    }

    fun reset() {
        _status.value = TaskStatus.PENDING
    }
}
