package com.winter.muplayer.ui.activity

import android.graphics.Bitmap
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import com.winter.muplayer.config.SlotContext
import com.winter.muplayer.config.SlotRenderer
import com.winter.muplayer.config.ComponentEntry
import com.winter.muplayer.config.CssRuleTable
import com.winter.muplayer.config.StyleConfigLoader
import com.winter.muplayer.config.LiquidGlassBackdrop
import com.winter.muplayer.config.parseCssNumber
import com.winter.muplayer.config.parseCssDp
import com.winter.muplayer.ui.PluginUiOverlay
import com.winter.muplayer.ui.components.registerBuiltInComponents
import com.winter.muplayer.ui.components.ParticleBurstHost
import com.winter.muplayer.ui.components.ParticleBurstHostState
import com.winter.muplayer.ui.components.ParticleAlertDialog
import com.winter.muplayer.ui.components.ParticleLayer
import com.winter.muplayer.ui.components.ParticleModalSheet
import com.winter.muplayer.ui.components.LocalParticleBurstHost
import com.winter.muplayer.ui.components.LocalParticleBurstEnabled
import com.winter.muplayer.ui.components.LocalParticleBurstStyle
import com.winter.muplayer.ui.components.LocalParticleColorOverride
import com.winter.muplayer.ui.components.particleStyleFromSettings
import com.winter.muplayer.ui.components.rememberFingerBurst
import com.winter.muplayer.ui.components.RenderedColorRegistry
import com.winter.muplayer.ui.components.LocalRenderedColorRegistry
import com.winter.muplayer.ui.components.globalTapParticles
import com.winter.muplayer.ui.R
import com.winter.muplayer.ui.browser.TrackRow
import com.winter.muplayer.ui.components.getAlbumArtUri
import com.winter.muplayer.ui.components.cacheCoverFile
import com.winter.muplayer.ui.components.trimCoverCache
import com.winter.muplayer.ui.browser.LocalBrowserState
import com.winter.muplayer.ui.browser.MusicBrowserState
import com.winter.muplayer.ui.screens.SettingsScreen
import com.winter.muplayer.ui.screens.LayoutEditorScreen
import com.winter.muplayer.ui.theme.AppTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Size
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.core.QueueEntry
import com.winter.muplayer.core.SettingsManager
import com.winter.muplayer.core.scanner.LocalMusicScanner
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// ==================== 主界面 Activity ====================

class MusicUIActivity : ComponentActivity() {

    private lateinit var musicPlayerCore: MusicPlayerCore

    /** 音频读取权限是否已授予（Compose 状态：授权后触发音乐列表重新加载） */
    private val audioPermissionGranted = mutableStateOf(false)

