package com.winter.muplayer.base_ui

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.winter.muplayer.base_ui.ui.theme.AppTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.unit.dp
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.core.QueueEntry
import com.winter.muplayer.core.scanner.LocalMusicScanner
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import com.winter.muplayer.plugin_runtime.PluginWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

// ==================== 主界面 Activity ====================

class MusicUIActivity : ComponentActivity() {

    private lateinit var musicPlayerCore: MusicPlayerCore

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "需要音频权限才能读取本地音乐", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_AUDIO)
        }

        musicPlayerCore = MusicPlayerCore.getInstance(applicationContext)
        com.winter.muplayer.core.AppLogger.i("UI", "MusicUIActivity.onCreate")

        setContent {
            val settings = musicPlayerCore.settings
            val themeMode = settings.themeMode
            val dynamicColor = settings.dynamicColorEnabled

            val isDark = when (themeMode) {
                com.winter.muplayer.core.SettingsManager.ThemeMode.SYSTEM ->
                    isSystemInDarkTheme()
                com.winter.muplayer.core.SettingsManager.ThemeMode.DARK -> true
                com.winter.muplayer.core.SettingsManager.ThemeMode.LIGHT -> false
            }

            AppTheme(
                darkTheme = isDark,
                dynamicColor = dynamicColor
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicPlayerApp(musicPlayerCore = musicPlayerCore)
                }
            }
        }
    }
}

