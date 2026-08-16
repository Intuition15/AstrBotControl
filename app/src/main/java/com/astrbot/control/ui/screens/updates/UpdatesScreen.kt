package com.astrbot.control.ui.screens.updates

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.astrbot.control.ui.components.InfoRow
import com.astrbot.control.ui.components.JsonView
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONObject

class UpdatesVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var checkResult by mutableStateOf<JSONObject?>(null)
    var releases by mutableStateOf<String?>(null)
    var showPipDialog by mutableStateOf(false)
    var pipPackage by mutableStateOf("")
    var updating by mutableStateOf(false)

    fun check() {
        load {
            val r = api.get("/api/v1/updates/check")
            if (r.ok) checkResult = r.dataObj ?: r.dataArr?.let { JSONObject().put("data", it) }
            else error.value = r.message ?: "检查更新失败"
            val rr = api.get("/api/v1/updates/releases")
            if (rr.ok) releases = (rr.dataArr ?: rr.dataObj).toString()
        }
    }

    fun updateCore() {
        val latest = latestVersion()
        run {
            updating = true
            val body = JSONObject().apply {
                if (latest != null) put("version", latest)
                put("reboot", true)
            }
            val r = api.post("/api/v1/updates/core", body)
            updating = false
            if (r.ok) showToast(r.message ?: "更新指令已发送")
            else error.value = r.message ?: "更新失败"
        }
    }

    fun updateDashboard() {
        run {
            val r = api.post("/api/v1/updates/dashboard")
            if (r.ok) showToast(r.message ?: "Dashboard 更新指令已发送")
            else error.value = r.message ?: "更新失败"
        }
    }

    fun pipInstall() {
        run {
            val r = api.post("/api/v1/pip/install", JSONObject().put("package", pipPackage.trim()))
            if (r.ok) {
                showToast(r.message ?: "pip 安装完成")
                showPipDialog = false
                pipPackage = ""
            } else error.value = r.message ?: "pip 安装失败"
        }
    }

    private fun latestVersion(): String? {
        val rel = releases ?: return null
        return try {
            val arr = org.json.JSONArray(rel)
            arr.optJSONObject(0)?.optString("tag_name")?.ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun UpdatesScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: UpdatesVm = viewModel { UpdatesVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.checkResult == null) vm.check() }

    ScreenScaffold(
        title = "系统更新",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            IconButton(onClick = { vm.check() }) { Icon(Icons.Outlined.Refresh, "重新检查") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            LoadingBox(loading)
            val result = vm.checkResult
            if (result != null) {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        InfoRow("当前版本", result.optString("version").ifBlank { "-" })
                        InfoRow("有新版本", if (result.optBoolean("has_new_version", false)) "是" else "否")
                        InfoRow("Dashboard 版本", result.optString("dashboard_version").ifBlank { "-" })
                        InfoRow("Dashboard 有新版本", if (result.optBoolean("dashboard_has_new_version", false)) "是" else "否")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Button(onClick = { vm.updateCore() }, enabled = !vm.updating, modifier = Modifier.weight(1f)) {
                        if (vm.updating) Text("更新中…") else Text("更新核心")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.updateDashboard() }, modifier = Modifier.weight(1f)) { Text("更新 Dashboard") }
                }
                TextButton(onClick = { vm.showPipDialog = true }) { Text("pip 安装 Python 包") }
            }
            vm.releases?.let {
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("版本发布记录", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                    // 页面本身已可滚动，不再嵌套滚动
                    JsonView(it, scrollable = false)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (vm.showPipDialog) {
        AlertDialog(
            onDismissRequest = { vm.showPipDialog = false },
            title = { Text("pip 安装包") },
            text = {
                OutlinedTextField(
                    value = vm.pipPackage,
                    onValueChange = { vm.pipPackage = it },
                    label = { Text("包名，如 aiohttp==3.9.0") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.pipInstall() }, enabled = vm.pipPackage.isNotBlank()) { Text("安装") }
            },
            dismissButton = { TextButton(onClick = { vm.showPipDialog = false }) { Text("取消") } },
        )
    }
}

