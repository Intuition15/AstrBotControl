package com.astrbot.control.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.astrbot.control.data.ApiClient
import com.astrbot.control.util.ConfigI18n
import org.json.JSONArray
import org.json.JSONObject

private val SENSITIVE_WORDS = listOf("key", "token", "secret", "password", "passwd")

private fun isSensitive(name: String): Boolean {
    val n = name.lowercase()
    return SENSITIVE_WORDS.any { n.contains(it) }
}

// ---------- 点路径读写（支持 "dashboard.ssl.enable" 这类嵌套键） ----------

private fun getPath(root: JSONObject, path: String): Any? {
    val keys = path.split('.')
    var cur: Any? = root
    for (k in keys) {
        cur = (cur as? JSONObject)?.opt(k) ?: return null
    }
    return cur
}

private fun setPath(root: JSONObject, path: String, value: Any) {
    val keys = path.split('.')
    var cur = root
    for (i in 0 until keys.size - 1) {
        val k = keys[i]
        val next = cur.optJSONObject(k) ?: JSONObject().also { cur.put(k, it) }
        cur = next
    }
    cur.put(keys.last(), value)
}

private fun removePath(root: JSONObject, path: String) {
    val keys = path.split('.')
    var cur = root
    for (i in 0 until keys.size - 1) {
        cur = cur.optJSONObject(keys[i]) ?: return
    }
    cur.remove(keys.last())
}

/**
 * 由 AstrBot 后端 schema 驱动的表单渲染器，交互对齐原生 Web 前端：
 *  - bool→开关, string→输入框(敏感掩码), text→多行, int/float→数字, options→下拉/多选, list→列表增删编辑, dict→嵌套
 *  - description/hint/name 支持 i18n 键自动翻译为中文
 *  - _special 特殊控件：选择提供商/人设/知识库等
 */
@Composable
fun SchemaForm(
    config: JSONObject,
    metadata: JSONObject,
    version: Int,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    api: ApiClient? = null,
) {
    Column(modifier) {
        metadata.keys().forEach { sectionKey ->
            val section = metadata.optJSONObject(sectionKey) ?: return@forEach
            val rawName = section.optString("name").ifBlank { sectionKey }
            val name = ConfigI18n.text(rawName) ?: sectionKey
            val fields = section.optJSONObject("metadata") ?: section.optJSONObject("items")
            if (fields == null) return@forEach
            SectionTitle(name)
            fields.keys().forEach { fieldKey ->
                val meta = fields.optJSONObject(fieldKey) ?: return@forEach
                SchemaField(config, fieldKey, meta, version, onChanged, api)
            }
        }
    }
}

@Composable
private fun SchemaField(
    config: JSONObject,
    key: String,
    meta: JSONObject,
    version: Int,
    onChanged: () -> Unit,
    api: ApiClient?,
) {
    // condition：满足条件才显示（条件键支持点路径）
    meta.optJSONObject("condition")?.let { cond ->
        cond.keys().forEach { ck ->
            val expected = cond.opt(ck).toString()
            val actual = getPath(config, ck)?.toString() ?: ""
            if (actual != expected) return
        }
    }
    if (meta.optBoolean("hidden", false) || meta.optBoolean("invisible", false)) return

    val type = meta.optString("type", "string")
    val label = ConfigI18n.label(meta, key)
    val hint = ConfigI18n.hint(meta)
    val readonly = meta.optBoolean("readonly", false)

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        when {
            // ---------- 特殊控件 ----------
            meta.optString("_special") == "select_provider" ||
                meta.optString("_special") == "select_provider_stt" ||
                meta.optString("_special") == "select_provider_tts" ||
                meta.optString("_special").startsWith("select_agent_runner_provider") -> {
                ProviderSelectField(config, key, label, hint, multiple = false, version, onChanged, api)
            }
            meta.optString("_special") == "select_providers" ||
                meta.optString("_special") == "provider_pool" -> {
                ProviderSelectField(config, key, label, hint, multiple = true, version, onChanged, api)
            }
            meta.optString("_special") == "select_persona" ||
                meta.optString("_special") == "persona_pool" -> {
                PersonaSelectField(config, key, label, hint, multiple = meta.optString("_special") == "persona_pool", version, onChanged, api)
            }
            meta.optString("_special") == "select_knowledgebase" -> {
                KbSelectField(config, key, label, hint, version, onChanged, api)
            }
            meta.optString("_special") == "get_embedding_dim" -> {
                NumberField(config, key, label, hint, readonly, version, onChanged)
            }
            // ---------- 常规类型 ----------
            type == "bool" -> {
                val checked = getPath(config, key) as? Boolean ?: meta.optBoolean("default", false)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                        hint?.let { FieldHint(it) }
                    }
                    Switch(
                        checked = checked,
                        enabled = !readonly,
                        onCheckedChange = {
                            if (it) setPath(config, key, true) else removePath(config, key)
                            onChanged()
                        },
                    )
                }
            }
            type == "int" || type == "float" || type == "number" -> {
                NumberField(config, key, label, hint, readonly, version, onChanged)
            }
            type == "text" -> {
                TextAreaField(config, key, label, hint, readonly, version, onChanged)
            }
            type == "list" -> {
                if (meta.has("options")) {
                    ListOptionsField(config, key, meta, label, hint, version, onChanged)
                } else {
                    TemplateListField(config, key, meta, label, version, onChanged)
                }
            }
            type == "dict" || type == "object" -> {
                DictField(config, key, meta, label, version, onChanged)
            }
            else -> { // string 及其它
                StringField(config, key, label, hint, readonly, version, onChanged)
            }
        }
    }
}

