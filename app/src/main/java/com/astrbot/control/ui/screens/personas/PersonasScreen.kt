package com.astrbot.control.ui.screens.personas

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
import com.astrbot.control.ui.components.JsonView

class PersonasVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var personas by mutableStateOf<JSONArray?>(null)
    var raw by mutableStateOf<String?>(null)
    var showCreate by mutableStateOf(false)
    var newName by mutableStateOf("")
    var newPrompt by mutableStateOf("")
    var creating by mutableStateOf(false)
    var deleteTarget by mutableStateOf<JSONObject?>(null)
    var editTarget by mutableStateOf<JSONObject?>(null)
    var editPrompt by mutableStateOf("")

    fun load() {
        load {
            val r = api.get("/api/v1/personas")
            if (r.ok) {
                val arr = r.dataArr ?: r.dataObj?.optJSONArray("personas") ?: r.dataObj?.optJSONArray("data")
                if (arr != null) {
                    personas = arr
                } else {
                    raw = prettyOf(r.dataObj, r.dataArr)
                }
            } else error.value = r.message ?: "获取人设失败"
        }
    }

    fun create() {
        run {
            creating = true
            val body = JSONObject().apply {
                put("name", newName)
                if (newPrompt.isNotBlank()) put("prompt", newPrompt)
            }
            val r = api.post("/api/v1/personas", body)
            creating = false
            if (r.ok) {
                showToast(r.message ?: "创建成功")
                showCreate = false
                newName = ""
                newPrompt = ""
                load()
            } else error.value = r.message ?: "创建失败"
        }
    }

    fun saveEdit() {
        val target = editTarget ?: return
        val id = target.s("persona_id").ifBlank { target.s("id") }
        run {
            val r = api.put("/api/v1/personas/${enc(id)}", JSONObject().apply {
                put("name", target.s("name"))
                put("prompt", editPrompt)
            })
            if (r.ok) {
                showToast(r.message ?: "保存成功")
                editTarget = null
                load()
            } else error.value = r.message ?: "保存失败"
        }
    }

    fun delete() {
        val target = deleteTarget ?: return
        val id = target.s("persona_id").ifBlank { target.s("id") }
        run {
            val r = api.delete("/api/v1/personas/${enc(id)}")
            if (r.ok) {
                showToast("已删除")
                deleteTarget = null
                load()
            } else error.value = r.message ?: "删除失败"
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
fun PersonasScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: PersonasVm = viewModel { PersonasVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.personas == null && vm.raw == null) vm.load() }

    ScreenScaffold(
        title = "人设管理",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { vm.showCreate = true }) { Icon(Icons.Outlined.Add, "新建人设") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)
            val list = vm.personas
            if (list != null) {
                if (list.length() == 0) EmptyHint("暂无数据（可能是 JSON 结构，见下方原始数据）")
                else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(JSONArrayToList(list)) { _, p ->
                            val name = p.s("name")
                            val prompt = p.s("prompt")
                            if (name.isNotBlank()) {
                                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (prompt.isNotBlank()) {
                                                Text(prompt.take(120), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        Box {
                                            var menuOpen by remember { mutableStateOf(false) }
                                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "操作") }
                                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                                DropdownMenuItem(text = { Text("编辑提示词") }, onClick = { menuOpen = false; vm.editTarget = p; vm.editPrompt = prompt })
                                                DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; vm.deleteTarget = p })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (vm.raw != null) {
                    JsonView(vm.raw!!)
                } else if (!loading) {
                    EmptyHint("暂无数据")
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (vm.showCreate) {
        AlertDialog(
            onDismissRequest = { vm.showCreate = false },
            title = { Text("新建人设") },
            text = {
                Column {
                    OutlinedTextField(value = vm.newName, onValueChange = { vm.newName = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = vm.newPrompt, onValueChange = { vm.newPrompt = it }, label = { Text("提示词 (prompt)") }, modifier = Modifier.fillMaxWidth().height(120.dp))
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

    vm.editTarget?.let {
        AlertDialog(
            onDismissRequest = { vm.editTarget = null },
            title = { Text("编辑人设提示词") },
            text = {
                OutlinedTextField(value = vm.editPrompt, onValueChange = { vm.editPrompt = it }, modifier = Modifier.fillMaxWidth().height(200.dp))
            },
            confirmButton = { TextButton(onClick = { vm.saveEdit() }) { Text("保存") } },
            dismissButton = { TextButton(onClick = { vm.editTarget = null }) { Text("取消") } },
        )
    }

    vm.deleteTarget?.let { p ->
        ConfirmDialog(
            title = "删除人设",
            text = "确定删除人设「${p.s("name")}」吗？",
            onConfirm = { vm.delete() },
            onDismiss = { vm.deleteTarget = null },
        )
    }
}

private fun JSONArrayToList(arr: JSONArray): List<JSONObject> =
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }

