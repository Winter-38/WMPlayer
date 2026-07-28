package com.winter.muplayer.ui.browser

import com.winter.muplayer.ui.R
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.EditText
import android.text.TextWatcher
import android.text.Editable
import android.os.Build
import android.text.InputType
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winter.muplayer.core.MusicIndexCache
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.model.Track
import com.winter.muplayer.config.LocalSlotContext

// 分类枚举移至 MusicBrowserState.kt

// ==================== 本地音乐浏览（内嵌版，用于主界面） ====================

/**
 * 本地音乐浏览组件 — 带「全部/歌手/专辑」Tab 和搜索。
 * 可直接放在任何 Column/Box 中，不包含 BottomSheet 封装。
 */
@Composable
fun LocalMusicBrowser(
    tracks: List<Track>,
    isLoading: Boolean,
    coverCache: Map<Long, String>,
    musicIndexCache: MusicIndexCache,
    /** @param onTrackClick (点击的曲目, 当前分类上下文曲目列表) */
    onTrackClick: (track: Track, contextTracks: List<Track>) -> Unit = { _, _ -> },
    /** 外部传入的搜索关键词（搜索栏已移至 TopAppBar） */
    searchQuery: String = ""
) {
    var selectedCategory by remember { mutableStateOf(MusicCategory.ALL) }
    // 排序方式持久化 — 从 SettingsManager 读取，修改时同步写入
    val context = LocalContext.current
    val settings = remember { MusicPlayerCore.getInstance(context).settings }
    var sortField by remember { mutableIntStateOf(settings.sortField) }
    var sortAsc by remember { mutableStateOf(settings.sortAsc) }

    // 排序方式修改 → 同步写入 SettingsManager
    LaunchedEffect(sortField) { settings.sortField = sortField }
    LaunchedEffect(sortAsc) { settings.sortAsc = sortAsc }

    val sortNames = listOf(
        stringResource(R.string.sort_name),
        stringResource(R.string.sort_duration),
        stringResource(R.string.sort_file_size),
        stringResource(R.string.sort_date_added),
        stringResource(R.string.sort_file_type)
    )

    val filteredTracks = remember(tracks, searchQuery, sortField, sortAsc) {
        val base = if (searchQuery.isBlank()) tracks
        else tracks.filter { track ->
            track.title.contains(searchQuery, ignoreCase = true) ||
                    track.artist.contains(searchQuery, ignoreCase = true) ||
                    track.album.contains(searchQuery, ignoreCase = true)
        }
        val sorted = when (sortField) {
            1 -> base.sortedBy { it.duration }
            2 -> base.sortedBy { it.fileSize }
            3 -> base.sortedBy { it.dateAdded }
            4 -> base.sortedBy { it.fileType }
            else -> base.sortedBy { it.title }
        }
        if (sortAsc) sorted else sorted.reversed()
    }

    val unknownArtist = stringResource(R.string.unknown_artist)
    val unknownAlbum = stringResource(R.string.unknown_album)
    // 歌手/专辑分组改为按需惰性计算——仅在对应 Tab 选中时才执行，
    // 避免每次重组都遍历全量数据
    val artistGroups = if (selectedCategory == MusicCategory.ARTIST) {
        remember(filteredTracks, unknownArtist) {
            filteredTracks.groupBy { it.artist.ifBlank { unknownArtist } }.toSortedMap()
        }
    } else emptyMap()
    val albumGroups = if (selectedCategory == MusicCategory.ALBUM) {
        remember(filteredTracks, unknownAlbum) {
            filteredTracks.groupBy { it.album.ifBlank { unknownAlbum } }.toSortedMap()
        }
    } else emptyMap()

    Column(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = when {
                isLoading && tracks.isEmpty() -> 0
                tracks.isEmpty() -> 1
                else -> 2
            },
            animationSpec = tween(300),
            label = "music_browser"
        ) { state ->
            when (state) {
                0 -> {
            // 加载中
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.scanning_music), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        1 -> {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painterResource(R.drawable.ic_music_off),
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_music_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        2 -> {
            // ========== Tab 栏 + 列表 ==========
            Column(modifier = Modifier.fillMaxSize()) {
                Box {
                    TabRow(
                        selectedTabIndex = selectedCategory.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
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
                                        }
                                        Icon(
                                            painterResource(icon),
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(category.displayName())
                                    }
                                }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val summaryText = when (selectedCategory) {
                        MusicCategory.ALL -> stringResource(R.string.track_count, filteredTracks.size)
                        MusicCategory.ARTIST -> stringResource(R.string.artist_count, artistGroups.size)
                        MusicCategory.ALBUM -> stringResource(R.string.album_count, albumGroups.size)
                    }
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    var showSortMenu by remember { mutableStateOf(false) }
                    Box {
                        Text(
                            text = stringResource(R.string.sort_label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.clickable { showSortMenu = true }
                        )
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            sortNames.forEachIndexed { i, name ->
                                DropdownMenuItem(
                                    text = { Text(name, fontWeight = if (sortField == i) FontWeight.Bold else FontWeight.Normal) },
                                    onClick = { sortField = i; showSortMenu = false },
                                    trailingIcon = { if (sortField == i) Text("✓", fontWeight = FontWeight.Bold) }
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(if (sortAsc) stringResource(R.string.sort_asc) else stringResource(R.string.sort_desc), fontWeight = FontWeight.Bold) },
                                onClick = { sortAsc = !sortAsc; showSortMenu = false }
                            )
                        }
                    }
                }

                when (selectedCategory) {
                    MusicCategory.ALL -> AllSongsTab(
                        tracks = filteredTracks,
                        isLoading = isLoading,
                        coverCache = coverCache,
                        onTrackClick = { track -> onTrackClick(track, filteredTracks) }
                    )
                    MusicCategory.ARTIST -> ArtistTab(
                        artistGroups = artistGroups,
                        coverCache = coverCache,
                        // 按曲目所属的歌手分组传递上下文
                        onTrackClick = { track ->
                            val artistName = track.artist.ifBlank { unknownArtist }
                            val contextTracks = artistGroups[artistName] ?: listOf(track)
                            onTrackClick(track, contextTracks)
                        }
                    )
                    MusicCategory.ALBUM -> AlbumTab(
                        albumGroups = albumGroups,
                        coverCache = coverCache,
                        // 按曲目所属的专辑分组传递上下文
                        onTrackClick = { track ->
                            val albumName = track.album.ifBlank { unknownAlbum }
                            val contextTracks = albumGroups[albumName] ?: listOf(track)
                            onTrackClick(track, contextTracks)
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
            }
        }
    }
}

// ==================== Tab: 全部歌曲 ====================

@Composable
fun AllSongsTab(
    tracks: List<Track>,
    isLoading: Boolean,
    coverCache: Map<Long, String>,
    onTrackClick: (Track) -> Unit = {},
    onTrackLongClick: (Track) -> Unit = {}
) {
    if (tracks.isEmpty()) {
        EmptyState(stringResource(R.string.no_music))
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(items = tracks, key = { "${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    coverCache = coverCache,
                    onClick = { onTrackClick(track) },
                    onLongClick = { onTrackLongClick(track) }
                )
            }
        }
    }
}

// ==================== Tab: 按歌手分组 ====================

@Composable
fun ArtistTab(
    artistGroups: Map<String, List<Track>>,
    coverCache: Map<Long, String>,
    onTrackClick: (Track) -> Unit = {},
    onTrackLongClick: (Track) -> Unit = {}
) {
    if (artistGroups.isEmpty()) {
        EmptyState(stringResource(R.string.no_music))
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            val sortedArtists = artistGroups.entries.toList()
            sortedArtists.forEach { (artist, artistTracks) ->
                item {
                    ArtistSection(
                        artistName = artist,
                        tracks = artistTracks,
                        coverCache = coverCache,
                        onTrackClick = onTrackClick,
                        onTrackLongClick = onTrackLongClick
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistSection(
    artistName: String,
    tracks: List<Track>,
    coverCache: Map<Long, String>,
    onTrackClick: (Track) -> Unit = {},
    onTrackLongClick: (Track) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                onClick = { expanded = !expanded },
                color = Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val firstTrack = tracks.firstOrNull()
                    if (firstTrack != null && (coverCache.containsKey(firstTrack.id) || firstTrack.albumId > 0L)) {
                        AlbumThumb(
                            albumTrack = firstTrack,
                            coverCache = coverCache,
                            size = 44.dp
                        )
                    } else {
                        Icon(
                            painter = painterResource(R.drawable.ic_person),
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artistName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = stringResource(R.string.song_count, tracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (expanded) 270f else 90f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    tracks.forEachIndexed { i, track ->
                        if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        TrackRow(
                            track = track,
                            coverCache = coverCache,
                            onClick = { onTrackClick(track) },
                            onLongClick = { onTrackLongClick(track) }
                        )
                    }
                }
            }
        }
    }
}

// ==================== Tab: 按专辑分组 ====================

@Composable
fun AlbumTab(
    albumGroups: Map<String, List<Track>>,
    coverCache: Map<Long, String>,
    onTrackClick: (Track) -> Unit = {},
    onTrackLongClick: (Track) -> Unit = {}
) {
    if (albumGroups.isEmpty()) {
        EmptyState(stringResource(R.string.no_music))
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            val sortedAlbums = albumGroups.entries.toList()
            sortedAlbums.forEach { (album, albumTracks) ->
                item {
                    AlbumSection(
                        albumName = album,
                        tracks = albumTracks,
                        coverCache = coverCache,
                        onTrackClick = onTrackClick,
                        onTrackLongClick = onTrackLongClick
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumSection(
    albumName: String,
    tracks: List<Track>,
    coverCache: Map<Long, String>,
    onTrackClick: (Track) -> Unit = {},
    onTrackLongClick: (Track) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val albumTrack = tracks.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                onClick = { expanded = !expanded },
                color = Color.Transparent,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumThumb(albumTrack = albumTrack, coverCache = coverCache, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = albumName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.song_count, tracks.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        painter = painterResource(R.drawable.ic_arrow_back),
                        contentDescription = if (expanded) stringResource(R.string.collapse) else stringResource(R.string.expand),
                        modifier = Modifier
                            .size(18.dp)
                            .rotate(if (expanded) 270f else 90f),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    tracks.forEachIndexed { i, track ->
                        if (i > 0) HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                        TrackRow(
                            track = track,
                            coverCache = coverCache,
                            onClick = { onTrackClick(track) },
                            onLongClick = { onTrackLongClick(track) }
                        )
                    }
                }
            }
        }
    }
}

// ==================== 单曲行 ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    coverCache: Map<Long, String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
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

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== 专辑缩略图 ====================

@Composable
fun AlbumThumb(
    albumTrack: Track?,
    coverCache: Map<Long, String>,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    if (albumTrack == null) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                modifier = Modifier.size(size * 0.5f),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        val hasCover = coverCache.containsKey(albumTrack.id) || albumTrack.albumId > 0L
        if (hasCover) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(getAlbumArtUri(albumTrack, coverCache))
                    .crossfade(true)
                    .build(),
                contentDescription = albumTrack.album,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painterResource(R.drawable.ic_music_note),
                    contentDescription = null,
                    modifier = Modifier.size(size * 0.5f),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== 空状态组件 ====================

@Composable
fun EmptyState(message: String) {
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
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ==================== 独立组件：Tab 栏 ====================

/**
 * 音乐浏览器 Tab 栏 —— 全部 / 歌手 / 专辑。
 * 通过 LocalBrowserState 与排序 / 列表共享选中状态。
 */
@Composable
fun MusicBrowserTabs() {
    val state = LocalBrowserState.current
    TabRow(
        selectedTabIndex = state.selectedCategory.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth()
    ) {
        MusicCategory.entries.forEach { category ->
            Tab(
                selected = state.selectedCategory == category,
                onClick = { state.selectedCategory = category },
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val icon = when (category) {
                            MusicCategory.ALL -> R.drawable.ic_library_music
                            MusicCategory.ARTIST -> R.drawable.ic_person
                            MusicCategory.ALBUM -> R.drawable.ic_disc
                        }
                        Icon(
                            painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(category.displayName())
                    }
                }
            )
        }
    }
}

// ==================== 独立组件：排序按钮 ====================

@Composable
fun MusicBrowserSort() {
    val state = LocalBrowserState.current
    val sortNames = listOf(
        stringResource(R.string.sort_name),
        stringResource(R.string.sort_duration),
        stringResource(R.string.sort_file_size),
        stringResource(R.string.sort_date_added),
        stringResource(R.string.sort_file_type)
    )
    var showSortMenu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val summaryText = when (state.selectedCategory) {
            MusicCategory.ALL -> stringResource(R.string.track_count, state.tracks.size)
            MusicCategory.ARTIST -> {
                val groups = state.tracks.groupBy { it.artist.ifBlank { stringResource(R.string.unknown_artist) } }
                stringResource(R.string.artist_count, groups.size)
            }
            MusicCategory.ALBUM -> {
                val groups = state.tracks.groupBy { it.album.ifBlank { stringResource(R.string.unknown_album) } }
                stringResource(R.string.album_count, groups.size)
            }
        }
        Text(
            text = summaryText,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Box {
            Text(
                text = stringResource(R.string.sort_label),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { showSortMenu = true }
            )
            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                sortNames.forEachIndexed { i, name ->
                    DropdownMenuItem(
                        text = { Text(name, fontWeight = if (state.sortField == i) FontWeight.Bold else FontWeight.Normal) },
                        onClick = { state.sortField = i; showSortMenu = false },
                        trailingIcon = { if (state.sortField == i) Text("✓", fontWeight = FontWeight.Bold) }
                    )
                }
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(if (state.sortAsc) stringResource(R.string.sort_asc) else stringResource(R.string.sort_desc), fontWeight = FontWeight.Bold) },
                    onClick = { state.sortAsc = !state.sortAsc; showSortMenu = false }
                )
            }
        }
    }
}

// ==================== 独立组件：歌曲列表 ====================

@Composable
fun MusicBrowserList(
    coverCache: Map<Long, String> = emptyMap(),
    onTrackClick: (Track, List<Track>) -> Unit = { _, _ -> },
    onTrackLongClick: (Track) -> Unit = {},
) {
    val state = LocalBrowserState.current

    val unknownArtist = stringResource(R.string.unknown_artist)
    val unknownAlbum = stringResource(R.string.unknown_album)

    // 过滤 + 排序
    val filteredTracks = remember(state.tracks, state.searchQuery, state.sortField, state.sortAsc) {
        val base = if (state.searchQuery.isBlank()) state.tracks
        else state.tracks.filter { track ->
            track.title.contains(state.searchQuery, ignoreCase = true) ||
                    track.artist.contains(state.searchQuery, ignoreCase = true) ||
                    track.album.contains(state.searchQuery, ignoreCase = true)
        }
        val sorted = when (state.sortField) {
            1 -> base.sortedBy { it.duration }
            2 -> base.sortedBy { it.fileSize }
            3 -> base.sortedBy { it.dateAdded }
            4 -> base.sortedBy { it.fileType }
            else -> base.sortedBy { it.title }
        }
        if (state.sortAsc) sorted else sorted.reversed()
    }

    val artistGroups = remember(filteredTracks, unknownArtist) {
        filteredTracks.groupBy { it.artist.ifBlank { unknownArtist } }.toSortedMap()
    }
    val albumGroups = remember(filteredTracks, unknownAlbum) {
        filteredTracks.groupBy { it.album.ifBlank { unknownAlbum } }.toSortedMap()
    }

    when (state.selectedCategory) {
        MusicCategory.ALL -> AllSongsTab(
            tracks = filteredTracks,
            isLoading = state.isLoading,
            coverCache = coverCache,
            onTrackClick = { track -> onTrackClick(track, filteredTracks) },
            onTrackLongClick = onTrackLongClick
        )
        MusicCategory.ARTIST -> ArtistTab(
            artistGroups = artistGroups,
            coverCache = coverCache,
            onTrackClick = { track ->
                val artistName = track.artist.ifBlank { unknownArtist }
                val contextTracks = artistGroups[artistName] ?: listOf(track)
                onTrackClick(track, contextTracks)
            },
            onTrackLongClick = onTrackLongClick
        )
        MusicCategory.ALBUM -> AlbumTab(
            albumGroups = albumGroups,
            coverCache = coverCache,
            onTrackClick = { track ->
                val albumName = track.album.ifBlank { unknownAlbum }
                val contextTracks = albumGroups[albumName] ?: listOf(track)
                onTrackClick(track, contextTracks)
            },
            onTrackLongClick = onTrackLongClick
        )
    }
}

// ==================== 辅助函数 ====================

fun getAlbumArtUri(track: Track, coverCache: Map<Long, String>): Any? {
    if (track.albumId > 0L) {
        return android.net.Uri.parse(
            "content://media/external/audio/albumart/${track.albumId}"
        )
    }
    return coverCache[track.id]
}