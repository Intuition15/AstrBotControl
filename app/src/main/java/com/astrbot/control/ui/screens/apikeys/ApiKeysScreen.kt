package com.astrbot.control.ui.screens.apikeys

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.components.ConfirmDialog
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.components.s
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONArray
import org.json.JSONObject

class ApiKeysVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var keys by mutableStateOf<JSONArray?>(null)
    var showCreate by mutableStateOf(false)
    var newName by mutableStateOf("")
    var newScopes by mutableStateOf("*")
    var newExpires by mutableStateOf("365")
    var creating by mutableStateOf(false)
    var createdKey by mutableStateOf<String?>(null)
    var revokeTarget by mutableStateOf<JSONObject?>(null)
    var deleteTarget by mutableStateOf<JSONObject?>(null)

    fun load() {
        load {
            val r = api.get("/api/v1/api-keys")
            if (r.ok) keys = r.dataArr ?: r.dataObj?.optJSONArray("keys") ?: JSONArray()
            else error.value = r.message ?: "获取 API 密钥失败"
        }
    }

    fun create() {
        run {
            creating = true
            val body = JSONObject().apply {
                put("name", newName)
                put("scopes", JSONArray().apply { newScopes.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { put(it) } })
                put("expires_in_days", newExpires.toIntOrNull() ?: 365)
            }
            val r = api.post("/api/v1/api-keys", body)
            creating = false
            if (r.ok) {
                val key = r.dataObj?.optString("api_key") ?: r.dataStr
                createdKey = key ?: "（服务器未返回密钥文本）"
                showCreate = false
                newName = ""
                load()
            } else error.value = r.message ?: "创建失败"
        }
    }

    fun revoke() {
        val k = revokeTarget ?: return
        run {
            val r = api.post("/api/v1/api-keys/${enc(k.s("key_id"))}/revoke")
            if (r.ok) {
                showToast(r.message ?: "已吊销")
                revokeTarget = null
                load()
            } else error.value = r.message ?: "吊销失败"
        }
    }

    fun delete() {
        val k = deleteTarget ?: return
        run {
            val r = api.delete("/api/v1/api-keys/${enc(k.s("key_id"))}")
            if (r.ok) {
                showToast("已删除")
                deleteTarget = null
                load()
            } else error.value = r.message ?: "删除失败"
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

@Composable
fun ApiKeysScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: ApiKeysVm = viewModel { ApiKeysVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.keys == null) vm.load() }

    ScreenScaffold(
        title = "API 密钥",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { vm.showCreate = true }) { Icon(Icons.Outlined.Add, "新建") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)
            val list = vm.keys
            if (list == null) {
                if (!loading) EmptyHint("暂无 API 密钥")
            } else if (list.length() == 0) {
                EmptyHint("暂无 API 密钥，点击右上角 + 新建")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(JSONArrayToList(list)) { _, k ->
                        KeyItem(
                            k = k,
                            onRevoke = { vm.revokeTarget = k },
                            onDelete = { vm.deleteTarget = k },
                        )
                    }
                }
            }
        }
    }

    if (vm.showCreate) {
        AlertDialog(
            onDismissRequest = { vm.showCreate = false },
            title = { Text("新建 API 密钥") },
            text = {
                Column {
                    OutlinedTextField(value = vm.newName, onValueChange = { vm.newName = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = vm.newScopes, onValueChange = { vm.newScopes = it }, label = { Text("权限范围（逗号分隔，* 为全部）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = vm.newExpires, onValueChange = { vm.newExpires = it }, label = { Text("有效期（天）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { vm.create() }, enabled = !vm.creating) {
                    if (vm.creating) Text("创建中…") else Text("创建")
                }
            },
            dismissButton = { TextButton(onClick = { vm.showCreate = false }) { Text("取消") } },
        )
    }

    vm.createdKey?.let { key ->
        AlertDialog(
            onDismissRequest = { vm.createdKey = null },
            title = { Text("密钥已创建") },
            text = {
                Column {
                    Text("请立即复制保存，此密钥只显示一次：")
                    Text(key, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = { TextButton(onClick = { vm.createdKey = null }) { Text("知道了") } },
        )
    }

    vm.revokeTarget?.let { k ->
        ConfirmDialog(
            title = "吊销密钥",
            text = "确定吊销密钥「${k.s("name")}」吗？",
            confirmText = "吊销",
            onConfirm = { vm.revoke() },
            onDismiss = { vm.revokeTarget = null },
        )
    }

    vm.deleteTarget?.let { k ->
        ConfirmDialog(
            title = "删除密钥",
            text = "确定删除密钥「${k.s("name")}」吗？",
            onConfirm = { vm.delete() },
            onDismiss = { vm.deleteTarget = null },
        )
    }
}

@Composable
private fun KeyItem(k: JSONObject, onRevoke: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(k.s("name").ifBlank { k.s("key_id") }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("ID: ${k.s("key_id")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val scopes = k.optJSONArray("scopes")
                if (scopes != null && scopes.length() > 0) {
                    val scopesStr = (0 until scopes.length()).joinToString(", ") { scopes.optString(it) }
                    Text("范围: $scopesStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val lastUsed = k.optString("last_used_at")
                if (lastUsed.isNotBlank()) {
                    Text("最近使用: $lastUsed", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "操作") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("吊销") }, onClick = { menuOpen = false; onRevoke() })
                    DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

private fun JSONArrayToList(arr: JSONArray): List<JSONObject> =
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
