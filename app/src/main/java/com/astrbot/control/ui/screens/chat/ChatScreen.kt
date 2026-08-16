package com.astrbot.control.ui.screens.chat

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.astrbot.control.ui.components.EmptyHint
import com.astrbot.control.ui.components.ScreenScaffold
import com.astrbot.control.ui.components.rememberApi
import com.astrbot.control.ui.vm.BaseVm
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okhttp3.Call
import org.json.JSONObject

data class ChatMsg(val role: String, val content: String)

class ChatVm(api: com.astrbot.control.data.ApiClient) : BaseVm(api) {
    var sessionId by mutableStateOf<String?>(null)
    var threadId by mutableStateOf<String?>(null)
    var messages by mutableStateOf<List<ChatMsg>>(emptyList())
    var input by mutableStateOf("")
    var busy by mutableStateOf(false)
    var streaming by mutableStateOf(false)
    private var call: Call? = null

    fun init() {
        run {
            val r = api.get("/api/v1/chat/sessions/new")
            if (r.ok) {
                sessionId = r.dataObj?.optString("session_id") ?: r.dataStr
                showToast("会话已创建: ${sessionId ?: "?"}")
            } else error.value = r.message ?: "创建会话失败"
        }
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        input = ""
        messages = messages + ChatMsg("user", text)
        run {
            busy = true
            try {
                val sid = sessionId
                var tid = threadId
                if (sid != null && tid == null) {
                    val tr = api.post("/api/v1/chat/threads", JSONObject().apply {
                        put("session_id", sid)
                        put("parent_message_id", "")
                        put("selected_text", "")
                    })
                    if (tr.ok) {
                        tid = tr.dataObj?.optString("thread_id") ?: tr.dataObj?.optString("id")
                        threadId = tid
                    } else {
                        error.value = tr.message ?: "创建会话线程失败"
                        return@run
                    }
                }
                if (tid == null) {
                    error.value = "无法创建会话线程"
                    return@run
                }
                streaming = true
                call = api.streamSsePost(
                    path = "/api/v1/chat/threads/${java.net.URLEncoder.encode(tid, "UTF-8")}/messages",
                    body = JSONObject().apply {
                        put("message", text)
                        put("enable_streaming", true)
                    },
                    onEvent = { _, data ->
                        viewModelScope.launch {
                            messages = messages + ChatMsg("assistant", data)
                        }
                    },
                    onError = { msg -> viewModelScope.launch { toast.value = "流中断: $msg" } },
                    onClosed = { viewModelScope.launch { streaming = false } },
                )
            } finally {
                busy = false
            }
        }
    }

    fun stop() {
        call?.cancel()
        streaming = false
    }

    fun clear() {
        messages = emptyList()
    }

    override fun onCleared() {
        call?.cancel()
        super.onCleared()
    }
}

@Composable
fun ChatScreen(navController: NavHostController) {
    val api = rememberApi()
    val vm: ChatVm = viewModel { ChatVm(api) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { if (vm.sessionId == null) vm.init() }
    LaunchedEffect(vm.messages.size) {
        if (vm.messages.isNotEmpty()) listState.scrollToItem(vm.messages.size - 1)
    }

    ScreenScaffold(
        title = "对话测试",
        onBack = { navController.popBackStack() },
        vm = vm,
        actions = {
            androidx.compose.material3.TextButton(onClick = { vm.clear() }) { Text("清空") }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (vm.messages.isEmpty()) {
                EmptyHint("输入消息开始与机器人对话")
            } else {
                LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                    itemsIndexed(vm.messages) { _, msg ->
                        ChatBubble(msg)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = vm.input,
                    onValueChange = { vm.input = it },
                    placeholder = { Text("输入消息…") },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                )
                Spacer(Modifier.width(8.dp))
                if (vm.streaming) {
                    FilledTonalIconButton(onClick = { vm.stop() }) {
                        Icon(Icons.Outlined.Stop, "停止")
                    }
                } else {
                    FilledTonalIconButton(onClick = { vm.send() }, enabled = vm.input.isNotBlank() && !vm.busy) {
                        Icon(Icons.Outlined.Send, "发送")
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(msg: ChatMsg) {
    val isUser = msg.role == "user"
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = if (isUser) androidx.compose.foundation.layout.Arrangement.End else androidx.compose.foundation.layout.Arrangement.Start,
    ) {
        Card(
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            ),
        ) {
            Text(
                msg.content,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}
