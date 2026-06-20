package com.winter.muplayer.base_ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winter.muplayer.core.PlaylistManager
import com.winter.muplayer.model.Playlist
import com.winter.muplayer.model.Track
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

// ==================== 分类枚举 ====================

private enum class MusicCategory(val label: String) {
    ALL("全部"),
    ARTIST("歌手"),
    ALBUM("专辑"),
    PLAYLIST("播放列表")
}

// ==================== 主分类 BottomSheet ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorizedMusicSheet(
    tracks: List<Track>,
    isLoading: Boolean,
    coverCache: Map<Long, String>,
    playlistManager: PlaylistManager,
    onDismiss: () -> Unit,
    onAddTrack: (Track) -> Unit,
    onAddAll: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf(MusicCategory.ALL) }

    // 搜索状态
    var searchQuery by remember { mutableStateOf("") }

    // 根据搜索关键词过滤曲目
    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter { track ->
            track.title.contains(searchQuery, ignoreCase = true) ||
                    track.artist.contains(searchQuery, ignoreCase = true) ||
                    track.album.contains(searchQuery, ignoreCase = true)
        }
    }

    // 播放列表状态
    val playlists by playlistManager.playlists.collectAsState()
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<Playlist?>(null) }
    var showAddToPlaylistTrack by remember { mutableStateOf<Track?>(null) }

    // 按歌手/专辑分组（基于过滤后的曲目）
    val artistGroups = remember(filteredTracks) {
        filteredTracks.groupBy { it.artist.ifBlank { "未知艺术家" } }
            .toSortedMap()
    }
    val albumGroups = remember(filteredTracks) {
        filteredTracks.groupBy { it.album.ifBlank { "未知专辑" } }
            .toSortedMap()
    }

    val preventSheetBounce = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                return Offset(0f, available.y)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            selectedPlaylist = null
            onDismiss()
        },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp, max = 700.dp)
                .nestedScroll(preventSheetBounce)
        ) {
            // ========== 标题栏 ==========
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedPlaylist != null) {
                    // 播放列表详情：显示返回按钮
                    IconButton(onClick = { selectedPlaylist = null }) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = "返回"
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = selectedPlaylist!!.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${selectedPlaylist!!.trackIds.size} 首",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "本地音乐",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        onAddAll()
                        onDismiss()
                    }) {
                        Text("全部添加")
                    }
                }
            }

            // ========== 搜索栏（不在播放列表详情时显示） ==========
            if (selectedPlaylist == null && tracks.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("搜索本地音乐...") },
                    leadingIcon = {
                        Icon(painterResource(R.drawable.ic_search), contentDescription = "搜索")
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(painterResource(R.drawable.ic_clear), contentDescription = "清除")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )
            }

            if (selectedPlaylist != null) {
                // ========== 播放列表详情视图 ==========
                PlaylistDetailContent(
                    playlist = selectedPlaylist!!,
                    allTracks = tracks,
                    coverCache = coverCache,
                    playlistManager = playlistManager,
                    onAddTrack = onAddTrack,
                    scope = scope
                )
            } else {
                // ========== Tab 栏 ==========
                TabRow(
                    selectedTabIndex = selectedCategory.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    MusicCategory.entries.forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val icon = when (category) {
                                        MusicCategory.ALL -> R.drawable.ic_library_music
                                        MusicCategory.ARTIST -> R.drawable.ic_person
                                        MusicCategory.ALBUM -> R.drawable.ic_disc
                                        MusicCategory.PLAYLIST -> R.drawable.ic_playlist_music
                                    }
                                    Icon(
                                        painterResource(icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(category.label)
                                }
                            }
                        )
                    }
                }

                // ========== Tab 内容 ==========
                when (selectedCategory) {
                    MusicCategory.ALL -> AllSongsTab(
                        tracks = filteredTracks,
                        isLoading = isLoading,
                        coverCache = coverCache,
                        onAddTrack = onAddTrack,
                        onAddToPlaylist = { track -> showAddToPlaylistTrack = track }
                    )
                    MusicCategory.ARTIST -> ArtistTab(
                        artistGroups = artistGroups,
                        coverCache = coverCache,
                        onAddTrack = onAddTrack,
                        onAddToPlaylist = { track -> showAddToPlaylistTrack = track }
                    )
                    MusicCategory.ALBUM -> AlbumTab(
                        albumGroups = albumGroups,
                        coverCache = coverCache,
                        onAddTrack = onAddTrack,
                        onAddToPlaylist = { track -> showAddToPlaylistTrack = track }
                    )
                    MusicCategory.PLAYLIST -> PlaylistTab(
                        playlists = playlists,
                        onPlaylistClick = { selectedPlaylist = it },
                        onPlaylistDelete = { showDeleteConfirm = it },
                        onCreateNew = { showCreateDialog = true }
                    )
                }

                // 底部间距
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // ========== 创建播放列表弹窗 ==========
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                scope.launch {
                    playlistManager.create(name)
                }
                showCreateDialog = false
            }
        )
    }

    // ========== 删除确认弹窗 ==========
    showDeleteConfirm?.let { playlist ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("删除播放列表") },
            text = { Text("确定要删除「${playlist.name}」吗？此操作不可撤销。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            playlistManager.delete(playlist.id)
                        }
                        showDeleteConfirm = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = null }) {
                    Text("取消")
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    // ========== 添加到播放列表弹窗 ==========
    showAddToPlaylistTrack?.let { track ->
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistTrack = null },
            onSelectPlaylist = { playlist ->
                scope.launch {
                    playlistManager.addTrack(playlist.id, track.id)
                }
                showAddToPlaylistTrack = null
            },
            onCreateNew = {
                showAddToPlaylistTrack = null
                showCreateDialog = true
            }
        )
    }
}

