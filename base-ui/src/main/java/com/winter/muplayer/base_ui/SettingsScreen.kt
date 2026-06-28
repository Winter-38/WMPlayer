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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.winter.muplayer.core.AppLogger
import com.winter.muplayer.core.SettingsManager

/**
 * 设置页面——包含”播放“、”显示“、”扫描“、”关于“等所有配置项。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: SettingsManager,
    onBack: () -> Unit,
    onRescan: () -> Unit = {},
    onSettingChanged: () -> Unit = {},
    onSetPlayMode: (com.winter.muplayer.model.PlayMode) -> Unit = {},
    cacheInfo: CacheInfo = CacheInfo(),
    onShowPluginManager: () -> Unit = {},
    onCrossfadeChange: (Int) -> Unit = {}
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
                    contentDescription = stringResource(R.string.back)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showLog = !showLog }) {
                Text(if (showLog) stringResource(R.string.hide_log) else stringResource(R.string.debug_log))
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
                item { SectionHeader(stringResource(R.string.section_playback)) }

                item { PlayModeSetting(settings, onSettingChanged, onSetPlayMode) }
                item { CrossfadeSetting(settings, onCrossfadeChange) }
                item { AudioFocusSetting(settings) }

                // ========== 显示主题 ==========
                item { SectionHeader(stringResource(R.string.section_display)) }

                item { ThemeModeSetting(settings, onSettingChanged) }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    item { DynamicColorSetting(settings, onSettingChanged) }
                }
                item { BlurBackgroundSetting(settings, onSettingChanged) }

                // ========== 音乐扫描 ==========
                item { SectionHeader(stringResource(R.string.section_scan)) }

                item { AutoScanSetting(settings) }
                item {
                    SettingsActionItem(
                        title = stringResource(R.string.rescan_music),
                        subtitle = null,
                        onClick = onRescan,
                        showArrow = false
                    )
                }

                // ========== 存储 ==========
                item { SectionHeader(stringResource(R.string.section_storage)) }

                item {
                    SettingsActionItem(
                        title = stringResource(R.string.clear_cache),
                        subtitle = stringResource(R.string.cache_size_prefix, cacheInfo.formattedSize),
                        onClick = cacheInfo.onClearCache,
                        showArrow = false
                    )
                }

                // ========== 扩展 ==========
                item { SectionHeader(stringResource(R.string.section_extensions)) }

                item {
                    SettingsActionItem(
                        title = stringResource(R.string.plugin_manager),
                        subtitle = null,
                        onClick = onShowPluginManager,
                        showArrow = true
                    )
                }

                // ========== 关于 ==========
                item { SectionHeader(stringResource(R.string.section_about)) }

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
private fun PlayModeSetting(
    settings: SettingsManager,
    onSettingChanged: () -> Unit,
    onSetPlayMode: (com.winter.muplayer.model.PlayMode) -> Unit
) {
    var currentMode by remember { mutableStateOf(settings.defaultPlayMode) }
    var expanded by remember { mutableStateOf(false) }
    val modeLabel = when (currentMode) {
        com.winter.muplayer.model.PlayMode.SEQUENTIAL -> stringResource(R.string.sequential)
        com.winter.muplayer.model.PlayMode.SHUFFLE -> stringResource(R.string.shuffle)
        com.winter.muplayer.model.PlayMode.SINGLE_LOOP -> stringResource(R.string.single_loop)
        com.winter.muplayer.model.PlayMode.REPEAT_ALL -> stringResource(R.string.repeat_all)
    }
    val allModes = com.winter.muplayer.model.PlayMode.entries
    val modeNames = mapOf(
        com.winter.muplayer.model.PlayMode.SEQUENTIAL to stringResource(R.string.sequential),
        com.winter.muplayer.model.PlayMode.SHUFFLE to stringResource(R.string.shuffle),
        com.winter.muplayer.model.PlayMode.SINGLE_LOOP to stringResource(R.string.single_loop),
        com.winter.muplayer.model.PlayMode.REPEAT_ALL to stringResource(R.string.repeat_all)
    )

    SettingsClickItem(
        title = stringResource(R.string.default_play_mode),
        subtitle = stringResource(R.string.default_mode_subtitle, modeLabel),
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
                        stringResource(R.string.default_play_mode),
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
                                    onSetPlayMode(mode)
                                    onSettingChanged()
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
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun CrossfadeSetting(settings: SettingsManager, onCrossfadeChange: (Int) -> Unit = {}) {
    var duration by remember { mutableStateOf(settings.crossfadeDurationMs) }
    val label = when {
        duration == 0 -> stringResource(R.string.off)
        duration < 1000 -> "${duration}ms"
        else -> "${duration / 1000}s"
    }

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.crossfade), style = MaterialTheme.typography.bodyLarge)
            Text(label, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = duration.toFloat() / 5000f,
            onValueChange = { duration = (it * 5000).toInt()
                settings.crossfadeDurationMs = duration
                onCrossfadeChange(duration) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.off), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.crossfade_5s), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AudioFocusSetting(settings: SettingsManager) {
    var duck by remember { mutableStateOf(settings.audioFocusDuck) }
    SettingsSwitchItem(
        title = stringResource(R.string.audio_focus),
        subtitle = if (duck) stringResource(R.string.audio_duck) else stringResource(R.string.audio_pause),
        checked = duck,
        onCheckedChange = { duck = it; settings.audioFocusDuck = it }
    )
}

@Composable
private fun ThemeModeSetting(settings: SettingsManager, onSettingChanged: () -> Unit) {
    var mode by remember { mutableStateOf(settings.themeMode) }
    var expanded by remember { mutableStateOf(false) }
    val modeLabel = when (mode) {
        SettingsManager.ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
        SettingsManager.ThemeMode.LIGHT -> stringResource(R.string.theme_light)
        SettingsManager.ThemeMode.DARK -> stringResource(R.string.theme_dark)
    }
    val allModes = SettingsManager.ThemeMode.entries
    val modeNames = mapOf(
        SettingsManager.ThemeMode.SYSTEM to stringResource(R.string.theme_system),
        SettingsManager.ThemeMode.LIGHT to stringResource(R.string.theme_light),
        SettingsManager.ThemeMode.DARK to stringResource(R.string.theme_dark)
    )
    SettingsClickItem(
        title = stringResource(R.string.theme_mode),
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
                        stringResource(R.string.theme_mode),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    allModes.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // === DEBUG ===
                                    android.util.Log.d("WMPlayer_Theme",
                                        "ThemeMode Row click: $item")
                                    AppLogger.i("DEBUG_THEME",
                                        "ThemeMode selected via Row: $item")
                                    mode = item
                                    settings.themeMode = item
                                    onSettingChanged()
                                    expanded = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = item == mode,
                                onClick = {
                                    // === DEBUG + BUG FIX: 补上 onSettingChanged() ===
                                    android.util.Log.d("WMPlayer_Theme",
                                        "ThemeMode RadioButton click: $item")
                                    AppLogger.i("DEBUG_THEME",
                                        "ThemeMode selected via RadioButton: $item")
                                    mode = item
                                    settings.themeMode = item
                                    onSettingChanged()   // ← 这行原来缺失！
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
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun DynamicColorSetting(settings: SettingsManager, onSettingChanged: () -> Unit) {
    var enabled by remember { mutableStateOf(settings.dynamicColorEnabled) }
    SettingsSwitchItem(
        title = stringResource(R.string.dynamic_color),
        subtitle = stringResource(R.string.dynamic_color_subtitle),
        checked = enabled,
        onCheckedChange = { enabled = it; settings.dynamicColorEnabled = it; onSettingChanged() }
    )
}

@Composable
private fun BlurBackgroundSetting(settings: SettingsManager, onSettingChanged: () -> Unit) {
    var enabled by remember { mutableStateOf(settings.blurBackground) }
    SettingsSwitchItem(
        title = stringResource(R.string.blur_background),
        subtitle = stringResource(R.string.blur_background_subtitle),
        checked = enabled,
        onCheckedChange = { enabled = it; settings.blurBackground = it; onSettingChanged() }
    )
}

@Composable
private fun AutoScanSetting(settings: SettingsManager) {
    var enabled by remember { mutableStateOf(settings.autoScanOnStart) }
    SettingsSwitchItem(
        title = stringResource(R.string.auto_scan),
        subtitle = stringResource(R.string.auto_scan_subtitle),
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
        InfoRow(stringResource(R.string.label_app_name), "WinterMuPlayer")
        InfoRow(stringResource(R.string.label_version), "0.5.4-SNAPSHOT")
        InfoRow(stringResource(R.string.label_package), "com.winter.muplayer")
        InfoRow(stringResource(R.string.label_compile_sdk), "API ${Build.VERSION.SDK_INT}")
        InfoRow(stringResource(R.string.label_device), "${Build.MANUFACTURER} ${Build.MODEL}")
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.framework_info),
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
    onClick: () -> Unit,
    showArrow: Boolean = true
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
            if (showArrow) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.execute),
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
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
    val formattedSize: String = "—",
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
                stringResource(R.string.app_log_title, entries.size),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            TextButton(onClick = {
                AppLogger.clear()
                entries.clear()
            }) {
                Text(stringResource(R.string.clear_log), color = MaterialTheme.colorScheme.error)
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