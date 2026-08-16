package com.astrbot.control.ui.screens.logs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Call
import org.json.JSONArray
import org.json.JSONObject
import androidx.lifecycle.viewModelScope

data class LogEntry(val time: String, val level: String, val message: String, val logger: String)

class LogsVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var entries by mutableStateOf<List<LogEntry>>(emptyList())
    var filter by mutableStateOf("全部")
    var live by mutableStateOf(false)
    var autoScroll by mutableStateOf(true)
    var started by mutableStateOf(false)

    private var call: Call? = null
    private var reconnectJob: Job? = null
    private var running = false
    private var lastEventId: String? = null

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            val r = api.get("/api/v1/logs/history")
            if (r.ok) {
                val arr = r.dataObj?.optJSONArray("logs") ?: JSONArray()
                entries = (0 until arr.length()).mapNotNull { parseEntry(arr.optJSONObject(it)) }
            } else {
                error.value = r.message ?: "获取日志历史失败"
            }
            beginStream()
        }
    }

    fun beginStream() {
        if (running) return
        running = true
        live = true
        val headers = lastEventId?.let { mapOf("Last-Event-ID" to it) } ?: emptyMap()
        call = api.streamSse(
            path = "/api/v1/logs/live",
            extraHeaders = headers,
            onEvent = { id, data ->
                lastEventId = id
                try {
                    parseEntry(JSONObject(data))?.let { entry ->
                        // 后台线程回调 → 切回主线程更新状态，避免快照竞争
                        viewModelScope.launch {
                            entries = (entries + entry).takeLast(1000)
                        }
                    }
                } catch (_: Exception) {
                }
            },
            onError = { msg -> viewModelScope.launch { toast.value = "日志流中断: $msg" } },
            onClosed = {
                viewModelScope.launch { live = false }
                if (running) {
                    reconnectJob = viewModelScope.launch {
                        delay(2000)
                        beginStream()
                    }
                }
            },
        )
    }

    fun stop() {
        running = false
        live = false
        call?.cancel()
        reconnectJob?.cancel()
    }

    fun clear() {
        entries = emptyList()
        lastEventId = null
    }

    override fun onCleared() {
        stop()
        super.onCleared()
    }

    /**
     * AstrBot 日志记录结构：
     * {"level": "INFO", "time": 1786..., "data": "[2026-08-16 17:00:00.123] [Core] [INFO] [file:123]: 内容", "category": "system"}
     * 真正的日志文本在 data 字段（含 ANSI 颜色码，需剥离）；旧版本兼容读取 message。
     */
    private fun parseEntry(obj: JSONObject?): LogEntry? {
        if (obj == null) return null
        val level = obj.optString("level", "INFO")
        val data = obj.optString("data").ifBlank { obj.optString("message") }
        return LogEntry(
            time = obj.optString("time", ""),
            level = level,
            message = stripAnsi(data),
            logger = obj.optString("category", "").ifBlank { obj.optString("logger") },
        )
    }

    companion object {
        private val ANSI_REGEX = Regex("\u001B\\[[0-9;]*m")

        /** 去除 AstrBot 日志里的 ANSI 颜色控制码 */
        fun stripAnsi(s: String): String = ANSI_REGEX.replace(s, "")
    }
}

private val levels = listOf("全部", "INFO", "WARNING", "ERROR", "DEBUG")

@Composable
fun LogsScreen() {
    val api = rememberApi()
    val vm: LogsVm = viewModel { LogsVm(api) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(vm.entries.size) {
        if (vm.autoScroll && vm.entries.isNotEmpty()) {
            listState.scrollToItem(vm.entries.size - 1)
        }
    }

    ScreenScaffold(
        title = "运行日志",
        vm = vm,
        actions = {
            IconButton(onClick = { if (vm.live) vm.stop() else vm.beginStream() }) {
                Icon(if (vm.live) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, if (vm.live) "停止实时日志" else "开始实时日志")
            }
            IconButton(onClick = { vm.clear() }) { Icon(Icons.Outlined.DeleteSweep, "清空显示") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                levels.forEach { lv ->
                    FilterChip(
                        selected = vm.filter == lv,
                        onClick = { vm.filter = lv },
                        label = { Text(lv) },
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { vm.autoScroll = !vm.autoScroll }) {
                    Text(if (vm.autoScroll) "自动滚动:开" else "自动滚动:关")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (vm.live) {
                    CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp)
                    Spacer(Modifier.size(6.dp))
                    Text("实时接收中…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("已暂停", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (vm.entries.isEmpty()) {
                EmptyHint("暂无日志")
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(vm.entries) { _, entry ->
                        if (vm.filter == "全部" || entry.level == vm.filter) {
                            LogRow(entry)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val color = when (entry.level) {
        "ERROR", "CRITICAL" -> MaterialTheme.colorScheme.error
        "WARNING", "WARN" -> Color(0xFFB26A00)
        "DEBUG" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            "[${entry.level}]",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.size(6.dp))
        // 完整日志行（data 字段已包含时间/标签/级别/来源/内容）
        Text(
            entry.message.ifBlank { entry.time },
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
    }
}

