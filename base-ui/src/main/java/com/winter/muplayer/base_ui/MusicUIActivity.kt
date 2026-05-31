// MusicUIActivity.kt
package com.winter.muplayer.base_ui

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.model.Track
import com.winter.muplayer.model.PlayerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicUIActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MusicPlayerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MusicPlayerApp() {
    val context = LocalContext.current.applicationContext
    val core = remember { MusicPlayerCore.getInstance(context) }
    val scope = rememberCoroutineScope()

    val playerState by core.playerState.collectAsState()
    val progressState by core.playerState.collectAsState()
    val queue by core.queueManager.queue.collectAsState()
    val currentIndex by core.queueManager.currentIndex.collectAsState()

    var showClearDialog by remember { mutableStateOf(false) }
    var showSearchSheet by remember { mutableStateOf(false) }

    // 搜索相关状态
    var searchQuery by remember { mutableStateOf("") }
    var localTracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoadingLocal by remember { mutableStateOf(false) }

    // 进度拖动状态
    var isSeeking by remember { mutableStateOf(false) }
    var seekPosition by remember { mutableStateOf(0L) }

    // 计算进度滑块值
    val duration = progressState.duration.coerceAtLeast(1L)
    val progress = if (isSeeking) seekPosition else progressState.progress
    val sliderValue = (progress.toFloat() / duration).coerceIn(0f, 1f)

    // 本地音乐加载
    LaunchedEffect(Unit) {
        isLoadingLocal = true
        localTracks = withContext(Dispatchers.IO) {
            loadLocalMusic(context.contentResolver)
        }
        isLoadingLocal = false
    }

    // 添加本地音乐启动器
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        scope.launch {
            val tracks = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    createTrackFromUri(context, uri)
                }
            }
            if (tracks.isNotEmpty()) {
                core.addTracks(tracks)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("音乐播放器") },
                actions = {
                    IconButton(onClick = { showSearchSheet = true }) {
                        Icon(Icons.Filled.Search, contentDescription = "搜索本地音乐")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    launcher.launch(arrayOf("audio/*"))
                }
            ) {
                Icon(Icons.Filled.Add, contentDescription = "添加本地音乐")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 播放控制区
            PlayerControls(
                playerState = playerState,
                progress = sliderValue,
                onProgressChange = { newValue ->
                    isSeeking = true
                    seekPosition = (newValue * duration).toLong()
                },
                onProgressChangeFinished = {
                    core.seekTo(seekPosition)
                    isSeeking = false
                },
                onPlayPause = {
                    if (playerState.state == PlayerState.PLAYING) {
                        core.pause()
                    } else {
                        core.play()
                    }
                },
                onStop = { core.stop() },
                onPrevious = { core.playPrevious() },
                onNext = { core.playNext() }
            )

            Divider()

            // 队列标题行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("播放队列 (${queue.size})", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { showClearDialog = true }) {
                    Text("清空")
                }
            }

            // 队列列表
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(queue) { index, track ->
                    TrackItem(
                        track = track,
                        isCurrent = index == currentIndex,
                        onClick = { core.playTrackAtIndex(index) },
                        onLongClick = { core.removeTrack(index) }
                    )
                }
                if (queue.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("队列为空，请添加音乐")
                        }
                    }
                }
            }
        }
    }

    // 清空确认对话框
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空播放队列") },
            text = { Text("确定要清空队列吗？") },
            confirmButton = {
                TextButton(onClick = {
                    core.clearQueue()
                    showClearDialog = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }

    // 本地音乐搜索底部弹窗
    if (showSearchSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSearchSheet = false }
        ) {
            LocalMusicSearchSheet(
                tracks = localTracks,
                searchQuery = searchQuery,
                onQueryChange = { searchQuery = it },
                isLoading = isLoadingLocal,
                onTrackClick = { track ->
                    core.addTrack(track)
                    showSearchSheet = false
                },
                onDismiss = { showSearchSheet = false }
            )
        }
    }
}