// ==================== 主界面组件 ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(musicPlayerCore: MusicPlayerCore) {
    val playerState by musicPlayerCore.playerState.collectAsState()
    val playMode by musicPlayerCore.playMode.collectAsState()
    val queue by musicPlayerCore.queueManager.queue.collectAsState()
    val currentIndex by musicPlayerCore.queueManager.currentIndex.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val coverCache = remember { mutableStateMapOf<Long, String>() }
    val scanner = remember { LocalMusicScanner(context) }

    var showPluginManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }
    var showTrackDetail by remember { mutableStateOf<Track?>(null) }
    var showQueue by remember { mutableStateOf(false) }

    // 本地音乐列表
    var localMusicList by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoadingLocal by remember { mutableStateOf(false) }

    // 插件 UI Slot（Shadow 架构：插件通过 IPlugin.getSlotView 提供 View，
    // PluginWidget 用于 Compose 原生渲染——此处为兼容旧 Slot 机制保留）
    val slotWidgets =
        remember { mutableStateMapOf<String, List<com.winter.muplayer.plugin_runtime.PluginWidget>>() }

    // 首次加载 + 曲目切换时刷新插件状态
    LaunchedEffect(playerState.currentTrack?.id) {
        // Shadow 架构下插件已通过 loadAll 加载，无需在每次切歌时重复发现。
        // slotWidgets 目前为空，未来插件可通过 ShadowPluginHost.getSlotWidgets() 填充。
    }

    // 首次加载时扫描本地音乐（受设置控制）
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            val permissionGranted = android.Manifest.permission.READ_MEDIA_AUDIO.let { perm ->
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, perm
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (!permissionGranted) return@LaunchedEffect
        }
        if (!musicPlayerCore.settings.autoScanOnStart) return@LaunchedEffect
        isLoadingLocal = true
        val tracks = withContext(Dispatchers.IO) { scanner.scan() }
        for (track in tracks) {
            if (track.albumId > 0L || coverCache.containsKey(track.id)) continue
            withContext(Dispatchers.IO) {
                val filePath = track.uri.removePrefix("file://")
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(filePath)
                    val picture = retriever.embeddedPicture ?: return@withContext
                    val cacheDir = File(context.cacheDir, "covers")
                    cacheDir.mkdirs()
                    val coverFile = File(cacheDir, "${track.id}.jpg")
                    coverFile.writeBytes(picture)
                    coverCache[track.id] = coverFile.absolutePath
                } catch (_: Exception) {
                } finally {
                    retriever.release()
                }
            }
        }
        localMusicList = tracks
        isLoadingLocal = false
    }

    // ==================== 布局 ====================

    Box(modifier = Modifier.fillMaxSize()) {
        // 设置页面 — 从右滑入，滑出到右
        AnimatedVisibility(
            visible = showSettings,
            enter = slideInHorizontally(animationSpec = tween(200)) { it },
            exit = slideOutHorizontally(animationSpec = tween(200)) { it }
        ) {
            BackHandler { showSettings = false }
            val settings = musicPlayerCore.settings
            SettingsScreen(
                settings = settings,
                onBack = { showSettings = false },
                onRescan = {
                    scope.launch {
                        scanner.invalidateCache()
                        isLoadingLocal = true
                        val tracks = withContext(Dispatchers.IO) { scanner.scan() }
                        for (track in tracks) {
                            if (track.albumId > 0L || coverCache.containsKey(track.id)) continue
                            withContext(Dispatchers.IO) {
                                val filePath = track.uri.removePrefix("file://")
                                val retriever = android.media.MediaMetadataRetriever()
                                try {
                                    retriever.setDataSource(filePath)
                                    val picture = retriever.embeddedPicture ?: return@withContext
                                    val cacheDir = File(context.cacheDir, "covers")
                                    cacheDir.mkdirs()
                                    val coverFile = File(cacheDir, "${track.id}.jpg")
                                    coverFile.writeBytes(picture)
                                    coverCache[track.id] = coverFile.absolutePath
                                } catch (_: Exception) {
                                } finally {
                                    retriever.release()
                                }
                            }
                        }
                        localMusicList = tracks
                        isLoadingLocal = false
                    }
                },
                cacheInfo = com.winter.muplayer.base_ui.CacheInfo(
                    formattedSize = run {
                        val dir = File(context.cacheDir, "covers")
                        if (!dir.exists()) return@run "0 KB"
                        val bytes = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                        when {
                            bytes < 1024 -> "0 KB"
                            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
                        }
                    },
                    onClearCache = {
                        coverCache.clear()
                        context.cacheDir.deleteRecursively()
                        context.cacheDir.mkdirs()
                    }
                )
            )
        }

        // 插件管理 — 从右滑入，滑出到右
        AnimatedVisibility(
            visible = showPluginManager,
            enter = slideInHorizontally(animationSpec = tween(200)) { it },
            exit = slideOutHorizontally(animationSpec = tween(200)) { it }
        ) {
            BackHandler { showPluginManager = false }
            PluginManagerScreen(
                shadowPluginHost = musicPlayerCore.shadowPluginHost,
                onBack = { showPluginManager = false }
            )
        }

        // 主界面 — 从左滑入（返回时）
        AnimatedVisibility(
            visible = !showSettings && !showPluginManager,
            enter = slideInHorizontally(animationSpec = tween(200)) { -it },
            exit = slideOutHorizontally(animationSpec = tween(200)) { -it }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Text(
                            text = "WMPlayer",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        IconButton(onClick = { showPluginManager = true }) {
                            Icon(
                                painterResource(R.drawable.ic_extendsion),
                                contentDescription = "插件管理"
                            )
                        }
                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                painterResource(R.drawable.ic_settings),
                                contentDescription = "设置"
                            )
                        }
                    }
                )

                Box(modifier = Modifier.weight(1f)) {
                    LocalMusicBrowser(
                        tracks = localMusicList,
                        isLoading = isLoadingLocal,
                        coverCache = coverCache,
                        onTrackClick = { track, contextTracks ->
                            musicPlayerCore.playTrackSmart(track, contextTracks)
                        }
                    )
                }

                MiniPlayerBar(
                    playerState = playerState,
                    coverCache = coverCache,
                    onPlay = musicPlayerCore::play,
                    onPause = musicPlayerCore::pause,
                    onNext = musicPlayerCore::playNext,
                    onPrevious = musicPlayerCore::playPrevious,
                    onClick = { showFullPlayer = true },
                    onShowQueue = { showQueue = true }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }

    // ====== 全屏播放面板（从下滑入，退出由面板内手动控制） ======
    AnimatedVisibility(
        visible = showFullPlayer,
        enter = slideInVertically(animationSpec = tween(300)) { it },
        exit = slideOutVertically(animationSpec = tween(0)) { it }
    ) {
        BackHandler { showFullPlayer = false }
        FullPlayerPanel(
            playerState = playerState,
            playMode = playMode,
            coverCache = coverCache,
            slotWidgets = slotWidgets["below_controls"] ?: emptyList(),
            blurBackground = musicPlayerCore.settings.blurBackground,
            onPlay = musicPlayerCore::play,
            onPause = musicPlayerCore::pause,
            onNext = musicPlayerCore::playNext,
            onPrevious = musicPlayerCore::playPrevious,
            onSeek = musicPlayerCore::seekTo,
            onPlayModeChange = musicPlayerCore::setPlayMode,
            onShowQueue = { showQueue = true },
            onDismiss = { showFullPlayer = false }
        )
    }

    // ====== 播放队列面板 ======
    if (showQueue) {
        QueueSheet(
            queue = queue,
            currentIndex = currentIndex,
            coverCache = coverCache,
            slotWidgets = slotWidgets["above_queue"] ?: emptyList(),
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
}
// ==================== 迷你底部播放条（参考网易云音乐风格） ====================

@Composable
fun MiniPlayerBar(
    playerState: PlayerStateData,
    coverCache: Map<Long, String>,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClick: () -> Unit,
    onShowQueue: () -> Unit
) {
    val currentTrack = playerState.currentTrack
    val isPlaying = playerState.state == PlayerState.PLAYING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面缩略图
            if (currentTrack != null) {
                AlbumThumb(
                    albumTrack = currentTrack,
                    coverCache = coverCache,
                    size = 56.dp
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            // 歌曲信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentTrack?.title ?: "未在播放",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (currentTrack != null) {
                    Text(
                        text = currentTrack.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 上一首
            IconButton(onClick = onPrevious) {
                Icon(
                    painterResource(R.drawable.ic_skip_previous),
                    contentDescription = "上一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            // 播放/暂停
            Surface(
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                tonalElevation = 2.dp
            ) {
                IconButton(
                    onClick = { if (isPlaying) onPause() else onPlay() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        painter = if (isPlaying) painterResource(R.drawable.ic_pause)
                        else painterResource(R.drawable.ic_play),
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 下一首
            IconButton(onClick = onNext) {
                Icon(
                    painterResource(R.drawable.ic_skip_next),
                    contentDescription = "下一首",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            // 播放列表按钮
            IconButton(onClick = onShowQueue) {
                Icon(
                    painterResource(R.drawable.ic_playlist_music),
                    contentDescription = "播放列表",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

// ==================== 全屏播放面板（BottomSheet） ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerPanel(
    playerState: PlayerStateData,
    playMode: PlayMode,
    coverCache: Map<Long, String>,
    slotWidgets: List<PluginWidget>,
    blurBackground: Boolean = false,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayModeChange: (PlayMode) -> Unit,
    onShowQueue: () -> Unit,
    onDismiss: () -> Unit
) {
    val currentTrack = playerState.currentTrack
    val isPlaying = playerState.state == PlayerState.PLAYING
    val scope = rememberCoroutineScope()
    var offsetY by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(0f) }
    var isExiting by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val performDismiss: () -> Unit = {
        scope.launch {
            isExiting = true
            animate(initialValue = offsetY, targetValue = itemHeight) { value, _ ->
                offsetY = value
            }
            onDismiss()
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

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
                val hasCover = coverCache.containsKey(currentTrack.id) || currentTrack.albumId > 0L
                if (hasCover) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(getAlbumArtUri(currentTrack, coverCache))
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

            // ====== 前景内容 ======
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ====== 封面（占上半部空间，下滑退出手势） ======
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 12.dp)
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (offsetY > itemHeight * 0.25f) {
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
                        val hasCover = coverCache.containsKey(currentTrack.id) || currentTrack.albumId > 0L
                        if (hasCover) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(getAlbumArtUri(currentTrack, coverCache))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .shadow(16.dp, RoundedCornerShape(24.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .shadow(16.dp, RoundedCornerShape(24.dp)),
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
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(24.dp))
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
                        text = currentTrack?.title ?: "未选择曲目",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (currentTrack != null) {
                        Text(
                            text = "${currentTrack.artist} • ${currentTrack.album}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    // ====== 进度条 ======
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = if (playerState.duration > 0)
                                playerState.progress.toFloat() / playerState.duration.toFloat()
                            else 0f,
                            onValueChange = { fraction ->
                                onSeek((fraction * playerState.duration).toLong())
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(playerState.progress),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = formatDuration(playerState.duration),
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
                            }
                        )

                        // 上一首
                        ControlButton(
                            icon = painterResource(R.drawable.ic_skip_previous),
                            onClick = onPrevious,
                            size = 48.dp
                        )

                        // 播放/暂停
                        PlayPauseButton(
                            isPlaying = isPlaying,
                            isLoading = playerState.state == PlayerState.LOADING,
                            onPlay = onPlay,
                            onPause = onPause
                        )

                        // 下一首
                        ControlButton(
                            icon = painterResource(R.drawable.ic_skip_next),
                            onClick = onNext,
                            size = 48.dp
                        )

                        // 播放列表
                        IconButton(onClick = onShowQueue) {
                            Icon(
                                painterResource(R.drawable.ic_playlist_music),
                                contentDescription = "播放列表",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // ====== 插件 Slot ======
                    if (slotWidgets.isNotEmpty()) {
                        PluginSlot(
                            slotName = "below_controls",
                            widgets = slotWidgets,
                            onWidgetAction = { action ->
                                android.util.Log.d("PluginSlot", "Widget action: $action")
                            }
                        )
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
    coverCache: Map<Long, String>,
    slotWidgets: List<PluginWidget>,
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
                    text = "当前播放",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (queue.isNotEmpty()) {
                    TextButton(onClick = onClearQueue) {
                        Text("清空")
                    }
                }
            }

            // 插件 Slot
            if (slotWidgets.isNotEmpty()) {
                PluginSlot(
                    slotName = "above_queue",
                    widgets = slotWidgets,
                    onWidgetAction = { action ->
                        android.util.Log.d("PluginSlot", "Widget action: $action")
                    }
                )
            }

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

@Composable
fun PlayerControlCard(
    playerState: PlayerStateData,
    playMode: PlayMode,
    coverCache: Map<Long, String>,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayModeChange: (PlayMode) -> Unit
) {
    val currentTrack = playerState.currentTrack
    val isPlaying = playerState.state == PlayerState.PLAYING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 封面（可旋转动画）
            AlbumCover(
                track = currentTrack,
                isPlaying = isPlaying,
                coverCache = coverCache
            )

            Spacer(Modifier.height(20.dp))

            // 歌名 & 艺术家（跑马灯）
            TrackInfoMarquee(
                track = currentTrack,
                isPlaying = isPlaying
            )

            Spacer(Modifier.height(16.dp))

            // 进度条
            PlayerProgressBar(
                progress = playerState.progress,
                duration = playerState.duration,
                onSeek = onSeek
            )

            Spacer(Modifier.height(16.dp))

            // 控制按钮（播放模式、上下首、播放/暂停等）
            PlayerControls(
                playerState = playerState.state,
                playMode = playMode,
                onPlay = onPlay,
                onPause = onPause,
                onNext = onNext,
                onPrevious = onPrevious,
                onPlayModeChange = onPlayModeChange
            )
        }
    }
}

// ==================== 专辑封面组件 ====================

@Composable
fun AlbumCover(
    track: Track?,
    isPlaying: Boolean,
    coverCache: Map<Long, String>
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        if (track != null) {
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
                        .clip(CircleShape)
                        .graphicsLayer {
                            shadowElevation = 8f
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .shadow(16.dp, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painterResource(R.drawable.ic_music_off),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .shadow(16.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_music_off),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

// ==================== 曲目信息 & 跑马灯 ====================

@Composable
fun TrackInfoMarquee(
    track: Track?,
    isPlaying: Boolean
) {
    if (track == null) {
        Text(
            text = "未选择曲目",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MarqueeText(
            text = track.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            isPlaying = isPlaying
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${track.artist} • ${track.album}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MarqueeText(
    text: String,
    style: TextStyle,
    isPlaying: Boolean,
    textAlign: TextAlign? = null
) {
    val textWidth = remember { mutableIntStateOf(0) }
    val containerWidth = remember { mutableIntStateOf(0) }

    val offset = remember { Animatable(0f) }

    val isOverflowing = textWidth.intValue > containerWidth.intValue

    // 定时测量宽度，确保布局稳定后再检测溢出
    LaunchedEffect(text) {
        // 等一帧让布局完成
        delay(100)
        if (isOverflowing && isPlaying) {
            while (true) {
                offset.animateTo(
                    targetValue = -(textWidth.intValue - containerWidth.intValue).toFloat(),
                    animationSpec = tween(
                        durationMillis = (text.length * 150).coerceAtLeast(2000),
                        easing = LinearEasing
                    )
                )
                delay(1500)
                offset.animateTo(0f, animationSpec = tween(300))
                delay(2000)
            }
        } else {
            offset.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clipToBounds()
            .onSizeChanged { containerWidth.intValue = it.width }
    ) {
        Text(
            text = text,
            style = style,
            maxLines = 1,
            textAlign = textAlign,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .onSizeChanged { textWidth.intValue = it.width }
        )
    }
}

// ==================== 进度条组件 ====================

@Composable
fun PlayerProgressBar(
    progress: Long,
    duration: Long,
    onSeek: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = if (duration > 0) progress.toFloat() / duration.toFloat() else 0f,
            onValueChange = { fraction ->
                onSeek((fraction * duration).toLong())
            },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
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
}

// ==================== 播放控制按钮组 ====================

@Composable
fun PlayerControls(
    playerState: PlayerState,
    playMode: PlayMode,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onPlayModeChange: (PlayMode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
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
            }
        )

        ControlButton(
            icon = painterResource(R.drawable.ic_skip_previous),
            onClick = onPrevious,
            size = 48.dp
        )

        PlayPauseButton(
            isPlaying = playerState == PlayerState.PLAYING,
            isLoading = playerState == PlayerState.LOADING,
            onPlay = onPlay,
            onPause = onPause
        )

        ControlButton(
            icon = painterResource(R.drawable.ic_skip_next),
            onClick = onNext,
            size = 48.dp
        )
    }
}

// ==================== 播放/暂停按钮 ====================

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit
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
            .shadow(8.dp, CircleShape)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape
            )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 3.dp
            )
        } else {
            Icon(
                painter = if (isPlaying) painterResource(R.drawable.ic_pause) else painterResource(R.drawable.ic_play),
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

// ==================== 播放模式按钮 ====================

@Composable
fun PlayModeButton(
    playMode: PlayMode,
    onClick: () -> Unit
) {
    val icon = when (playMode) {
        PlayMode.SEQUENTIAL -> painterResource(R.drawable.ic_shuffle_disabled)
        PlayMode.SHUFFLE -> painterResource(R.drawable.ic_shuffle)
        PlayMode.SINGLE_LOOP -> painterResource(R.drawable.ic_repeat_one)
        PlayMode.REPEAT_ALL -> painterResource(R.drawable.ic_repeat)
    }

    val label = when (playMode) {
        PlayMode.SEQUENTIAL -> "顺序"
        PlayMode.SHUFFLE -> "随机"
        PlayMode.SINGLE_LOOP -> "单曲"
        PlayMode.REPEAT_ALL -> "循环"
    }

    IconButton(onClick = onClick) {
        Icon(
            painter = icon,
            contentDescription = label,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ==================== 通用控制按钮 ====================

@Composable
fun ControlButton(
    icon: Painter,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(size * 0.65f),
            tint = MaterialTheme.colorScheme.onSurface
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
                    text = "播放队列为空",
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
                        overflow = TextOverflow.Ellipsis
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
                        contentDescription = "删除",
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
                text = "曲目详情",
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

                DetailItem("标题", track.title)
                DetailItem("艺术家", track.artist)
                DetailItem("专辑", track.album)
                DetailItem("时长", formatDuration(track.duration))
                DetailItem("文件路径", track.uri)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
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

/**
 * 加载歌曲封面并缓存到本地文件。
 * 在协程上下文中调用，使用 withContext 切换到 IO 线程。
 */
private suspend fun loadCoverIfNeeded(
    track: Track,
    coverCache: MutableMap<Long, String>,
    context: android.content.Context
) {
    if (track.albumId > 0L || coverCache.containsKey(track.id)) return
    val filePath = track.uri.removePrefix("file://")
    withContext(Dispatchers.IO) {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(filePath)
            val picture = retriever.embeddedPicture ?: return@withContext
            val cacheDir = File(context.cacheDir, "covers")
            cacheDir.mkdirs()
            val coverFile = File(cacheDir, "${track.id}.jpg")
            coverFile.writeBytes(picture)
            coverCache[track.id] = coverFile.absolutePath
        } catch (_: Exception) {
            // 无封面
        } finally {
            retriever.release()
        }
    }
}


