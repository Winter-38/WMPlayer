package com.winter.muplayer.ui.screens

import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.clickable
import com.winter.muplayer.ui.PluginHost
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
import com.winter.muplayer.config.LiquidGlassBackdrop
import com.winter.muplayer.core.SettingsManager
import kotlin.math.roundToInt
import java.util.Locale

/**
 * 液态玻璃视觉参数（与 styles.css `#playbar` liquid-* 一一对应；仅液态玻璃 liquid 模式生效）：
 * edge/refraction 为 dp 语义（原版示例同源），blurRadiusDp 为模糊半径（毛玻璃/液态玻璃共用），
 * surfaceAlpha 为表面基色不透明度（0..1，越大越不透明），其余强度类无单位。
 */
data class LiquidGlassParams(
    val blurRadiusDp: Float = LiquidGlassBackdrop.DEFAULT_BLUR_RADIUS_DP,
    val edgeWidthDp: Float = LiquidGlassBackdrop.DEFAULT_EDGE_WIDTH_DP,
    val refractionDp: Float = LiquidGlassBackdrop.DEFAULT_REFRACTION_DP,
    val surfaceAlpha: Float = LiquidGlassBackdrop.DEFAULT_SURFACE_ALPHA,
    val specular: Float = LiquidGlassBackdrop.DEFAULT_SPECULAR,
    val shininess: Float = LiquidGlassBackdrop.DEFAULT_SHININESS,
    val rimStrength: Float = LiquidGlassBackdrop.DEFAULT_RIM_STRENGTH,
)

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
    onOpenLogsDir: () -> Unit = {},
    onClearErrorLogs: () -> Unit = {},
    onCrossfadeChange: (Int) -> Unit = {},
    onLanguageChange: () -> Unit = {},
    onReloadConfig: () -> Unit = {},
    // 布局与样式编辑器入口
    onOpenLayoutEditor: () -> Unit = {},
    // 迷你播放栏渲染样式：none（无效果）/ semi-tran（半透明）/ blur（毛玻璃），状态读自 CSS、切换写 CSS
    miniRenderStyle: String = "none",
    onMiniRenderStyleChange: (String) -> Unit = {},
    // 液态玻璃视觉参数：状态读自 CSS（#playbar liquid-*）、滑块松手时写 CSS + 热重载
    liquidGlassParams: LiquidGlassParams = LiquidGlassParams(),
    onLiquidGlassParamsChange: (LiquidGlassParams) -> Unit = {}
) {
    val context = LocalContext.current
    // 插件管理子页面：设置内仅保留入口，点入独立管理界面
    var showPluginManager by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        // 设置页主体：切到插件管理时左滑淡出
        AnimatedVisibility(
            visible = !showPluginManager,
            enter = fadeIn(tween(220)),
            exit = slideOutHorizontally(tween(260)) { -it / 3 } + fadeOut(tween(180)),
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
            item { MiniBlurSetting(miniRenderStyle, onMiniRenderStyleChange) }
            // 毛玻璃 / 液态玻璃模式都可调玻璃参数（模糊度、高光）；折射相关仅液态玻璃显示
            if (miniRenderStyle == "liquid" || miniRenderStyle == "blur") {
                item {
                    LiquidGlassSetting(
                        liquidGlassParams, onLiquidGlassParamsChange,
                        showRefraction = miniRenderStyle == "liquid",
                    )
                }
            }
            item { AdaptiveTintSetting(settings, onSettingChanged) }
            item { ParticleEffectSetting(settings, onSettingChanged) }

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
            item {
                SettingsActionItem(
                    title = stringResource(R.string.layout_editor),
                    subtitle = stringResource(R.string.layout_editor_subtitle),
                    onClick = onOpenLayoutEditor,
                    showArrow = true
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

            item {
                SettingsActionItem(
                    title = stringResource(R.string.clear_error_logs),
                    subtitle = null,
                    onClick = onClearErrorLogs,
                    showArrow = false
                )
            }

            item {
                SettingsActionItem(
                    title = stringResource(R.string.open_error_logs),
                    subtitle = null,
                    onClick = onOpenLogsDir,
                    showArrow = false
                )
            }

            // ========== 语言设置 ==========
            item { SectionHeader(stringResource(R.string.section_language)) }

            item {
                LanguageSetting(settings, onLanguageChange)
            }

            // ========== 插件 ==========
            item { SectionHeader(stringResource(R.string.section_plugins)) }

            item {
                val pluginCount = remember(showPluginManager) {
                    PluginHost.get(context).installedPlugins().size
                }
                SettingsClickItem(
                    title = stringResource(R.string.plugin_manager),
                    subtitle = stringResource(R.string.plugin_manager_subtitle, pluginCount),
                    onClick = { showPluginManager = true },
                )
            }

            // ========== 关于 ==========
            item { SectionHeader(stringResource(R.string.section_about)) }

            item { AboutSection() }
            }
        }
        }

        // 插件管理界面：从右滑入 + 淡入
        AnimatedVisibility(
            visible = showPluginManager,
            enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(220)),
            exit = fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it },
        ) {
            PluginManagerScreen(onBack = { showPluginManager = false })
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
private fun MiniBlurSetting(style: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = listOf(
        "none" to stringResource(R.string.mini_blur_none),
        "semi-tran" to stringResource(R.string.mini_blur_semi),
        "blur" to stringResource(R.string.mini_blur_blur),
        "liquid" to stringResource(R.string.mini_blur_liquid),
    )
    val currentLabel = options.firstOrNull { it.first == style }?.second ?: options[0].second
    SettingsClickItem(
        title = stringResource(R.string.mini_blur_background),
        subtitle = currentLabel,
        onClick = { expanded = true },
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
                        stringResource(R.string.mini_blur_background),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    options.forEach { (value, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onChange(value)
                                    expanded = false
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = value == style,
                                onClick = {
                                    onChange(value)
                                    expanded = false
                                }
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = label,
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

/**
 * 液态玻璃视觉参数设置（液态玻璃 liquid / 毛玻璃 blur 模式显示）：
 * - 模糊度（blur-radius）与高光滑块两模式共用；
 * - 折射相关滑块（边缘隆起/折射强度/表面不透明度）仅液态玻璃模式显示。
 * 拖动只更新本地 state（即时预览数值），松手（onValueChangeFinished）才经 onChange 写 CSS + 热重载，
 * 避免滑块滑动过程中频繁重写文件 / 重建布局导致的卡顿。
 */
@Composable
private fun LiquidGlassSetting(
    params: LiquidGlassParams,
    onChange: (LiquidGlassParams) -> Unit,
    showRefraction: Boolean = true,
) {
    var blurRadius by remember(params) { mutableStateOf(params.blurRadiusDp) }
    var edge by remember(params) { mutableStateOf(params.edgeWidthDp) }
    var refraction by remember(params) { mutableStateOf(params.refractionDp) }
    var surfaceAlpha by remember(params) { mutableStateOf(params.surfaceAlpha) }
    var specular by remember(params) { mutableStateOf(params.specular) }
    var shininess by remember(params) { mutableStateOf(params.shininess) }
    var rim by remember(params) { mutableStateOf(params.rimStrength) }

    fun commit() = onChange(
        LiquidGlassParams(
            blurRadiusDp = blurRadius,
            edgeWidthDp = edge,
            refractionDp = refraction,
            surfaceAlpha = surfaceAlpha,
            specular = specular,
            shininess = shininess,
            rimStrength = rim,
        )
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = stringResource(R.string.liquid_glass_title),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.liquid_glass_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LiquidGlassSlider(
            label = stringResource(R.string.liquid_blur),
            value = blurRadius,
            valueText = "${blurRadius.roundToInt()} dp",
            range = 0f..32f,
            step = 1f,
            onValueChange = { blurRadius = it },
            onValueChangeFinished = ::commit,
        )
        if (showRefraction) {
            LiquidGlassSlider(
                label = stringResource(R.string.liquid_edge),
                value = edge,
                valueText = "${edge.roundToInt()} dp",
                range = 2f..72f,
                step = 1f,
                onValueChange = { edge = it },
                onValueChangeFinished = ::commit,
            )
            LiquidGlassSlider(
                label = stringResource(R.string.liquid_refraction),
                value = refraction,
                valueText = "${refraction.roundToInt()} dp",
                range = 2f..72f,
                step = 1f,
                onValueChange = { refraction = it },
                onValueChangeFinished = ::commit,
            )
            LiquidGlassSlider(
                label = stringResource(R.string.liquid_opacity),
                value = surfaceAlpha,
                valueText = String.format(Locale.US, "%.2f", surfaceAlpha),
                range = 0.05f..0.6f,
                step = 0.05f,
                onValueChange = { surfaceAlpha = it },
                onValueChangeFinished = ::commit,
            )
        }
        LiquidGlassSlider(
            label = stringResource(R.string.liquid_specular),
            value = specular,
            valueText = String.format(Locale.US, "%.2f", specular),
            range = 0f..1.5f,
            step = 0.05f,
            onValueChange = { specular = it },
            onValueChangeFinished = ::commit,
        )
        LiquidGlassSlider(
            label = stringResource(R.string.liquid_shininess),
            value = shininess,
            valueText = shininess.roundToInt().toString(),
            range = 8f..256f,
            step = 1f,
            onValueChange = { shininess = it },
            onValueChangeFinished = ::commit,
        )
        LiquidGlassSlider(
            label = stringResource(R.string.liquid_rim),
            value = rim,
            valueText = String.format(Locale.US, "%.2f", rim),
            range = 0f..1.5f,
            step = 0.05f,
            onValueChange = { rim = it },
            onValueChangeFinished = ::commit,
        )
    }
}

/** 滑块行：标签 + 当前值 + Slider（步进吸附，松手回调） */
@Composable
private fun LiquidGlassSlider(
    label: String,
    value: Float,
    valueText: String,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Slider(
            value = value,
            onValueChange = { raw -> onValueChange((raw / step).roundToInt() * step) },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = range,
        )
    }
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
private fun ParticleEffectSetting(settings: SettingsManager, onSettingChanged: () -> Unit) {
    var enabled by remember { mutableStateOf(settings.particleEffectEnabled) }
    SettingsSwitchItem(
        title = stringResource(R.string.particle_effect),
        subtitle = stringResource(R.string.particle_effect_subtitle),
        checked = enabled,
        onCheckedChange = { enabled = it; settings.particleEffectEnabled = it; onSettingChanged() }
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
    val context = LocalContext.current
    // 版本号从 manifest 读取（packageInfo.versionName），避免硬编码与构建配置漂移
    val versionName = remember {
        try {
            val pm = context.packageManager
            val info = if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, 0)
            }
            info.versionName ?: "—"
        } catch (_: Exception) {
            "—"
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        InfoRow(stringResource(R.string.label_app_name), "WinterMuPlayer")
        InfoRow(stringResource(R.string.label_version), versionName)
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
