package com.astrbot.control.ui.screens.platforms

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
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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

class PlatformsVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var bots by mutableStateOf<JSONArray?>(null)
    var deleteTarget by mutableStateOf<JSONObject?>(null)

    fun load() {
        load {
            val r = api.get("/api/v1/bots")
            if (r.ok) {
                bots = r.dataObj?.optJSONArray("bots") ?: r.dataArr ?: JSONArray()
            } else error.value = r.message ?: "获取平台列表失败"
        }
    }

    fun toggle(bot: JSONObject, enabled: Boolean) {
        run {
            val id = bot.s("id")
            val r = api.patch("/api/v1/bots/${enc(id)}/enabled", JSONObject().put("enabled", enabled))
            if (r.ok) {
                bot.put("enable", enabled)
                showToast("已${if (enabled) "启用" else "停用"} $id")
            } else error.value = r.message ?: "操作失败"
        }
    }

    fun delete() {
        val bot = deleteTarget ?: return
        run {
            val r = api.delete("/api/v1/bots/${enc(bot.s("id"))}")
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
fun PlatformsScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: PlatformsVm = viewModel { PlatformsVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.bots == null) vm.load() }

    ScreenScaffold(
        title = "平台管理",
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { navController.navigate(Routes.platformEdit(null)) }) { Icon(Icons.Outlined.Add, "新增平台") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)
            val list = vm.bots
            if (list == null) {
                if (!loading) EmptyHint("暂无平台配置")
            } else if (list.length() == 0) {
                EmptyHint("暂无平台配置，点击右上角 + 新增")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(JSONArrayToList(list)) { _, bot ->
                        BotItem(
                            bot = bot,
                            onToggle = { vm.toggle(bot, it) },
                            onEdit = { navController.navigate(Routes.platformEdit(bot.s("id"))) },
                            onDelete = { vm.deleteTarget = bot },
                        )
                    }
                }
            }
        }
    }

    vm.deleteTarget?.let { bot ->
        ConfirmDialog(
            title = "删除平台",
            text = "确定删除平台「${bot.s("id")}」吗？",
            onConfirm = { vm.delete() },
            onDismiss = { vm.deleteTarget = null },
        )
    }
}

@Composable
private fun BotItem(bot: JSONObject, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val enabled = bot.optBoolean("enable", bot.optBoolean("enabled", false))
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        bot.s("name").ifBlank { bot.s("id") },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(bot.s("type"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(
                    "ID: ${bot.s("id")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
