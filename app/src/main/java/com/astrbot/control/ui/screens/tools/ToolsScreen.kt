package com.astrbot.control.ui.screens.tools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.JsonView
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.components.s
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONArray
import org.json.JSONObject

class ToolsVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var tools by mutableStateOf<JSONArray?>(null)
    var toolsRaw by mutableStateOf<String?>(null)
    var mcp by mutableStateOf<String?>(null)
    var showMcpDialog by mutableStateOf(false)
    var mcpJson by mutableStateOf("{}")

    fun load() {
        load {
            val r = api.get("/api/v1/tools")
            if (r.ok) {
                val arr = r.dataArr ?: r.dataObj?.optJSONArray("tools")
                if (arr != null) {
                    tools = arr
                } else {
                    toolsRaw = prettyOf(r.dataObj, r.dataArr)
                }
            } else error.value = r.message ?: "获取工具失败"
            val rm = api.get("/api/v1/mcp/servers")
            if (rm.ok) mcp = prettyOf(rm.dataObj, rm.dataArr)
        }
    }

    fun toggleTool(tool: JSONObject, enabled: Boolean) {
        run {
            val id = tool.s("tool_id").ifBlank { tool.s("name") }
            val r = api.patch("/api/v1/tools/${enc(id)}/enabled", JSONObject().put("enabled", enabled))
            if (r.ok) {
                tool.put("enabled", enabled)
                showToast("已${if (enabled) "启用" else "停用"} $id")
            } else error.value = r.message ?: "操作失败"
        }
    }

    fun addMcp() {
        run {
            val body = try {
                JSONObject(mcpJson)
            } catch (e: Exception) {
                error.value = "JSON 格式错误: ${e.message}"
                return@run
            }
            val r = api.post("/api/v1/mcp/servers", body)
            if (r.ok) {
                showToast(r.message ?: "添加成功")
                showMcpDialog = false
                mcpJson = "{}"
                load()
            } else error.value = r.message ?: "添加失败"
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    private fun prettyOf(obj: JSONObject?, arr: JSONArray?): String = when {
        obj != null -> obj.toString(4)
        arr != null -> arr.toString(4)
        else -> "{}"
    }
}

@Composable
fun ToolsScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: ToolsVm = viewModel { ToolsVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.tools == null && vm.toolsRaw == null) vm.load() }

    ScreenScaffold(
        title = "工具与 MCP",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { vm.showMcpDialog = true }) { Icon(Icons.Outlined.Add, "添加 MCP 服务器") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)
            val list = vm.tools
            if (list != null) {
                if (list.length() == 0) EmptyHint("暂无工具")
                else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(JSONArrayToList(list)) { _, tool ->
                            val id = tool.s("tool_id").ifBlank { tool.s("name") }
                            if (id.isNotBlank()) {
                                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(id, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val desc = tool.s("desc").ifBlank { tool.s("description") }
                                            if (desc.isNotBlank()) {
                                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        Switch(
                                            checked = tool.optBoolean("enabled", tool.optBoolean("enable", true)),
                                            onCheckedChange = { vm.toggleTool(tool, it) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (vm.toolsRaw != null) {
                    JsonView(vm.toolsRaw!!)
                } else if (!loading) {
                    EmptyHint("暂无工具数据")
                }
            }
            vm.mcp?.let { mcp ->
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("MCP 服务器", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                    JsonView(mcp)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (vm.showMcpDialog) {
        AlertDialog(
            onDismissRequest = { vm.showMcpDialog = false },
            title = { Text("添加 MCP 服务器 (JSON)") },
            text = {
                OutlinedTextField(
                    value = vm.mcpJson,
                    onValueChange = { vm.mcpJson = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                )
            },
            confirmButton = { Button(onClick = { vm.addMcp() }) { Text("添加") } },
            dismissButton = { TextButton(onClick = { vm.showMcpDialog = false }) { Text("取消") } },
        )
    }
}

private fun JSONArrayToList(arr: JSONArray): List<JSONObject> =
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }

