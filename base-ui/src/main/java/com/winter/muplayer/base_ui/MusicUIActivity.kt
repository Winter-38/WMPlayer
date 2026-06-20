package com.winter.muplayer.base_ui

import android.os.Bundle
import android.os.Build
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.core.scanner.LocalMusicScanner
import com.winter.muplayer.model.*
import com.winter.muplayer.plugin.PluginHost
import com.winter.muplayer.plugin_api.SlotWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================== 主 Activity ====================

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
        // 请求权限
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_AUDIO)
        }

        musicPlayerCore = MusicPlayerCore.getInstance(applicationContext)

        setContent {
            MaterialTheme {
                MusicPlayerApp(musicPlayerCore = musicPlayerCore)
            }
        }
    }
}


// ==================== 主应用组件 ====================
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

    // 插件 UI Slot
    val slotHost = remember { PluginHost(context) }
    val slotWidgets = remember { mutableStateMapOf<String, List<SlotWidget>>() }

    // 曲目切换时刷新插件 UI Slot
    LaunchedEffect(playerState.currentTrack?.id) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            slotHost.discover()
            slotHost.refreshSlots()
        }
        slotWidgets.clear()
        slotWidgets.putAll(slotHost.slotWidgets)
    }

    val rotation = remember { Animatable(0f) }

    var showPluginManager by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showTrackDetail by remember { mutableStateOf<Track?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showLocalMusicSheet by remember { mutableStateOf(false) }
    var localMusicList by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoadingLocal by remember { mutableStateOf(false) }

    /** 惰性加载文件系统曲目的封面（仅 albumId==0 的旧文件系统 track） */
    fun loadCoverIfNeeded(track: Track) {
        if (track.albumId > 0L || coverCache.containsKey(track.id)) return
        val filePath = track.uri.removePrefix("file://")
        scope.launch(Dispatchers.IO) {
            val retriever = android.media.MediaMetadataRetriever()
            try {
                retriever.setDataSource(filePath)
                val picture = retriever.embeddedPicture ?: return@launch
                val cacheDir = java.io.File(context.cacheDir, "covers")
                cacheDir.mkdirs()
                val coverFile = java.io.File(cacheDir, "${track.id}.jpg")
                coverFile.writeBytes(picture)
                coverCache[track.id] = coverFile.absolutePath
            } catch (_: Exception) {
                // 无封面
            } finally {
                retriever.release()
            }
        }
    }

    fun loadLocalMusic() {
        scope.launch {
            if (Build.VERSION.SDK_INT >= 33) {
                val permissionGranted = android.Manifest.permission.READ_MEDIA_AUDIO.let { perm ->
                    androidx.core.content.ContextCompat.checkSelfPermission(context, perm) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }
                if (!permissionGranted) {
                    Toast.makeText(context, "需要音频权限才能读取本地音乐", Toast.LENGTH_SHORT).show()
                    isLoadingLocal = false
                    return@launch
                }
            }
            isLoadingLocal = true
            localMusicList = withContext(Dispatchers.IO) {
                scanner.scan()
            }
            // 对文件系统 track 触发封面惰性加载（异步，不阻塞）
            for (track in localMusicList) {
                loadCoverIfNeeded(track)
            }
            isLoadingLocal = false
        }
    }



    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "WMPlayer-0.2.3-SNAPSHOT",
                        fontWeight = FontWeight.Bold
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    IconButton(onClick = { showPluginManager = !showPluginManager }) {
                        Icon(painterResource(R.drawable.ic_extendsion), contentDescription = "插件管理")
                    }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = "设置")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    showLocalMusicSheet = true
                    if (localMusicList.isEmpty()) {
                        loadLocalMusic()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    painterResource(R.drawable.ic_library_music),
                    contentDescription = "添加本地音乐",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (showSettings) {
                BackHandler(enabled = showSettings) {
                    showSettings = false
                }
                SettingsScreen(onBack = { showSettings = false })
            } else if (showPluginManager) {
                BackHandler(enabled = showPluginManager) {
                    showPluginManager = false
                }
                PluginManagerScreen()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        PlayerControlCard(
                            playerState = playerState,
                            playMode = playMode,
                            coverCache = coverCache,
                            onPlay = musicPlayerCore::play,
                            onPause = musicPlayerCore::pause,
                            onNext = musicPlayerCore::playNext,
                            onPrevious = musicPlayerCore::playPrevious,
                            onSeek = musicPlayerCore::seekTo,
                            onPlayModeChange = musicPlayerCore::setPlayMode
                        )
                    }

                    // 插件 UI Slot：控制栏下方
                    item {
                        PluginSlot(
                            slotName = "below_controls",
                            widgets = slotWidgets["below_controls"] ?: emptyList()
                        )
                    }

                    item {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // 插件 UI Slot：列表上方
                    item {
                        PluginSlot(
                            slotName = "above_queue",
                            widgets = slotWidgets["above_queue"] ?: emptyList()
                        )
                    }

                    item {
                        PlayQueueSection(
                            queue = queue,
                            currentIndex = currentIndex,
                            searchQuery = searchQuery,
                            coverCache = coverCache,
                            onPlayTrack = musicPlayerCore::playTrackAtIndex,
                            onRemoveTrack = musicPlayerCore::removeTrack,
                            onClearQueue = musicPlayerCore::clearQueue,
                            onTrackLongPress = { track -> showTrackDetail = track },
                            modifier = Modifier.heightIn(max = 600.dp)
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }

    // 本地音乐搜索 BottomSheet
    @Suppress("UNUSED_VALUE")
    if (showLocalMusicSheet) {
        LocalMusicBottomSheet(
            tracks = localMusicList,
            isLoading = isLoadingLocal,
            coverCache = coverCache,
            onDismiss = { showLocalMusicSheet = false },
            onAddTrack = { track ->
                musicPlayerCore.addTrack(track)
            },
            onAddAll = {
                musicPlayerCore.addTracks(localMusicList)
                showLocalMusicSheet = false
            }
        )
    }

    // 曲目详情弹窗
    showTrackDetail?.let { track ->
        TrackDetailDialog(
            track = track,
            coverCache = coverCache,
            onDismiss = { showTrackDetail = null }
        )
    }
}

// ==================== 播放控制卡片 ====================

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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.surfaceVariant
                        )
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 专辑封面
            AlbumArtwork(
                track = playerState.currentTrack,
                isPlaying = playerState.state == PlayerState.PLAYING,
                coverCache = coverCache
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 曲目信息（跑马灯效果）
            TrackInfoMarquee(
                track = playerState.currentTrack,
                isPlaying = playerState.state == PlayerState.PLAYING
            )

            Spacer(modifier = Modifier.height(16.dp))
            // 进度条
            SeekBarWithPreview(
                progress = playerState.progress,
                duration = playerState.duration,
                onSeek = onSeek,
                enabled = playerState.currentTrack != null
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 播放控制按钮
            PlaybackControls(
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

// ==================== 专辑封面（带旋转动画） ====================

@Composable
fun AlbumArtwork(
    track: Track?,
    isPlaying: Boolean,
    coverCache: Map<Long, String>,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    // 切歌时复位旋转角
    LaunchedEffect(track?.id) {
        rotation.snapTo(0f)
    }

    // 持续前进旋转（永不跳回 0，暂停时停在当前位置）
    LaunchedEffect(isPlaying) {
        if (isPlaying && track != null) {
            while (isActive) {
                rotation.animateTo(
                    targetValue = rotation.value + 360f,
                    animationSpec = tween(8000, easing = LinearEasing)
                )
            }
        }
    }

    // 缩放动画（播放时微放大 + 暂停时复位）
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            scale.animateTo(1.08f, animationSpec = spring(dampingRatio = 0.5f))
        } else {
            scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.5f))
        }
    }

    Box(
        modifier = modifier
            .scale(scale.value)
            .size(200.dp),
        contentAlignment = Alignment.Center
    ) {
        if (track != null) {
            val hasCover = coverCache.containsKey(track.id) || track.albumId > 0L
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE1BEE7)) // 淡紫色占位背景
                    .shadow(16.dp, CircleShape)
            ) {
                if (hasCover) {
                    val coverData = getAlbumArtUri(track, coverCache)
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverData)
                            .crossfade(true)
                            .build(),
                        contentDescription = "专辑封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .graphicsLayer {
                                rotationZ = rotation.value
                                shadowElevation = 8f
                            }
                    )
                }
            }
        } else {
            // 空白状态：淡紫色圆形 + 音乐图标
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFFE1BEE7)) // 淡紫色（替换了原 sweepGradient 多边形渐变）
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

