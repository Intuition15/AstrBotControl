package com.astrbot.control.ui.screens.subagents

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.Routes
import com.astrbot.control.ui.components.JsonView
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm

class SubAgentsVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var config by mutableStateOf<String?>(null)

    fun load() {
        load {
            val r = api.get("/api/v1/subagents/config")
            if (r.ok) {
                config = prettyOf(r.dataObj, r.dataArr)
            } else error.value = r.message ?: "获取子代理配置失败"
        }
    }

    private fun prettyOf(obj: org.json.JSONObject?, arr: org.json.JSONArray?): String = when {
        obj != null -> obj.toString(4)
        arr != null -> arr.toString(4)
        else -> "{}"
    }
}

@Composable
fun SubAgentsScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: SubAgentsVm = viewModel { SubAgentsVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.config == null) vm.load() }

    ScreenScaffold(
        title = "子代理配置",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            androidx.compose.material3.TextButton(onClick = { navController.navigate(Routes.jsonEdit("subagent_config")) }) {
                androidx.compose.material3.Text("编辑 JSON")
            }
        },
    ) { padding ->
        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)
            vm.config?.let { JsonView(it) }
        }
    }
}

