package com.astrbot.control.ui.screens.plugins

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
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import com.astrbot.control.ui.components.ConfirmDialog
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.InfoRow
import com.astrbot.control.ui.components.JsonView
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.SectionTitle
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.components.s
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONObject
import androidx.compose.foundation.layout.size

class PluginDetailVm(
    api: com.astrbot.control.data.ApiClient,
    private val pluginName: String,
) : BaseVm(api) {
    var detail by mutableStateOf<JSONObject?>(null)
    var readme by mutableStateOf<String?>(null)
    var confirmUninstall by mutableStateOf(false)

    fun load() {
        load {
            val r = api.get("/api/plugin/detail", mapOf("name" to pluginName))
            if (r.ok) detail = r.dataObj ?: r.dataArr?.let { JSONObject().put("list", it) }
            else error.value = r.message ?: "获取插件详情失败"

            val rr = api.getPluginReadme(pluginName)
            if (rr.ok) readme = rr.dataObj?.optString("content")?.ifBlank { null }
        }
    }

    fun toggle(enabled: Boolean) {
        run {
            val r = api.setPluginEnabled(pluginName, enabled)
            if (r.ok) {
                showToast(if (enabled) "已启用" else "已停用")
                load()
            } else error.value = r.message ?: "操作失败"
        }
    }

    fun update() {
        run {
            val r = api.updatePlugin(pluginName)
            if (r.ok) showToast(r.message ?: "更新成功") else error.value = r.message ?: "更新失败"
        }
    }

    fun reload() {
        run {
            val r = api.reloadPlugin(pluginName)
            if (r.ok) showToast(r.message ?: "重载成功") else error.value = r.message ?: "重载失败"
        }
    }

    fun uninstall(onDone: () -> Unit) {
        run {
            val r = api.uninstallPlugin(pluginName)
            if (r.ok) {
                showToast(r.message ?: "卸载成功")
                onDone()
            } else error.value = r.message ?: "卸载失败"
        }
    }
}

@Composable
fun PluginDetailScreen(navController: NavHostController, pluginName: String) {
    val api = rememberApi()
    val vm: PluginDetailVm = viewModel { PluginDetailVm(api, pluginName) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(pluginName) { vm.load() }

    ScreenScaffold(
        title = pluginName,
        onBack = { navController.popBackStack() },
        vm = vm,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            LoadingBox(loading)
            if (loading) return@ScreenScaffold

            val detail = vm.detail
            if (detail != null) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Column(Modifier.padding(vertical = 8.dp)) {
                        InfoRow("名称", detail.s("name"))
                        InfoRow("显示名", detail.s("display_name").ifBlank { "-" })
                        InfoRow("版本", detail.s("version").ifBlank { "-" })
                        InfoRow("作者", detail.s("author").ifBlank { "-" })
                        InfoRow("状态", if (detail.optBoolean("activated", false)) "已启用" else "已停用")
                        InfoRow("仓库", detail.s("repo").ifBlank { "-" })
                        InfoRow("描述", detail.s("desc").ifBlank { "-" })
                    }
                }

                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    OutlinedButton(
                        onClick = { vm.toggle(!detail.optBoolean("activated", false)) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (detail.optBoolean("activated", false)) "停用" else "启用")
                    }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(onClick = { vm.reload() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.height(16.dp))
                        Text("重载")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    OutlinedButton(onClick = { vm.update() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Download, null, Modifier.height(16.dp))
                        Text("更新")
                    }
                    Spacer(Modifier.size(8.dp))
                    OutlinedButton(
                        onClick = { navController.navigate(Routes.jsonEdit("plugin:${pluginName}")) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Settings, null, Modifier.height(16.dp))
                        Text("配置")
                    }
                }
                Button(
                    onClick = { vm.confirmUninstall = true },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
                ) {
                    Icon(Icons.Outlined.Delete, null, Modifier.height(16.dp))
                    Text("卸载插件")
                }
            }

            SectionTitle("README")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                if (vm.readme == null) {
                    EmptyHint("该插件没有 README")
                } else {
                    Text(
                        vm.readme!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            if (detail != null) {
                SectionTitle("完整信息")
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    // 页面本身已可滚动，不再嵌套滚动
                    JsonView(detail.toString(), scrollable = false)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (vm.confirmUninstall) {
        ConfirmDialog(
            title = "卸载插件",
            text = "确定要卸载插件 $pluginName 吗？",
            confirmText = "卸载",
            onConfirm = {
                vm.confirmUninstall = false
                vm.uninstall { navController.popBackStack() }
            },
            onDismiss = { vm.confirmUninstall = false },
        )
    }
}


