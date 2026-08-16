package com.astrbot.control.ui.screens.config

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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Route
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.Routes
import com.astrbot.control.ui.components.ConfirmDialog
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.SectionTitle
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.components.s
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONArray
import org.json.JSONObject

class ConfigVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var profiles by mutableStateOf<JSONArray?>(null)
    var showCreate by mutableStateOf(false)
    var newName by mutableStateOf("")
    var creating by mutableStateOf(false)
    var renameTarget by mutableStateOf<JSONObject?>(null)
    var renameName by mutableStateOf("")
    var deleteTarget by mutableStateOf<JSONObject?>(null)

    fun load() {
        load {
            val r = api.get("/api/v1/config-profiles")
            if (r.ok) {
                profiles = r.dataObj?.optJSONArray("info_list") ?: r.dataArr ?: JSONArray()
            } else error.value = r.message ?: "获取配置失败"
        }
    }

    fun create() {
        run {
            creating = true
            val r = api.post("/api/v1/config-profiles", JSONObject().apply {
                put("name", newName)
                put("config", JSONObject())
            })
            creating = false
            if (r.ok) {
                showToast(r.message ?: "创建成功")
                showCreate = false
                newName = ""
                load()
            } else error.value = r.message ?: "创建失败"
        }
    }

    fun rename() {
        val target = renameTarget ?: return
        val id = target.s("config_id").ifBlank { target.s("id") }
        run {
            val r = api.patch("/api/v1/config-profiles/${java.net.URLEncoder.encode(id, "UTF-8")}", JSONObject().put("name", renameName))
            if (r.ok) {
                showToast(r.message ?: "重命名成功")
                renameTarget = null
                load()
            } else error.value = r.message ?: "重命名失败"
        }
    }

    fun delete() {
        val target = deleteTarget ?: return
        val id = target.s("config_id").ifBlank { target.s("id") }
        run {
            val r = api.delete("/api/v1/config-profiles/${java.net.URLEncoder.encode(id, "UTF-8")}")
            if (r.ok) {
                showToast(r.message ?: "删除成功")
                deleteTarget = null
                load()
            } else error.value = r.message ?: "删除失败"
        }
    }
}

@Composable
fun ConfigScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: ConfigVm = viewModel { ConfigVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.profiles == null) vm.load() }

    ScreenScaffold(
        title = "配置管理",
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { vm.showCreate = true }) { Icon(Icons.Outlined.Add, "新建配置") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)

            SectionTitle("系统配置")
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Settings, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("AstrBot 系统配置", style = MaterialTheme.typography.titleSmall)
                        Text("完整的 config.json，可编辑后保存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { navController.navigate(Routes.jsonEdit("system_config")) }) { Text("编辑") }
                }
            }
            Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Route, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("配置路由 (UMO → 配置)", style = MaterialTheme.typography.titleSmall)
                        Text("将不同平台会话路由到不同配置", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { navController.navigate(Routes.jsonEdit("config_routes")) }) { Text("编辑") }
                }
            }

            SectionTitle("配置档 (Profile)")
            val list = vm.profiles
            if (list == null) {
                if (!loading) EmptyHint("暂无配置档")
            } else if (list.length() == 0) {
                EmptyHint("暂无配置档，点击右上角 + 新建")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(JSONArrayToList(list)) { _, item ->
                        val id = item.s("config_id").ifBlank { item.s("id") }
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.s("name").ifBlank { id }, style = MaterialTheme.typography.titleSmall)
                                    Text("ID: $id", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { vm.renameTarget = item; vm.renameName = item.s("name") }) {
                                    Icon(Icons.Outlined.Edit, "重命名")
                                }
                                IconButton(onClick = { vm.deleteTarget = item }) {
                                    Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                                }
                                Button(onClick = { navController.navigate(Routes.jsonEdit("profile:$id")) }) { Text("编辑") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (vm.showCreate) {
        AlertDialog(
            onDismissRequest = { vm.showCreate = false },
            title = { Text("新建配置档") },
            text = {
                OutlinedTextField(
                    value = vm.newName,
                    onValueChange = { vm.newName = it },
                    label = { Text("配置档名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(onClick = { vm.create() }, enabled = vm.newName.isNotBlank() && !vm.creating) {
                    if (vm.creating) Text("创建中…") else Text("创建")
                }
            },
            dismissButton = { TextButton(onClick = { vm.showCreate = false }) { Text("取消") } },
        )
    }

    vm.renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { vm.renameTarget = null },
            title = { Text("重命名配置档") },
            text = {
                OutlinedTextField(
                    value = vm.renameName,
                    onValueChange = { vm.renameName = it },
                    label = { Text("新名称") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.rename() }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { vm.renameTarget = null }) { Text("取消") } },
        )
    }

    vm.deleteTarget?.let { target ->
        ConfirmDialog(
            title = "删除配置档",
            text = "确定删除配置档「${target.s("name").ifBlank { target.s("config_id") }}」吗？",
            onConfirm = { vm.delete() },
            onDismiss = { vm.deleteTarget = null },
        )
    }
}

private fun JSONArrayToList(arr: JSONArray): List<JSONObject> =
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }

