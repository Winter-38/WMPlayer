package com.winter.muplayer.ui.browser

import com.winter.muplayer.ui.R
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winter.muplayer.model.Track

// 分类枚举移至 MusicBrowserState.kt


// ==================== Tab: 全部歌曲 ====================

@Composable
fun AllSongsTab(
    tracks: List<Track>,
    isLoading: Boolean,
    coverCache: Map<Long, String>,
    state: LazyListState,
    onTrackClick: (Track) -> Unit = {},
    onTrackLongClick: (Track) -> Unit = {},
    itemStyle: ItemStyle = ItemStyle()
) {
    if (isLoading) {
        // 数据加载中：显示占位，不渲染列表。
        // 确保 LazyColumn 首次出现在屏幕上即为全新滚动状态（顶部），
        // 避免启动时闪现其他位置的列表帧。
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (tracks.isEmpty()) {
        EmptyState(stringResource(R.string.no_music))
    } else {
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth()) {
            items(items = tracks, key = { "${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    coverCache = coverCache,
                    onClick = { onTrackClick(track) },
                    onLongClick = { onTrackLongClick(track) },
                    itemStyle = itemStyle
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
    state: LazyListState,
    onTrackClick: (Track) -> Unit = {},
    onTrackLongClick: (Track) -> Unit = {},
    itemStyle: ItemStyle = ItemStyle()
) {
    if (artistGroups.isEmpty()) {
        EmptyState(stringResource(R.string.no_music))
    } else {
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth()) {
            val sortedArtists = artistGroups.entries.toList()
            sortedArtists.forEach { (artist, artistTracks) ->
                item {
                    ArtistSection(
                        artistName = artist,
                        tracks = artistTracks,
                        coverCache = coverCache,
                        onTrackClick = onTrackClick,
                        onTrackLongClick = onTrackLongClick,
                        itemStyle = itemStyle
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
    onTrackLongClick: (Track) -> Unit = {},
    itemStyle: ItemStyle = ItemStyle()
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
                            onLongClick = { onTrackLongClick(track) },
                            itemStyle = itemStyle
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
    state: LazyListState,
    onTrackClick: (Track) -> Unit = {},
    onTrackLongClick: (Track) -> Unit = {},
    itemStyle: ItemStyle = ItemStyle()
) {
    if (albumGroups.isEmpty()) {
        EmptyState(stringResource(R.string.no_music))
    } else {
        LazyColumn(state = state, modifier = Modifier.fillMaxWidth()) {
            val sortedAlbums = albumGroups.entries.toList()
            sortedAlbums.forEach { (album, albumTracks) ->
                item {
                    AlbumSection(
                        albumName = album,
                        tracks = albumTracks,
                        coverCache = coverCache,
                        onTrackClick = onTrackClick,
                        onTrackLongClick = onTrackLongClick,
                        itemStyle = itemStyle
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
    onTrackLongClick: (Track) -> Unit = {},
    itemStyle: ItemStyle = ItemStyle()
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
                            onLongClick = { onTrackLongClick(track) },
                            itemStyle = itemStyle
                        )
                    }
                }
            }
        }
    }
}

// ==================== 条目样式 ====================

/**
 * 播放列表条目样式 —— 由 `#playlist` 组件 CSS 的 `item-*` 属性解析而来。
 * 所有字段可空：null = 使用 Material 主题默认样式。
 *
 * 对应 CSS 属性：
 * - `item-bg` / `item-background`：条目背景色（支持 hex / rgb() / rgba()）
 * - `item-radius`：条目圆角（px / dp）
 * - `item-color` / `item-text-color`：主文字（歌名）颜色
 * - `item-font-size`：主文字字号（px / dp）
 * - `item-sub-color`：副文字（歌手 • 专辑）颜色
 * - `item-sub-size`：副文字字号
 */
data class ItemStyle(
    val background: Color? = null,
    val radius: Dp? = null,
    val titleColor: Color? = null,
    val titleSize: TextUnit? = null,
    val subColor: Color? = null,
    val subSize: TextUnit? = null,
    val fontFamily: FontFamily? = null,
)

// ==================== 单曲行 ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
    track: Track,
    coverCache: Map<Long, String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    itemStyle: ItemStyle = ItemStyle()
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(itemStyle.radius ?: 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = itemStyle.background
                ?: MaterialTheme.colorScheme.secondaryContainer
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
                    style = itemStyle.titleSize?.let {
                        MaterialTheme.typography.bodyLarge.copy(fontSize = it)
                    } ?: MaterialTheme.typography.bodyLarge,
                    fontFamily = itemStyle.fontFamily ?: FontFamily.Default,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = itemStyle.titleColor
                        ?: MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = itemStyle.subSize?.let {
                        MaterialTheme.typography.bodySmall.copy(fontSize = it)
                    } ?: MaterialTheme.typography.bodySmall,
                    fontFamily = itemStyle.fontFamily ?: FontFamily.Default,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = itemStyle.subColor
                        ?: MaterialTheme.colorScheme.onSurfaceVariant
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

// ==================== 独立组件：歌曲列表 ====================

@Composable
fun MusicBrowserList(
    coverCache: Map<Long, String> = emptyMap(),
    onTrackClick: (Track, List<Track>) -> Unit = { _, _ -> },
    onTrackLongClick: (Track) -> Unit = {},
    itemStyle: ItemStyle = ItemStyle(),
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

    // 列表滚动状态：普通 remember + 按需重建（listKey）。
    // 冷启动、排序变化、数据加载完成时都通过重建 LazyListState 实例
    // 强制回到顶部 —— 新实例必然从位置 0 开始，从机制上排除任何
    // 位置恢复/漂移的可能（比 scrollToItem 更彻底，不受协程时序影响）。
    var listKey by remember { mutableIntStateOf(0) }

    // 排序方式/方向变化 → 重建滚动状态，回到顶部
    LaunchedEffect(state.sortField, state.sortAsc) {
        listKey++
    }

    // 数据加载完成（冷启动/刷新/权限变化）→ 重建滚动状态，回到顶部
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) listKey++
    }

    val listState = remember(listKey) { LazyListState() }

    when (state.selectedCategory) {
        MusicCategory.ALL -> AllSongsTab(
            tracks = filteredTracks,
            isLoading = state.isLoading,
            coverCache = coverCache,
            state = listState,
            onTrackClick = { track -> onTrackClick(track, filteredTracks) },
            onTrackLongClick = onTrackLongClick,
            itemStyle = itemStyle
        )
        MusicCategory.ARTIST -> ArtistTab(
            artistGroups = artistGroups,
            coverCache = coverCache,
            state = listState,
            onTrackClick = { track ->
                val artistName = track.artist.ifBlank { unknownArtist }
                val contextTracks = artistGroups[artistName] ?: listOf(track)
                onTrackClick(track, contextTracks)
            },
            onTrackLongClick = onTrackLongClick,
            itemStyle = itemStyle
        )
        MusicCategory.ALBUM -> AlbumTab(
            albumGroups = albumGroups,
            coverCache = coverCache,
            state = listState,
            onTrackClick = { track ->
                val albumName = track.album.ifBlank { unknownAlbum }
                val contextTracks = albumGroups[albumName] ?: listOf(track)
                onTrackClick(track, contextTracks)
            },
            onTrackLongClick = onTrackLongClick,
            itemStyle = itemStyle
        )
    }
}

// ==================== 辅助函数 ====================

fun getAlbumArtUri(track: Track, coverCache: Map<Long, String>): Any? {
    // 优先本地原始内嵌封面缓存；未缓存时用 MediaStore 缩略图临时兜底
    return coverCache[track.id] ?: if (track.albumId > 0L) {
        android.net.Uri.parse(
            "content://media/external/audio/albumart/${track.albumId}"
        )
    } else null
}