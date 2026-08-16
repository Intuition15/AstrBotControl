package com.astrbot.control.ui.screens.providers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.components.InferredObjectFields
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.SectionTitle
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONObject
import androidx.compose.runtime.setValue

class ProviderEditVm(
    api: com.astrbot.control.data.ApiClient,
    private val providerId: String?,
) : BaseVm(api) {
    var isNew by mutableStateOf(providerId.isNullOrBlank() || providerId == "new")
    var id by mutableStateOf("")
    var name by mutableStateOf("")
    var type by mutableStateOf("")
    var enabled by mutableStateOf(true)
    var config by mutableStateOf<JSONObject?>(null)
    var templates by mutableStateOf<JSONObject?>(null)
    var version by mutableStateOf(0)
    var saving by mutableStateOf(false)
    var loaded by mutableStateOf(false)

    fun load() {
        load {
            val rs = api.get("/api/v1/providers/schema")
            if (rs.ok) {
                templates = rs.dataObj?.optJSONObject("config_schema")
                    ?.optJSONObject("provider")
                    ?.optJSONObject("config_template")
            }
            if (!isNew) {
                val rp = api.get("/api/v1/providers/by-id", mapOf("provider_id" to providerId!!))
                if (rp.ok) {
                    val p = rp.dataObj?.optJSONObject("provider") ?: rp.dataObj ?: JSONObject()
                    id = p.optString("id", providerId!!)
                    name = p.optString("name")
                    type = p.optString("provider_type").ifBlank { p.optString("type") }
                    enabled = p.optBoolean("enable", p.optBoolean("enabled", true))
                    config = JSONObject(p.toString())
                } else {
                    error.value = rp.message ?: "获取提供方配置失败"
                }
            }
            if (config == null) config = JSONObject().apply {
                put("id", "")
                put("name", "")
                put("enable", true)
            }
            loaded = true
        }
    }

    fun applyTemplate(t: String) {
        val tmpl = templates?.optJSONObject(t) ?: return
        val cfg = JSONObject(tmpl.toString())
        if (cfg.optString("id").isBlank()) cfg.put("id", id)
        if (cfg.optString("name").isBlank()) cfg.put("name", name)
        if (!cfg.has("enable")) cfg.put("enable", enabled)
        type = t
        config = cfg
        version++
    }

    fun save(onDone: () -> Unit) {
        val cfg = config ?: return
        run {
            saving = true
            cfg.put("id", id)
            if (name.isNotBlank()) cfg.put("name", name)
            cfg.put("enable", enabled)
            if (type.isNotBlank() && !cfg.has("provider_type") && !cfg.has("type")) {
                cfg.put("provider_type", type)
            }
            val r = if (isNew) {
                api.post("/api/v1/providers", JSONObject().put("config", cfg))
            } else {
                api.put("/api/v1/providers/${enc(providerId!!)}", JSONObject().apply {
                    put("id", providerId)
                    put("config", cfg)
                })
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderEditScreen(navController: NavHostController, providerId: String?) {
    val api = rememberApi()
    val vm: ProviderEditVm = viewModel { ProviderEditVm(api, providerId) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (!vm.loaded) vm.load() }

    ScreenScaffold(
        title = if (vm.isNew) "新增提供方" else "编辑提供方",
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

            if (vm.isNew) {
                OutlinedTextField(
                    value = vm.id,
                    onValueChange = { vm.id = it },
                    label = { Text("提供方 ID（唯一标识）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it },
                label = { Text("名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))

            SectionTitle("提供方类型")
            vm.templates?.let { tmpl ->
                FlowRow(Modifier.fillMaxWidth()) {
                    tmpl.keys().forEach { t ->
                        FilterChip(
                            selected = vm.type == t,
                            onClick = {
                                vm.type = t
                                vm.applyTemplate(t)
                            },
                            label = { Text(t) },
                            modifier = Modifier.padding(end = 6.dp, bottom = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用该提供方", modifier = Modifier.weight(1f))
                Switch(checked = vm.enabled, onCheckedChange = { vm.enabled = it })
            }
            Spacer(Modifier.height(10.dp))

            SectionTitle("配置参数")
            Text(
                "API Key 等敏感字段已自动掩码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            vm.config?.let { cfg ->
                InferredObjectFields(
                    item = cfg,
                    version = vm.version,
                    onChanged = { vm.version++ },
                    excludeKeys = setOf("id", "name", "enable", "enabled", "type", "provider_type"),
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { vm.save { navController.popBackStack() } },
                enabled = !vm.saving && vm.id.isNotBlank() && vm.type.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (vm.saving) Text("保存中…") else Text("保存")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

