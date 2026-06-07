package com.winter.muplayer.base_ui

import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
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
import com.winter.muplayer.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ==================== 主 Activity ====================

class MusicUIActivity : ComponentActivity() {

    private lateinit var musicPlayerCore: MusicPlayerCore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 通过单例获取内核实例
        musicPlayerCore = MusicPlayerCore.getInstance(applicationContext)

        setContent {
            MaterialTheme {
                MusicPlayerApp(musicPlayerCore = musicPlayerCore)
            }
        }
    }
}


// ==================== 主应用组件 ====================
private fun collectMusicFiles(dir: java.io.File, tracks: MutableList<Track>) {
    val files = dir.listFiles() ?: return
    for (file in files) {
        when {
            file.isDirectory -> com.winter.muplayer.base_ui.collectMusicFiles(file, tracks)
            file.name.lowercase().matches(Regex(".*\\.(mp3|flac|wav|m4a|ogg)$")) -> {
                tracks.add(createTrackFromFile(file))
            }
        }
    }
}

fun createTrackFromFile(file: java.io.File): Track {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        Track(
            id = file.hashCode().toLong(),
            title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.nameWithoutExtension,
            artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: "未知艺术家",
            album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "未知专辑",
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0,
            uri = file.absolutePath
        )
    } catch (e: Exception) {
        Track(
            id = file.hashCode().toLong(),
            title = file.nameWithoutExtension,
            artist = "未知艺术家",
            album = "未知专辑",
            duration = 0,
            uri = file.absolutePath
        )
    } finally {
        retriever.release()
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicPlayerApp(musicPlayerCore: MusicPlayerCore) {
    val playerState by musicPlayerCore.playerState.collectAsState()
    val playMode by musicPlayerCore.playMode.collectAsState()
    val queue by musicPlayerCore.queueManager.queue.collectAsState()
    val currentIndex by musicPlayerCore.queueManager.currentIndex.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showPluginManager by remember { mutableStateOf(false) }
    var showTrackDetail by remember { mutableStateOf<Track?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showLocalMusicSheet by remember { mutableStateOf(false) }
    var localMusicList by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoadingLocal by remember { mutableStateOf(false) }





    fun loadLocalMusic() {
        scope.launch {
            isLoadingLocal = true
            localMusicList = withContext(Dispatchers.IO) {
                val musicDir = java.io.File(Environment.getExternalStorageDirectory(), "Music")
                val tracks = mutableListOf<Track>()

                if (musicDir.exists()) {
                    collectMusicFiles(musicDir, tracks)
                }

                tracks
            }
            isLoadingLocal = false
        }
    }



        Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MuPlayer",
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { showPluginManager = true }) {
                        Icon(Icons.Outlined.Extension, contentDescription = "插件管理")
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
                    Icons.Filled.LibraryMusic,
                    contentDescription = "添加本地音乐",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            if (showPluginManager) {
                // 添加 BackHandler 处理返回
                BackHandler(enabled = showPluginManager) {
                    showPluginManager = false
                }
                PluginManagerScreen()
            } else {
                // 使用 LazyColumn 替代 Column + verticalScroll，避免滚动冲突
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // 播放控制区域
                    item {
                        PlayerControlCard(
                            playerState = playerState,
                            playMode = playMode,
                            onPlay = musicPlayerCore::play,
                            onPause = musicPlayerCore::pause,
                            onStop = musicPlayerCore::stop,
                            onNext = musicPlayerCore::playNext,
                            onPrevious = musicPlayerCore::playPrevious,
                            onSeek = musicPlayerCore::seekTo,
                            onPlayModeChange = musicPlayerCore::setPlayMode
                        )
                    }

                    // 搜索栏
                    item {
                        SearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    // 播放队列

                    item {
                        PlayQueueSection(
                            queue = queue,
                            currentIndex = currentIndex,
                            searchQuery = searchQuery,
                            onPlayTrack = musicPlayerCore::playTrackAtIndex,
                            onRemoveTrack = musicPlayerCore::removeTrack,
                            onClearQueue = musicPlayerCore::clearQueue,
                            onTrackLongPress = { track -> showTrackDetail = track },
                            modifier = Modifier.heightIn(max = 600.dp)  // 添加高度限制
                        )
                    }

                    // 底部间距
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
            onDismiss = { showTrackDetail = null }
        )
    }
}

// ==================== 播放控制卡片 ====================

@Composable
fun PlayerControlCard(
    playerState: PlayerStateData,
    playMode: PlayMode,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onPlayModeChange: (PlayMode) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 专辑封面
            AlbumArtwork(
                track = playerState.currentTrack,
                isPlaying = playerState.state == PlayerState.PLAYING,
                modifier = Modifier.size(200.dp)
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
                onStop = onStop,
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
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(10000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                )
            )
        } else {
            rotation.stop()
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            scale.animateTo(1.05f, animationSpec = tween(300))
        } else {
            scale.animateTo(1f, animationSpec = tween(300))
        }
    }

    Box(
        modifier = modifier.scale(scale.value),
        contentAlignment = Alignment.Center
    ) {
        if (track != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(getAlbumArtUri(track))
                    .crossfade(true)
                    .build(),
                contentDescription = "专辑封面",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .graphicsLayer { rotationZ = if (isPlaying) rotation.value else 0f },
                placeholder = painterResource(R.drawable.ic_audio_track),
                error = rememberVectorPainter(
                    Icons.Outlined.BrokenImage
                )
            )
        } else {
            // 空白状态占位
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(
                        Brush.sweepGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.MusicNote,
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
    onStop: () -> Unit,
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
            icon = Icons.Filled.SkipPrevious,
            onClick = onPrevious,
            size = 48.dp
        )

        // 播放/暂停（缩放动画）
        PlayPauseButton(
            isPlaying = playerState == PlayerState.PLAYING,
            isLoading = playerState == PlayerState.LOADING,
            onPlay = onPlay,
            onPause = onPause
        )

        // 下一首
        ControlButton(
            icon = Icons.Filled.SkipNext,
            onClick = onNext,
            size = 48.dp
        )

        // 停止
        ControlButton(
            icon = Icons.Filled.Stop,
            onClick = onStop,
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
    val scale = remember { Animatable(1f) }

    LaunchedEffect(isPlaying) {
        scale.animateTo(1.1f, animationSpec = spring(dampingRatio = 0.6f))
        scale.animateTo(1f, animationSpec = spring(dampingRatio = 0.6f))
    }

    IconButton(
        onClick = { if (isPlaying) onPause() else onPlay() },
        modifier = Modifier
            .size(72.dp)
            .scale(scale.value)
            .background(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                strokeWidth = 3.dp
            )
        } else {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "暂停" else "播放",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}@Composable
fun PlayModeButton(
    playMode: PlayMode,
    onClick: () -> Unit
) {
    val icon = when (playMode) {
        PlayMode.SEQUENTIAL -> Icons.Filled.Repeat
        PlayMode.SHUFFLE -> Icons.Filled.Shuffle
        PlayMode.SINGLE_LOOP -> Icons.Filled.RepeatOne
    }

    val label = when (playMode) {
        PlayMode.SEQUENTIAL -> "顺序"
        PlayMode.SHUFFLE -> "随机"
        PlayMode.SINGLE_LOOP -> "单曲"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ControlButton(
            icon = icon,
            onClick = onClick,
            size = 40.dp,
            tint = if (playMode != PlayMode.SEQUENTIAL)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun ControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(size)
    ) {
        Icon(
            imageVector = icon,
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
            Icon(Icons.Outlined.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Clear, contentDescription = "清除")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(16.dp)
    )
}

// ==================== 播放队列 ====================

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun PlayQueueSection(
    queue: List<Track>,
    currentIndex: Int,
    searchQuery: String,
    onPlayTrack: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit,
    onClearQueue: () -> Unit,
    onTrackLongPress: (Track) -> Unit,
    modifier: Modifier = Modifier  // 添加 modifier 参数
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
        modifier = modifier.fillMaxWidth()  // 应用 modifier
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
                        onPlay = { onPlayTrack(originalIndex) },
                        onRemove = { onRemoveTrack(originalIndex) },
                        onLongPress = { onTrackLongPress(track) },
                        modifier = Modifier.animateItemPlacement()
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
                imageVector = if (isEmpty) Icons.Outlined.QueueMusic else Icons.Outlined.SearchOff,
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
                    Icons.Filled.Delete,
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
                    containerColor = if (isCurrentTrack)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(
                    defaultElevation = if (isCurrentTrack) 4.dp else 0.dp
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 当前播放指示器
                    if (isCurrentTrack) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // 封面缩略图（添加 placeholder 和 error 占位）
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(getAlbumArtUri(track))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        placeholder = painterResource(R.drawable.ic_audio_track),
                        error = rememberVectorPainter(
                            Icons.Outlined.BrokenImage
                        )
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // 曲目信息
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isCurrentTrack)
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else
                                MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${track.artist} • ${track.album}",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (isCurrentTrack)
                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 时长
                    Text(
                        text = formatDuration(track.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                            Icons.Outlined.MusicOff,
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
                                        .data(getAlbumArtUri(track))
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
                                    Icons.Filled.Add,
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
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(getAlbumArtUri(track))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                )

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

fun getAlbumArtUri(track: Track): String {
    return "content://media/external/audio/albumart/${track.id}"
}