    private fun hasAudioPermission(): Boolean = when {
        Build.VERSION.SDK_INT >= 33 ->
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        else ->
            ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        val permission = if (Build.VERSION.SDK_INT >= 33) {
            android.Manifest.permission.READ_MEDIA_AUDIO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        requestPermissionLauncher.launch(permission)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // 无论授予与否都更新状态：授予后 MusicPlayerApp 的 LaunchedEffect
        // 会因状态变化而重新执行，从而自动加载本地音乐（修复首次启动
        // 未授权时授权后不扫描的问题）。
        audioPermissionGranted.value = granted
        if (!granted) {
            Toast.makeText(this, getString(R.string.audio_permission_required), Toast.LENGTH_SHORT).show()
        }
        // 音频权限就绪后，顺手请求通知权限（Android 13+ 媒体通知需要）
        if (granted) {
            requestNotificationPermissionIfNeeded()
        }
    }

    /** 通知权限请求（Android 13+ 显示媒体通知用；拒绝时后台播放仍工作，只是通知不显示） */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 无需额外处理 */ }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onKeyLongPress(keyCode: Int, event: KeyEvent?): Boolean {
        return super.onKeyLongPress(keyCode, event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 记录当前权限状态；未授权则弹出系统授权框（API 33+ 用
        // READ_MEDIA_AUDIO，API 24-32 用 READ_EXTERNAL_STORAGE）。
        audioPermissionGranted.value = hasAudioPermission()
        if (!audioPermissionGranted.value) {
            requestAudioPermission()
        } else {
            // 音频权限已就绪，直接请求通知权限（Android 13+）
            requestNotificationPermissionIfNeeded()
        }

        musicPlayerCore = MusicPlayerCore.getInstance(applicationContext)
        android.util.Log.d("WMPlayer-Perf", "获取播放器实例完成")
        com.winter.muplayer.core.AppLogger.i("UI", "MusicUIActivity.onCreate")

        // setContent 之前同步加载缓存（首帧即用真实配置）；缓存不存在时回退后台线程
        if (!com.winter.muplayer.config.ConfigPreload.loadIfCached(applicationContext)) {
            com.winter.muplayer.config.ConfigPreload.start(applicationContext)
        }
        android.util.Log.d("WMPlayer-Perf", "配置加载完成")

        setContent {
            android.util.Log.d("WMPlayer-Perf", "setContent 开始")
            val settings = musicPlayerCore.settings
            var currentThemeMode by remember { mutableStateOf(settings.themeMode) }
            var currentDynamicColor by remember { mutableStateOf(settings.dynamicColorEnabled) }
            var currentBlurBg by remember { mutableStateOf(settings.blurBackground) }
            var currentAdaptiveTintStyle by remember { mutableStateOf(settings.adaptiveTintStyle) }
            var currentParticleBurst by remember { mutableStateOf(settings.particleEffectEnabled) }
            var currentParticleStyle by remember { mutableStateOf(settings.particleStyle) }
            var currentParticleColorMode by remember { mutableStateOf(settings.particleColorMode) }
            var currentParticleColor by remember { mutableStateOf(settings.particleColor) }

            // 全局粒子宿主：在根部创建并提供给 CompositionLocal，
            // 所有按钮（ParticleBurstBox）才能读到并发射粒子
            val scope = rememberCoroutineScope()
            val particleBurstHost = remember { ParticleBurstHostState(scope) }
            // 渲染色注册表：全局点击粒子据此自动取点击位置的渲染色
            val renderedColorRegistry = remember { RenderedColorRegistry() }

            CompositionLocalProvider(
                LocalParticleBurstHost provides particleBurstHost,
                LocalParticleBurstEnabled provides currentParticleBurst,
                LocalParticleBurstStyle provides particleStyleFromSettings(currentParticleStyle),
                LocalParticleColorOverride provides
                    (if (currentParticleColorMode == 1) Color(currentParticleColor) else null),
                LocalRenderedColorRegistry provides renderedColorRegistry,
            ) {
            AppTheme(
                darkTheme = when (currentThemeMode) {
                    com.winter.muplayer.core.SettingsManager.ThemeMode.SYSTEM ->
                        isSystemInDarkTheme()
                    com.winter.muplayer.core.SettingsManager.ThemeMode.DARK -> true
                    com.winter.muplayer.core.SettingsManager.ThemeMode.LIGHT -> false
                },
                dynamicColor = currentDynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 全局点击粒子层挂在根 Box：任意位置点击都喷粒子（不消费事件、不拦截点击）
                    Box(Modifier.fillMaxSize().globalTapParticles(particleBurstHost)) {
                        MusicPlayerApp(
                            musicPlayerCore = musicPlayerCore,
                            hasAudioPermission = audioPermissionGranted.value,
                            blurBackground = currentBlurBg,
                            adaptiveTintStyle = currentAdaptiveTintStyle,
                            onSettingChanged = {
                                currentThemeMode = settings.themeMode
                                currentDynamicColor = settings.dynamicColorEnabled
                                currentBlurBg = settings.blurBackground
                                currentAdaptiveTintStyle = settings.adaptiveTintStyle
                                currentParticleBurst = settings.particleEffectEnabled
                                currentParticleStyle = settings.particleStyle
                                currentParticleColorMode = settings.particleColorMode
                                currentParticleColor = settings.particleColor
                            })
                        // 全局粒子层：挂载在最上层，粒子可遮挡其他按钮（仅视觉）
                        ParticleBurstHost(hostState = particleBurstHost, modifier = Modifier.matchParentSize())

                        // 插件页面/widget 浮层（最上层）
                        PluginUiOverlay()
                    }
                }
            }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // 从系统设置授权后返回应用时刷新状态，同样触发重新扫描
        val granted = hasAudioPermission()
        if (granted != audioPermissionGranted.value) {
            audioPermissionGranted.value = granted
        }
    }
}

// ==================== 主界面组件 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(
    musicPlayerCore: MusicPlayerCore,
    hasAudioPermission: Boolean = true,
    blurBackground: Boolean = false,
    adaptiveTintStyle: com.winter.muplayer.core.SettingsManager.AdaptiveTintStyle =
        com.winter.muplayer.core.SettingsManager.AdaptiveTintStyle.MONOCHROME,
    onSettingChanged: () -> Unit = {}
) {
    android.util.Log.d("WMPlayer-Perf", "MusicPlayerApp 开始组合")
    val playerState by musicPlayerCore.playerState.collectAsState()
    val playMode by musicPlayerCore.playMode.collectAsState()
    val queue by musicPlayerCore.queueManager.queue.collectAsState()
    val currentIndex by musicPlayerCore.queueManager.currentIndex.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val coverCache = remember { mutableStateMapOf<Long, String>() }

    var showSettings by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showLayoutEditor by remember { mutableStateOf(false) }

    // 布局调试开关：true 时 SlotRenderer 显示 slot/组件之间的边界（调试布局用，完成后请改回 false）
    val isDebug = false

    val safeMode by remember { mutableStateOf(false) }
    val pluginColors by remember { mutableStateOf(emptyMap<String, Any>()) }

    var showSearchScreen by remember { mutableStateOf(false) }
    var showTrackDetail by remember { mutableStateOf<Track?>(null) }
    var showQueue by remember { mutableStateOf(false) }

    // 共享音乐浏览状态（排序选择通过 SettingsManager 持久化，重启后恢复）
    val browserState = remember { MusicBrowserState(settings = musicPlayerCore.settings) }

    // ── 组件配置系统（JSON 布局 + CSS 样式） ──
    // 内置组件必须在 SlotRenderer 组合之前注册，否则首帧渲染时
    // registry 为空，所有组件跳过渲染，之后普通 map 更新无法触发重组。
    remember { registerBuiltInComponents() }

    val configLoader = remember { StyleConfigLoader(context) }
    val configState by configLoader.config.collectAsState()
    val cssRules by configLoader.cssRules.collectAsState()

    // 迷你播放栏渲染样式：唯一事实来源 = styles.css 中 #playbar / #pb-backdrop 的 render-style
    // 四态：none（无效果）/ semi-tran（半透明）/ blur（毛玻璃）/ liquid（液态玻璃）
    val miniRenderStyle = remember(cssRules) {
        val v = cssRules.rules["#playbar"]?.get("render-style")
            ?: cssRules.rules["#pb-backdrop"]?.get("render-style")
        val s = v?.trim()?.trim('"')?.trim('\'')
        if (s == "semi-tran" || s == "blur" || s == "liquid") s else "none"
    }

    // 液态玻璃视觉参数：唯一事实来源 = styles.css 中 #playbar 的 liquid-*（缺失时兜底默认值）
    // edge/refraction 为 dp 语义（原版示例同源），滑块直接读写 dp 值
    val liquidGlassParams = remember(cssRules) {
        val r = cssRules.rules["#playbar"].orEmpty()
        com.winter.muplayer.ui.screens.LiquidGlassParams(
            blurRadiusDp = r["blur-radius"]?.let { parseCssDp(it).value }
                ?: LiquidGlassBackdrop.DEFAULT_BLUR_RADIUS_DP,
            edgeWidthDp = r["liquid-edge"]?.let { parseCssDp(it).value }
                ?: LiquidGlassBackdrop.DEFAULT_EDGE_WIDTH_DP,
            refractionDp = r["liquid-refraction"]?.let { parseCssDp(it).value }
                ?: LiquidGlassBackdrop.DEFAULT_REFRACTION_DP,
            surfaceAlpha = (parseCssNumber(r["liquid-opacity"])
                ?: LiquidGlassBackdrop.DEFAULT_SURFACE_ALPHA).coerceIn(0f, 1f),
            specular = parseCssNumber(r["liquid-specular"])
                ?: LiquidGlassBackdrop.DEFAULT_SPECULAR,
            shininess = parseCssNumber(r["liquid-shininess"])
                ?: LiquidGlassBackdrop.DEFAULT_SHININESS,
            rimStrength = parseCssNumber(r["liquid-rim"])
                ?: LiquidGlassBackdrop.DEFAULT_RIM_STRENGTH,
        )
    }

    LaunchedEffect(Unit) {
        configLoader.initialize()  // 写入默认配置（如需）+ 从磁盘重载
    }

    // 本地音乐列表（初始空列表，后台 LaunchedEffect 加载缓存后再更新）
    val scanner = remember { LocalMusicScanner(context) }
    var coverCacheSize by remember { mutableStateOf("0 KB") }

    // 首次加载：先扫 50 首极速显示（~5ms），后台再加载全量缓存/扫描。
    // key 为 hasAudioPermission：授权完成后（状态变 true）会自动重新
    // 执行完整扫描，避免首次启动未授权时授权后不加载音乐的问题。
    LaunchedEffect(hasAudioPermission) {
        android.util.Log.d("WMPlayer-Perf", "LaunchedEffect 开始")
        browserState.isLoading = true

        // 1. 权限检查：无权限则只能读缓存
        if (!hasAudioPermission) {
            val cached = withContext(Dispatchers.IO) { scanner.getCachedTracks() }
            browserState.tracks = cached
            browserState.isLoading = false
            return@LaunchedEffect
        }

        // 2. 阶段①：MediaStore 快扫 50 首 —— 纯 SQLite 索引扫描，~5ms
        android.util.Log.d("WMPlayer-Perf", "scanFast(50) 开始")
        val fastTracks = withContext(Dispatchers.IO) { scanner.scanFast(50) }
        android.util.Log.d("WMPlayer-Perf", "scanFast(50) 完成: ${fastTracks.size} 首")
        browserState.tracks = fastTracks
        browserState.isLoading = false

        // 3. 阶段②：后台加载全量磁盘缓存（若有）或全量扫描
        val allTracks = withContext(Dispatchers.IO) {
            val cached = scanner.getCachedTracks()
            if (cached.isNotEmpty()) cached
            else scanner.scanFull()
        }

        if (allTracks.size > fastTracks.size) {
            browserState.tracks = allTracks
        }
        musicPlayerCore.restorePlaybackState(allTracks)

        // 4. 封面缓存：不再全量预缓存（仅在使用封面的组件处按需缓存，LRU 上限裁剪），
        //    这里只裁剪旧缓存并统计大小供设置页显示
        launch(Dispatchers.IO) {
            trimCoverCache(context)
            withContext(Dispatchers.Main) {
                coverCacheSize = computeCoverCacheSize(context)
            }
        }
    }

    // ==================== 布局 ====================

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 设置页面 — 从右滑入，滑出到右
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(animationSpec = tween(300)) { it },
            exit = slideOutHorizontally(animationSpec = tween(300)) { -it }
        ) {
            BackHandler { showSettings = false }
            val settings = musicPlayerCore.settings
            SettingsScreen(
                settings = settings,
                onBack = { showSettings = false },
                onRescan = {
                    scope.launch {
                        scanner.invalidateCache()
                        browserState.isLoading = true
                        val tracks = withContext(Dispatchers.IO) { scanner.scanFull() }
                        // 先显示歌单
                        browserState.tracks = tracks
                        browserState.isLoading = false
                        // 封面缓存：按需（显示处触发）+ LRU 裁剪，这里只统计大小
                        launch(Dispatchers.IO) {
                            trimCoverCache(context)
                            withContext(Dispatchers.Main) {
                                coverCacheSize = computeCoverCacheSize(context)
                            }
                        }
                    }
                },
                cacheInfo = com.winter.muplayer.ui.screens.CacheInfo(
                    formattedSize = coverCacheSize,
                    onClearCache = {
                        coverCache.clear()
                        // 清理封面缓存（当前原始封面目录 + 兼容旧目录）
                        listOf("album_covers_hi", "album_covers", "covers").forEach { name ->
                            val dir = File(context.cacheDir, name)
                            if (dir.exists()) dir.deleteRecursively()
                        }
                        coverCacheSize = "0 KB"
                    }
                ),
                onSettingChanged = onSettingChanged,
                onOpenLogsDir = {
                    openLogsDir(context)
                },
                onClearErrorLogs = {
                    val count = com.winter.muplayer.core.CrashLogManager.clearLogs(context)
                    Toast.makeText(
                        context,
                        if (count > 0) {
                            context.getString(com.winter.muplayer.ui.R.string.error_logs_cleared, count)
                        } else {
                            context.getString(com.winter.muplayer.ui.R.string.error_logs_none)
                        },
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onCrossfadeChange = { ms ->
                    (musicPlayerCore.engine as? com.winter.muplayer.core.engine.ExoPlayerEngine)
                        ?.setCrossfadeDuration(ms)
                },
                onLanguageChange = {
                    applyAppLanguage(context, settings.appLanguage)
                },
                onReloadConfig = {
                    configLoader.reload()
                    Toast.makeText(
                        context,
                        com.winter.muplayer.ui.R.string.config_reloaded,
                        Toast.LENGTH_SHORT
                    ).show()
                },
                // 迷你播放栏渲染样式三态：none / semi-tran / blur，切换即写 CSS + 按需切布局并热重载
                miniRenderStyle = miniRenderStyle,
                onMiniRenderStyleChange = { style ->
                    configLoader.setMiniRenderStyle(style)
                    configLoader.reload()
                },
                // 液态玻璃参数：滑块松手时写 CSS（#playbar liquid-*）+ 热重载生效
                liquidGlassParams = liquidGlassParams,
                onLiquidGlassParamsChange = { p ->
                    configLoader.setLiquidGlassParams(
                        p.blurRadiusDp, p.edgeWidthDp, p.refractionDp, p.surfaceAlpha, p.specular, p.shininess, p.rimStrength,
                    )
                    configLoader.reload()
                },
                // 布局与样式编辑器入口
                onOpenLayoutEditor = { showLayoutEditor = true },
            )
        }

        // 布局与样式编辑器（覆盖在设置页之上，从右滑入）
        AnimatedVisibility(
            visible = showLayoutEditor,
            enter = slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(animationSpec = tween(280)) { it } + fadeOut(tween(160)),
        ) {
            BackHandler { showLayoutEditor = false }
            LayoutEditorScreen(
                onBack = { showLayoutEditor = false },
                configLoader = configLoader,
                musicPlayerCore = musicPlayerCore,
                browserState = browserState,
                coverCache = coverCache,
                playerState = playerState,
                playMode = playMode,
                blurBackground = blurBackground,
                adaptiveTintStyle = adaptiveTintStyle,
            )
        }

        // 主界面
        AnimatedVisibility(
            visible = !showSettings,
            enter = slideInHorizontally(animationSpec = tween(300)) { -it },
            exit = slideOutHorizontally(animationSpec = tween(300)) { -it }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                CompositionLocalProvider(LocalBrowserState provides browserState) {
                    SlotRenderer(
                        slots = configState.slots,
                        css = cssRules,
                        customComponents = configState.customComponents,
                        debug = isDebug,
                        context = SlotContext(
                            slotName = "",
                            onOpenSearch = { showSearchScreen = true },
                            onOpenSettings = { showSettings = true },
                            localMusicList = browserState.tracks,
                            isLoadingLocal = browserState.isLoading,
                            coverCache = coverCache,
                            musicPlayerCore = musicPlayerCore,
                            onPlayTrackSmart = { track, contextTracks ->
                                musicPlayerCore.playTrackSmart(track, contextTracks)
                            },
                            playerState = playerState,
                            onPlay = musicPlayerCore::play,
                            onPause = musicPlayerCore::pause,
                            onNext = musicPlayerCore::playNext,
                            onPrevious = musicPlayerCore::playPrevious,
                            onOpenFullPlayer = { showFullPlayer = true },
                            onOpenQueue = { showQueue = true },
                        ),
                    )
                }
            }
        }

        // ====== 主界面插件插槽（预留） ======
    }

    // ====== 全屏播放面板（从下滑入，退出由面板内手动控制） ======
    AnimatedVisibility(
        visible = showFullPlayer,
        enter = slideInVertically(animationSpec = tween(300)) { it },
        exit = slideOutVertically(animationSpec = tween(300)) { it }
    ) {
        BackHandler { showFullPlayer = false }
        val progressState by musicPlayerCore.progressState.collectAsState()
        FullPlayerPanel(
            isVisible = showFullPlayer,
            playerState = playerState,
            playMode = playMode,
            coverCache = coverCache,
            blurBackground = blurBackground,
            adaptiveTintStyle = adaptiveTintStyle,
            debug = isDebug,
            musicPlayerCore = musicPlayerCore,
            fullPlayerSlots = configState.fullPlayerSlots,
            css = cssRules,
            customComponents = configState.customComponents,
            onPlay = musicPlayerCore::play,
            onPause = musicPlayerCore::pause,
            onNext = musicPlayerCore::playNext,
            onPrevious = musicPlayerCore::playPrevious,
            onSeek = musicPlayerCore::seekTo,
            onPlayModeChange = musicPlayerCore::setPlayMode,
            onShowQueue = { showQueue = true },
            onDismiss = { showFullPlayer = false },
        )
    }

    // ====== 播放队列面板 ======
    if (showQueue) {
        QueueSheet(
            queue = queue,
            currentIndex = currentIndex,
            playerState = playerState,
            coverCache = coverCache,
            onPlayTrack = musicPlayerCore::playTrackAtIndex,
            onRemoveTrack = musicPlayerCore::removeTrack,
            onClearQueue = musicPlayerCore::clearQueue,
            onTrackLongPress = { showTrackDetail = it },
            onDismiss = { showQueue = false }
        )
    }

    // ====== 曲目详情弹窗 ======
    showTrackDetail?.let { track ->
        TrackDetailDialog(
            track = track,
            coverCache = coverCache,
            onDismiss = { showTrackDetail = null }
        )
    }

    // ====== 安全模式指示条（保护层，始终在最顶层） ======
    if (safeMode) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .background(Color(0xDDFF1744))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "⚠ 安全模式 — 所有插件已禁用 | 长按音量减键恢复",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }

    // ====== 全屏搜索（覆盖层） ======
    AnimatedVisibility(
        visible = showSearchScreen,
        enter = fadeIn(animationSpec = tween(300)) + slideInHorizontally(animationSpec = tween(300)) { it },
        exit = fadeOut(animationSpec = tween(300)) + slideOutHorizontally(animationSpec = tween(300)) { it },
    ) {
        BackHandler { showSearchScreen = false }
        SearchScreen(
            tracks = browserState.tracks,
            coverCache = coverCache,
            onDismiss = { showSearchScreen = false },
            onPlayTrack = { track, contextTracks ->
                musicPlayerCore.playTrackSmart(track, contextTracks)
                showSearchScreen = false
            },
        )
    }
}
// ==================== 全屏搜索界面 ====================

@Composable
private fun SearchScreen(
    tracks: List<Track>,
    coverCache: Map<Long, String>,
    onDismiss: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val filtered = remember(searchQuery, tracks) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶栏：返回箭头 + 搜索框
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.search_local_music)) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_clear),
                                contentDescription = stringResource(R.string.clear_search),
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.primary,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onDismiss() }),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (searchQuery.isNotEmpty()) {
            Text(
                text = "找到 ${filtered.size} 首歌曲",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        if (filtered.isEmpty() && searchQuery.isNotEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "未找到匹配的歌曲",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 8.dp),
            ) {
                items(filtered, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        coverCache = coverCache,
                        onClick = { onPlayTrack(track, filtered) },
                    )
                }
            }
        }
    }
}