@Composable
fun PlayerControls(
    playerState: com.winter.muplayer.model.PlayerStateData,
    progress: Float,
    onProgressChange: (Float) -> Unit,
    onProgressChangeFinished: () -> Unit,
    onPlayPause: () -> Unit,
    onStop: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 当前曲目信息
        Text(
            text = playerState.currentTrack?.title ?: "未选择曲目",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1
        )
        Text(
            text = playerState.currentTrack?.artist ?: "未知艺术家",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        // 进度条
        Slider(
            value = progress,
            onValueChange = onProgressChange,
            onValueChangeFinished = onProgressChangeFinished,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatDuration((progress * playerState.duration.coerceAtLeast(1)).toLong()))
            Text(formatDuration(playerState.duration))
        }
        Spacer(modifier = Modifier.height(8.dp))
        // 控制按钮
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier.fillMaxWidth()
        ) {
            IconButton(onClick = onPrevious) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "上一首")
            }
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playerState.state == PlayerState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playerState.state == PlayerState.PLAYING) "暂停" else "播放"
                )
            }
            IconButton(onClick = onStop) {
                Icon(Icons.Filled.Stop, contentDescription = "停止")
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.SkipNext, contentDescription = "下一首")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackItem(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        tonalElevation = if (isCurrent) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (isCurrent) Icons.Filled.MusicNote else Icons.Filled.LibraryMusic,
                contentDescription = null,
                tint = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Text(
                text = formatDuration(track.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalMusicSearchSheet(
    tracks: List<Track>,
    searchQuery: String,
    onQueryChange: (String) -> Unit,
    isLoading: Boolean,
    onTrackClick: (Track) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.padding(16.dp)
    ) {
        Text(
            "本地音乐",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索歌曲或艺术家...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
        Spacer(modifier = Modifier.height(8.dp))
        if (isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val filtered = remember(tracks, searchQuery) {
                if (searchQuery.isBlank()) tracks
                else tracks.filter {
                    it.title.contains(searchQuery, ignoreCase = true) ||
                            it.artist.contains(searchQuery, ignoreCase = true)
                }
            }
            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(filtered) { _, track ->
                    TrackItem(
                        track = track,
                        isCurrent = false,
                        onClick = { onTrackClick(track) },
                        onLongClick = {}
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Text("没有找到匹配的音乐")
                        }
                    }
                }
            }
        }
    }
}

// 工具函数：格式化时长 (毫秒 -> mm:ss)
fun formatDuration(millis: Long): String {
    if (millis <= 0) return "00:00"
    val totalSeconds = millis / 1000
    val minutes = (totalSeconds / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    return String.format("%02d:%02d", minutes, seconds)
}

// 从 ContentResolver 加载本地音频文件
suspend fun loadLocalMusic(contentResolver: ContentResolver): List<Track> {
    val result = mutableListOf<Track>()
    val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val projection = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.DISPLAY_NAME,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATA
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

    contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val title = cursor.getString(titleCol) ?: "未知"
            val artist = cursor.getString(artistCol) ?: "未知艺术家"
            val album = cursor.getString(albumCol) ?: ""
            val duration = cursor.getLong(durationCol)
            val data = cursor.getString(dataCol)
            if (!data.isNullOrEmpty()) {
                val trackUri = Uri.parse("content://media/external/audio/media/$id")
                result.add(
                    Track(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        duration = duration,
                        uri = trackUri.toString()
                    )
                )
            }
        }
    }
    return result
}

// 从用户选择的文件 URI 创建 Track
suspend fun createTrackFromUri(context: Context, uri: Uri): Track? {
    return try {
        withContext(Dispatchers.IO) {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: getFileName(context, uri)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "未知艺术家"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L
            retriever.release()

            Track(
                id = System.currentTimeMillis(), // 简单唯一标识
                title = title,
                artist = artist,
                album = album,
                duration = duration,
                uri = uri.toString()
            )
        }
    } catch (e: Exception) {
        null
    }
}

// 通过 URI 获取文件名
fun getFileName(context: Context, uri: Uri): String {
    var name = "未知文件"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) {
                name = cursor.getString(index) ?: name
            }
        }
    }
    return name
}