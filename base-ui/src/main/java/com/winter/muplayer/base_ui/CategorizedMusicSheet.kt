package com.winter.muplayer.base_ui

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winter.muplayer.base_ui.ui.theme.itemBorderColor
import com.winter.muplayer.model.Track

// ==================== 分类枚举 ====================

enum class MusicCategory(val label: String) {
    ALL("全部"),
    ARTIST("歌手"),
    ALBUM("专辑")
}

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
    /** @param onTrackClick (点击的曲目, 当前分类上下文曲目列表) */
    onTrackClick: (track: Track, contextTracks: List<Track>) -> Unit = { _, _ -> }
) {
    var selectedCategory by remember { mutableStateOf(MusicCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter { track ->
            track.title.contains(searchQuery, ignoreCase = true) ||
                    track.artist.contains(searchQuery, ignoreCase = true) ||
                    track.album.contains(searchQuery, ignoreCase = true)
        }
    }

    val artistGroups = remember(filteredTracks) {
        filteredTracks.groupBy { it.artist.ifBlank { "未知艺术家" } }
            .toSortedMap()
    }
    val albumGroups = remember(filteredTracks) {
        filteredTracks.groupBy { it.album.ifBlank { "未知专辑" } }
            .toSortedMap()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ========== 搜索栏 ==========
        if (tracks.isNotEmpty()) {
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

        if (isLoading && tracks.isEmpty()) {
            // 加载中
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("正在扫描本地音乐...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (tracks.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
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
                        "未找到本地音乐文件",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // ========== Tab 栏 + 列表 ==========
            Column(modifier = Modifier.fillMaxSize()) {
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
                                    }
                                    Icon(
                                        painterResource(icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(category.label)
                                }
                            }
                        )
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
                            val artistName = track.artist.ifBlank { "未知艺术家" }
                            val contextTracks = artistGroups[artistName] ?: listOf(track)
                            onTrackClick(track, contextTracks)
                        }
                    )
                    MusicCategory.ALBUM -> AlbumTab(
                        albumGroups = albumGroups,
                        coverCache = coverCache,
                        // 按曲目所属的专辑分组传递上下文
                        onTrackClick = { track ->
                            val albumName = track.album.ifBlank { "未知专辑" }
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

// ==================== Tab: 全部歌曲 ====================

@Composable
fun AllSongsTab(
    tracks: List<Track>,
    isLoading: Boolean,
    coverCache: Map<Long, String>,
    onTrackClick: (Track) -> Unit = {}
) {
    if (tracks.isEmpty()) {
        EmptyState("暂无音乐")
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Text(
                    text = "共 ${tracks.size} 首歌曲",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            items(items = tracks, key = { "${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    coverCache = coverCache,
                    onClick = { onTrackClick(track) }
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
    onTrackClick: (Track) -> Unit = {}
) {
    if (artistGroups.isEmpty()) {
        EmptyState("暂无音乐")
    } else {
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                Text(
                    text = "共 ${artistGroups.size} 位歌手",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
            val sortedArtists = artistGroups.entries.toList()
            sortedArtists.forEach { (artist, artistTracks) ->
                item {
                    ArtistSection(
                        artistName = artist,
                        tracks = artistTracks,
                        coverCache = coverCache,
                        onTrackClick = onTrackClick
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
    onTrackClick: (Track) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded },
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .border(
                    width = 2.5.dp,
                    color = MaterialTheme.colorScheme.itemBorderColor,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 歌手头像：取第一首歌的封面，没有则用默认图标
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
                        text = "${tracks.size} 首歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (expanded) 270f else 90f),
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
                        onClick = { onTrackClick(track) }
                    )
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
    onTrackClick: (Track) -> Unit = {}
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
                        onTrackClick = onTrackClick
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
    onTrackClick: (Track) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val albumTrack = tracks.firstOrNull()

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded },
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
                .border(
                    width = 2.5.dp,
                    color = MaterialTheme.colorScheme.itemBorderColor,
                    shape = RoundedCornerShape(16.dp)
                )
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
                        text = "${tracks.size} 首歌曲",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = if (expanded) "收起" else "展开",
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(if (expanded) 270f else 90f),
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
                        onClick = { onTrackClick(track) }
                    )
                }
            }
        }
    }
}

// ==================== 单曲行 ====================

@Composable
fun TrackRow(
    track: Track,
    coverCache: Map<Long, String>,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .clickable(onClick = onClick)
            .border(
                width = 2.5.dp,
                color = MaterialTheme.colorScheme.itemBorderColor,
                shape = RoundedCornerShape(12.dp)
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

// ==================== 辅助函数 ====================

fun getAlbumArtUri(track: Track, coverCache: Map<Long, String>): Any? {
    if (track.albumId > 0L) {
        return android.net.Uri.parse(
            "content://media/external/audio/albumart/${track.albumId}"
        )
    }
    return coverCache[track.id]
}
