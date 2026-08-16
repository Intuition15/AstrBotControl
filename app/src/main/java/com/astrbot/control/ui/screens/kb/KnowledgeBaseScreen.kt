package com.astrbot.control.ui.screens.kb

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
import com.astrbot.control.ui.components.JsonView
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.components.s
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONArray
import org.json.JSONObject

class KbVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var kbs by mutableStateOf<JSONArray?>(null)
    var raw by mutableStateOf<String?>(null)
    var docsJson by mutableStateOf<String?>(null)
    var showCreate by mutableStateOf(false)
    var newName by mutableStateOf("")
    var newDesc by mutableStateOf("")
    var newEmbedding by mutableStateOf("")
    var creating by mutableStateOf(false)
    var deleteTarget by mutableStateOf<JSONObject?>(null)

    fun load() {
        load {
            val r = api.get("/api/v1/knowledge-bases")
            if (r.ok) {
                val arr = r.dataArr ?: r.dataObj?.optJSONArray("kbs") ?: r.dataObj?.optJSONArray("data")
                if (arr != null) {
                    kbs = arr
                } else {
                    raw = prettyOf(r.dataObj, r.dataArr)
                }
            } else error.value = r.message ?: "获取知识库失败"
        }
    }

    fun loadDocs(kb: JSONObject) {
        run {
            val id = kb.s("kb_id").ifBlank { kb.s("id") }
            val r = api.get("/api/v1/knowledge-bases/${enc(id)}/documents")
            if (r.ok) {
                docsJson = prettyOf(r.dataObj, r.dataArr)
            } else {
                error.value = r.message ?: "获取文档失败"
            }
        }
    }

    fun create() {
        run {
            creating = true
            val body = JSONObject().apply {
                put("name", newName)
                if (newDesc.isNotBlank()) put("description", newDesc)
                if (newEmbedding.isNotBlank()) put("embedding_provider_id", newEmbedding)
            }
            val r = api.post("/api/v1/knowledge-bases", body)
            creating = false
            if (r.ok) {
                showToast(r.message ?: "创建成功")
                showCreate = false
                newName = ""
                newDesc = ""
                newEmbedding = ""
                load()
            } else error.value = r.message ?: "创建失败"
        }
    }

    fun delete() {
        val kb = deleteTarget ?: return
        val id = kb.s("kb_id").ifBlank { kb.s("id") }
        run {
            val r = api.delete("/api/v1/knowledge-bases/${enc(id)}")
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
fun KnowledgeBaseScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: KbVm = viewModel { KbVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.kbs == null && vm.raw == null) vm.load() }

    ScreenScaffold(
        title = "知识库",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { vm.showCreate = true }) { Icon(Icons.Outlined.Add, "新建知识库") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)
            val list = vm.kbs
            if (list != null) {
                if (list.length() == 0) EmptyHint("暂无知识库")
                else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(JSONArrayToList(list)) { _, kb ->
                            val name = kb.s("name")
                            if (name.isNotBlank()) {
                                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val desc = kb.s("description")
                                            if (desc.isNotBlank()) {
                                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        Box {
                                            var menuOpen by remember { mutableStateOf(false) }
                                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "操作") }
                                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                                DropdownMenuItem(text = { Text("查看文档") }, onClick = { menuOpen = false; vm.loadDocs(kb) })
                                                DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; vm.deleteTarget = kb })
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
            vm.docsJson?.let { docs ->
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("文档列表", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                    JsonView(docs)
                    TextButton(onClick = { vm.docsJson = null }, modifier = Modifier.padding(8.dp)) { Text("关闭") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (vm.showCreate) {
        AlertDialog(
            onDismissRequest = { vm.showCreate = false },
            title = { Text("新建知识库") },
            text = {
                Column {
                    OutlinedTextField(value = vm.newName, onValueChange = { vm.newName = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = vm.newDesc, onValueChange = { vm.newDesc = it }, label = { Text("描述") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = vm.newEmbedding, onValueChange = { vm.newEmbedding = it }, label = { Text("Embedding 提供方 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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

    vm.deleteTarget?.let { kb ->
        ConfirmDialog(
            title = "删除知识库",
            text = "确定删除知识库「${kb.s("name")}」吗？",
            onConfirm = { vm.delete() },
            onDismiss = { vm.deleteTarget = null },
        )
    }
}

private fun JSONArrayToList(arr: JSONArray): List<JSONObject> =
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
