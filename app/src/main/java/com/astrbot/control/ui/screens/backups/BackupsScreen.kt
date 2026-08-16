package com.astrbot.control.ui.screens.backups

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class BackupsVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var backups by mutableStateOf<JSONArray?>(null)
    var exporting by mutableStateOf(false)
    var deleteTarget by mutableStateOf<JSONObject?>(null)
    var importTarget by mutableStateOf<JSONObject?>(null)

    fun load() {
        load {
            val r = api.get("/api/v1/backups")
            if (r.ok) backups = r.dataArr ?: r.dataObj?.optJSONArray("backups") ?: JSONArray()
            else error.value = r.message ?: "获取备份列表失败"
        }
    }

    fun export() {
        run {
            exporting = true
            val r = api.post("/api/v1/backups")
            exporting = false
            if (r.ok) {
                showToast(r.message ?: "备份已创建")
                load()
            } else error.value = r.message ?: "创建备份失败"
        }
    }

    fun delete() {
        val b = deleteTarget ?: return
        run {
            val r = api.delete("/api/v1/backups/${enc(b.s("filename"))}")
            if (r.ok) {
                showToast("已删除")
                deleteTarget = null
                load()
            } else error.value = r.message ?: "删除失败"
        }
    }

    fun import() {
        val b = importTarget ?: return
        run {
            val r = api.post("/api/v1/backups/${enc(b.s("filename"))}/import", JSONObject().put("confirmed", true))
            if (r.ok) {
                showToast(r.message ?: "导入完成")
                importTarget = null
            } else error.value = r.message ?: "导入失败"
        }
    }

    suspend fun downloadBytes(filename: String): ByteArray? =
        api.downloadBytes("/api/v1/backups/${enc(filename)}")

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

@Composable
fun BackupsScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: BackupsVm = viewModel { BackupsVm(api) }
    val loading by vm.loading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) { if (vm.backups == null) vm.load() }

    var pendingDownload by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val savePicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        uri?.let { u ->
            val pending = pendingDownload
            if (pending != null) {
                scope.launch {
                    try {
                        val bytes = vm.downloadBytes(pending) ?: throw Exception("下载失败")
                        context.contentResolver.openOutputStream(u)?.use { it.write(bytes) }
                        vm.showToast("已保存备份")
                    } catch (e: Exception) {
                        vm.error.value = "保存失败: ${e.message}"
                    }
                }
                pendingDownload = null
            }
        }
    }

    ScreenScaffold(
        title = "备份管理",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { vm.export() }) {
                if (vm.exporting) {
                    androidx.compose.material3.CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.Add, "创建备份")
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)
            val list = vm.backups
            if (list == null) {
                if (!loading) EmptyHint("暂无备份")
            } else if (list.length() == 0) {
                EmptyHint("暂无备份，点击右上角 + 创建")
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(JSONArrayToList(list)) { _, b ->
                        BackupItem(
                            b = b,
                            onDownload = {
                                pendingDownload = b.s("filename")
                                savePicker.launch(b.s("filename"))
                            },
                            onImport = { vm.importTarget = b },
                            onDelete = { vm.deleteTarget = b },
                        )
                    }
                }
            }
        }
    }

    vm.deleteTarget?.let { b ->
        ConfirmDialog(
            title = "删除备份",
            text = "确定删除备份「${b.s("filename")}」吗？",
            onConfirm = { vm.delete() },
            onDismiss = { vm.deleteTarget = null },
        )
    }

    vm.importTarget?.let { b ->
        ConfirmDialog(
            title = "导入备份",
            text = "导入备份「${b.s("filename")}」将覆盖当前配置，确定继续吗？",
            confirmText = "导入",
            onConfirm = { vm.import() },
            onDismiss = { vm.importTarget = null },
        )
    }
}

@Composable
private fun BackupItem(b: JSONObject, onDownload: () -> Unit, onImport: () -> Unit, onDelete: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(b.s("filename"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    formatSize(b.optLong("size")),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "操作") }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("下载") }, onClick = { menuOpen = false; onDownload() })
                    DropdownMenuItem(text = { Text("导入") }, onClick = { menuOpen = false; onImport() })
                    DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { menuOpen = false; onDelete() })
                }
            }
        }
    }
}

private fun formatSize(size: Long): String {
    if (size <= 0) return "-"
    return when {
        size >= 1024 * 1024 -> String.format("%.2f MB", size / 1024.0 / 1024.0)
        size >= 1024 -> String.format("%.1f KB", size / 1024.0)
        else -> "$size B"
    }
}

private fun JSONArrayToList(arr: JSONArray): List<JSONObject> =
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
