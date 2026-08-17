package com.astrbot.control.util

import android.content.Context
import com.astrbot.control.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * AstrBot 配置元数据国际化解析器。
 *
 * 后端会把配置字段的 description/hint/name/labels 转成 i18n 键（如
 * "ai_group.agent_runner.enable.description"），原生 Web 前端用内置翻译表解析。
 * 这里加载官方 zh-CN 翻译表，实现同样的中文展示。
 */
object ConfigI18n {

    private var tree: JSONObject? = null

    /** 从 raw 资源加载翻译表（在 Application 启动时调用一次） */
    fun init(context: Context) {
        if (tree != null) return
        try {
            val text = context.resources.openRawResource(R.raw.config_metadata_zh)
                .bufferedReader().use { it.readText() }
            tree = JSONObject(text)
        } catch (_: Exception) {
            tree = JSONObject()
        }
    }

    /** 判断字符串是否像 i18n 键（小写点分路径，如 xx.yy.zz.description） */
    fun isKey(value: String): Boolean {
        if (value.isBlank() || value.length > 120) return false
        if (!value.contains('.')) return false
        return value.matches(Regex("^[a-z0-9_.]+$"))
    }

    /** 按键在翻译树中取值；找不到返回 null */
    fun resolve(key: String): String? {
        val t = tree ?: return null
        if (key.isBlank()) return null
        var node: Any? = t
        for (p in key.split('.')) {
            node = (node as? JSONObject)?.opt(p) ?: return null
        }
        return when (node) {
            is String -> node
            is JSONArray -> (0 until node.length()).joinToString(", ") { node.optString(it) }
            else -> node?.toString()
        }
    }

    /** 解析 description/name/hint：键→翻译；非键→原文；失败→返回空 */
    fun text(value: String): String? {
        if (value.isBlank()) return null
        return if (isKey(value)) resolve(value) else value
    }

    /** 兜底：取键的最后一段作为可读标签（如 enable.description → enable） */
    fun keyLast(value: String): String {
        if (!value.contains('.')) return value
        val segs = value.split('.')
        // 去掉末尾的 attr（description/hint/labels/name）
        var end = segs.size
        if (end >= 2 && segs[end - 1] in setOf("description", "hint", "labels", "name")) end--
        return segs[end - 1]
    }

    /** 字段标签：优先翻译，其次原文，最后键尾 */
    fun label(meta: JSONObject?, key: String): String {
        val raw = meta?.optString("description").orEmpty().ifBlank { key }
        val resolved = text(raw)
        return resolved ?: if (isKey(raw)) keyLast(raw) else key
    }

    /** 字段提示（hint） */
    fun hint(meta: JSONObject?): String? {
        val raw = meta?.optString("hint").orEmpty()
        if (raw.isBlank()) return null
        return text(raw) ?: keyLast(raw)
    }
}
