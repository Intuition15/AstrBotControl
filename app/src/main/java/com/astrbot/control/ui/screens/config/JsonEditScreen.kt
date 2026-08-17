package com.astrbot.control.ui.screens.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.components.JsonEditorField
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.SchemaForm
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONObject
import java.net.URLEncoder
import androidx.compose.runtime.setValue

/**
 * 配置编辑页：优先使用后端 schema 渲染成表单（开关/输入框/下拉等，对齐 Web 前端）；
 * 无 schema 的配置（配置路由、子代理）或需要精细操作时可用「JSON 高级编辑」。
 *
 * key 取值：
 *  - system_config      系统配置（schema 表单）
 *  - profile:{id}       配置档（schema 表单）
 *  - plugin:{name}      插件配置（schema 表单）
 *  - config_routes      配置路由（JSON）
 *  - subagent_config    子代理配置（JSON）
 */
class JsonEditVm(
    api: com.astrbot.control.data.ApiClient,
    private val key: String,
) : BaseVm(api) {
    var title by mutableStateOf("配置")
    var config by mutableStateOf<JSONObject?>(null)
    var metadata by mutableStateOf<JSONObject?>(null)
    var initialJson by mutableStateOf<String?>(null)
    var version by mutableStateOf(0)
    var saving by mutableStateOf(false)
    var advancedJson by mutableStateOf(false)
    var loaded by mutableStateOf(false)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    fun load() {
        load {
            when {
                key == "system_config" -> {
                    title = "系统配置"
                    val rs = api.get("/api/v1/system-config/schema")
                    if (rs.ok) {
                        metadata = rs.dataObj?.optJSONObject("metadata")
                        val rc = api.get("/api/v1/system-config")
                        config = when {
                            rc.ok && rc.dataObj?.optJSONObject("config") != null -> rc.dataObj!!.optJSONObject("config")
                            rc.ok && rc.dataObj != null -> rc.dataObj
                            else -> rs.dataObj?.optJSONObject("config") ?: JSONObject()
                        }
                        if (!rc.ok) error.value = rc.message ?: "获取配置失败"
                    } else error.value = rs.message ?: "获取配置失败"
                }
                key == "config_routes" -> {
                    title = "配置路由"
                    val r = api.get("/api/v1/config-routes")
                    if (r.ok) initialJson = prettyOf(r.dataObj, r.dataArr)
                    else error.value = r.message ?: "获取配置失败"
                    advancedJson = true
                }
                key == "subagent_config" -> {
                    title = "子代理配置"
                    val r = api.get("/api/v1/subagents/config")
                    if (r.ok) initialJson = prettyOf(r.dataObj, r.dataArr)
                    else error.value = r.message ?: "获取配置失败"
                    advancedJson = true
                }
                key.startsWith("profile:") -> {
                    val id = key.removePrefix("profile:")
                    title = "配置档 $id"
                    val rs = api.get("/api/v1/config-profiles/schema")
                    if (rs.ok) metadata = rs.dataObj?.optJSONObject("metadata")
                    val rc = api.get("/api/v1/config-profiles/${enc(id)}")
                    val defaults = rs.dataObj?.optJSONObject("config") ?: JSONObject()
                    val current = if (rc.ok) (rc.dataObj?.optJSONObject("config") ?: rc.dataObj) else null
                    config = if (current != null) mergeDefaults(defaults, current) else defaults
                    if (!rc.ok) error.value = rc.message ?: "获取配置档失败"
                }
                key.startsWith("plugin:") -> {
                    val name = key.removePrefix("plugin:")
                    title = "插件配置 $name"
                    val r = api.get("/api/v1/plugins/${enc(name)}/config/schema")
                    if (r.ok) {
                        metadata = r.dataObj?.optJSONObject("metadata")
                        config = r.dataObj?.optJSONObject("config") ?: JSONObject()
                    } else error.value = r.message ?: "获取插件配置失败"
                }
                else -> {
                    title = "JSON 编辑"
                    initialJson = "{}"
                    advancedJson = true
                }
            }
            loaded = true
        }
    }

    private fun mergeDefaults(defaults: JSONObject, current: JSONObject): JSONObject {
        val merged = JSONObject(defaults.toString())
        current.keys().forEach { k -> merged.put(k, current.opt(k)) }
        return merged
    }

    private fun prettyOf(obj: JSONObject?, arr: org.json.JSONArray?): String = when {
        obj != null -> obj.toString(4)
        arr != null -> arr.toString(4)
        else -> "{}"
    }

    fun saveForm(onDone: () -> Unit) {
        val cfg = config ?: return
        run {
            saving = true
            val r = when {
                key == "system_config" -> api.put("/api/v1/system-config", cfg)
                key.startsWith("profile:") -> api.put("/api/v1/config-profiles/${enc(key.removePrefix("profile:"))}", cfg)
                key.startsWith("plugin:") -> api.put("/api/v1/plugins/${enc(key.removePrefix("plugin:"))}/config", cfg)
                else -> null
            }
            saving = false
            if (r != null) {
                if (r.ok) {
                    showToast(r.message ?: "保存成功")
                    onDone()
                } else error.value = r.message ?: "保存失败"
            }
        }
    }

    fun saveJson(json: String, onDone: () -> Unit) {
        run {
            saving = true
            val r = when {
                key == "config_routes" -> api.put("/api/v1/config-routes", json)
                key == "subagent_config" -> api.put("/api/v1/subagents/config", json)
                else -> null
            }
            saving = false
            if (r != null) {
                if (r.ok) {
                    showToast(r.message ?: "保存成功")
                    onDone()
                } else error.value = r.message ?: "保存失败"
            }
        }
    }
}

@Composable
fun JsonEditScreen(navController: NavHostController, key: String) {
    val api = rememberApi()
    val vm: JsonEditVm = viewModel { JsonEditVm(api, key) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(key) { if (!vm.loaded) vm.load() }

    ScreenScaffold(
        title = vm.title,
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            if (!vm.advancedJson) {
                TextButton(onClick = { vm.advancedJson = true }) { Text("JSON 高级编辑") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)
            if (loading) return@Box
            when {
                vm.advancedJson -> {
                    vm.initialJson?.let { json ->
                        JsonEditorField(
                            initialJson = json,
                            saving = vm.saving,
                            onSave = { text ->
                                vm.initialJson = text
                                vm.saveJson(text) { navController.popBackStack() }
                            },
                        )
                    }
                }
                vm.config != null && vm.metadata != null -> {
                    androidx.compose.foundation.layout.Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 8.dp)
                    ) {
                        SchemaForm(
                            config = vm.config!!,
                            metadata = vm.metadata!!,
                            version = vm.version,
                            onChanged = { vm.version++ },
                            api = api,
                        )
                        Spacer(Modifier.padding(8.dp))
                        Button(
                            onClick = { vm.saveForm { navController.popBackStack() } },
                            enabled = !vm.saving,
                            modifier = Modifier.padding(16.dp),
                        ) {
                            if (vm.saving) {
                                CircularProgressIndicator(
                                    Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text("保存")
                            }
                        }
                    }
                }
                else -> {
                    Text("配置加载失败", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

