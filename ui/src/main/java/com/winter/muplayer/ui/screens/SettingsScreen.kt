package com.winter.muplayer.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import com.winter.muplayer.ui.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    cacheInfo: CacheInfo = CacheInfo(),
    onCrossfadeChange: (Int) -> Unit = {},
    onLanguageChange: () -> Unit = {},
    onReloadConfig: () -> Unit = {}
) {
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
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // ========== 播放设置 ==========
            item { SectionHeader(stringResource(R.string.section_playback)) }

            item { CrossfadeSetting(settings, onCrossfadeChange) }
            item { AudioFocusSetting(settings) }

            // ========== 显示主题 ==========
            item { SectionHeader(stringResource(R.string.section_display)) }

            item { ThemeModeSetting(settings, onSettingChanged) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item { DynamicColorSetting(settings, onSettingChanged) }
            }
            item { BlurBackgroundSetting(settings, onSettingChanged) }
            item { AdaptiveTintSetting(settings, onSettingChanged) }

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
            item {
                SettingsActionItem(
                    title = stringResource(R.string.reload_config),
                    subtitle = null,
                    onClick = onReloadConfig,
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

            // ========== 语言设置 ==========
            item { SectionHeader(stringResource(R.string.section_language)) }

            item {
                LanguageSetting(settings, onLanguageChange)
            }

            // ========== 关于 ==========
            item { SectionHeader(stringResource(R.string.section_about)) }

            item { AboutSection() }
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
                                    // 保留 BUG FIX: 补上 onSettingChanged()
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
private fun AdaptiveTintSetting(settings: SettingsManager, onSettingChanged: () -> Unit) {
    var style by remember { mutableStateOf(settings.adaptiveTintStyle) }
    var expanded by remember { mutableStateOf(false) }
    val styleLabel = when (style) {
        SettingsManager.AdaptiveTintStyle.COLOR -> stringResource(R.string.adaptive_tint_color)
        SettingsManager.AdaptiveTintStyle.INVERT -> stringResource(R.string.adaptive_tint_invert)
        SettingsManager.AdaptiveTintStyle.MONOCHROME -> stringResource(R.string.adaptive_tint_mono)
    }
    val allStyles = SettingsManager.AdaptiveTintStyle.entries
    val styleNames = mapOf(
        SettingsManager.AdaptiveTintStyle.COLOR to stringResource(R.string.adaptive_tint_color),
        SettingsManager.AdaptiveTintStyle.INVERT to stringResource(R.string.adaptive_tint_invert),
        SettingsManager.AdaptiveTintStyle.MONOCHROME to stringResource(R.string.adaptive_tint_mono)
    )

    SettingsClickItem(
        title = stringResource(R.string.adaptive_tint),
        subtitle = styleLabel,
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
                        stringResource(R.string.adaptive_tint),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    allStyles.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    style = item
                                    settings.adaptiveTintStyle = item
                                    onSettingChanged()
                                    expanded = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = item == style,
                                onClick = {
                                    style = item
                                    settings.adaptiveTintStyle = item
                                    onSettingChanged()
                                    expanded = false
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = styleNames[item] ?: item.name,
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

// ==================== 语言选择 ====================

@Composable
private fun LanguageSetting(settings: SettingsManager, onLanguageChange: () -> Unit) {
    var language by remember { mutableStateOf(settings.appLanguage) }
    var expanded by remember { mutableStateOf(false) }
    val languageLabel = when (language) {
        SettingsManager.AppLanguage.SYSTEM -> stringResource(R.string.language_system)
        SettingsManager.AppLanguage.ZH -> stringResource(R.string.language_zh)
        SettingsManager.AppLanguage.EN -> stringResource(R.string.language_en)
    }
    val allLanguages = SettingsManager.AppLanguage.entries

    SettingsClickItem(
        title = stringResource(R.string.language),
        subtitle = languageLabel,
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
                        stringResource(R.string.language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    allLanguages.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    language = item
                                    settings.appLanguage = item
                                    onLanguageChange()
                                    expanded = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = item == language,
                                onClick = {
                                    language = item
                                    settings.appLanguage = item
                                    onLanguageChange()
                                    expanded = false
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (item) {
                                    SettingsManager.AppLanguage.SYSTEM -> stringResource(R.string.language_system)
                                    SettingsManager.AppLanguage.ZH -> stringResource(R.string.language_zh)
                                    SettingsManager.AppLanguage.EN -> stringResource(R.string.language_en)
                                },
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.app_restart_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}