// ==================== 迷你底部播放条（参考网易云音乐风格） ====================


// ==================== 全屏播放面板（BottomSheet） ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerPanel(
    isVisible: Boolean = true,
    playerState: PlayerStateData,
    playMode: PlayMode,
    coverCache: MutableMap<Long, String>,
    blurBackground: Boolean = false,
    adaptiveTintStyle: com.winter.muplayer.core.SettingsManager.AdaptiveTintStyle =
        com.winter.muplayer.core.SettingsManager.AdaptiveTintStyle.MONOCHROME,
    debug: Boolean = false,
    musicPlayerCore: MusicPlayerCore,
    fullPlayerSlots: Map<String, List<ComponentEntry>>,
    css: CssRuleTable,
    customComponents: Map<String, Map<String, Any?>>,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayModeChange: (PlayMode) -> Unit,
    onShowQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val currentTrack = playerState.currentTrack
    val scope = rememberCoroutineScope()
    var offsetY by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(0f) }

    // 每次进入时重置下滑偏移：防止上次退出中断（exit 动画未完成时再次打开）
    // 导致 offsetY 残留、面板整体偏移到屏幕外不可见，表现为"再次点击
    // playbar 无法展开全屏播放器"。
    LaunchedEffect(isVisible) {
        if (isVisible) offsetY = 0f
    }

    // 退出由 AnimatedVisibility 的 exit 动画（slideOutVertically）统一处理
    val performDismiss: () -> Unit = { onDismiss() }

    val surfaceColor = MaterialTheme.colorScheme.surface

    // ====== 自适应按钮颜色（封面模糊背景时根据封面亮度决定） ======
    val currentContext = LocalContext.current
    var coverBitmap by remember(blurBackground, currentTrack?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(blurBackground, currentTrack?.id) {
        if (blurBackground && currentTrack != null) {
            // 全屏模糊背景：直接使用原始无损封面
            val uri = getAlbumArtUri(currentTrack, coverCache, preferOriginal = true)
            if (uri != null) {
                val loader = coil.ImageLoader(currentContext)
                val request = ImageRequest.Builder(currentContext)
                    .data(uri)
                    .size(100, 100)
                    .crossfade(false)
                    .build()
                val result = loader.execute(request)
                val drawable = result.drawable
                if (drawable is android.graphics.drawable.BitmapDrawable) {
                    coverBitmap = drawable.bitmap
                }
            }
        } else {
            coverBitmap = null
        }
    }
    val defaultOnSurface = MaterialTheme.colorScheme.onSurface
    val adaptiveTint = remember(coverBitmap, defaultOnSurface, adaptiveTintStyle) {
        if (blurBackground) {
            coverBitmap?.let { computeAdaptiveTint(it, adaptiveTintStyle) } ?: defaultOnSurface
        } else {
            defaultOnSurface
        }
    }

    BackHandler(onBack = performDismiss)

    // ====== 组件化内容：SlotRenderer 渲染 main slots（含 backdrop 背景层） ======
    val fpContext = SlotContext(
        slotName = "main",
        onOpenSearch = {},
        onOpenSettings = {},
        localMusicList = emptyList(),
        isLoadingLocal = false,
        coverCache = coverCache,
        musicPlayerCore = musicPlayerCore,
        onPlayTrackSmart = { _, _ -> },
        playerState = playerState,
        onPlay = onPlay,
        onPause = onPause,
        onNext = onNext,
        onPrevious = onPrevious,
        onOpenFullPlayer = {},
        onOpenQueue = onShowQueue,
        onSeek = onSeek,
        onPlayModeChange = onPlayModeChange,
        playMode = playMode,
        adaptiveTint = adaptiveTint,
        blurBackground = blurBackground,
    )

    // 全屏播放器整体放入独立粒子层：按钮等自触发粒子在面板内可见，不依赖主窗口粒子层；
    // autoTap=true —— 面板空白点击由本层（layerHost）兜底：其渲染色命中判定按粒子宿主层过滤，
    // 不会误命中本层之下被盖住的主界面列表行渲染色（否则面板中部点击会被全局层误跳过、无特效）。
    ParticleLayer(modifier = Modifier.fillMaxSize(), autoTap = true) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { itemHeight = it.height.toFloat() }
            .offset { IntOffset(0, offsetY.roundToInt()) }
            .drawWithContent {
                // 背景色根据 offsetY 从实色渐变到透明，露出下层主界面
                val bgProgress = (offsetY / size.height).coerceIn(0f, 1f)
                drawRect(
                    color = surfaceColor.copy(alpha = 1f - bgProgress),
                    size = size
                )
                // 从顶部裁剪 offsetY 像素，让主界面随着下滑逐渐从顶部露出
                clipRect(top = offsetY, bottom = size.height) {
                    this@drawWithContent.drawContent()
                }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (offsetY > itemHeight * 0.15f) {
                            performDismiss()
                        } else {
                            scope.launch {
                                animate(initialValue = offsetY, targetValue = 0f) { value, _ ->
                                    offsetY = value
                                }
                            }
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0f) {
                            change.consume()
                            val newOffset = (offsetY + dragAmount).coerceIn(0f, itemHeight)
                            offsetY = newOffset
                        }
                    }
                )
            }
    ) {
        // 全屏播放器独立于主界面：外层排列方向从 .full-player 读取（而非全局 .main），
        // children 子 slot 之间方向由容器组件 #fp-backdrop 的 arrange 控制，互不影响主界面。
        SlotRenderer(
            slots = fullPlayerSlots,
            context = fpContext,
            css = css,
            customComponents = customComponents,
            debug = debug,
            outerArrange = css.rules[".full-player"]?.get("arrange"),
        )
    }
    }
}

