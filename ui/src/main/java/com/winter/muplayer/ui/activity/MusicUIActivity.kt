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
import com.winter.muplayer.config.StyleConfigLoader
import com.winter.muplayer.ui.components.registerBuiltInComponents
import com.winter.muplayer.ui.R
import com.winter.muplayer.ui.browser.TrackRow
import com.winter.muplayer.ui.components.getAlbumArtUri
import com.winter.muplayer.ui.components.cacheCoverFiles
import com.winter.muplayer.ui.browser.LocalBrowserState
import com.winter.muplayer.ui.browser.MusicBrowserState
import com.winter.muplayer.ui.screens.SettingsScreen
import com.winter.muplayer.ui.theme.AppTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
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
                    Box(Modifier.fillMaxSize()) {
                        MusicPlayerApp(
                            musicPlayerCore = musicPlayerCore,
                            hasAudioPermission = audioPermissionGranted.value,
                            blurBackground = currentBlurBg,
                            onSettingChanged = {
                                currentThemeMode = settings.themeMode
                                currentDynamicColor = settings.dynamicColorEnabled
                                currentBlurBg = settings.blurBackground
                            },
                            onSetPlayMode = musicPlayerCore::setPlayMode)
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
    onSettingChanged: () -> Unit = {},
    onSetPlayMode: (PlayMode) -> Unit = {}
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

        // 4. 封面缓存（后台不阻塞）
        launch(Dispatchers.IO) {
            cacheCoverFiles(context, allTracks, coverCache)
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
                        // 封面缓存放后台
                        launch(Dispatchers.IO) {
                            cacheCoverFiles(context, tracks, coverCache)
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
                onSetPlayMode = onSetPlayMode,
                onCrossfadeChange = { ms ->
                    (musicPlayerCore.engine as? com.winter.muplayer.core.engine.ExoPlayerEngine)
                        ?.setCrossfadeDuration(ms)
                },
                onLanguageChange = {
                    applyAppLanguage(context, settings.appLanguage)
                }
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
                        debug = false,
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
            progress = progressState.progress,
            duration = progressState.duration,
            playMode = playMode,
            coverCache = coverCache,
            blurBackground = blurBackground,
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
    progress: Long,
    duration: Long,
    playMode: PlayMode,
    coverCache: Map<Long, String>,
    blurBackground: Boolean = false,
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
    val isPlaying = playerState.state == PlayerState.PLAYING
    val scope = rememberCoroutineScope()
    var offsetY by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()

    // 每次进入时重置下滑偏移：防止上次退出中断（exit 动画未完成时再次打开）
    // 导致 offsetY 残留、面板整体偏移到屏幕外不可见，表现为"再次点击
    // playbar 无法展开全屏播放器"。
    LaunchedEffect(isVisible) {
        if (isVisible) offsetY = 0f
    }

    // 退出由 AnimatedVisibility 的 exit 动画（slideOutVertically）统一处理：
    // 立即回调 onDismiss，避免"内部动画完成后才关闭"造成退出期间再次
    // 打开时状态卡死（showFullPlayer 已为 true 且面板停留在屏幕外）。
    val performDismiss: () -> Unit = {
        onDismiss()
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

    // ====== 自适应按钮颜色（封面模糊背景时根据封面亮度决定） ======
    val currentContext = LocalContext.current
    var coverBitmap by remember(blurBackground, currentTrack?.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(blurBackground, currentTrack?.id) {
        if (blurBackground && currentTrack != null) {
            val uri = getAlbumArtUri(currentTrack, coverCache)
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
    val adaptiveTint = remember(coverBitmap, defaultOnSurface) {
        if (blurBackground) {
            coverBitmap?.let { computeAdaptiveTint(it) } ?: defaultOnSurface
        } else {
            defaultOnSurface
        }
    }

    // ====== 封面显示状态：切歌时保持旧封面直到新封面加载完成 ======
    // coverState 记录「当前已就绪的封面」（歌曲 id → 封面 URI / null）。
    // 切歌瞬间新封面未就绪时继续渲染旧封面，新封面预加载成功后才更新
    // coverState 并切换显示（Coil 内存缓存命中 → crossfade 平滑过渡），
    // 从根本上消除切歌时的黑帧 / 占位块闪烁。
    var coverState by remember { mutableStateOf<Pair<Long, Any?>?>(null) }
    var lastCoverUri by remember { mutableStateOf<Any?>(null) }
    LaunchedEffect(currentTrack?.id, coverCache) {
        val track = currentTrack
        if (track == null) {
            coverState = null
            lastCoverUri = null
            return@LaunchedEffect
        }
        val uri: Any? = getAlbumArtUri(track, coverCache)
        if (coverState?.first == track.id && lastCoverUri == uri) return@LaunchedEffect
        val loaded = if (uri != null) {
            withContext(Dispatchers.IO) {
                try {
                    val loader = currentContext.imageLoader
                    loader.execute(
                        ImageRequest.Builder(currentContext)
                            .data(uri)
                            .size(Size.ORIGINAL)
                            .build()
                    ).drawable != null
                } catch (_: Exception) {
                    false
                }
            }
        } else false
        lastCoverUri = uri
        coverState = track.id to (if (loaded) uri else null)
    }
    // 当前应显示的封面：切歌瞬间（新封面未就绪）保持旧封面
    val displayedCover: Any? = coverState?.second

    BackHandler(onBack = performDismiss)

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
    ) {
            // ====== 封面模糊背景层 ======
            if (blurBackground && currentTrack != null) {
                // 模糊背景同样交叉渐变，与主封面过渡同步
                Crossfade(
                    targetState = displayedCover,
                    animationSpec = tween(durationMillis = 400),
                    label = "fullPlayerBlurCover"
                ) { cover ->
                    if (cover != null) {
                        // Box 占位底色：模糊封面加载中避免露出深色背景（黑帧）
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(cover)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .blur(radius = 48.dp)
                            )
                        }
                    }
                }
            }

            // ====== 前景内容 ======
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ====== 插件 Slot（above_music_list，由主界面统一管理） ======
                // Hello World 卡片在主列表上方渲染，全屏面板不重复渲染

                // ====== 封面（占上半部空间，下滑退出手势） ======
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    // 下滑超过面板高度 15% 即关闭（降低阈值，更小的下滑动作也能退出全屏）
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
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (currentTrack != null) {
                        // 封面交叉渐变：displayedCover 变化时旧封面渐出、新封面渐入
                        Crossfade(
                            targetState = displayedCover,
                            animationSpec = tween(durationMillis = 400),
                            label = "fullPlayerCover"
                        ) { cover ->
                            if (cover != null) {
                                val albumArtCorner = 24.dp
                                val albumArtW = 0.dp
                                val albumArtH = 0.dp
                                // Box 占位底色：封面加载中显示 surfaceVariant（透明时可见）
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .then(
                                            if (albumArtW > 0.dp && albumArtH > 0.dp)
                                                Modifier.size(albumArtW, albumArtH)
                                            else Modifier
                                        )
                                        .clip(RoundedCornerShape(albumArtCorner))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .shadow(16.dp, RoundedCornerShape(albumArtCorner))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(cover)
                                            // 全屏播放器封面：加载原始尺寸，不经过 Coil 采样压缩
                                            .size(Size.ORIGINAL)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                val albumArtCorner = 24.dp
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(albumArtCorner))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .shadow(16.dp, RoundedCornerShape(albumArtCorner)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painterResource(R.drawable.ic_music_off),
                                        contentDescription = null,
                                        modifier = Modifier.size(100.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    } else {
                        val albumArtCorner = 24.dp
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(albumArtCorner))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_music_off),
                                contentDescription = null,
                                modifier = Modifier.size(100.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    }
                }

                // ====== 下半部分 ======
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // ====== 歌曲信息 ======
                    Text(
                        text = currentTrack?.title ?: stringResource(R.string.no_track_selected),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = adaptiveTint
                    )

                    if (currentTrack != null) {
                        Text(
                            text = "${currentTrack.artist} • ${currentTrack.album}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = adaptiveTint.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // ====== 进度条 ======
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = if (duration > 0)
                                progress.toFloat() / duration.toFloat()
                            else 0f,
                            onValueChange = { fraction ->
                                onSeek((fraction * duration).toLong())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(progress),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDuration(duration),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ====== 播放控制按钮 ======
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 播放模式
                        PlayModeButton(
                            playMode = playMode,
                            onClick = {
                                val newMode = when (playMode) {
                                    PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
                                    PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
                                    PlayMode.SINGLE_LOOP -> PlayMode.REPEAT_ALL
                                    PlayMode.REPEAT_ALL -> PlayMode.SEQUENTIAL
                                }
                                onPlayModeChange(newMode)
                            },
                            tint = adaptiveTint
                        )

                        // 上一首
                        ControlButton(
                            icon = painterResource(R.drawable.ic_skip_previous),
                            onClick = onPrevious,
                            size = 48.dp,
                            tint = adaptiveTint
                        )

                        // 播放/暂停
                        PlayPauseButton(
                            isPlaying = isPlaying,
                            isLoading = playerState.state == PlayerState.LOADING,
                            onPlay = onPlay,
                            onPause = onPause,
                            containerColor = adaptiveTint.copy(alpha = 0.2f),
                            iconTint = adaptiveTint
                        )

                        // 下一首
                        ControlButton(
                            icon = painterResource(R.drawable.ic_skip_next),
                            onClick = onNext,
                            size = 48.dp,
                            tint = adaptiveTint
                        )

                        // 播放列表
                        IconButton(onClick = onShowQueue) {
                            Icon(
                                painterResource(R.drawable.ic_playlist_music),
                                contentDescription = stringResource(R.string.playlist),
                                modifier = Modifier.size(32.dp),
                                tint = adaptiveTint
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                }
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
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

    LaunchedEffect(isRemoving) {
        if (isRemoving) {
            delay(150)
            onRemove()
        }
    }

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
                    onClick = onPlay,
                    onLongClick = onLongPress
                ),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 当前播放指示器竖条
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (isCurrentTrack) MaterialTheme.colorScheme.primary
                            else Color.Transparent
                        )
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    AlertDialog(
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

// cacheCoverFiles 已迁移到 com.winter.muplayer.ui.components.Utils.kt

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
private fun computeAdaptiveTint(bitmap: Bitmap): Color {
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
    val avgLuminance = (0.299f * sumR + 0.587f * sumG + 0.114f * sumB) / (count * 255f)
    return if (avgLuminance > 0.55f) Color(0xFF1A1A1A) else Color.White
}

// ==================== 语言切换 ====================

/**
 * 应用语言设置。
 * Android 13+ 使用 [android.app.LocaleManager] / AppCompatDelegate，
 * 低版本使用 [android.content.res.Configuration.setLocale]。
 */
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