@Composable
private fun FieldHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
    )
}

@Composable
private fun StringField(
    config: JSONObject,
    key: String,
    label: String,
    hint: String?,
    readonly: Boolean,
    version: Int,
    onChanged: () -> Unit,
) {
    val current = getPath(config, key)?.toString() ?: ""
    var value by remember(key, version) { mutableStateOf(current) }
    var show by remember(key, version) { mutableStateOf(!isSensitive(key)) }
    val sensitive = isSensitive(key)
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            value = v
            if (v.isEmpty()) removePath(config, key) else setPath(config, key, v)
            onChanged()
        },
        label = { Text(label) },
        enabled = !readonly,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (sensitive && !show) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = {
            if (sensitive) {
                IconButton(onClick = { show = !show }) {
                    Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (show) "隐藏" else "显示")
                }
            }
        },
    )
    hint?.let { FieldHint(it) }
}

@Composable
private fun NumberField(
    config: JSONObject,
    key: String,
    label: String,
    hint: String?,
    readonly: Boolean,
    version: Int,
    onChanged: () -> Unit,
) {
    val raw = getPath(config, key)
    val current = when (raw) {
        is Number -> raw.toString()
        else -> raw?.toString() ?: ""
    }
    var text by remember(key, version) { mutableStateOf(current) }
    OutlinedTextField(
        value = text,
        onValueChange = { v ->
            text = v
            if (v.isNotEmpty()) {
                val parsed: Any? = when (raw) {
                    is Int -> v.toIntOrNull()
                    is Long -> v.toLongOrNull()
                    is Double -> v.toDoubleOrNull()
                    is Float -> v.toFloatOrNull()
                    else -> v.toDoubleOrNull()
                }
                if (parsed != null) {
                    setPath(config, key, parsed)
                    onChanged()
                }
            }
        },
        label = { Text(label) },
        enabled = !readonly,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    hint?.let { FieldHint(it) }
}

@Composable
private fun TextAreaField(
    config: JSONObject,
    key: String,
    label: String,
    hint: String?,
    readonly: Boolean,
    version: Int,
    onChanged: () -> Unit,
) {
    val current = getPath(config, key)?.toString() ?: ""
    var value by remember(key, version) { mutableStateOf(current) }
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            value = v
            if (v.isEmpty()) removePath(config, key) else setPath(config, key, v)
            onChanged()
        },
        label = { Text(label) },
        enabled = !readonly,
        modifier = Modifier.fillMaxWidth(),
        minLines = 2,
    )
    hint?.let { FieldHint(it) }
}

// ---------- 特殊选择控件 ----------

