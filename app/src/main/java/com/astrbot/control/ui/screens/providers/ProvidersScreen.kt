package com.astrbot.control.ui.screens.providers

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.Routes
import com.astrbot.control.ui.components.ConfirmDialog
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.components.s
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONArray
import org.json.JSONObject

class ProvidersVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var providers by mutableStateOf<JSONArray?>(null)
    var sources by mutableStateOf<JSONArray?>(null)
    var deleteTarget by mutableStateOf<JSONObject?>(null)
    var showSourceDialog by mutableStateOf(false)
    var sourceJson by mutableStateOf("{}")

    fun load() {
        load {
            val r = api.get("/api/v1/providers")
            if (r.ok) providers = r.dataArr ?: r.dataObj?.optJSONArray("providers") ?: JSONArray()
            else error.value = r.message ?: "获取提供方失败"
            val rs = api.get("/api/v1/provider-sources")
            if (rs.ok) sources = rs.dataArr ?: rs.dataObj?.optJSONArray("sources") ?: rs.dataObj?.optJSONArray("provider_sources")
        }
    }

    fun toggle(p: JSONObject, enabled: Boolean) {
        run {
            val id = p.s("id")
            val r = api.patch("/api/v1/providers/${enc(id)}/enabled", JSONObject().put("enabled", enabled))
            if (r.ok) {
                p.put("enable", enabled)
                showToast("已${if (enabled) "启用" else "停用"} ${p.s("name").ifBlank { id }}")
            } else error.value = r.message ?: "操作失败"
        }
    }

    fun delete() {
        val p = deleteTarget ?: return
        run {
            val r = api.delete("/api/v1/providers/${enc(p.s("id"))}")
            if (r.ok) {
                showToast("已删除")
                deleteTarget = null
                load()
            } else error.value = r.message ?: "删除失败"
        }
    }

    fun addSource() {
        run {
            val body = try {
                JSONObject(sourceJson)
            } catch (e: Exception) {
                error.value = "JSON 格式错误: ${e.message}"
                return@run
            }
            val r = api.post("/api/v1/provider-sources", body)
            if (r.ok) {
                showToast(r.message ?: "添加成功")
                showSourceDialog = false
                sourceJson = "{}"
                load()
            } else error.value = r.message ?: "添加失败"
        }
    }

    fun deleteSource(source: JSONObject) {
        run {
            val r = api.delete("/api/v1/provider-sources/${enc(source.s("id"))}")
            if (r.ok) {
                showToast("已删除")
                load()
            } else error.value = r.message ?: "删除失败"
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

@Composable
fun ProvidersScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: ProvidersVm = viewModel { ProvidersVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.providers == null) vm.load() }

    ScreenScaffold(
        title = "提供方管理",
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { vm.showSourceDialog = true }) { Icon(Icons.Outlined.Add, "添加提供方源") }
            IconButton(onClick = { navController.navigate(Routes.providerEdit(null)) }) { Icon(Icons.Outlined.Add, "新增提供方") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)
            val list = vm.providers
            if (list == null) {
                if (!loading) EmptyHint("暂无提供方")
            } else if (list.length() == 0) {
                EmptyHint("暂无提供方，点击右上角 + 新增")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(JSONArrayToList(list)) { _, p ->
                        ProviderItem(
                            p = p,
                            onToggle = { vm.toggle(p, it) },
                            onEdit = { navController.navigate(Routes.providerEdit(p.s("id"))) },
                            onDelete = { vm.deleteTarget = p },
                        )
                    }
                }
            }
        }
    }

    vm.deleteTarget?.let { p ->
        ConfirmDialog(
            title = "删除提供方",
            text = "确定删除提供方「${p.s("name").ifBlank { p.s("id") }}」吗？",
            onConfirm = { vm.delete() },
            onDismiss = { vm.deleteTarget = null },
        )
    }

    if (vm.showSourceDialog) {
        AlertDialog(
            onDismissRequest = { vm.showSourceDialog = false },
            title = { Text("添加提供方源 (JSON)") },
            text = {
                OutlinedTextField(
                    value = vm.sourceJson,
                    onValueChange = { vm.sourceJson = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                )
            },
            confirmButton = {
                Button(onClick = { vm.addSource() }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { vm.showSourceDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun ProviderItem(p: JSONObject, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val enabled = p.optBoolean("enable", p.optBoolean("enabled", false))
    val name = p.s("name").ifBlank { p.s("id") }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        p.s("provider_type").ifBlank { p.s("type") },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(p.s("id"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "操作") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("编辑") }, onClick = { menuOpen = false; onEdit() })
                    DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

private fun JSONArrayToList(arr: JSONArray): List<JSONObject> =
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