// ==================== 播放队列面板（BottomSheet） ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<QueueEntry>,
    currentIndex: Int,
    playerState: PlayerStateData,
    coverCache: Map<Long, String>,
    onPlayTrack: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onTrackLongPress: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    ParticleModalSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.current_playlist),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    if (queue.isNotEmpty()) {
                        TextButton(onClick = onClearQueue) {
                            Text(stringResource(R.string.clear))
                        }
                    }
                }

                val isCurrentlyDark = isSystemInDarkTheme()

                // 队列列表
                PlayQueueSection(
                    queue = queue,
                    currentIndex = currentIndex,
                    coverCache = coverCache,
                    onPlayTrack = onPlayTrack,
                    onRemoveTrack = onRemoveTrack,
                    onClearQueue = {}, // 已在上方处理
                    onTrackLongPress = onTrackLongPress,
                    modifier = Modifier.heightIn(max = 500.dp)
                )

                Spacer(Modifier.height(32.dp))
            }
    }
}

// ==================== 播放控制卡片（保留，用于全屏面板内部引用） ====================


// ==================== 专辑封面组件 ====================


// ==================== 曲目信息 & 跑马灯 ====================


// ==================== 进度条组件 ====================


// ==================== 播放控制按钮组 ====================


// ==================== 播放/暂停按钮 ====================

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = MaterialTheme.colorScheme.onPrimary
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            scale.animateTo(0.9f, animationSpec = spring(dampingRatio = 0.8f))
            scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.4f))
        }
    }

    LaunchedEffect(isPlaying) {
        scale.animateTo(1.15f, animationSpec = spring(dampingRatio = 0.5f))
        scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f))
    }

    IconButton(
        onClick = {
            isPressed = true
            if (isPlaying) onPause() else onPlay()
        },
        modifier = Modifier
            .size(72.dp)
            .scale(scale.value)
            .background(
                color = containerColor,
                shape = CircleShape
            )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = iconTint,
                strokeWidth = 3.dp
            )
        } else {
            Icon(
                painter = if (isPlaying) painterResource(R.drawable.ic_pause) else painterResource(R.drawable.ic_play),
                contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                modifier = Modifier.size(40.dp),
                tint = iconTint
            )
        }
    }
}

// ==================== 播放模式按钮 ====================

@Composable
fun PlayModeButton(
    playMode: PlayMode,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    val icon = when (playMode) {
        PlayMode.SEQUENTIAL -> painterResource(R.drawable.ic_shuffle_disabled)
        PlayMode.SHUFFLE -> painterResource(R.drawable.ic_shuffle)
        PlayMode.SINGLE_LOOP -> painterResource(R.drawable.ic_repeat_one)
        PlayMode.REPEAT_ALL -> painterResource(R.drawable.ic_repeat)
    }

    val label = when (playMode) {
        PlayMode.SEQUENTIAL -> stringResource(R.string.mode_sequential)
        PlayMode.SHUFFLE -> stringResource(R.string.mode_shuffle)
        PlayMode.SINGLE_LOOP -> stringResource(R.string.mode_single_loop)
        PlayMode.REPEAT_ALL -> stringResource(R.string.mode_repeat_all)
    }

    IconButton(onClick = onClick) {
        Icon(
            painter = icon,
            contentDescription = label,
            modifier = Modifier.size(28.dp),
            tint = tint
        )
    }
}

// ==================== 通用控制按钮 ====================

@Composable
fun ControlButton(
    icon: Painter,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.65f),
            tint = tint
        )
    }
}