@Composable
private fun ProviderSelectField(
    config: JSONObject,
    key: String,
    label: String,
    hint: String?,
    multiple: Boolean,
    version: Int,
    onChanged: () -> Unit,
    api: ApiClient?,
) {
    var providers by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(Unit) {
        val r = api?.get("/api/v1/providers") ?: return@LaunchedEffect
        if (r.ok) providers = r.dataArr ?: r.dataObj?.optJSONArray("providers")
    }
    val current = getPath(config, key)
    val selected: MutableSet<String> = if (multiple) {
        (current as? JSONArray)?.let { arr ->
            (0 until arr.length()).mapTo(mutableSetOf()) { arr.optString(it) }
        } ?: mutableSetOf()
    } else {
        mutableSetOf(current?.toString() ?: "")
    }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    val list = providers
    if (list == null) {
        Text("加载提供商列表…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (list.length() == 0) {
        Text("（暂无已配置的提供商，请先在「更多 → 提供方管理」中添加）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (i in 0 until list.length()) {
                val p = list.optJSONObject(i) ?: continue
                val pid = p.optString("id")
                val pname = p.optString("name").ifBlank { pid }
                if (pid.isBlank()) continue
                val isSel = selected.contains(pid)
                FilterChip(
                    selected = isSel,
                    onClick = {
                        if (multiple) {
                            val arr = current as? JSONArray ?: JSONArray()
                            if (isSel) {
                                for (j in arr.length() - 1 downTo 0) if (arr.optString(j) == pid) arr.remove(j)
                            } else {
                                arr.put(pid)
                            }
                            setPath(config, key, arr)
                        } else {
                            setPath(config, key, pid)
                        }
                        onChanged()
                    },
                    label = { Text(pname) },
                )
            }
        }
    }
    hint?.let { FieldHint(it) }
}

@Composable
private fun PersonaSelectField(
    config: JSONObject,
    key: String,
    label: String,
    hint: String?,
    multiple: Boolean,
    version: Int,
    onChanged: () -> Unit,
    api: ApiClient?,
) {
    var personas by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(Unit) {
        val r = api?.get("/api/v1/personas") ?: return@LaunchedEffect
        if (r.ok) personas = r.dataArr ?: r.dataObj?.optJSONArray("personas") ?: r.dataObj?.optJSONArray("data")
    }
    val current = getPath(config, key)
    val selected: MutableSet<String> = if (multiple) {
        (current as? JSONArray)?.let { arr ->
            (0 until arr.length()).mapTo(mutableSetOf()) { arr.optString(it) }
        } ?: mutableSetOf()
    } else {
        mutableSetOf(current?.toString() ?: "")
    }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    val list = personas
    if (list == null) {
        Text("加载人设列表…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (list.length() == 0) {
        Text("（暂无已配置的人设）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (i in 0 until list.length()) {
                val p = list.optJSONObject(i) ?: continue
                val pid = p.optString("persona_id").ifBlank { p.optString("id") }.ifBlank { p.optString("name") }
                val pname = p.optString("name").ifBlank { pid }
                if (pid.isBlank()) continue
                val isSel = selected.contains(pid)
                FilterChip(
                    selected = isSel,
                    onClick = {
                        if (multiple) {
                            val arr = current as? JSONArray ?: JSONArray()
                            if (isSel) {
                                for (j in arr.length() - 1 downTo 0) if (arr.optString(j) == pid) arr.remove(j)
                            } else {
                                arr.put(pid)
                            }
                            setPath(config, key, arr)
                        } else {
                            setPath(config, key, pid)
                        }
                        onChanged()
                    },
                    label = { Text(pname) },
                )
            }
        }
    }
    hint?.let { FieldHint(it) }
}

@Composable
private fun KbSelectField(
    config: JSONObject,
    key: String,
    label: String,
    hint: String?,
    version: Int,
    onChanged: () -> Unit,
    api: ApiClient?,
) {
    var kbs by remember { mutableStateOf<JSONArray?>(null) }
    LaunchedEffect(Unit) {
        val r = api?.get("/api/v1/knowledge-bases") ?: return@LaunchedEffect
        if (r.ok) kbs = r.dataArr ?: r.dataObj?.optJSONArray("kbs") ?: r.dataObj?.optJSONArray("data")
    }
    val current = getPath(config, key)?.toString() ?: ""
    Text(label, style = MaterialTheme.typography.bodyMedium)
    val list = kbs
    if (list == null) {
        Text("加载知识库列表…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (list.length() == 0) {
        Text("（暂无知识库）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (i in 0 until list.length()) {
                val kb = list.optJSONObject(i) ?: continue
                val kid = kb.optString("kb_id").ifBlank { kb.optString("id") }
                val kname = kb.optString("name").ifBlank { kid }
                if (kid.isBlank()) continue
                FilterChip(
                    selected = current == kid,
                    onClick = {
                        setPath(config, key, kid)
                        onChanged()
                    },
                    label = { Text(kname) },
                )
            }
        }
    }
    hint?.let { FieldHint(it) }
}

// ---------- options 列表 ----------

private fun contains(arr: JSONArray, v: String): Boolean {
    for (i in 0 until arr.length()) if (arr.optString(i) == v) return true
    return false
}

