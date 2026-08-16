package com.astrbot.control.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "astrbot_control")

/** 连接配置（服务器地址 + 登录信息 + 会话 Token）。Token 与密码均以 AES/GCM 加密后落盘。 */
data class Settings(
    val host: String = "",
    val port: String = "6185",
    val useHttps: Boolean = false,
    val username: String = "",
    val password: String = "",
    val rememberPassword: Boolean = true,
    val token: String = "",
) {
    /** 拼接后的完整服务器地址，例如 http://192.168.1.10:6185 */
    val baseUrl: String
        get() {
            val scheme = if (useHttps) "https" else "http"
            val h = host.trim().trimEnd('/')
            val p = port.trim().ifBlank { "6185" }
            return if (h.contains("://")) {
                val normalized = if (useHttps) h.replaceFirst(Regex("^http://"), "https://") else h.replaceFirst(Regex("^https://"), "http://")
                "$normalized:$p"
            } else {
                "$scheme://$h:$p"
            }
        }

    companion object {
        val EMPTY = Settings()
    }
}

class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            host = p[Keys.HOST] ?: "",
            port = p[Keys.PORT] ?: "6185",
            useHttps = p[Keys.HTTPS] ?: false,
            username = p[Keys.USERNAME] ?: "",
            password = decryptValue(p[Keys.PASSWORD] ?: ""),
            rememberPassword = p[Keys.REMEMBER] ?: true,
            token = decryptValue(p[Keys.TOKEN] ?: ""),
        )
    }

    suspend fun get(): Settings = settings.first()

    suspend fun save(s: Settings) {
        context.dataStore.edit { p ->
            p[Keys.HOST] = s.host.trim()
            p[Keys.PORT] = s.port.trim().ifBlank { "6185" }
            p[Keys.HTTPS] = s.useHttps
            p[Keys.USERNAME] = s.username
            if (s.rememberPassword && s.password.isNotEmpty()) {
                p[Keys.PASSWORD] = encryptValue(s.password)
            } else {
                p.remove(Keys.PASSWORD)
            }
            p[Keys.REMEMBER] = s.rememberPassword
            if (s.token.isNotEmpty()) {
                p[Keys.TOKEN] = encryptValue(s.token)
            } else {
                p.remove(Keys.TOKEN)
            }
        }
    }

    suspend fun updateToken(token: String) {
        val cur = get()
        save(cur.copy(token = token))
    }

    suspend fun clearToken() {
        val cur = get()
        save(cur.copy(token = ""))
    }

    private suspend fun encryptValue(plain: String): String = withContext(Dispatchers.IO) {
        CryptoStore.encrypt(plain)
    }

    private fun decryptValue(encoded: String): String {
        if (encoded.isEmpty() || !CryptoStore.isEncrypted(encoded)) return encoded
        return CryptoStore.decrypt(encoded)
    }

    private object Keys {
        val HOST = stringPreferencesKey("host")
        val PORT = stringPreferencesKey("port")
        val HTTPS = booleanPreferencesKey("https")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val REMEMBER = booleanPreferencesKey("remember_password")
        val TOKEN = stringPreferencesKey("token")
    }
}
