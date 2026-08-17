package com.astrbot.control.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** 统一的 API 返回封装：无论 HTTP 状态如何都会返回，用 [ok] 判断业务是否成功 */
data class ApiData(
    val ok: Boolean,
    val httpCode: Int,
    val message: String?,
    val raw: String,
    val obj: JSONObject?,
    val dataObj: JSONObject?,
    val dataArr: JSONArray?,
    val dataStr: String?,
) {
    val data: Any? get() = dataObj ?: dataArr ?: dataStr

    companion object {
        fun networkError(e: Exception, url: String? = null): ApiData {
            val urlInfo = if (url != null) "（请求地址: $url）" else ""
            return ApiData(false, 0, "网络错误: ${e.message ?: e.javaClass.simpleName}$urlInfo", "", null, null, null, null)
        }
    }
}

class ApiException(message: String) : Exception(message)

/**
 * AstrBot Dashboard API 客户端。
 *
 * 认证方式：Bearer Token（登录后从 data.token 获取）。
 * 兼容 AstrBot v3.5 与 v4：插件管理使用 /api/plugin 系列接口（两版通用），其余功能使用 /api/v1 系列接口。
 */
class ApiClient(private val context: Context, private val store: SettingsStore) {

    @Volatile var baseUrl: String = "http://127.0.0.1:6185"
    @Volatile var token: String = ""

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val streamHttp = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** 从本地存储加载连接配置 */
    suspend fun init() = withContext(Dispatchers.IO) {
        val s = store.get()
        baseUrl = s.baseUrl
        token = s.token
    }

    fun apply(settings: Settings) {
        baseUrl = settings.baseUrl
        token = settings.token
    }

    // ---------------- 基础请求 ----------------

    private fun builder(path: String, query: Map<String, String> = emptyMap()): Request.Builder {
        val urlBuilder = "$baseUrl$path".toHttpUrlOrNull()?.newBuilder()
            ?: throw ApiException("无效的服务器地址: $baseUrl$path")
        query.forEach { (k, v) -> urlBuilder.addQueryParameter(k, v) }
        val rb = Request.Builder().url(urlBuilder.build())
        if (token.isNotBlank()) rb.header("Authorization", "Bearer $token")
        rb.header("Accept-Language", "zh-CN")
        rb.header("Accept", "application/json")
        return rb
    }

    private fun jsonBody(body: Any?): RequestBody {
        val text = when (body) {
            null -> "{}"
            is String -> body
            is JSONObject, is JSONArray -> body.toString()
            else -> body.toString()
        }
        return text.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
    }

    private suspend fun execute(req: Request): ApiData = withContext(Dispatchers.IO) {
        try {
            http.newCall(req).execute().use { resp ->
                val raw = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                parseResponse(resp.code, raw)
            }
        } catch (e: Exception) {
            ApiData.networkError(e, req.url.toString())
        }
    }

