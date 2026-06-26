package com.winter.muplayer.base_ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winter.muplayer.core.AppLogger

/**
 * 设置页面 — 带日志查看面板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    var showLog by remember { mutableStateOf(false) }
    val logEntries by remember { mutableStateOf(AppLogger.getEntries()) }

    // 每 2 秒刷新日志列表
    LaunchedEffect(showLog) {
        while (showLog) {
            kotlinx.coroutines.delay(2000)
            // 触发重组的方式
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // 顶部的返回栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = "返回"
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "设置",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            // Debug 开关
            TextButton(onClick = { showLog = !showLog }) {
                Text(if (showLog) "隐藏日志" else "调试日志")
            }
        }

        if (showLog) {
            DebugLogPanel()
        } else {
            // 占位内容
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_settings),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Text(
                        "设置页面开发中",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "后续将在此处添加音效、主题、缓存管理等选项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 48.dp),
                        softWrap = true
                    )
                }
            }
        }
    }
}

@Composable
private fun DebugLogPanel() {
    val entries = remember { mutableStateListOf<AppLogger.LogEntry>() }

    // 定时刷新
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            entries.clear()
            entries.addAll(AppLogger.getEntries())
        }
    }

    // 首次加载
    LaunchedEffect(Unit) {
        entries.addAll(AppLogger.getEntries())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "应用日志 (${entries.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = {
                AppLogger.clear()
                entries.clear()
            }) {
                Text("清空", color = MaterialTheme.colorScheme.error)
            }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            items(items = entries, key = { "${it.time}_${it.tag}_${it.message.hashCode()}" }) { entry ->
                LogRow(entry)
            }
        }
    }
}

@Composable
private fun LogRow(entry: AppLogger.LogEntry) {
    val levelColor = when {
        entry.message.startsWith("[E]") -> Color(0xFFB3261E)
        entry.message.startsWith("[W]") -> Color(0xFFFF8F00)
        entry.message.startsWith("[I]") -> Color(0xFF1E88E5)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.time,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = entry.tag,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            ),
            color = levelColor,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            ),
            color = levelColor,
            modifier = Modifier.weight(1f),
            maxLines = 3
        )
    }
}
