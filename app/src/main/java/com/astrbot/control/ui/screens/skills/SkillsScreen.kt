package com.astrbot.control.ui.screens.skills

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.JsonView
import com.astrbot.control.ui.components.LoadingBox
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.components.s
import com.astrbot.control.ui.vm.BaseVm
import org.json.JSONArray
import org.json.JSONObject

class SkillsVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var skills by mutableStateOf<JSONArray?>(null)
    var rawJson by mutableStateOf<String?>(null)

    fun load() {
        load {
            val r = api.get("/api/v1/skills")
            if (r.ok) {
                val arr = r.dataArr ?: r.dataObj?.optJSONArray("skills") ?: r.dataObj?.optJSONArray("data")
                if (arr != null) {
                    skills = arr
                } else {
                    rawJson = prettyOf(r.dataObj, r.dataArr)
                }
            } else error.value = r.message ?: "获取技能失败"
        }
    }

    fun toggle(skill: JSONObject, active: Boolean) {
        run {
            val name = skill.s("skill_name").ifBlank { skill.s("name") }
            val r = api.patch("/api/v1/skills/by-name", JSONObject().apply {
                put("skill_name", name)
                put("active", active)
            })
            if (r.ok) {
                skill.put("active", active)
                showToast("已${if (active) "启用" else "停用"} $name")
            } else error.value = r.message ?: "操作失败"
        }
    }

    private fun prettyOf(obj: JSONObject?, arr: JSONArray?): String = when {
        obj != null -> obj.toString(4)
        arr != null -> arr.toString(4)
        else -> "{}"
    }
}

@Composable
fun SkillsScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: SkillsVm = viewModel { SkillsVm(api) }
    val loading by vm.loading.collectAsState()

    LaunchedEffect(Unit) { if (vm.skills == null && vm.rawJson == null) vm.load() }

    ScreenScaffold(
        title = "技能管理",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            IconButton(onClick = { vm.load() }) { Icon(Icons.Outlined.Refresh, "刷新") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LoadingBox(loading)
            val list = vm.skills
            if (list != null) {
                if (list.length() == 0) EmptyHint("暂无技能")
                else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        itemsIndexed(JSONArrayToList(list)) { _, skill ->
                            val name = skill.s("skill_name").ifBlank { skill.s("name") }
                            if (name.isNotBlank()) {
                                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) {
                                            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val desc = skill.s("desc").ifBlank { skill.s("description") }
                                            if (desc.isNotBlank()) {
                                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        Switch(
                                            checked = skill.optBoolean("active", skill.optBoolean("enabled", true)),
                                            onCheckedChange = { vm.toggle(skill, it) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                if (vm.rawJson != null) {
                    JsonView(vm.rawJson!!)
                } else if (!loading) {
                    EmptyHint("暂无技能数据")
                }
            }
        }
    }
}

private fun JSONArrayToList(arr: JSONArray): List<JSONObject> =
    (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }

