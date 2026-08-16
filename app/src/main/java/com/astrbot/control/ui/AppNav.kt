package com.astrbot.control.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.astrbot.control.data.ApiClient
import com.astrbot.control.ui.screens.chat.ChatScreen
import com.astrbot.control.ui.screens.config.ConfigScreen
import com.astrbot.control.ui.screens.config.JsonEditScreen
import com.astrbot.control.ui.screens.connect.ConnectScreen
import com.astrbot.control.ui.screens.cron.CronEditScreen
import com.astrbot.control.ui.screens.cron.CronScreen
import com.astrbot.control.ui.screens.kb.KnowledgeBaseScreen
import com.astrbot.control.ui.screens.logs.LogsScreen
import com.astrbot.control.ui.screens.more.MoreScreen
import com.astrbot.control.ui.screens.personas.PersonasScreen
import com.astrbot.control.ui.screens.platforms.PlatformEditScreen
import com.astrbot.control.ui.screens.platforms.PlatformsScreen
import com.astrbot.control.ui.screens.plugins.PluginDetailScreen
import com.astrbot.control.ui.screens.plugins.PluginMarketScreen
import com.astrbot.control.ui.screens.plugins.PluginsScreen
import com.astrbot.control.ui.screens.providers.ProviderEditScreen
import com.astrbot.control.ui.screens.providers.ProvidersScreen
import com.astrbot.control.ui.screens.sessions.SessionsScreen
import com.astrbot.control.ui.screens.settings.SettingsScreen
import com.astrbot.control.ui.screens.skills.SkillsScreen
import com.astrbot.control.ui.screens.status.StatusScreen
import com.astrbot.control.ui.screens.subagents.SubAgentsScreen
import com.astrbot.control.ui.screens.tools.ToolsScreen
import com.astrbot.control.ui.screens.updates.UpdatesScreen
import com.astrbot.control.ui.screens.apikeys.ApiKeysScreen
import com.astrbot.control.ui.screens.backups.BackupsScreen

object Routes {
    const val CONNECT = "connect"
    const val STATUS = "status"
    const val PLUGINS = "plugins"
    const val CONFIG = "config"
    const val LOGS = "logs"
    const val MORE = "more"

    const val PLUGIN_MARKET = "plugin_market"
    const val PLUGIN_DETAIL = "plugin_detail/{name}"
    const val JSON_EDIT = "json_edit/{key}"
    const val PLATFORMS = "platforms"
    const val PLATFORM_EDIT = "platform_edit/{id}"
    const val PROVIDERS = "providers"
    const val PROVIDER_EDIT = "provider_edit/{id}"
    const val CRON = "cron"
    const val CRON_EDIT = "cron_edit/{id}"
    const val SESSIONS = "sessions"
    const val SKILLS = "skills"
    const val BACKUPS = "backups"
    const val UPDATES = "updates"
    const val API_KEYS = "api_keys"
    const val TOOLS = "tools"
    const val PERSONAS = "personas"
    const val KB = "kb"
    const val SUBAGENTS = "subagents"
    const val CHAT = "chat"
    const val SETTINGS = "settings"

    fun pluginDetail(name: String) = "plugin_detail/${Uri.encode(name)}"
    fun jsonEdit(key: String) = "json_edit/${Uri.encode(key)}"
    fun platformEdit(id: String?) = if (id.isNullOrBlank()) "platform_edit/new" else "platform_edit/${Uri.encode(id)}"
    fun providerEdit(id: String?) = if (id.isNullOrBlank()) "provider_edit/new" else "provider_edit/${Uri.encode(id)}"
    fun cronEdit(id: String?) = if (id.isNullOrBlank()) "cron_edit/new" else "cron_edit/${Uri.encode(id)}"
}

data class TabItem(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    TabItem(Routes.STATUS, "状态", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    TabItem(Routes.PLUGINS, "插件", Icons.Outlined.Extension, Icons.Filled.Extension),
    TabItem(Routes.CONFIG, "配置", Icons.Outlined.Tune, Icons.Filled.Tune),
    TabItem(Routes.LOGS, "日志", Icons.Outlined.Terminal, Icons.Filled.Terminal),
    TabItem(Routes.MORE, "更多", Icons.Outlined.MoreHoriz, Icons.Filled.MoreHoriz),
)

private val tabRoutes = tabs.map { it.route }.toSet()

@Composable
fun AstrBotNav(navController: NavHostController, api: ApiClient) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTab = currentRoute in tabRoutes

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (isTab) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(Routes.STATUS) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    if (currentRoute == tab.route) tab.selectedIcon else tab.icon,
                                    contentDescription = tab.label,
                                )
                            },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (api.token.isBlank()) Routes.CONNECT else Routes.STATUS,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            composable(Routes.CONNECT) { ConnectScreen(navController) }
            composable(Routes.STATUS) { StatusScreen(navController) }
            composable(Routes.PLUGINS) { PluginsScreen(navController) }
            composable(Routes.CONFIG) { ConfigScreen(navController) }
            composable(Routes.LOGS) { LogsScreen() }
            composable(Routes.MORE) { MoreScreen(navController) }

            composable(Routes.PLUGIN_MARKET) { PluginMarketScreen(navController) }
            composable(
                Routes.PLUGIN_DETAIL,
                arguments = listOf(navArgument("name") { type = NavType.StringType }),
            ) { entry ->
                PluginDetailScreen(navController, entry.arguments?.getString("name") ?: "")
            }
            composable(
                Routes.JSON_EDIT,
                arguments = listOf(navArgument("key") { type = NavType.StringType }),
            ) { entry ->
                JsonEditScreen(navController, entry.arguments?.getString("key") ?: "")
            }

            composable(Routes.PLATFORMS) { PlatformsScreen(navController) }
            composable(
                Routes.PLATFORM_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "new" }),
            ) { entry -> PlatformEditScreen(navController, entry.arguments?.getString("id")) }

            composable(Routes.PROVIDERS) { ProvidersScreen(navController) }
            composable(
                Routes.PROVIDER_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "new" }),
            ) { entry -> ProviderEditScreen(navController, entry.arguments?.getString("id")) }

            composable(Routes.CRON) { CronScreen(navController) }
            composable(
                Routes.CRON_EDIT,
                arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "new" }),
            ) { entry -> CronEditScreen(navController, entry.arguments?.getString("id")) }

            composable(Routes.SESSIONS) { SessionsScreen(navController) }
            composable(Routes.SKILLS) { SkillsScreen(navController) }
            composable(Routes.BACKUPS) { BackupsScreen(navController) }
            composable(Routes.UPDATES) { UpdatesScreen(navController) }
            composable(Routes.API_KEYS) { ApiKeysScreen(navController) }
            composable(Routes.TOOLS) { ToolsScreen(navController) }
            composable(Routes.PERSONAS) { PersonasScreen(navController) }
            composable(Routes.KB) { KnowledgeBaseScreen(navController) }
            composable(Routes.SUBAGENTS) { SubAgentsScreen(navController) }
            composable(Routes.CHAT) { ChatScreen(navController) }
            composable(Routes.SETTINGS) { SettingsScreen(navController) }
        }
    }
}
