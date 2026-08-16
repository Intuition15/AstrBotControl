package com.astrbot.control.ui.screens.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.Routes
import com.astrbot.control.ui.components.ClickableCard
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.ErrorBox
import com.astrbot.control.ui.components.InfoRow
import com.astrbot.control.ui.components.JsonView
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.SectionTitle
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONObject

class StatusVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var version by mutableStateOf<String?>(null)
    var versionObj by mutableStateOf<JSONObject?>(null)
    var stats by mutableStateOf<JSONObject?>(null)
    var storage by mutableStateOf<JSONObject?>(null)
    var loaded by mutableStateOf(false)

    fun load() {
        load {
            val rv = api.get("/api/v1/stats/version")
            if (rv.ok) {
                versionObj = rv.dataObj
                version = rv.dataObj?.optString("version")
                    ?: rv.dataStr
                    ?: "未知版本"
            } else {
                error.value = rv.message ?: "获取版本失败"
            }
            val rs = api.get("/api/v1/stats")
            if (rs.ok) stats = rs.dataObj else error.value = rs.message ?: "获取统计失败"
            val rst = api.get("/api/v1/stats/storage")
            if (rst.ok) storage = rst.dataObj else error.value = rst.message ?: "获取存储信息失败"
            loaded = true
        }
    }

    fun restart() {
        run {
            val r = api.post("/api/v1/system/restart")
            if (r.ok) showToast("已发送重启指令，AstrBot 核心正在重启…")
            else error.value = r.message ?: "重启失败"
        }
    }

    fun cleanupStorage() {
        run {
            val r = api.post("/api/v1/stats/storage/cleanup", JSONObject().put("target", "all"))
            if (r.ok) showToast(r.message ?: "清理完成")
            else error.value = r.message ?: "清理失败"
        }
    }
}

@Composable
fun StatusScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: StatusVm = viewModel { StatusVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (!vm.loaded) vm.load() }

    ScreenScaffold(title = "状态概览", vm = vm) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            LoadingBox(loading)
            if (loading) return@ScreenScaffold

            SectionTitle("服务器")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    vm.version?.let { InfoRow("AstrBot 版本", it) }
                    val st = vm.stats
                    if (st != null) {
                        InfoRow("运行时长", formatDuration(st.optJSONObject("running")))
                        InfoRow("启动时间", formatTs(st.optLong("start_time")))
                    }
                    vm.versionObj?.let { obj ->
                        InfoRow("Dashboard 版本", obj.optString("dashboard_version").ifBlank { "-" })
                    }
                }
            }

            SectionTitle("运行统计")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    val st = vm.stats ?: return@Card
                    InfoRow("累计消息数", st.optLong("message_count").toString())
                    InfoRow("在线平台实例", st.optInt("platform_count").toString())
                    InfoRow("已加载插件", st.optInt("plugin_count").toString())
                    InfoRow("CPU 占用", "${st.optDouble("cpu_percent", 0.0)} %")
                    st.optJSONObject("memory")?.let { mem ->
                        val process = mem.optLong("process") / 1024.0
                        InfoRow("进程内存", String.format("%.1f MB", process))
                    }
                    InfoRow("线程数", st.optInt("thread_count").toString())
                }
            }

            val platforms = vm.stats?.optJSONArray("platform")
            if (platforms != null && platforms.length() > 0) {
                SectionTitle("各平台消息量")
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        for (i in 0 until platforms.length()) {
                            val p = platforms.optJSONObject(i) ?: continue
                            InfoRow(p.optString("name").ifBlank { "未知平台" }, p.optLong("count").toString())
                        }
                    }
                }
            }

            SectionTitle("存储")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                val s = vm.storage
                if (s == null) {
                    EmptyHint("暂无存储信息")
                } else {
                    // 页面本身已可滚动，不再嵌套滚动
                    JsonView(s.toString(), scrollable = false)
                }
            }

            SectionTitle("操作")
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = { vm.restart() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) {
                    Icon(Icons.Outlined.RestartAlt, null, Modifier.height(18.dp))
                    Spacer(Modifier.height(0.dp))
                    Text("重启核心")
                }
                Button(
                    onClick = { vm.cleanupStorage() },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                ) {
                    Icon(Icons.Outlined.DeleteSweep, null, Modifier.height(18.dp))
                    Text("清理存储")
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                TextButton(onClick = { navController.navigate(Routes.UPDATES) }) {
                    Icon(Icons.Outlined.SystemUpdate, null, Modifier.height(18.dp))
                    Spacer(Modifier.height(0.dp))
                    Text("检查更新")
                }
                TextButton(onClick = { navController.navigate(Routes.CHAT) }) {
                    Icon(Icons.Outlined.Build, null, Modifier.height(18.dp))
                    Text("对话测试")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun formatDuration(running: JSONObject?): String {
    if (running == null) return "-"
    val h = running.optLong("hours")
    val m = running.optLong("minutes")
    val s = running.optLong("seconds")
    return "${h}时 ${m}分 ${s}秒"
}

private fun formatTs(ts: Long): String {
    if (ts <= 0) return "-"
    return try {
        java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            .format(java.util.Date(ts * 1000))
    } catch (_: Exception) {
        ts.toString()
    }
}