// ==================== Tab: 全部歌曲 ====================

@Composable
private fun AllSongsTab(
    tracks: List<Track>,
    isLoading: Boolean,
    coverCache: Map<Long, String>,
    onAddTrack: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(8.dp))
                Text("正在扫描本地音乐...", style = MaterialTheme.typography.bodyMedium)
            }
        }
    } else if (tracks.isEmpty()) {
        EmptyState("暂无本地音乐")
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Text(
                    text = "共 ${tracks.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            items(tracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    coverCache = coverCache,
                    onClick = { onAddTrack(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) }
                )
            }
        }
    }
}

// ==================== Tab: 按歌手分组 ====================

@Composable
private fun ArtistTab(
    artistGroups: Map<String, List<Track>>,
    coverCache: Map<Long, String>,
    onAddTrack: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    if (artistGroups.isEmpty()) {
        EmptyState("暂无音乐")
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            val sortedArtists = artistGroups.entries.toList()
            item {
                Text(
                    text = "共 ${sortedArtists.size} 位歌手",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            sortedArtists.forEach { (artist, artistTracks) ->
                item {
                    ArtistSection(
                        artistName = artist,
                        tracks = artistTracks,
                        coverCache = coverCache,
                        onAddTrack = onAddTrack,
                        onAddToPlaylist = onAddToPlaylist
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistSection(
    artistName: String,
    tracks: List<Track>,
    coverCache: Map<Long, String>,
    onAddTrack: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // 歌手标题栏
        Surface(
            onClick = { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(R.drawable.ic_person),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = artistName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${tracks.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = if (expanded) painterResource(R.drawable.ic_clear)
                    else painterResource(R.drawable.ic_add),
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 歌曲列表
        AnimatedVisibility(visible = expanded) {
            Column {
                tracks.forEach { track ->
                    TrackRow(
                        track = track,
                        coverCache = coverCache,
                        onClick = { onAddTrack(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        }
    }
}

// ==================== Tab: 按专辑分组 ====================

@Composable
private fun AlbumTab(
    albumGroups: Map<String, List<Track>>,
    coverCache: Map<Long, String>,
    onAddTrack: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    if (albumGroups.isEmpty()) {
        EmptyState("暂无音乐")
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            val sortedAlbums = albumGroups.entries.toList()
            item {
                Text(
                    text = "共 ${sortedAlbums.size} 张专辑",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            sortedAlbums.forEach { (album, albumTracks) ->
                item {
                    AlbumSection(
                        albumName = album,
                        tracks = albumTracks,
                        coverCache = coverCache,
                        onAddTrack = onAddTrack,
                        onAddToPlaylist = onAddToPlaylist
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumSection(
    albumName: String,
    tracks: List<Track>,
    coverCache: Map<Long, String>,
    onAddTrack: (Track) -> Unit,
    onAddToPlaylist: (Track) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    // 取专辑第一首的封面作为专辑封面
    val albumTrack = tracks.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 专辑封面缩略图（加大）
                AlbumThumb(albumTrack = albumTrack, coverCache = coverCache, size = 48.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = albumName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${tracks.size} 首",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = if (expanded) painterResource(R.drawable.ic_clear)
                    else painterResource(R.drawable.ic_add),
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                tracks.forEach { track ->
                    TrackRow(
                        track = track,
                        coverCache = coverCache,
                        onClick = { onAddTrack(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) }
                    )
                }
            }
        }
    }
}

// ==================== Tab: 播放列表 ====================

@Composable
private fun PlaylistTab(
    playlists: List<Playlist>,
    onPlaylistClick: (Playlist) -> Unit,
    onPlaylistDelete: (Playlist) -> Unit,
    onCreateNew: () -> Unit
) {
    if (playlists.isEmpty()) {
        // 空状态
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                painterResource(R.drawable.ic_playlist_music),
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "暂无播放列表",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击下方按钮创建你的第一个播放列表",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            FilledTonalButton(
                onClick = onCreateNew,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    painterResource(R.drawable.ic_playlist_add),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("创建播放列表")
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "共 ${playlists.size} 个播放列表",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalIconButton(
                        onClick = onCreateNew,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_playlist_add),
                            contentDescription = "新建播放列表",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
            items(playlists) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) },
                    onDelete = { onPlaylistDelete(playlist) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 播放列表图标
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_playlist_music),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.trackIds.size} 首 · 创建于 ${dateFormat.format(Date(playlist.createTime))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 删除按钮
            IconButton(onClick = onDelete) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ==================== 播放列表详情 ====================

@Composable
private fun PlaylistDetailContent(
    playlist: Playlist,
    allTracks: List<Track>,
    coverCache: Map<Long, String>,
    playlistManager: PlaylistManager,
    onAddTrack: (Track) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope
) {
    // 过滤出该播放列表中的曲目
    val playlistTracks = remember(playlist, allTracks) {
        val trackIdSet = playlist.trackIds.toSet()
        allTracks.filter { it.id in trackIdSet }
    }

    if (playlistTracks.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painterResource(R.drawable.ic_music_off),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "播放列表为空",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "在「全部」「歌手」「专辑」标签页中点击歌曲右侧的 + 号添加",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${playlistTracks.size} 首",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = {
                        playlistTracks.forEach { onAddTrack(it) }
                    }) {
                        Text("全部播放")
                    }
                }
            }
            items(playlistTracks, key = { it.id }) { track ->
                TrackRow(
                    track = track,
                    coverCache = coverCache,
                    onClick = { onAddTrack(track) },
                    onAddToPlaylist = null // 已在播放列表中
                )
            }
        }
    }
}

// ==================== 通用曲目行 ====================

@Composable
private fun TrackRow(
    track: Track,
    coverCache: Map<Long, String>,
    onClick: () -> Unit,
    onAddToPlaylist: (() -> Unit)?
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 封面（加大）
            AlbumThumb(albumTrack = track, coverCache = coverCache, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))

            // 曲目信息
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // 时长
            Text(
                text = formatDuration(track.duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 4.dp)
            )

            // 添加到播放列表按钮
            if (onAddToPlaylist != null) {
                IconButton(
                    onClick = onAddToPlaylist,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painterResource(R.drawable.ic_playlist_add),
                        contentDescription = "添加到播放列表",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

// ==================== 专辑封面缩略图 ====================

@Composable
private fun AlbumThumb(
    albumTrack: Track?,
    coverCache: Map<Long, String>,
    size: androidx.compose.ui.unit.Dp
) {
    val hasCover = albumTrack != null &&
            (coverCache.containsKey(albumTrack.id) || albumTrack.albumId > 0L)

    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFFE1BEE7))
    ) {
        if (hasCover && albumTrack != null) {
            val coverData = getAlbumArtUri(albumTrack, coverCache)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverData)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

// ==================== 空状态 ====================

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ==================== 创建播放列表弹窗 ====================

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建播放列表") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("播放列表名称") },
                placeholder = { Text("例如：我的最爱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name.trim())
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

// ==================== 添加到播放列表弹窗 ====================

@Composable
private fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (Playlist) -> Unit,
    onCreateNew: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加到播放列表") },
        text = {
            if (playlists.isEmpty()) {
                Text(
                    text = "暂无播放列表，请先创建一个",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp)
                ) {
                    items(playlists) { playlist ->
                        Surface(
                            onClick = { onSelectPlaylist(playlist) },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_playlist_music),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = playlist.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${playlist.trackIds.size} 首",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (playlists.isEmpty()) {
                TextButton(onClick = {
                    onDismiss()
                    onCreateNew()
                }) {
                    Text("创建播放列表")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}
