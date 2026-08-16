package com.astrbot.control.ui.screens.plugins

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
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
import com.astrbot.control.ui.components.b
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import androidx.compose.foundation.layout.Box

class PluginsVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var plugins by mutableStateOf<JSONArray?>(null)
    var failedPlugins by mutableStateOf<JSONObject?>(null)
    var showInstallDialog by mutableStateOf(false)
    var installUrl by mutableStateOf("")
    var ignoreVersion by mutableStateOf(false)
    var installing by mutableStateOf(false)
    var showFailedDialog by mutableStateOf(false)
    var uploading by mutableStateOf(false)

    fun load() {
        load {
            val r = api.getPlugins()
            if (r.ok) plugins = r.dataArr ?: r.dataObj?.optJSONArray("plugins") ?: JSONArray()
            else error.value = r.message ?: "获取插件列表失败"
        }
    }

    fun toggle(plugin: JSONObject) {
        run {
            val name = plugin.s("name")
            val target = !plugin.optBoolean("activated", false)
            val r = api.setPluginEnabled(name, target)
            if (r.ok) {
                plugin.put("activated", target)
                showToast(if (target) "已启用 $name" else "已停用 $name")
            } else error.value = r.message ?: "操作失败"
        }
    }

    fun update(name: String) {
        run {
            val r = api.updatePlugin(name)
            if (r.ok) showToast(r.message ?: "更新成功") else error.value = r.message ?: "更新失败"
        }
    }

    fun reload(name: String) {
        run {
            val r = api.reloadPlugin(name)
            if (r.ok) showToast(r.message ?: "重载成功") else error.value = r.message ?: "重载失败"
        }
    }

    fun uninstall(name: String) {
        run {
            val r = api.uninstallPlugin(name)
            if (r.ok) {
                showToast(r.message ?: "卸载成功")
                load()
            } else error.value = r.message ?: "卸载失败"
        }
    }

    fun install() {
        run {
            installing = true
            val r = api.installPluginByUrl(installUrl.trim(), ignoreVersion)
            installing = false
            if (r.ok) {
                showToast(r.message ?: "安装成功")
                showInstallDialog = false
                installUrl = ""
                load()
            } else {
                error.value = r.message ?: "安装失败"
            }
        }
    }

    fun uploadFile(file: File) {
        run {
            uploading = true
            val r = api.installPluginByFile(file)
            uploading = false
            if (r.ok) {
                showToast(r.message ?: "上传安装成功")
                load()
            } else error.value = r.message ?: "上传安装失败"
        }
    }

    fun loadFailed() {
        run {
            val r = api.getFailedPlugins()
            if (r.ok) failedPlugins = r.dataObj ?: r.dataArr?.let { JSONObject().put("list", it) }
            else error.value = r.message ?: "获取失败插件失败"
        }
    }
}

