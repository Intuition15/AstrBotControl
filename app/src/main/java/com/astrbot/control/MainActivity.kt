package com.astrbot.control

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.rememberNavController
import com.astrbot.control.ui.AstrBotNav
import com.astrbot.control.ui.theme.AstrBotTheme
import com.astrbot.control.util.CrashReport

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AstrBotTheme {
                val app = application as AstrBotApp
                val navController = rememberNavController()
                var ready by remember { mutableStateOf(false) }
                var crashLog by remember { mutableStateOf<String?>(null) }
                LaunchedEffect(Unit) {
                    app.api.init()
                    crashLog = CrashReport.read(this@MainActivity)
                    ready = true
                }
                if (!ready) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    AstrBotNav(navController, app.api)
                }
                crashLog?.let { log ->
                    CrashDialog(
                        log = log,
                        onKeep = { crashLog = null },
                        onDelete = {
                            CrashReport.clear(this@MainActivity)
                            crashLog = null
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CrashDialog(log: String, onKeep: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("上次应用崩溃") },
        text = {
            Column {
                Text(
                    "已捕获到崩溃信息，请把下面的内容复制发给开发者，或截图：",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        log,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) { Text("删除日志") }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text("保留") }
        },
    )
}
