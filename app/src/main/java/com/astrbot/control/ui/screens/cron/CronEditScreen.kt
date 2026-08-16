package com.astrbot.control.ui.screens.cron

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONObject

class CronEditVm(
    api: com.astrbot.control.data.ApiClient,
    private val jobId: String?,
) : BaseVm(api) {
    var isNew by mutableStateOf(jobId.isNullOrBlank() || jobId == "new")
    var name by mutableStateOf("")
    var cronExpr by mutableStateOf("")
    var note by mutableStateOf("")
    var session by mutableStateOf("")
    var timezone by mutableStateOf("")
    var enabled by mutableStateOf(true)
    var runOnce by mutableStateOf(false)
    var runAt by mutableStateOf("")
    var extraJson by mutableStateOf("{}")
    var saving by mutableStateOf(false)
    var loaded by mutableStateOf(false)

    fun load() {
        load {
            if (!isNew) {
                val r = api.get("/api/v1/cron/jobs")
                if (r.ok) {
                    val arr = r.dataArr ?: r.dataObj?.optJSONArray("jobs")
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val j = arr.optJSONObject(i) ?: continue
                            if (j.optString("id") == jobId) {
                                name = j.optString("name")
                                cronExpr = j.optString("cron_expression")
                                note = j.optString("note").ifBlank { j.optString("description") }
                                session = j.optString("session")
                                timezone = j.optString("timezone")
                                enabled = j.optBoolean("enabled", true)
                                runOnce = j.optBoolean("run_once", false)
                                runAt = j.optString("run_at")
                                val extra = JSONObject()
                                listOf("persona_id", "provider_id", "kwargs").forEach { k ->
                                    if (j.has(k)) extra.put(k, j.opt(k))
                                }
                                extraJson = if (extra.length() == 0) "{}" else extra.toString(4)
                                break
                            }
                        }
                    }
                } else error.value = r.message ?: "获取任务失败"
            }
            loaded = true
        }
    }

    fun save(onDone: () -> Unit) {
        run {
            saving = true
            val body = JSONObject()
            body.put("name", name)
            body.put("cron_expression", cronExpr)
            if (note.isNotBlank()) body.put("note", note)
            if (session.isNotBlank()) body.put("session", session)
            if (timezone.isNotBlank()) body.put("timezone", timezone)
            body.put("enabled", enabled)
            body.put("run_once", runOnce)
            if (runAt.isNotBlank()) body.put("run_at", runAt)
            try {
                val extra = JSONObject(extraJson)
                extra.keys().forEach { k -> body.put(k, extra.opt(k)) }
            } catch (e: Exception) {
                error.value = "额外参数 JSON 格式错误: ${e.message}"
                saving = false
                return@run
            }

            val r = if (isNew) {
                api.post("/api/v1/cron/jobs", body)
            } else {
                api.patch("/api/v1/cron/jobs/${enc(jobId!!)}", body)
            }
            saving = false
            if (r.ok) {
                showToast(r.message ?: "保存成功")
                onDone()
            } else error.value = r.message ?: "保存失败"
        }
    }

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

@Composable
fun CronEditScreen(navController: NavHostController, jobId: String?) {
    val api = rememberApi()
    val vm: CronEditVm = viewModel { CronEditVm(api, jobId) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (!vm.loaded) vm.load() }

    ScreenScaffold(
        title = if (vm.isNew) "新增定时任务" else "编辑定时任务",
        onBack = { navController.popBackStack() },
        vm = vm,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            LoadingBox(loading)
            if (loading) return@ScreenScaffold

            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it },
                label = { Text("任务名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("单次执行", modifier = Modifier.weight(1f))
                Switch(checked = vm.runOnce, onCheckedChange = { vm.runOnce = it })
            }
            if (vm.runOnce) {
                OutlinedTextField(
                    value = vm.runAt,
                    onValueChange = { vm.runAt = it },
                    label = { Text("执行时间 (run_at)") },
                    placeholder = { Text("2026-01-01 00:00:00") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            } else {
                OutlinedTextField(
                    value = vm.cronExpr,
                    onValueChange = { vm.cronExpr = it },
                    label = { Text("Cron 表达式") },
                    placeholder = { Text("*/5 * * * *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.note,
                onValueChange = { vm.note = it },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.session,
                onValueChange = { vm.session = it },
                label = { Text("会话 (session)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = vm.timezone,
                onValueChange = { vm.timezone = it },
                label = { Text("时区") },
                placeholder = { Text("Asia/Shanghai") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用", modifier = Modifier.weight(1f))
                Switch(checked = vm.enabled, onCheckedChange = { vm.enabled = it })
            }
            Spacer(Modifier.height(10.dp))
            Text("额外参数 (JSON：persona_id / provider_id / kwargs 等)", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(
                value = vm.extraJson,
                onValueChange = { vm.extraJson = it },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.save { navController.popBackStack() } },
                enabled = !vm.saving && vm.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.saving) Text("保存中…") else Text("保存")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