// ==================== 播放队列视图 ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayQueueSection(
    queue: List<QueueEntry>,
    currentIndex: Int,
    coverCache: Map<Long, String>,
    onPlayTrack: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onTrackLongPress: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    if (queue.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(R.drawable.ic_music_off),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.queue_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    } else {
        Column(modifier = modifier) {
            LazyColumn {
                itemsIndexed(
                    items = queue,
                    key = { index, entry -> "${entry.track.id}_$index" }
                ) { index, entry ->
                    // animateItem：队列增删 / 当前条目前移时其它行平滑重排（无弹性，避免"抖"）
                    Box(
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = tween(180),
                                placementSpec = tween(240),
                                fadeOutSpec = tween(150),
                            )
                            .fillMaxWidth()
                    ) {
                        QueueTrackItem(
                            track = entry.track,
                            index = index,
                            isCurrentTrack = index == currentIndex,
                            coverCache = coverCache,
                            onPlay = { onPlayTrack(index) },
                            onRemove = { onRemoveTrack(index) },
                            onLongPress = { onTrackLongPress(entry.track) }
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// ==================== 队列中的单曲行 ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun QueueTrackItem(
    track: Track,
    index: Int,
    isCurrentTrack: Boolean,
    coverCache: Map<Long, String>,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var offsetX by remember { mutableFloatStateOf(0f) }
    var isRemoving by remember { mutableStateOf(false) }
    var itemWidth by remember { mutableFloatStateOf(0f) }

    // 退出动画：400ms 滑出 + 渐隐
    val exitAnim by animateFloatAsState(
        targetValue = if (isRemoving) -1f else 0f,
        animationSpec = tween(150, easing = LinearEasing),
        label = "exitSlide"
    )
    val exitAlpha by animateFloatAsState(
        targetValue = if (isRemoving) 0f else 1f,
        animationSpec = tween(200, easing = LinearEasing),
        label = "exitAlpha"
    )

    // ── 当前播放切换过渡：正在播放的条目从 A 切到 B 时，旧行高亮淡出、新行高亮淡入 ──
    // 竖条 / 行背景 / 标题文字颜色都做渐变动画（切歌时不再瞬时跳变）
    val scheme = MaterialTheme.colorScheme
    // 柔和高亮：当前播放行的背景不用饱和的 primaryContainer 整块填充，
    // 而是与普通行底色 secondaryContainer 做低比例混合，视觉更柔和；
    // 标题文字也用常规 onSurface（不再用高对比 onPrimaryContainer）
    val barColor by animateColorAsState(
        targetValue = if (isCurrentTrack) scheme.primary else Color.Transparent,
        animationSpec = tween(260),
        label = "playingBar"
    )
    val cardBg by animateColorAsState(
        targetValue = if (isCurrentTrack)
            lerp(scheme.secondaryContainer, scheme.primaryContainer, 0.35f)
        else scheme.secondaryContainer,
        animationSpec = tween(260),
        label = "playingCardBg"
    )
    val titleColor by animateColorAsState(
        targetValue = if (isCurrentTrack) scheme.onSurface
        else scheme.onSurfaceVariant,
        animationSpec = tween(260),
        label = "playingTitleColor"
    )

    LaunchedEffect(isRemoving) {
        if (isRemoving) {
            delay(150)
            onRemove()
        }
    }

    // 队列行点击粒子：显式触发（点击 / 长按必有），爆发点优先手指按下位置
    val (burst, fingerMod) = rememberFingerBurst(
        color = MaterialTheme.colorScheme.primary,
        radius = 48.dp,
    )

    // 滑动时删除图标透明度
    val bgAlpha = (offsetX / -200f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clipToBounds()
            .onSizeChanged { itemWidth = it.width.toFloat() }
    ) {
        // 卡片层
        Card(
            modifier = Modifier
                .offset { IntOffset((offsetX + exitAnim * itemWidth * 0.5f).roundToInt(), 0) }
                .graphicsLayer {
                    alpha = exitAlpha
                }
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX < -150f) {
                                // 滑过阈值 → 退出动画接手当前位置继续滑出
                                isRemoving = true
                            } else {
                                // 未过阈值 → 回弹
                                scope.launch {
                                    animate(initialValue = offsetX, targetValue = 0f) { value, _ ->
                                        offsetX = value
                                    }
                                }
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-itemWidth * 0.5f, 0f)
                        }
                    )
                }
                .combinedClickable(
                    onClick = { burst.burst(); onPlay() },
                    onLongClick = { burst.burst(); onLongPress },
                )
                .then(fingerMod),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardBg
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 当前播放指示器竖条（颜色随播放状态渐变切换）
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor)
                )
                Spacer(Modifier.width(12.dp))

                // 封面缩略图
                val hasCover = coverCache.containsKey(track.id) || track.albumId > 0L
                if (hasCover) {
                    val coverData = getAlbumArtUri(track, coverCache)
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverData)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_music_note),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // 歌曲信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = titleColor
                    )
                    Text(
                        text = "${track.artist} • ${track.album}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 时长
                Text(
                    text = formatDuration(track.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 删除图标（左滑时渐显，固定在右侧）
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(24.dp)
                        .graphicsLayer { alpha = bgAlpha }
                        .padding(start = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete),
                        contentDescription = stringResource(R.string.delete),
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

}

// ==================== 曲目详情弹窗 ====================

@Composable
fun TrackDetailDialog(
    track: Track,
    coverCache: Map<Long, String>,
    onDismiss: () -> Unit
) {
    ParticleAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.track_detail),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val hasCover = coverCache.containsKey(track.id) || track.albumId > 0L
                if (hasCover) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(getAlbumArtUri(track, coverCache))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }

                Spacer(Modifier.height(16.dp))

                DetailItem(stringResource(R.string.label_title), track.title)
                DetailItem(stringResource(R.string.label_artist), track.artist)
                DetailItem(stringResource(R.string.label_album), track.album)
                DetailItem(stringResource(R.string.label_duration), formatDuration(track.duration))
                DetailItem(stringResource(R.string.label_file_path), track.uri)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun DetailItem(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}

// ==================== 辅助函数 ====================

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}


// ==================== 辅助函数 ====================

// 封面缓存已改为按需单曲缓存（cacheCoverFile/trimCoverCache，见 Utils.kt）：
// 不再全量预缓存，仅在使用封面的组件处缓存并受 LRU 上限裁剪。

/**
 * 计算封面缓存目录的大小，返回人类可读的字符串。
 */
