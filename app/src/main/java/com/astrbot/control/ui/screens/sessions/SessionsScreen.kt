package com.astrbot.control.ui.screens.sessions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.JsonView
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONObject

class SessionsVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var sessionsJson by mutableStateOf<String?>(null)
    var groupsJson by mutableStateOf<String?>(null)
    var dialogTitle by mutableStateOf<String?>(null)
    var dialogBody by mutableStateOf("{}")
    var dialogTarget by mutableStateOf<String?>(null) // provider / service

    fun load() {
        load {
            val r = api.get("/api/v1/sessions")
            if (r.ok) {
                sessionsJson = prettyOf(r.dataObj, r.dataArr)
            } else {
                error.value = r.message ?: "获取会话失败"
            }
        }
    }

    fun loadGroups() {
        run {
            val r = api.get("/api/v1/session-groups")
            if (r.ok) {
                groupsJson = prettyOf(r.dataObj, r.dataArr)
            } else {
                error.value = r.message ?: "获取会话组失败"
            }
        }
    }

    fun openBatchDialog(target: String) {
        dialogTitle = if (target == "provider") "批量设置提供方" else "批量设置服务"
        dialogBody = "{}"
        dialogTarget = target
    }

    fun submitBatch() {
        val target = dialogTarget ?: return
        run {
            val body = try {
                JSONObject(dialogBody)
            } catch (e: Exception) {
                error.value = "JSON 格式错误: ${e.message}"
                return@run
            }
            val r = api.patch("/api/v1/sessions/$target", body)
            if (r.ok) {
                showToast(r.message ?: "操作成功")
                dialogTarget = null
            } else error.value = r.message ?: "操作失败"
        }
    }

    private fun prettyOf(obj: JSONObject?, arr: org.json.JSONArray?): String = when {
        obj != null -> obj.toString(4)
        arr != null -> arr.toString(4)
        else -> "{}"
    }
}

@Composable
fun SessionsScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: SessionsVm = viewModel { SessionsVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.sessionsJson == null) vm.load() }

    ScreenScaffold(
        title = "会话管理",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
            IconButton(onClick = { vm.loadGroups() }) { Icon(Icons.Outlined.Groups, "会话组") }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            LoadingBox(loading)
            val json = vm.sessionsJson
            if (json != null) {
                // 页面本身已可滚动，不再嵌套滚动
                JsonView(json, scrollable = false)
                Row(Modifier.fillMaxWidth().padding(12.dp)) {
                    Button(onClick = { vm.openBatchDialog("provider") }, modifier = Modifier.weight(1f)) { Text("批量设置提供方") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { vm.openBatchDialog("service") }, modifier = Modifier.weight(1f)) { Text("批量设置服务") }
                }
            } else if (!loading) {
                EmptyHint("暂无会话数据")
            }
            vm.groupsJson?.let { groups ->
                Card(Modifier.fillMaxWidth().padding(12.dp)) {
                    Text("会话组", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(12.dp))
                    JsonView(groups, scrollable = false)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    vm.dialogTarget?.let {
        AlertDialog(
            onDismissRequest = { vm.dialogTarget = null },
            title = { Text(vm.dialogTitle ?: "") },
            text = {
                OutlinedTextField(
                    value = vm.dialogBody,
                    onValueChange = { vm.dialogBody = it },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                )
            },
            confirmButton = { TextButton(onClick = { vm.submitBatch() }) { Text("提交") } },
            dismissButton = { TextButton(onClick = { vm.dialogTarget = null }) { Text("取消") } },
        )
    }
}