@Composable
fun PluginsScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: PluginsVm = viewModel { PluginsVm(api) }
    val loading by vm.loading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { if (vm.plugins == null) vm.load() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let { u ->
            runCatching {
                val resolver = context.contentResolver
                val name = resolver.query(u, null, null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(c.getColumnIndexOrThrow(android.provider.OpenableColumns.DISPLAY_NAME)) else "plugin.zip"
                } ?: "plugin.zip"
                val tmp = File(context.cacheDir, "upload_${System.currentTimeMillis()}_$name")
                resolver.openInputStream(u)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                vm.uploadFile(tmp)
            }.onFailure { vm.error.value = "读取文件失败: ${it.message}" }
        }
    }

    ScreenScaffold(
        title = "插件管理",
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { vm.loadFailed(); vm.showFailedDialog = true }) { Icon(Icons.Outlined.BrokenImage, "失败插件") }
            IconButton(onClick = { navController.navigate(Routes.PLUGIN_MARKET) }) { Icon(Icons.Outlined.Storefront, "插件市场") }
            IconButton(onClick = { filePicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*")) }) { Icon(Icons.Outlined.FileUpload, "上传插件") }
            IconButton(onClick = { vm.showInstallDialog = true }) { Icon(Icons.Outlined.Add, "安装插件") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.uploading) {
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                    Text("正在上传安装插件…", color = MaterialTheme.colorScheme.primary)
                }
            }
            LoadingBox(loading)
            val list = vm.plugins
            if (list == null) {
                if (!loading) EmptyHint("暂无插件数据，点击右上角刷新")
            } else if (list.length() == 0) {
                EmptyHint("未安装任何插件")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list.length()) { idx ->
                        val plugin = list.optJSONObject(idx) ?: return@items
                        PluginItem(
                            plugin = plugin,
                            onToggle = { vm.toggle(plugin) },
                            onDetail = { navController.navigate(Routes.pluginDetail(plugin.s("name"))) },
                            onUpdate = { vm.update(plugin.s("name")) },
                            onReload = { vm.reload(plugin.s("name")) },
                            onUninstall = { vm.uninstall(plugin.s("name")) },
                            onConfig = { navController.navigate(Routes.jsonEdit("plugin:${plugin.s("name")}")) },
                        )
                    }
                }
            }
        }
    }

    if (vm.showInstallDialog) {
        AlertDialog(
            onDismissRequest = { vm.showInstallDialog = false },
            title = { Text("安装插件") },
            text = {
                Column {
                    OutlinedTextField(
                        value = vm.installUrl,
                        onValueChange = { vm.installUrl = it },
                        label = { Text("插件 Git 仓库地址") },
                        placeholder = { Text("https://github.com/xxx/astrbot_plugin_xxx.git") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vm.ignoreVersion, onCheckedChange = { vm.ignoreVersion = it })
                        Text("忽略 AstrBot 版本兼容检查")
                    }
                    Text("也可以直接粘贴 AstrBot 插件市场的仓库链接。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                Button(onClick = { vm.install() }, enabled = vm.installUrl.isNotBlank() && !vm.installing) {
                    if (vm.installing) Text("安装中…") else Text("安装")
                }
            },
            dismissButton = { TextButton(onClick = { vm.showInstallDialog = false }) { Text("取消") } },
        )
    }

    if (vm.showFailedDialog) {
        AlertDialog(
            onDismissRequest = { vm.showFailedDialog = false },
            title = { Text("加载失败的插件") },
            text = {
                val failed = vm.failedPlugins
                if (failed == null || failed.length() == 0) {
                    Text("没有加载失败的插件")
                } else {
                    Column(Modifier.fillMaxWidth()) {
                        failed.keys().forEach { key ->
                            val info = failed.opt(key).toString()
                            Text(key, style = MaterialTheme.typography.titleSmall)
                            Text(info, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { vm.showFailedDialog = false }) { Text("关闭") } },
        )
    }
}

@Composable
private fun PluginItem(
    plugin: JSONObject,
    onToggle: () -> Unit,
    onDetail: () -> Unit,
    onUpdate: () -> Unit,
    onReload: () -> Unit,
    onUninstall: () -> Unit,
    onConfig: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }
    val name = plugin.s("name")
    val activated = plugin.optBoolean("activated", false)
    val desc = plugin.s("desc").ifBlank { plugin.s("display_name") }

    Card(
        onClick = onDetail,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.size(8.dp))
                    Text("v${plugin.s("version")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (desc.isNotBlank()) {
                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (activated) "已启用" else "已停用",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(plugin.s("author").ifBlank { "-" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(checked = activated, onCheckedChange = { onToggle() })
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "操作") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("插件详情") }, onClick = { menuOpen = false; onDetail() })
                    DropdownMenuItem(text = { Text("插件配置") }, onClick = { menuOpen = false; onConfig() })
                    DropdownMenuItem(text = { Text("更新") }, onClick = { menuOpen = false; onUpdate() })
                    DropdownMenuItem(text = { Text("重载") }, onClick = { menuOpen = false; onReload() })
                    DropdownMenuItem(text = { Text("卸载", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; confirmUninstall = true })
                }
            }
        }
    }

    if (confirmUninstall) {
        ConfirmDialog(
            title = "卸载插件",
            text = "确定要卸载插件 $name 吗？",
            confirmText = "卸载",
            onConfirm = { confirmUninstall = false; onUninstall() },
            onDismiss = { confirmUninstall = false },
        )
    }
}

