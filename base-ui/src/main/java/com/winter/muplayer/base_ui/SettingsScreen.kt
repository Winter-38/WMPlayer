package com.winter.muplayer.base_ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winter.muplayer.core.AppLogger
import com.winter.muplayer.core.SettingsManager

/**
 * 设置页面 — 包含播放、显示、扫描、关于等所有配置项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsManager,
    onBack: () -> Unit,
    onRescan: () -> Unit = {},
    cacheInfo: CacheInfo = CacheInfo()
) {
    var showLog by remember { mutableStateOf(false) }
    val logEntries by remember { mutableStateOf(AppLogger.getEntries()) }

    // 每 2 秒刷新日志列表
    LaunchedEffect(showLog) {
        while (showLog) {
            kotlinx.coroutines.delay(2000)
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
            TextButton(onClick = { showLog = !showLog }) {
                Text(if (showLog) "隐藏日志" else "调试日志")
            }
        }

        if (showLog) {
            DebugLogPanel()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // ========== 播放设置 ==========
                item { SectionHeader("播放设置") }

                item { PlayModeSetting(settings) }
                item { CrossfadeSetting(settings) }
                item { AudioFocusSetting(settings) }

                // ========== 显示主题 ==========
                item { SectionHeader("显示主题") }

                item { ThemeModeSetting(settings) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    item { DynamicColorSetting(settings) }
                }
                item { BlurBackgroundSetting(settings) }

                // ========== 音乐扫描 ==========
                item { SectionHeader("音乐扫描") }

                item { AutoScanSetting(settings) }
                item {
                    SettingsActionItem(
                        title = "重新扫描音乐库",
                        subtitle = null,
                        onClick = onRescan
                    )
                }

                // ========== 存储 ==========
                item { SectionHeader("存储") }

                item {
                    SettingsActionItem(
                        title = "清除专辑封面缓存",
                        subtitle = "缓存大小：${cacheInfo.formattedSize}",
                        onClick = cacheInfo.onClearCache
                    )
                }

                // ========== 关于 ==========
                item { SectionHeader("关于") }

                item { AboutSection() }
            }
        }
    }
}

// ==================== 各组设置项 ====================

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
private fun PlayModeSetting(settings: SettingsManager) {
    var currentMode by remember { mutableStateOf(settings.defaultPlayMode) }
    var expanded by remember { mutableStateOf(false) }
    val modeLabel = when (currentMode) {
        com.winter.muplayer.model.PlayMode.SEQUENTIAL -> "顺序播放"
        com.winter.muplayer.model.PlayMode.SHUFFLE -> "随机播放"
        com.winter.muplayer.model.PlayMode.SINGLE_LOOP -> "单曲循环"
        com.winter.muplayer.model.PlayMode.REPEAT_ALL -> "列表循环"
    }
    val allModes = com.winter.muplayer.model.PlayMode.entries
    val modeNames = mapOf(
        com.winter.muplayer.model.PlayMode.SEQUENTIAL to "顺序播放",
        com.winter.muplayer.model.PlayMode.SHUFFLE to "随机播放",
        com.winter.muplayer.model.PlayMode.SINGLE_LOOP to "单曲循环",
        com.winter.muplayer.model.PlayMode.REPEAT_ALL to "列表循环"
    )

    SettingsClickItem(
        title = "默认播放模式",
        subtitle = "启动后默认使用「$modeLabel」模式",
        onClick = { expanded = true }
    )
    if (expanded) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { expanded = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "默认播放模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    allModes.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    currentMode = mode
                                    settings.defaultPlayMode = mode
                                    expanded = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = mode == currentMode,
                                onClick = {
                                    currentMode = mode
                                    settings.defaultPlayMode = mode
                                    expanded = false
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = modeNames[mode] ?: mode.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { expanded = false }) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun CrossfadeSetting(settings: SettingsManager) {
    var duration by remember { mutableStateOf(settings.crossfadeDurationMs) }
    val label = when {
        duration == 0 -> "关闭"
        duration < 1000 -> "${duration}ms"
        else -> "${duration / 1000}s"
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("跨fade 淡入淡出", style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = duration.toFloat() / 5000f,
            onValueChange = { duration = (it * 5000).toInt()
                settings.crossfadeDurationMs = duration },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("关闭", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("5s", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AudioFocusSetting(settings: SettingsManager) {
    var duck by remember { mutableStateOf(settings.audioFocusDuck) }
    SettingsSwitchItem(
        title = "音频焦点处理",
        subtitle = if (duck) "来消息时降低音量" else "来消息时暂停播放",
        checked = duck,
        onCheckedChange = { duck = it; settings.audioFocusDuck = it }
    )
}

@Composable
private fun ThemeModeSetting(settings: SettingsManager) {
    var mode by remember { mutableStateOf(settings.themeMode) }
    var expanded by remember { mutableStateOf(false) }
    val modeLabel = when (mode) {
        SettingsManager.ThemeMode.SYSTEM -> "跟随系统"
        SettingsManager.ThemeMode.LIGHT -> "亮色"
        SettingsManager.ThemeMode.DARK -> "暗色"
    }
    val allModes = SettingsManager.ThemeMode.entries
    val modeNames = mapOf(
        SettingsManager.ThemeMode.SYSTEM to "跟随系统",
        SettingsManager.ThemeMode.LIGHT to "亮色",
        SettingsManager.ThemeMode.DARK to "暗色"
    )

    SettingsClickItem(
        title = "主题模式",
        subtitle = modeLabel,
        onClick = { expanded = true }
    )
    if (expanded) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { expanded = false }) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "主题模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    allModes.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    mode = item
                                    settings.themeMode = item
                                    expanded = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = item == mode,
                                onClick = {
                                    mode = item
                                    settings.themeMode = item
                                    expanded = false
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = modeNames[item] ?: item.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { expanded = false }) {
                        Text("关闭")
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicColorSetting(settings: SettingsManager) {
    var enabled by remember { mutableStateOf(settings.dynamicColorEnabled) }
    SettingsSwitchItem(
        title = "动态取色",
        subtitle = "使用壁纸颜色生成主题（Android 12+）",
        checked = enabled,
        onCheckedChange = { enabled = it; settings.dynamicColorEnabled = it }
    )
}

@Composable
private fun BlurBackgroundSetting(settings: SettingsManager) {
    var enabled by remember { mutableStateOf(settings.blurBackground) }
    SettingsSwitchItem(
        title = "封面模糊背景",
        subtitle = "全屏播放时封面图模糊作为背景",
        checked = enabled,
        onCheckedChange = { enabled = it; settings.blurBackground = it }
    )
}

@Composable
private fun AutoScanSetting(settings: SettingsManager) {
    var enabled by remember { mutableStateOf(settings.autoScanOnStart) }
    SettingsSwitchItem(
        title = "启动时自动扫描",
        subtitle = "打开应用后自动扫描本地音乐文件",
        checked = enabled,
        onCheckedChange = { enabled = it; settings.autoScanOnStart = it }
    )
}

@Composable
private fun AboutSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        InfoRow("应用名称", "WinterMuPlayer")
        InfoRow("版本号", "0.5.4-SNAPSHOT")
        InfoRow("包名", "com.winter.muplayer")
        InfoRow("编译 SDK", "API ${Build.VERSION.SDK_INT}")
        InfoRow("设备", "${Build.MANUFACTURER} ${Build.MODEL}")
        Spacer(Modifier.height(8.dp))
        Text(
            text = "基于 Shadow 插件框架 · Jetpack Compose + Material3",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// ==================== 通用小组件 ====================

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String?,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsClickItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SettingsActionItem(
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(
                painterResource(R.drawable.ic_arrow_back),
                contentDescription = "执行",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium)
    }
}

// ==================== 缓存信息数据类 ====================

data class CacheInfo(
    val formattedSize: String = "未知",
    val onClearCache: () -> Unit = {}
)

// ==================== 调试日志面板 ====================

@Composable
private fun DebugLogPanel() {
    val entries = remember { mutableStateListOf<AppLogger.LogEntry>() }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            entries.clear()
            entries.addAll(AppLogger.getEntries())
        }
    }

    LaunchedEffect(Unit) {
        entries.addAll(AppLogger.getEntries())
    }

    Column(modifier = Modifier.fillMaxSize()) {
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = entry.time,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, fontSize = 9.sp
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.width(72.dp)
        )
        Text(
            text = entry.tag,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, fontSize = 9.sp
            ),
            color = levelColor,
            modifier = Modifier.width(64.dp)
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace, fontSize = 9.sp
            ),
            color = levelColor,
            modifier = Modifier.weight(1f)
        )
    }
}