// ==================== 曲目信息跑马灯 ====================

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
        // 标题（跑马灯）
        MarqueeText(
            text = track.title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold
            ),
            isPlaying = isPlaying
        )

        Spacer(modifier = Modifier.height(4.dp))

        // 艺术家和专辑
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
    isPlaying: Boolean
) {
    val density = LocalDensity.current
    val textWidth = remember { mutableIntStateOf(0) }
    val containerWidth = remember { mutableIntStateOf(0) }

    val offset = remember { Animatable(0f) }

    LaunchedEffect(text, isPlaying, textWidth.intValue, containerWidth.intValue) {
        if (textWidth.intValue > containerWidth.intValue && isPlaying) {
            while (true) {
                offset.animateTo(
                    targetValue = -(textWidth.intValue - containerWidth.intValue).toFloat(),
                    animationSpec = tween(
                        durationMillis = text.length * 150,
                        easing = LinearEasing
                    )
                )
                delay(1000)
                offset.snapTo(containerWidth.intValue.toFloat())
            }
        } else {
            offset.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .onSizeChanged { containerWidth.intValue = it.width }
            .clipToBounds()
    ) {
        Text(
            text = text,
            style = style,
            textAlign = TextAlign.Center,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = with(density) { offset.value.toDp() })
                .onSizeChanged { textWidth.intValue = it.width }
        )
    }
}