    private fun parseResponse(code: Int, raw: String): ApiData {
        var obj: JSONObject? = null
        var dataObj: JSONObject? = null
        var dataArr: JSONArray? = null
        var dataStr: String? = null
        var message: String? = null
        var ok = code in 200..299
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty()) {
            try {
                if (trimmed.startsWith("{")) {
                    obj = JSONObject(trimmed)
                    if (obj.has("status")) {
                        message = obj.optString("message").ifBlank { null }
                        val status = obj.optString("status")
                        ok = status == "ok" || status == "success" || status == "warning"
                        when (val d = obj.opt("data")) {
                            is JSONObject -> dataObj = d
                            is JSONArray -> dataArr = d
                            JSONObject.NULL -> {}
                            null -> {}
                            else -> dataStr = d.toString()
                        }
                    } else {
                        dataObj = obj
                    }
                } else if (trimmed.startsWith("[")) {
                    dataArr = JSONArray(trimmed)
                } else {
                    dataStr = trimmed
                }
            } catch (_: Exception) {
                // 非 JSON 内容，保持 raw
            }
        }
        if (!ok && message.isNullOrBlank()) {
            message = when (code) {
                401 -> "未授权或登录已过期"
                403 -> "没有权限执行此操作"
                404 -> "接口不存在（服务器版本可能过旧）"
                else -> "请求失败 (HTTP $code)"
            }
        }
        return ApiData(ok, code, message, raw, obj, dataObj, dataArr, dataStr)
    }

    suspend fun get(path: String, query: Map<String, String> = emptyMap()): ApiData =
        execute(builder(path, query).get().build())

    suspend fun post(path: String, body: Any? = null, query: Map<String, String> = emptyMap()): ApiData =
        execute(builder(path, query).post(jsonBody(body)).build())

    suspend fun put(path: String, body: Any? = null, query: Map<String, String> = emptyMap()): ApiData =
        execute(builder(path, query).put(jsonBody(body)).build())

    suspend fun patch(path: String, body: Any? = null, query: Map<String, String> = emptyMap()): ApiData =
        execute(builder(path, query).patch(jsonBody(body)).build())

    suspend fun delete(path: String, body: Any? = null, query: Map<String, String> = emptyMap()): ApiData =
        execute(builder(path, query).delete(if (body == null) null else jsonBody(body)).build())

    /** 通用文件上传（multipart/form-data） */
    suspend fun upload(
        path: String,
        file: File,
        partName: String = "file",
        extraForm: Map<String, String> = emptyMap(),
    ): ApiData = withContext(Dispatchers.IO) {
        try {
            val mime = when (file.extension.lowercase()) {
                "zip" -> "application/zip"
                "json" -> "application/json"
                "py" -> "text/x-python"
                "md" -> "text/markdown"
                "txt" -> "text/plain"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "application/octet-stream"
            }
            val mb = MultipartBody.Builder().setType(MultipartBody.FORM)
            extraForm.forEach { (k, v) -> mb.addFormDataPart(k, v) }
            mb.addFormDataPart(partName, file.name, file.asRequestBody(mime.toMediaTypeOrNull()))
            val req = builder(path).post(mb.build()).build()
            http.newCall(req).execute().use { resp ->
                val raw = try { resp.body?.string() ?: "" } catch (_: Exception) { "" }
                parseResponse(resp.code, raw)
            }
        } catch (e: Exception) {
            ApiData.networkError(e)
        }
    }

    /** 下载二进制内容（如备份文件） */
    suspend fun downloadBytes(path: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            http.newCall(builder(path).get().build()).execute().use { resp ->
                if (!resp.isSuccessful) null else resp.body?.bytes()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 从中转站/OpenAI 兼容接口获取可用模型列表（GET {base}/models，Bearer 认证）。
     * 依次尝试 {base}/models 与 {base}/v1/models。
     */
    suspend fun fetchOpenAIModels(baseUrl: String, apiKey: String): List<String> =
        withContext(Dispatchers.IO) {
            val candidates = listOf(
                baseUrl.trim().trimEnd('/') + "/models",
                baseUrl.trim().trimEnd('/') + "/v1/models",
            )
            for (url in candidates) {
                val models: List<String>? = try {
                    val rb = Request.Builder().url(url)
                    if (apiKey.isNotBlank()) rb.header("Authorization", "Bearer $apiKey")
                    http.newCall(rb.get().build()).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            null
                        } else {
                            val body = try { resp.body?.string() } catch (_: Exception) { null }
                            val arr = body?.let { runCatching { JSONObject(it).optJSONArray("data") }.getOrNull() }
                            arr?.let { a ->
                                (0 until a.length()).mapNotNull {
                                    a.optJSONObject(it)?.optString("id")?.ifBlank { null }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    null
                }
                if (models != null && models.isNotEmpty()) return@withContext models
            }
            emptyList()
        }

    /**
     * SSE 流式读取（如实时日志）。返回 Call 以便调用方 cancel。
     */
    fun streamSse(
        path: String,
        query: Map<String, String> = emptyMap(),
        extraHeaders: Map<String, String> = emptyMap(),
        onEvent: (eventId: String, data: String) -> Unit,
        onError: (String) -> Unit,
        onClosed: () -> Unit,
    ): Call {
        val rb = builder(path, query)
        extraHeaders.forEach { (k, v) -> rb.header(k, v) }
        val call = streamHttp.newCall(rb.build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) onError(e.message ?: "连接失败")
                onClosed()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val source = it.body?.source()
                    if (source == null) {
                        onClosed()
                        return
                    }
                    var lastId: String? = null
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.startsWith("id:") -> lastId = line.substring(3).trim()
                                line.startsWith("data:") -> {
                                    val payload = line.substring(5).trim()
                                    if (payload.isNotEmpty()) onEvent(lastId ?: "", payload)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (!call.isCanceled()) onError(e.message ?: "流已中断")
                    } finally {
                        onClosed()
                    }
                }
            }
        })
        return call
    }

    /**
     * POST 版 SSE 流式读取（如对话回复）。返回 Call 以便调用方 cancel。
     */
    fun streamSsePost(
        path: String,
        body: Any? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        onEvent: (eventId: String, data: String) -> Unit,
        onError: (String) -> Unit,
        onClosed: () -> Unit,
    ): Call {
        val rb = builder(path)
        extraHeaders.forEach { (k, v) -> rb.header(k, v) }
        rb.post(jsonBody(body))
        val call = streamHttp.newCall(rb.build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) onError(e.message ?: "连接失败")
                onClosed()
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val source = it.body?.source()
                    if (source == null) {
                        onClosed()
                        return
                    }
                    var lastId: String? = null
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            when {
                                line.startsWith("id:") -> lastId = line.substring(3).trim()
                                line.startsWith("data:") -> {
                                    val payload = line.substring(5).trim()
                                    if (payload.isNotEmpty()) onEvent(lastId ?: "", payload)
                                }
                            }
                        }
                    } catch (e: IOException) {
                        if (!call.isCanceled()) onError(e.message ?: "流已中断")
                    } finally {
                        onClosed()
                    }
                }
            }
        })
        return call
    }

    // ---------------- 业务方法 ----------------

    /** 探测服务器是否可达（无需登录） */
    suspend fun testConnection(): ApiData = get("/api/auth/setup-status")

    /** 登录，返回的 ApiData.dataObj 中带 token */
    suspend fun login(username: String, password: String, code: String? = null): ApiData {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
            if (!code.isNullOrBlank()) put("code", code)
            put("trust_device_flag", false)
        }
        return post("/api/auth/login", body)
    }

    /** 获取插件列表（兼容 v3.5 / v4） */
    suspend fun getPlugins(): ApiData = get("/api/plugin/get")

    /** 启用/停用插件 */
    suspend fun setPluginEnabled(name: String, enabled: Boolean): ApiData =
        post(if (enabled) "/api/plugin/on" else "/api/plugin/off", JSONObject().put("name", name))

    suspend fun uninstallPlugin(name: String): ApiData =
        post("/api/plugin/uninstall", JSONObject().put("name", name))

    suspend fun updatePlugin(name: String): ApiData =
        post("/api/plugin/update", JSONObject().put("name", name))

    suspend fun reloadPlugin(name: String): ApiData =
        post("/api/plugin/reload", JSONObject().put("name", name))

    /** 通过 Git 仓库地址安装插件 */
    suspend fun installPluginByUrl(url: String, ignoreVersionCheck: Boolean = false): ApiData =
        post(
            "/api/plugin/install",
            JSONObject().apply {
                put("url", url)
                put("ignore_version_check", ignoreVersionCheck)
            }
        )

    /** 上传插件压缩包安装 */
    suspend fun installPluginByFile(file: File): ApiData =
        upload("/api/plugin/install-upload", file)

    /** 插件市场列表 */
    suspend fun getPluginMarket(forceRefresh: Boolean = false): ApiData =
        get("/api/plugin/market_list", mapOf("force_refresh" to forceRefresh.toString()))

    /** 插件市场安装（按市场条目安装） */
    suspend fun installMarketPlugin(body: JSONObject): ApiData =
        post("/api/plugin/install", body)

    /** 失败插件列表 */
    suspend fun getFailedPlugins(): ApiData = get("/api/plugin/source/get-failed-plugins")

    /** 插件 README */
    suspend fun getPluginReadme(name: String): ApiData =
        get("/api/plugin/readme", mapOf("name" to name))

    /** 插件自定义配置（v4: /api/v1/plugins/{id}/config） */
    suspend fun getPluginConfig(name: String): ApiData =
        get("/api/v1/plugins/${java.net.URLEncoder.encode(name, "UTF-8")}/config")

    suspend fun savePluginConfig(name: String, config: JSONObject): ApiData =
        put("/api/v1/plugins/${java.net.URLEncoder.encode(name, "UTF-8")}/config", config)

    /** 插件源 */
    suspend fun getPluginSources(): ApiData = get("/api/v1/plugin-sources")
    suspend fun savePluginSources(sources: JSONObject): ApiData = put("/api/v1/plugin-sources", sources)
}
