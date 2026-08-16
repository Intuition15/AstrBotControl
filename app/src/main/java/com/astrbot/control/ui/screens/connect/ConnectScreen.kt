package com.astrbot.control.ui.screens.connect

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.data.Settings
import com.astrbot.control.data.SettingsStore
import com.astrbot.control.ui.Routes
import com.astrbot.control.ui.components.FormField
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm
import com.astrbot.control.util.NetSecurity

class ConnectVm(
    api: com.astrbot.control.data.ApiClient,
    private val store: SettingsStore,
) : BaseVm(api) {
    var host by mutableStateOf("")
    var port by mutableStateOf("6185")
    var useHttps by mutableStateOf(false)
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var rememberPassword by mutableStateOf(true)
    var totpCode by mutableStateOf("")
    var totpRequired by mutableStateOf(false)
    var testing by mutableStateOf(false)
    var testInfo by mutableStateOf<String?>(null)
    var loggingIn by mutableStateOf(false)
    var loginHint by mutableStateOf<String?>(null)

    fun loadSaved() {
        run {
            val s = store.get()
            host = s.host
            port = s.port
            useHttps = s.useHttps
            username = s.username
            password = s.password
            rememberPassword = s.rememberPassword
            // 同步当前表单到客户端，避免首次使用空地址
            applyPending(s.token)
        }
    }

    /** 把界面上刚填写的服务器地址同步到 API 客户端（修复使用旧地址/空地址的问题） */
    private fun applyPending(token: String = api.token) {
        api.apply(
            Settings(
                host = host, port = port, useHttps = useHttps,
                username = username, password = password,
                rememberPassword = rememberPassword, token = token,
            )
        )
    }

    /** 公网地址提示（仅提示，不拦截：AstrBot 控制台本身仅支持 HTTP 直连） */
    private fun publicAddressHint(): String? {
        if (host.isNotBlank() && NetSecurity.isPublicAddress(host) && !useHttps) {
            return "当前为公网地址 + HTTP 明文连接，凭据可能被窃听；\n" +
                "如服务器支持，建议在反向代理层配置 HTTPS，或改用 VPN/内网穿透。"
        }
        return null
    }

    fun testConnection() {
        run {
            try {
                testing = true
                testInfo = null
                applyPending()
                loginHint = "正在连接 ${api.baseUrl} …"
                val r = api.testConnection()
                loginHint = null
                testInfo = if (r.ok) {
                    val setup = r.dataObj?.optBoolean("setup_required") ?: false
                    if (setup) "✓ 服务器可达（尚未完成初始化设置）" else "✓ 服务器可达"
                } else {
                    "✗ ${r.message ?: "连接失败"}"
                }
            } finally {
                testing = false
            }
        }
    }

    fun login(onSuccess: () -> Unit) {
        run {
            try {
                loggingIn = true
                applyPending()
                loginHint = "已发送登录请求到 ${api.baseUrl}，正在等待响应…"
                val r = api.login(username, password, if (totpRequired) totpCode else null)
                loginHint = null
                if (r.ok) {
                    val token = r.dataObj?.optString("token") ?: ""
                    if (token.isNotBlank()) {
                        val s = Settings(
                            host = host, port = port, useHttps = useHttps,
                            username = username, password = password,
                            rememberPassword = rememberPassword, token = token,
                        )
                        api.apply(s)
                        store.save(s)
                        onSuccess()
                    } else {
                        error.value = "登录成功但未返回 Token（服务器响应: ${r.raw.take(200)}）"
                    }
                } else {
                    if (r.dataObj?.optBoolean("totp_required") == true) {
                        totpRequired = true
                    } else {
                        totpRequired = false
                        error.value = (r.message ?: "登录失败") +
                            if (r.httpCode > 0) "（HTTP ${r.httpCode}）" else ""
                    }
                }
            } finally {
                loggingIn = false
            }
        }
    }
}

@Composable
fun ConnectScreen(navController: NavHostController) {
    val api = rememberApi()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.astrbot.control.AstrBotApp
    val vm: ConnectVm = viewModel { ConnectVm(api, app.settingsStore) }
    LaunchedEffect(Unit) { vm.loadSaved() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(
            Icons.Outlined.CastConnected,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text("AstrBot 控制台", style = MaterialTheme.typography.headlineSmall)
        Text(
            "连接你的 AstrBot 服务器，管理插件、配置与所有功能",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = vm.host,
                        onValueChange = { vm.host = it },
                        label = { Text("服务器 IP / 域名") },
                        placeholder = { Text("192.168.1.100") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    Spacer(Modifier.size(8.dp))
                    OutlinedTextField(
                        value = vm.port,
                        onValueChange = { vm.port = it.filter { c -> c.isDigit() } },
                        label = { Text("端口") },
                        modifier = Modifier.width(96.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("使用 HTTPS", modifier = Modifier.weight(1f))
                    Switch(checked = vm.useHttps, onCheckedChange = { vm.useHttps = it })
                }
                FormField(vm.username, { vm.username = it }, "用户名")
                FormField(vm.password, { vm.password = it }, "密码", password = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = vm.rememberPassword, onCheckedChange = { vm.rememberPassword = it })
                    Text("记住密码")
                }
                if (vm.totpRequired) {
                    FormField(vm.totpCode, { vm.totpCode = it }, "TOTP 两步验证码", keyboard = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (vm.testInfo != null) {
                    Text(
                        vm.testInfo!!,
                        color = if (vm.testInfo!!.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                val vmError by vm.error.collectAsState()
                if (vmError != null) {
                    Text(
                        vmError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (vm.loginHint != null) {
                    Text(
                        vm.loginHint!!,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(
                        onClick = { vm.testConnection() },
                        enabled = !vm.testing && !vm.loggingIn,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (vm.testing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Text("测试连接")
                        }
                    }
                    Button(
                        onClick = {
                            vm.login {
                                navController.navigate(Routes.STATUS) {
                                    popUpTo(Routes.CONNECT) { inclusive = true }
                                }
                            }
                        },
                        enabled = !vm.loggingIn && !vm.testing && vm.host.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        if (vm.loggingIn) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        } else {
                            Text("登录")
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        if (vm.host.isNotBlank() && NetSecurity.isPublicAddress(vm.host)) {
            Card(
                Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
            ) {
                Text(
                    "ℹ 当前为公网地址：\n" +
                        "• AstrBot 控制台本身仅支持 HTTP 直连，公网明文传输存在被窃听风险；\n" +
                        "• 建议在服务器用反向代理（如 Caddy/Nginx）配置 HTTPS 后，在 App 中勾选「使用 HTTPS」；\n" +
                        "• 请务必设置强密码，并建议开启控制台两步验证、限制管理端口来源 IP。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "提示：AstrBot 控制台（Dashboard）默认端口为 6185。\n" +
                "登录用户名/密码与浏览器打开 http://IP:端口 时的账号一致。\n" +
                "如开启了 API 密钥，也可在「更多 → API 密钥」中查看。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
    }
}
