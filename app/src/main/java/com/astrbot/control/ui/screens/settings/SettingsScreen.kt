package com.astrbot.control.ui.screens.settings

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
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.BuildConfig
import com.astrbot.control.data.Settings
import com.astrbot.control.data.SettingsStore
import com.astrbot.control.ui.Routes
import com.astrbot.control.ui.components.ConfirmDialog
import com.astrbot.control.ui.components.InfoRow
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.SectionTitle
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm

class SettingsVm(
    api: com.astrbot.control.data.ApiClient,
    private val store: SettingsStore,
) : BaseVm(api) {
    var host by mutableStateOf("")
    var port by mutableStateOf("6185")
    var useHttps by mutableStateOf(false)
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var rememberPassword by mutableStateOf(true)
    var connectedUser by mutableStateOf("")
    var showLogoutConfirm by mutableStateOf(false)
    var loaded by mutableStateOf(false)

    fun load() {
        run {
            val s = store.get()
            host = s.host
            port = s.port
            useHttps = s.useHttps
            username = s.username
            password = s.password
            rememberPassword = s.rememberPassword
            connectedUser = s.username
            loaded = true
        }
    }

    fun save() {
        run {
            val current = store.get()
            val s = current.copy(
                host = host, port = port, useHttps = useHttps,
                username = username, password = password,
                rememberPassword = rememberPassword,
            )
            store.save(s)
            api.apply(s)
            showToast("已保存并切换服务器")
        }
    }

    fun logout(onDone: () -> Unit) {
        run {
            store.clearToken()
            api.token = ""
            onDone()
        }
    }
}

@Composable
fun SettingsScreen(navController: NavHostController) {
    val api = rememberApi()
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.astrbot.control.AstrBotApp
    val vm: SettingsVm = viewModel { SettingsVm(api, app.settingsStore) }

    LaunchedEffect(Unit) { if (!vm.loaded) vm.load() }

    ScreenScaffold(title = "连接设置", onBack = { navController.popBackStack() }, vm = vm) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            SectionTitle("当前连接")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    InfoRow("服务器", api.baseUrl)
                    InfoRow("登录用户", vm.connectedUser.ifBlank { "-" })
                    InfoRow("已登录", if (api.token.isNotBlank()) "是" else "否")
                }
            }

            SectionTitle("修改服务器")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = vm.host,
                            onValueChange = { vm.host = it },
                            label = { Text("服务器 IP / 域名") },
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
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                        Text("使用 HTTPS", modifier = Modifier.weight(1f))
                        Switch(checked = vm.useHttps, onCheckedChange = { vm.useHttps = it })
                    }
                    OutlinedTextField(
                        value = vm.username,
                        onValueChange = { vm.username = it },
                        label = { Text("用户名") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = vm.password,
                        onValueChange = { vm.password = it },
                        label = { Text("密码") },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vm.rememberPassword, onCheckedChange = { vm.rememberPassword = it })
                        Text("记住密码")
                    }
                    Text(
                        "注意：修改服务器或用户名密码后需要重新登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { vm.save() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("保存设置") }
                    TextButton(
                        onClick = { navController.navigate(Routes.CONNECT) { popUpTo(Routes.SETTINGS) { inclusive = true } } },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("重新登录") }
                }
            }

            SectionTitle("账户")
            Button(
                onClick = { vm.showLogoutConfirm = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
                Icon(Icons.Outlined.Logout, null, Modifier.height(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("退出登录")
            }

            SectionTitle("关于")
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    InfoRow("应用名称", "AstrBot 控制台")
                    InfoRow("版本", BuildConfig.VERSION_NAME)
                    InfoRow("目标版本", "Android 16 (API 36)")
                    InfoRow("兼容 AstrBot", "v3.5 / v4（控制台 API）")
                }
            }
            Text(
                "本应用直接调用 AstrBot Dashboard 的控制台 API。插件管理使用 /api/plugin/* 兼容接口，其余功能使用 /api/v1/* 接口。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (vm.showLogoutConfirm) {
        ConfirmDialog(
            title = "退出登录",
            text = "确定退出当前 AstrBot 账号吗？",
            confirmText = "退出",
            onConfirm = {
                vm.showLogoutConfirm = false
                vm.logout {
                    navController.navigate(Routes.CONNECT) {
                        popUpTo(Routes.STATUS) { inclusive = true }
                    }
                }
            },
            onDismiss = { vm.showLogoutConfirm = false },
        )
    }
}

