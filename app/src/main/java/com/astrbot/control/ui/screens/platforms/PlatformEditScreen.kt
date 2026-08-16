package com.astrbot.control.ui.screens.platforms

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
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.runtime.setValue

class PlatformEditVm(
    api: com.astrbot.control.data.ApiClient,
    private val botId: String?,
) : BaseVm(api) {
    var isNew by mutableStateOf(botId.isNullOrBlank() || botId == "new")
    var id by mutableStateOf("")
    var type by mutableStateOf("")
    var name by mutableStateOf("")
    var enabled by mutableStateOf(true)
    var config by mutableStateOf<JSONObject?>(null)
    var botTypes by mutableStateOf<JSONArray?>(null)
    var version by mutableStateOf(0)
    var saving by mutableStateOf(false)
    var loaded by mutableStateOf(false)

    fun load() {
        load {
            val rt = api.get("/api/v1/bot-types")
            if (rt.ok) botTypes = rt.dataObj?.optJSONArray("bot_types") ?: rt.dataArr
            if (!isNew) {
                val r = api.get("/api/v1/bots/${enc(botId!!)}")
                if (r.ok) {
                    val bot = r.dataObj?.optJSONObject("bot") ?: r.dataObj ?: JSONObject()
                    id = bot.optString("id", botId!!)
                    type = bot.optString("type")
                    name = bot.optString("name")
                    enabled = bot.optBoolean("enable", bot.optBoolean("enabled", true))
                    config = JSONObject(bot.toString())
                } else {
                    error.value = r.message ?: "获取平台配置失败"
                }
            }
            if (config == null) config = JSONObject().apply {
                put("id", "")
                put("type", "")
                put("enable", true)
            }
            loaded = true
        }
    }

    /** 选择平台类型：应用默认配置模板 */
    fun applyType(t: String) {
        val types = botTypes ?: return
        for (i in 0 until types.length()) {
            val bt = types.optJSONObject(i) ?: continue
            if (bt.optString("type") != t && bt.optString("id") != t) continue
            val def = bt.optJSONObject("default_config") ?: JSONObject()
            val cfg = JSONObject(def.toString())
            cfg.put("type", t)
            if (cfg.optString("id").isBlank()) cfg.put("id", id)
            cfg.put("enable", enabled)
            type = t
            config = cfg
            version++
            return
        }
    }

    fun save(onDone: () -> Unit) {
        val cfg = config ?: return
        run {
            saving = true
            cfg.put("id", id)
            if (type.isNotBlank()) cfg.put("type", type)
            if (name.isNotBlank()) cfg.put("name", name)
            cfg.put("enable", enabled)

            val r = if (isNew) {
                api.post("/api/v1/bots", JSONObject().put("config", cfg))
            } else {
                api.put("/api/v1/bots/${enc(botId!!)}", JSONObject().apply {
                    put("bot_id", botId)
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
fun PlatformEditScreen(navController: NavHostController, botId: String?) {
    val api = rememberApi()
    val vm: PlatformEditVm = viewModel { PlatformEditVm(api, botId) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (!vm.loaded) vm.load() }

    ScreenScaffold(
        title = if (vm.isNew) "新增平台" else "编辑平台",
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
                    label = { Text("平台 ID（唯一标识）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
            }
            OutlinedTextField(
                value = vm.name,
                onValueChange = { vm.name = it },
                label = { Text("显示名称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(10.dp))

            SectionTitle("平台类型")
            vm.botTypes?.let { types ->
                FlowRow(Modifier.fillMaxWidth()) {
                    for (i in 0 until types.length()) {
                        val bt = types.optJSONObject(i) ?: continue
                        val label = bt.optString("display_name").ifBlank { bt.optString("type") }
                        val tname = bt.optString("type").ifBlank { bt.optString("id") }
                        if (tname.isNotBlank()) {
                            FilterChip(
                                selected = vm.type == tname,
                                onClick = {
                                    vm.type = tname
                                    vm.applyType(tname)
                                },
                                label = { Text(label) },
                                modifier = Modifier.padding(end = 6.dp, bottom = 4.dp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("启用该平台", modifier = Modifier.weight(1f))
                Switch(checked = vm.enabled, onCheckedChange = { vm.enabled = it })
            }
            Spacer(Modifier.height(10.dp))

            SectionTitle("平台参数")
            Text(
                "AppID / Token / Secret 等敏感字段已自动掩码。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            vm.config?.let { cfg ->
                InferredObjectFields(
                    item = cfg,
                    version = vm.version,
                    onChanged = { vm.version++ },
                    excludeKeys = setOf("id", "type", "enable", "enabled", "name"),
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