private fun removeValue(arr: JSONArray, v: String) {
    for (i in arr.length() - 1 downTo 0) {
        if (arr.optString(i) == v) arr.remove(i)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListOptionsField(
    config: JSONObject,
    key: String,
    meta: JSONObject,
    label: String,
    hint: String?,
    version: Int,
    onChanged: () -> Unit,
) {
    val options = meta.optJSONArray("options") ?: JSONArray()
    val labelsRaw = meta.optJSONArray("labels")
    fun labelOf(i: Int, v: String): String {
        labelsRaw?.let { lr ->
            val raw = lr.optString(i)
            if (raw.isNotBlank()) return ConfigI18n.text(raw) ?: raw
        }
        return v
    }
    val current = getPath(config, key) as? JSONArray
    Text(label, style = MaterialTheme.typography.bodyMedium)
    if (options.length() <= 8) {
        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (i in 0 until options.length()) {
                val opt = options.optString(i)
                val selected = current != null && contains(current, opt)
                FilterChip(
                    selected = selected,
                    onClick = {
                        val arr = current ?: JSONArray()
                        if (selected) removeValue(arr, opt) else arr.put(opt)
                        setPath(config, key, arr)
                        onChanged()
                    },
                    label = { Text(labelOf(i, opt)) },
                )
            }
        }
    } else {
        var open by remember(key, version) { mutableStateOf(false) }
        TextButton(onClick = { open = true }) {
            Text(if (current != null && current.length() > 0) "已选 ${current.length()} 项，点击修改" else "未选择，点击选择")
        }
        if (open) {
            AlertDialog(
                onDismissRequest = { open = false },
                title = { Text(label) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        for (i in 0 until options.length()) {
                            val opt = options.optString(i)
                            val selected = current != null && contains(current, opt)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = selected,
                                    onCheckedChange = {
                                        val arr = current ?: JSONArray()
                                        if (selected) removeValue(arr, opt) else arr.put(opt)
                                        setPath(config, key, arr)
                                        onChanged()
                                    },
                                )
                                Text(labelOf(i, opt), Modifier.weight(1f))
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { open = false }) { Text("完成") } },
            )
        }
    }
    hint?.let { FieldHint(it) }
}

// ---------- 模板列表（平台/提供方等） ----------

