package com.astrbot.control.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

private val SENSITIVE_WORDS = listOf("key", "token", "secret", "password", "passwd")

private fun isSensitive(name: String): Boolean {
    val n = name.lowercase()
    return SENSITIVE_WORDS.any { n.contains(it) }
}

/**
 * 由 AstrBot 后端 schema 驱动的表单渲染器，交互对齐原生 Web 前端：
 *  bool→开关, string→输入框, text→多行, int/float→数字, options→下拉/多选, list→列表增删编辑, dict→嵌套表单
 *  metadata 支持两种格式：
 *   - {分组: {name?, metadata: {字段: meta}}}
 *   - {对象: {type:"object", items: {字段: meta}}}
 */
@Composable
fun SchemaForm(
    config: JSONObject,
    metadata: JSONObject,
    version: Int,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        metadata.keys().forEach { sectionKey ->
            val section = metadata.optJSONObject(sectionKey) ?: return@forEach
            val name = section.optString("name").ifBlank { sectionKey }
            val fields = section.optJSONObject("metadata") ?: section.optJSONObject("items")
            if (fields == null) return@forEach
            SectionTitle(name)
            fields.keys().forEach { fieldKey ->
                val meta = fields.optJSONObject(fieldKey) ?: return@forEach
                SchemaField(config, fieldKey, meta, version, onChanged)
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
) {
    // condition：满足条件才显示
    meta.optJSONObject("condition")?.let { cond ->
        cond.keys().forEach { ck ->
            val expected = cond.opt(ck).toString()
            val actual = config.opt(ck)?.toString() ?: ""
            if (actual != expected) return
        }
    }
    if (meta.optBoolean("hidden", false)) return

    val type = meta.optString("type", "string")
    val title = meta.optString("description").ifBlank { key }
    val hint = meta.optString("hint").ifBlank { null }
    val readonly = meta.optBoolean("readonly", false)

    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
        when (type) {
            "bool" -> {
                val checked = config.optBoolean(key, meta.optBoolean("default", false))
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                        hint?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = checked,
                        enabled = !readonly,
                        onCheckedChange = {
                            config.put(key, it)
                            onChanged()
                        },
                    )
                }
            }
            "int", "float", "number" -> {
                val isInt = type != "float"
                val current = config.opt(key)
                val text = remember(key, version, current?.toString()) { mutableStateOf(current?.toString() ?: "") }
                OutlinedTextField(
                    value = text.value,
                    onValueChange = { v ->
                        text.value = v
                        if (v.isNotEmpty()) {
                            try {
                                if (isInt) config.put(key, v.toInt()) else config.put(key, v.toDouble())
                            } catch (_: Exception) {
                            }
                        }
                        onChanged()
                    },
                    label = { Text(title) },
                    enabled = !readonly,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                hint?.let { FieldHint(it) }
            }
            "text" -> {
                val current = config.optString(key)
                var value by remember(key, version) { mutableStateOf(current) }
                OutlinedTextField(
                    value = value,
                    onValueChange = { v ->
                        value = v
                        config.put(key, v)
                        onChanged()
                    },
                    label = { Text(title) },
                    enabled = !readonly,
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
                hint?.let { FieldHint(it) }
            }
            "list" -> {
                if (meta.has("options")) {
                    ListOptionsField(config, key, meta, title, version, onChanged)
                } else {
                    TemplateListField(config, key, meta, title, version, onChanged)
                }
            }
            "dict", "object" -> {
                DictField(config, key, meta, title, version, onChanged)
            }
            else -> { // string 及其它
                val current = config.optString(key)
                var value by remember(key, version) { mutableStateOf(current) }
                var show by remember(key) { mutableStateOf(!isSensitive(key)) }
                val sensitive = isSensitive(key)
                OutlinedTextField(
                    value = value,
                    onValueChange = { v ->
                        value = v
                        config.put(key, v)
                        onChanged()
                    },
                    label = { Text(title) },
                    enabled = !readonly,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (sensitive && !show) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = if (type == "int") KeyboardOptions(keyboardType = KeyboardType.Number) else KeyboardOptions.Default,
                    trailingIcon = {
                        if (sensitive) {
                            IconButton(onClick = { show = !show }) {
                                Icon(
                                    if (show) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    if (show) "隐藏" else "显示",
                                )
                            }
                        }
                    },
                )
                hint?.let { FieldHint(it) }
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

/** 带 options 的列表：单选下拉或多选 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListOptionsField(
    config: JSONObject,
    key: String,
    meta: JSONObject,
    title: String,
    version: Int,
    onChanged: () -> Unit,
) {
    val options = meta.optJSONArray("options") ?: JSONArray()
    val labels = meta.optJSONArray("labels")
    fun labelOf(i: Int, v: String) = labels?.optString(i)?.ifBlank { v } ?: v
    val current = config.optJSONArray(key)
    val multiple = meta.optString("render_type", "select") != "checkbox" && meta.optBoolean("multiple", meta.optString("render_type", "select") == "select")
    Text(title, style = MaterialTheme.typography.bodyMedium)
    if (meta.optString("render_type") == "checkbox" || options.length() <= 8) {
        FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            for (i in 0 until options.length()) {
                val opt = options.optString(i)
                val selected = current != null && contains(current, opt)
                FilterChip(
                    selected = selected,
                    onClick = {
                        val arr = current ?: JSONArray()
                        if (selected) removeValue(arr, opt) else arr.put(opt)
                        config.put(key, arr)
                        onChanged()
                    },
                    label = { Text(labelOf(i, opt)) },
                )
            }
        }
    } else {
        // 下拉选择（单选/多选简化：多选用弹窗多选）
        var open by remember(key, version) { mutableStateOf(false) }
        TextButton(onClick = { open = true }) {
            Text(if (current != null && current.length() > 0) "已选 ${current.length()} 项，点击修改" else "未选择，点击选择")
        }
        if (open) {
            AlertDialog(
                onDismissRequest = { open = false },
                title = { Text(title) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        options.let { opts ->
                            for (i in 0 until opts.length()) {
                                val opt = opts.optString(i)
                                val selected = current != null && contains(current, opt)
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.Checkbox(
                                        checked = selected,
                                        onCheckedChange = {
                                            val arr = current ?: JSONArray()
                                            if (selected) removeValue(arr, opt) else arr.put(opt)
                                            config.put(key, arr)
                                            onChanged()
                                        },
                                    )
                                    Text(labelOf(i, opt), Modifier.weight(1f))
                                }
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { open = false }) { Text("完成") } },
            )
        }
    }
}

private fun contains(arr: JSONArray, v: String): Boolean {
    for (i in 0 until arr.length()) if (arr.optString(i) == v) return true
    return false
}

private fun removeValue(arr: JSONArray, v: String) {
    for (i in arr.length() - 1 downTo 0) {
        if (arr.optString(i) == v) arr.remove(i)
    }
}

/** 模板列表：平台/提供方等 {config_template} 类型的列表，可增删编辑 */
@Composable
private fun TemplateListField(
    config: JSONObject,
    key: String,
    meta: JSONObject,
    title: String,
    version: Int,
    onChanged: () -> Unit,
) {
    val templates = meta.optJSONObject("config_template") ?: JSONObject()
    val items = config.optJSONArray(key) ?: JSONArray()
    var editingIndex by remember(key, version) { mutableStateOf(-1) }
    var pickTemplate by remember(key, version) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.bodyMedium)
        if (items.length() == 0) {
            Text("（暂无条目）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val label = item.optString("name").ifBlank { item.optString("type") }.ifBlank { item.optString("id") }.ifBlank { "条目 ${i + 1}" }
            Card(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "启用: ${if (item.optBoolean("enable", item.optBoolean("enabled", false))) "是" else "否"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editingIndex = i }) { Icon(Icons.Outlined.Edit, "编辑") }
                    IconButton(onClick = {
                        items.remove(i)
                        config.put(key, items)
                        onChanged()
                    }) { Icon(Icons.Outlined.Delete, "删除", tint = MaterialTheme.colorScheme.error) }
                }
            }
        }
        // 添加
        TextButton(onClick = {
            if (templates.length() > 0) {
                pickTemplate = true
            } else {
                // 无模板：直接添加空对象
                val obj = JSONObject().put("id", uniqueId(items, "item"))
                items.put(obj)
                config.put(key, items)
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
                                // 唯一化 id
                                val baseId = copy.optString("id").ifBlank { tname }
                                copy.put("id", uniqueId(items, baseId))
                                items.put(copy)
                                config.put(key, items)
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

/** 列表条目编辑对话框：按条目现有字段类型推断渲染 */
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

/**
 * 无 schema 时按值类型推断渲染对象字段：布尔→开关、数字→数字框、字符串→输入框（敏感键掩码）。
 * 供提供方/平台表单、列表条目编辑等复用。
 */
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
                    // 数组：按字符串拼接展示
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

/** 字典字段：按当前值推断字段渲染嵌套表单 */
@Composable
private fun DictField(
    config: JSONObject,
    key: String,
    meta: JSONObject,
    title: String,
    version: Int,
    onChanged: () -> Unit,
) {
    // 若字典有 items 元数据则递归；否则按当前值推断
    val nestedMeta = meta.optJSONObject("items")
    val value = config.optJSONObject(key) ?: JSONObject().also { config.put(key, it) }
    var showAdd by remember(key, version) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(start = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        if (nestedMeta != null) {
            nestedMeta.keys().forEach { fk ->
                val fm = nestedMeta.optJSONObject(fk) ?: return@forEach
                SchemaField(value, fk, fm, version, onChanged)
            }
        } else {
            // 推断渲染
            value.keys().forEach { fk ->
                val fv = value.opt(fk)
                val fakeMeta = JSONObject().put("description", fk)
                when (fv) {
                    is Boolean -> fakeMeta.put("type", "bool")
                    is Int, is Long -> fakeMeta.put("type", "int")
                    is Double -> fakeMeta.put("type", "float")
                    else -> fakeMeta.put("type", "string")
                }
                SchemaField(value, fk, fakeMeta, version, onChanged)
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