// ==================== 进度条（带预览） ====================
@Composable
fun SeekBarWithPreview(
    progress: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    enabled: Boolean
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    var showPreview by remember { mutableStateOf(false) }

    val progressFraction = if (enabled && duration > 0) {
        if (isDragging) dragPosition
        else (progress.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(modifier = Modifier.fillMaxWidth()) {
        // 预览时间提示
        if (showPreview && enabled) {
            val previewTime = (dragPosition * duration).toLong()
            Text(
                text = formatDuration(previewTime),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                textAlign = TextAlign.Center
            )
        }

        Slider(
            value = progressFraction,
            onValueChange = { value ->
                if (enabled) {
                    dragPosition = value
                    isDragging = true
                    showPreview = true
                }
            },
            onValueChangeFinished = {
                if (enabled) {
                    onSeek((dragPosition * duration).toLong())
                    isDragging = false
                    showPreview = false
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),  // 删除了 .pointerInput 修饰符
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )

        // 时间标签
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatDuration(if (isDragging) (dragPosition * duration).toLong() else progress),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = formatDuration(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 播放控制按钮 ====================

@Composable
fun PlaybackControls(
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
        // 播放模式
        PlayModeButton(
            playMode = playMode,
            onClick = {
                val newMode = when (playMode) {
                    PlayMode.SEQUENTIAL -> PlayMode.SHUFFLE
                    PlayMode.SHUFFLE -> PlayMode.SINGLE_LOOP
                    PlayMode.SINGLE_LOOP -> PlayMode.SEQUENTIAL
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
            isPlaying = playerState == PlayerState.PLAYING,
            isLoading = playerState == PlayerState.LOADING,
            onPlay = onPlay,
            onPause = onPause
        )

        // 下一首
        ControlButton(
            icon = painterResource(R.drawable.ic_skip_next),
            onClick = onNext,
            size = 48.dp
        )
    }
}

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }
    val iconTransition = remember { Animatable(if (isPlaying) 1f else 0f) }

    // 点击弹跳效果
    LaunchedEffect(isPressed) {
        if (isPressed) {
            scale.animateTo(0.9f, animationSpec = spring(dampingRatio = 0.8f))
            scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.4f))
        }
    }

    // 播放/暂停图标平滑过渡
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
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.8f
                        )
                    )
                ),
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
}@Composable
fun PlayModeButton(
    playMode: PlayMode,
    onClick: () -> Unit
) {
    val icon = when (playMode) {
        PlayMode.SEQUENTIAL -> painterResource(R.drawable.ic_shuffle_disabled)
        PlayMode.SHUFFLE -> painterResource(R.drawable.ic_shuffle)
        PlayMode.SINGLE_LOOP -> painterResource(R.drawable.ic_repeat_one)
    }

    val label = when (playMode) {
        PlayMode.SEQUENTIAL -> "顺序"
        PlayMode.SHUFFLE -> "随机"
        PlayMode.SINGLE_LOOP -> "单曲"
    }

    val isActive = playMode != PlayMode.SEQUENTIAL
    val activeColor = MaterialTheme.colorScheme.primary

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.1f)
                    else Color.Transparent
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = icon,
                contentDescription = label,
                modifier = Modifier.size(24.dp),
                tint = if (isActive) activeColor
                       else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) activeColor
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ControlButton(
    icon: Painter,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
    ) {
        Icon(
            painter = icon,
            contentDescription = null,
            modifier = Modifier.size(size - 16.dp),
            tint = tint
        )
    }
}