@Composable
private fun TemplateListField(
    config: JSONObject,
    key: String,
    meta: JSONObject,
    label: String,
    version: Int,
    onChanged: () -> Unit,
) {
    val templates = meta.optJSONObject("config_template") ?: JSONObject()
    val items = getPath(config, key) as? JSONArray ?: JSONArray().also { setPath(config, key, it) }
    var editingIndex by remember(key, version) { mutableStateOf(-1) }
    var pickTemplate by remember(key, version) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        if (items.length() == 0) {
            Text("（暂无条目）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val itemLabel = item.optString("name").ifBlank { item.optString("type") }.ifBlank { item.optString("id") }.ifBlank { "条目 ${i + 1}" }
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(itemLabel, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "启用: ${if (item.optBoolean("enable", item.optBoolean("enabled", false))) "是" else "否"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editingIndex = i }) { Icon(Icons.Outlined.Edit, "编辑") }
                    IconButton(onClick = {
                        items.remove(i)
                        onChanged()
                    }) { Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        TextButton(onClick = {
            if (templates.length() > 0) {
                pickTemplate = true
            } else {
                items.put(JSONObject().put("id", uniqueId(items, "item")))
                onChanged()
            }
        }) {
            Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
            Text("添加条目")
        }
        if (pickTemplate && templates.length() > 0) {
            AlertDialog(
                onDismissRequest = { pickTemplate = false },
                title = { Text("添加条目（选择模板）") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        templates.keys().forEach { tname ->
                            TextButton(onClick = {
                                val tmpl = templates.optJSONObject(tname) ?: return@TextButton
                                val copy = JSONObject(tmpl.toString())
                                val baseId = copy.optString("id").ifBlank { tname }
                                copy.put("id", uniqueId(items, baseId))
                                items.put(copy)
                                pickTemplate = false
                                onChanged()
                            }) { Text(tname) }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { pickTemplate = false }) { Text("取消") } },
            )
        }
    }
    if (editingIndex >= 0 && editingIndex < items.length()) {
        val item = items.optJSONObject(editingIndex)
        if (item != null) {
            ItemEditDialog(
                item = item,
                open = true,
                onDismiss = { editingIndex = -1 },
                onSave = { onChanged() },
            )
        }
    }
}

private fun uniqueId(items: JSONArray, base: String): String {
    var candidate = base
    var n = 2
    while (containsId(items, candidate)) {
        candidate = "$base$n"
        n++
    }
    return candidate
}

private fun containsId(items: JSONArray, id: String): Boolean {
    for (i in 0 until items.length()) {
        val o = items.optJSONObject(i) ?: continue
        if (o.optString("id") == id) return true
    }
    return false
}

@Composable
private fun ItemEditDialog(item: JSONObject, open: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    if (!open) return
    var version by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑条目") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                InferredObjectFields(item, version, onSave)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}

// ---------- 推断字段（无 schema 时按值类型渲染） ----------

@Composable
fun InferredObjectFields(
    item: JSONObject,
    version: Int,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
    excludeKeys: Set<String> = emptySet(),
) {
    Column(modifier) {
        item.keys().forEach { k ->
            if (k in excludeKeys) return@forEach
            val v = item.opt(k)
            when (v) {
                is Boolean -> {
                    var c by remember(k, version) { mutableStateOf(v) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, Modifier.weight(1f))
                        Switch(checked = c, onCheckedChange = { c = it; item.put(k, it); onChanged() })
                    }
                }
                is Int, is Long -> {
                    var t by remember(k, version) { mutableStateOf(v.toString()) }
                    OutlinedTextField(
                        value = t,
                        onValueChange = { t = it; runCatching { item.put(k, if (v is Int) it.toInt() else it.toLong()) }; onChanged() },
                        label = { Text(k) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                is Double -> {
                    var t by remember(k, version) { mutableStateOf(v.toString()) }
                    OutlinedTextField(
                        value = t,
                        onValueChange = { t = it; runCatching { item.put(k, it.toDouble()) }; onChanged() },
                        label = { Text(k) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                is JSONArray -> {
                    var t by remember(k, version) { mutableStateOf((0 until v.length()).joinToString(",") { v.optString(it) }) }
                    OutlinedTextField(
                        value = t,
                        onValueChange = { t = it; item.put(k, JSONArray().apply { t.split(",").map { s -> s.trim() }.filter { it.isNotEmpty() }.forEach { put(it) } }); onChanged() },
                        label = { Text(k) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        singleLine = true,
                    )
                }
                is JSONObject -> {
                    Text(k, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    InferredObjectFields(v, version, onChanged, Modifier.padding(start = 8.dp))
                }
                else -> {
                    var t by remember(k, version) { mutableStateOf(v?.toString() ?: "") }
                    var show by remember(k, version) { mutableStateOf(!isSensitive(k)) }
                    OutlinedTextField(
                        value = t,
                        onValueChange = { t = it; item.put(k, t); onChanged() },
                        label = { Text(k) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        singleLine = true,
                        visualTransformation = if (isSensitive(k) && !show) PasswordVisualTransformation() else VisualTransformation.None,
                        trailingIcon = {
                            if (isSensitive(k)) {
                                IconButton(onClick = { show = !show }) {
                                    Icon(if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "显示/隐藏")
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

// ---------- 字典字段 ----------

@Composable
private fun DictField(
    config: JSONObject,
    key: String,
    meta: JSONObject,
    label: String,
    version: Int,
    onChanged: () -> Unit,
) {
    val nestedMeta = meta.optJSONObject("items")
    val value = getPath(config, key) as? JSONObject ?: JSONObject().also { setPath(config, key, it) }
    var showAdd by remember(key, version) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (nestedMeta != null) {
            nestedMeta.keys().forEach { fk ->
                val fm = nestedMeta.optJSONObject(fk) ?: return@forEach
                SchemaField(value, fk, fm, version, onChanged, api = null)
            }
        } else {
            value.keys().forEach { fk ->
                val fv = value.opt(fk)
                val fakeMeta = JSONObject().put("description", fk)
                when (fv) {
                    is Boolean -> fakeMeta.put("type", "bool")
                    is Int, is Long -> fakeMeta.put("type", "int")
                    is Double -> fakeMeta.put("type", "float")
                    else -> fakeMeta.put("type", "string")
                }
                SchemaField(value, fk, fakeMeta, version, onChanged, api = null)
            }
            TextButton(onClick = { showAdd = true }) {
                Icon(Icons.Outlined.Add, null, Modifier.size(16.dp))
                Text("添加字段")
            }
        }
    }
    if (showAdd) {
        var input by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text("添加字段") },
            text = {
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("字段名") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (input.isNotBlank() && !value.has(input)) {
                        value.put(input, "")
                        onChanged()
                    }
                    showAdd = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAdd = false }) { Text("取消") } },
        )
    }
}
