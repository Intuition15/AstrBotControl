package com.astrbot.control.ui.screens.more

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.astrbot.control.ui.Routes
import com.astrbot.control.ui.components.ClickableCard
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.SectionTitle

private data class MoreEntry(val title: String, val desc: String, val route: String, val icon: ImageVector)

private val managementEntries = listOf(
    MoreEntry("平台管理", "配置与启停各聊天平台机器人", Routes.PLATFORMS, Icons.Outlined.Devices),
    MoreEntry("提供方管理", "配置 LLM/模型提供方", Routes.PROVIDERS, Icons.Outlined.Memory),
    MoreEntry("定时任务", "创建与管理 Cron 任务", Routes.CRON, Icons.Outlined.Schedule),
    MoreEntry("会话管理", "查看与调整会话规则", Routes.SESSIONS, Icons.Outlined.Groups),
    MoreEntry("技能管理", "查看与启停技能", Routes.SKILLS, Icons.Outlined.Extension),
    MoreEntry("备份管理", "创建、导入、下载备份", Routes.BACKUPS, Icons.Outlined.Backup),
    MoreEntry("系统更新", "检查更新、升级核心、pip 安装", Routes.UPDATES, Icons.Outlined.SystemUpdate),
    MoreEntry("API 密钥", "创建与管理 API Key", Routes.API_KEYS, Icons.Outlined.Api),
    MoreEntry("工具与 MCP", "工具开关与 MCP 服务器", Routes.TOOLS, Icons.Outlined.Hub),
    MoreEntry("人设管理", "创建与管理人设 (Persona)", Routes.PERSONAS, Icons.Outlined.SmartToy),
    MoreEntry("知识库", "管理与检索知识库", Routes.KB, Icons.Outlined.FolderSpecial),
    MoreEntry("子代理", "子代理 (SubAgent) 配置", Routes.SUBAGENTS, Icons.Outlined.AccountTree),
    MoreEntry("对话测试", "在控制台内与机器人对话", Routes.CHAT, Icons.Outlined.Chat),
)

private val appEntries = listOf(
    MoreEntry("连接设置", "修改服务器地址与登录信息", Routes.SETTINGS, Icons.Outlined.Link),
    MoreEntry("关于", "版本信息与使用说明", Routes.SETTINGS, Icons.Outlined.VerifiedUser),
)

@Composable
fun MoreScreen(navController: NavHostController) {
    ScreenScaffold(title = "更多功能") { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item { SectionTitle("管理") }
            items(managementEntries) { entry ->
                MoreItem(entry) { navController.navigate(entry.route) }
            }
            item { SectionTitle("应用") }
            items(appEntries) { entry ->
                MoreItem(entry) {
                    if (entry.title == "关于") {
                        navController.navigate(Routes.SETTINGS)
                    } else {
                        navController.navigate(entry.route)
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun MoreItem(entry: MoreEntry, onClick: () -> Unit) {
    ClickableCard(onClick = onClick, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                entry.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.size(12.dp))
            Column {
                Text(entry.title, style = MaterialTheme.typography.titleSmall)
                Text(entry.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