// ==================== 搜索栏 ====================

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("搜索本地音乐...") },
        leadingIcon = {
            Icon(painterResource(R.drawable.ic_search_off), contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(painterResource(R.drawable.ic_clear), contentDescription = "清除")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}

// ==================== 播放队列 ====================

@Composable
fun PlayQueueSection(
    queue: List<Track>,
    currentIndex: Int,
    searchQuery: String,
    coverCache: Map<Long, String>,
    onPlayTrack: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onTrackLongPress: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val filteredQueue = remember(queue, searchQuery) {
        if (searchQuery.isBlank()) queue
        else queue.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true) ||
                    it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "播放队列 (${queue.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (queue.isNotEmpty()) {
                TextButton(onClick = onClearQueue) {
                    Text("清空")
                }
            }
        }

        if (filteredQueue.isEmpty()) {
            EmptyQueuePlaceholder(
                isEmpty = queue.isEmpty(),
                isNoResult = searchQuery.isNotBlank() && queue.isNotEmpty()
            )
        } else {
            // 使用 LazyColumn 优化长列表，配合 heightIn 限制高度
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)  // 使用 weight 避免无限高度
            ) {
                itemsIndexed(
                    items = filteredQueue,
                    key = { _, track -> track.id }  // key 优化
                ) { index, track ->
                    val originalIndex = queue.indexOf(track)
                    QueueItem(
                        track = track,
                        isCurrentTrack = originalIndex == currentIndex,
                        coverCache = coverCache,
                        onPlay = { onPlayTrack(originalIndex) },
                        onRemove = { onRemoveTrack(originalIndex) },
                        onLongPress = { onTrackLongPress(track) }
                    )
                }
            }
        }
    }
}
@Composable
fun EmptyQueuePlaceholder(isEmpty: Boolean, isNoResult: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = if (isEmpty) painterResource(R.drawable.ic_playlist_music) else painterResource(R.drawable.ic_search_off),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = if (isEmpty) "播放队列为空" else "未找到匹配的曲目",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(
                text = if (isEmpty) "添加一些音乐开始播放吧" else "尝试其他关键词",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueItem(
    track: Track,
    isCurrentTrack: Boolean,
    coverCache: Map<Long, String>,
    onPlay: () -> Unit,
    onRemove: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { dismissValue ->
            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                onRemove()
                true
            } else {
                false
            }
        }
    )

    val animatedBgColor by animateColorAsState(
        targetValue = if (isCurrentTrack)
            MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surface,
        animationSpec = tween(300), label = "queueItemBg"
    )

    SwipeToDismissBox(
        state = swipeState,
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete),
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        content = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .combinedClickable(
                        onClick = onPlay,
                        onLongClick = onLongPress
                    ),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = animatedBgColor
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isCurrentTrack) 4.dp else 0.dp
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
                    Spacer(modifier = Modifier.width(12.dp))

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
                                .background(Color(0xFFE1BEE7))
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCurrentTrack)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isCurrentTrack) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    painterResource(R.drawable.ic_music_note),
                                    contentDescription = "正在播放",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${track.artist} • ${track.album}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isCurrentTrack)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 时长标签
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = formatDuration(track.duration),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    )
}

// ==================== 本地音乐 BottomSheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicBottomSheet(
    tracks: List<Track>,
    isLoading: Boolean,
    coverCache: Map<Long, String>,
    onDismiss: () -> Unit,
    onAddTrack: (Track) -> Unit,
    onAddAll: () -> Unit
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
                .heightIn(min = 300.dp, max = 500.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "本地音乐",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (tracks.isNotEmpty()) {
                    TextButton(onClick = onAddAll) {
                        Text("全部添加")
                    }
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (tracks.isEmpty()) {
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
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "未找到本地音乐文件",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn {
                    itemsIndexed(
                        items = tracks,
                        key = { _, track -> track.id }
                    ) { _, track ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clickable { onAddTrack(track) },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(getAlbumArtUri(track, coverCache))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} • ${track.album}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Icon(
                                    painter = painterResource(R.drawable.ic_add),
                                    contentDescription = "添加",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // 底部间距（处理导航栏）
            Spacer(modifier = Modifier.height(16.dp))
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

                Spacer(modifier = Modifier.height(16.dp))

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

// ==================== 工具函数 ====================

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}

fun getAlbumArtUri(track: Track, coverCache: Map<Long, String>): Any? {
    // MediaStore 方式：直接使用 album art content URI（Coil 可直接加载）
    if (track.albumId > 0L) {
        return android.net.Uri.parse(
            "content://media/external/audio/albumart/${track.albumId}"
        )
    }
    // 文件系统方式：检查 coverCache 中的本地提取封面
    return coverCache[track.id] // null 表示无封面
}
