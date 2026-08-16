package com.astrbot.control.ui.screens.plugins

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.astrbot.control.ui.components.ConfirmDialog
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.components.s
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONArray
import org.json.JSONObject

class MarketVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var market by mutableStateOf<JSONArray?>(null)
    var installingName by mutableStateOf<String?>(null)

    fun load(forceRefresh: Boolean = false) {
        load {
            val r = api.getPluginMarket(forceRefresh)
            if (r.ok) {
                // 市场数据可能是数组，也可能是 {"$meta":..., "作者/仓库": {...}, ...} 的字典
                market = r.dataArr ?: marketObjectToArray(r.dataObj)
            } else error.value = r.message ?: "获取插件市场失败"
        }
    }

    /** 把市场字典（键为 "作者/仓库名"）转换为插件数组 */
    private fun marketObjectToArray(obj: JSONObject?): JSONArray {
        val arr = JSONArray()
        if (obj == null) return arr
        obj.keys().forEach { key ->
            if (key == "\$meta") return@forEach
            val entry = obj.optJSONObject(key) ?: return@forEach
            // 字典键形如 "author/repo"，若条目缺少 name 则从键补全
            if (entry.optString("name").isBlank()) {
                entry.put("name", key.substringAfter('/'))
            }
            arr.put(entry)
        }
        return arr
    }

    fun install(entry: JSONObject) {
        run {
            installingName = entry.s("name")
            val body = JSONObject()
            val repo = entry.optString("repo").ifBlank { entry.optString("url") }
            val downloadUrl = entry.optString("download_url").ifBlank { entry.optString("downloadUrl") }
            if (repo.isNotBlank()) body.put("url", repo)
            if (downloadUrl.isNotBlank()) body.put("download_url", downloadUrl)
            body.put("ignore_version_check", true)
            val r = api.installMarketPlugin(body)
            installingName = null
            if (r.ok) {
                showToast(r.message ?: "安装成功：${entry.s("name")}")
            } else {
                error.value = r.message ?: "安装失败"
            }
        }
    }
}

@Composable
fun PluginMarketScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: MarketVm = viewModel { MarketVm(api) }
    val loading by vm.loading.collectAsState()
    var query by remember { mutableStateOf("") }
    var confirmEntry by remember { mutableStateOf<JSONObject?>(null) }

    LaunchedEffect(Unit) { if (vm.market == null) vm.load() }

    ScreenScaffold(
        title = "插件市场",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load(forceRefresh = true) }) { Icon(Icons.Outlined.Refresh, "强制刷新") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("搜索插件") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
            )
            LoadingBox(loading)
            val list = vm.market
            if (list == null) {
                if (!loading) EmptyHint("市场数据为空")
            } else {
                val filtered = remember(query, list.toString()) {
                    val q = query.trim().lowercase()
                    if (q.isEmpty()) list
                    else {
                        val out = JSONArray()
                        for (i in 0 until list.length()) {
                            val item = list.optJSONObject(i) ?: continue
                            val hay = "${item.s("name")} ${item.s("desc")} ${item.s("author")}".lowercase()
                            if (hay.contains(q)) out.put(item)
                        }
                        out
                    }
                }
                if (filtered.length() == 0) {
                    if (list.length() == 0) {
                        EmptyHint("插件市场为空，点击右上角刷新重试")
                    } else {
                        EmptyHint("没有匹配的插件")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(JSONArrayToList(filtered)) { _, item ->
                            MarketItem(
                                entry = item,
                                installing = vm.installingName == item.s("name"),
                                onInstall = { confirmEntry = item },
                            )
                        }
                    }
                }
            }
        }
    }

    confirmEntry?.let { entry ->
        ConfirmDialog(
            title = "安装插件",
            text = "确定从插件市场安装「${entry.s("name")}」吗？\n${entry.s("desc")}",
            confirmText = "安装",
            danger = false,
            onConfirm = {
                confirmEntry = null
                vm.install(entry)
            },
            onDismiss = { confirmEntry = null },
        )
    }
}

private fun JSONArrayToList(arr: JSONArray): List<JSONObject> {
    return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
}

@Composable
private fun MarketItem(
    entry: JSONObject,
    installing: Boolean,
    onInstall: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val title = entry.s("display_name").ifBlank { entry.s("name") }
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.size(8.dp))
                    Text("v${entry.s("version")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(entry.s("name"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val desc = entry.s("desc")
                if (desc.isNotBlank()) {
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "作者: ${entry.s("author").ifBlank { "-" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Button(onClick = onInstall, enabled = !installing) {
                if (installing) Text("安装中…") else Text("安装")
            }
        }
    }
}