private fun computeCoverCacheSize(context: android.content.Context): String {
    val dir = java.io.File(context.cacheDir, "album_covers_hi")
    if (!dir.exists()) return "0 KB"
    val bytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    return when {
        bytes < 1024 -> "0 KB"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}

/**
 * 从封面 Bitmap 采样像素，计算平均亮度，返回配适的按钮图标颜色。
 * 暗封面 → 白色，亮封面 → 深灰色，确保按钮在模糊背景上清晰可见。
 */
private fun computeAdaptiveTint(bitmap: Bitmap, style: SettingsManager.AdaptiveTintStyle): Color {
    // HARDWARE 位图不支持 getPixel()，先复制为软件可读格式
    val isHardware = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE
    val readable = if (isHardware) {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)
    } else {
        bitmap
    } ?: return Color(0xFF1A1A1A)

    var sumR = 0f; var sumG = 0f; var sumB = 0f; var count = 0
    val step = maxOf(1, minOf(readable.width, readable.height) / 8)
    for (x in 0 until readable.width step step) {
        for (y in 0 until readable.height step step) {
            val pixel = readable.getPixel(x, y)
            sumR += android.graphics.Color.red(pixel)
            sumG += android.graphics.Color.green(pixel)
            sumB += android.graphics.Color.blue(pixel)
            count++
        }
    }
    if (count == 0) return Color(0xFF1A1A1A)

    val avgR = sumR / count / 255f
    val avgG = sumG / count / 255f
    val avgB = sumB / count / 255f
    val avgLuminance = 0.299f * avgR + 0.587f * avgG + 0.114f * avgB

    // 平均色 → HSV（供正色/反色做饱和度与明度增强）
    val avgInt = android.graphics.Color.rgb(
        (avgR * 255f).toInt().coerceIn(0, 255),
        (avgG * 255f).toInt().coerceIn(0, 255),
        (avgB * 255f).toInt().coerceIn(0, 255),
    )
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(avgInt, hsv)

    return when (style) {
        // 正色：保留封面色调（色相），但饱和度保底 + 明度与背景取反，
        // 避免平均色低饱和、与模糊背景同明度导致的“淡”
        SettingsManager.AdaptiveTintStyle.COLOR -> {
            hsv[1] = maxOf(hsv[1], 0.6f)
            hsv[2] = if (avgLuminance > 0.5f) minOf(hsv[2], 0.35f) else maxOf(hsv[2], 0.85f)
            Color(android.graphics.Color.HSVToColor(hsv))
        }
        // 反色：反相已保证明度与背景相反，再补饱和度保底（灰封面时反色也灰）
        SettingsManager.AdaptiveTintStyle.INVERT -> {
            hsv[0] = (hsv[0] + 180f) % 360f
            hsv[1] = maxOf(hsv[1], 0.6f)
            hsv[2] = 1f - hsv[2]
            Color(android.graphics.Color.HSVToColor(hsv))
        }
        // 黑白：按平均亮度取纯黑/纯白（最大亮度对比）
        SettingsManager.AdaptiveTintStyle.MONOCHROME ->
            if (avgLuminance > 0.55f) Color(0xFF1A1A1A) else Color.White
    }
}

// ==================== 语言切换 ====================

/**
 * 应用语言设置。
 * Android 13+ 使用 [android.app.LocaleManager] / AppCompatDelegate，
 * 低版本使用 [android.content.res.Configuration.setLocale]。
 */
private fun openLogsDir(context: android.content.Context) {
    val dir = com.winter.muplayer.core.CrashLogManager.logDir(context)
    if (!dir.exists()) dir.mkdirs()

    // 首选：系统 DocumentsUI 定位目录（content://...externalstorage.documents/document/...）
    val docUri = DocumentsContract.buildDocumentUri(
        "com.android.externalstorage.documents",
        "primary:Android/data/${context.packageName}/files/logs",
    )
    val dirOpened = try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(docUri, "vnd.android.document/directory")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
        )
        true
    } catch (_: Exception) {
        false
    }
    if (dirOpened) return

    // 兜底：FileProvider 打开最新日志文件（部分系统文件管理器定位不到 Android/data 目录时）
    val latest = com.winter.muplayer.core.CrashLogManager.latestLogFile(context)
    if (latest != null) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", latest,
            )
            context.startActivity(
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "text/plain")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
            )
            return
        } catch (_: Exception) {
            // 落到下方 Toast
        }
    }

    Toast.makeText(
        context,
        com.winter.muplayer.ui.R.string.open_logs_failed,
        Toast.LENGTH_SHORT,
    ).show()
}

private fun applyAppLanguage(
    context: android.content.Context,
    language: com.winter.muplayer.core.SettingsManager.AppLanguage
) {
    when (language) {
        com.winter.muplayer.core.SettingsManager.AppLanguage.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= 33) {
                val lm = context.getSystemService(android.content.Context.LOCALE_SERVICE)
                        as? android.app.LocaleManager
                lm?.applicationLocales = android.os.LocaleList.getDefault()
            } else {
                @Suppress("DEPRECATION")
                val config = android.content.res.Configuration()
                config.setLocale(java.util.Locale.getDefault())
                @Suppress("DEPRECATION")
                android.content.res.Resources.getSystem().updateConfiguration(
                    config, android.content.res.Resources.getSystem().displayMetrics
                )
            }
        }
        com.winter.muplayer.core.SettingsManager.AppLanguage.ZH -> {
            if (Build.VERSION.SDK_INT >= 33) {
                val lm = context.getSystemService(android.content.Context.LOCALE_SERVICE)
                        as? android.app.LocaleManager
                lm?.applicationLocales = android.os.LocaleList.forLanguageTags("zh")
            } else {
                @Suppress("DEPRECATION")
                val config = android.content.res.Configuration()
                config.setLocale(java.util.Locale("zh"))
                @Suppress("DEPRECATION")
                android.content.res.Resources.getSystem().updateConfiguration(
                    config, android.content.res.Resources.getSystem().displayMetrics
                )
            }
        }
        com.winter.muplayer.core.SettingsManager.AppLanguage.EN -> {
            if (Build.VERSION.SDK_INT >= 33) {
                val lm = context.getSystemService(android.content.Context.LOCALE_SERVICE)
                        as? android.app.LocaleManager
                lm?.applicationLocales = android.os.LocaleList.forLanguageTags("en")
            } else {
                @Suppress("DEPRECATION")
                val config = android.content.res.Configuration()
                config.setLocale(java.util.Locale("en"))
                @Suppress("DEPRECATION")
                android.content.res.Resources.getSystem().updateConfiguration(
                    config, android.content.res.Resources.getSystem().displayMetrics
                )
            }
        }
    }
}