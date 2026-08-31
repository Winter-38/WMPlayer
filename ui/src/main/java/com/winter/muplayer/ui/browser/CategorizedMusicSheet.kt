package com.winter.muplayer.ui.browser

import com.winter.muplayer.ui.R
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
        Box(modifier = Modifier.fillMaxSize()) {
            if (itemStyle.layout == ItemLayout.GRID) {
                // 封面卡片网格：封面在上，曲名 + 歌手名在下。
                // 使用独立 LazyGridState（与列表模式不共享滚动位置，切换布局后从顶部开始）。
                val gridState = rememberLazyGridState()
                LazyVerticalGrid(
                    columns = GridCells.Fixed(itemStyle.columns),
                    state = gridState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(items = tracks, key = { "${it.id}" }) { track ->
                        TrackCard(
                            track = track,
                            coverCache = coverCache,
                            onClick = { onTrackClick(track) },
                            onLongClick = { onTrackLongClick(track) },
                            itemStyle = itemStyle,
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(),
                ) {
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
                ListScrollIndicator(
                    state = state,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
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
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
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
            ListScrollIndicator(
                state = state,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
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
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
            ) {
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
            ListScrollIndicator(
                state = state,
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            )
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

/** 播放列表条目布局：LIST=横向行（封面左/文字右）；GRID=封面卡片（封面在上/曲名歌手在下）。 */
enum class ItemLayout { LIST, GRID }

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
 * - `item-layout`：条目布局（list=横向行 / grid=封面卡片网格）
 * - `item-columns`：grid 布局的列数（默认 2，范围 1..6）
 */
data class ItemStyle(
    val background: Color? = null,
    val radius: Dp? = null,
    val titleColor: Color? = null,
    val titleSize: TextUnit? = null,
    val subColor: Color? = null,
    val subSize: TextUnit? = null,
    val fontFamily: FontFamily? = null,
    val layout: ItemLayout = ItemLayout.LIST,
    val columns: Int = 2,
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

// ==================== 封面卡片（grid 布局条目） ====================

/**
 * 封面卡片条目（grid 布局）：封面在上（正方形撑满），下方曲名 + 歌手名。
 * 由 `item-layout: grid` 启用，配合 LazyVerticalGrid 多列渲染。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackCard(
    track: Track,
    coverCache: Map<Long, String>,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    itemStyle: ItemStyle = ItemStyle(),
) {
    val shape = RoundedCornerShape(itemStyle.radius ?: 12.dp)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = itemStyle.background
                ?: MaterialTheme.colorScheme.secondaryContainer
        ),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            // 封面：正方形，宽度撑满卡片
            val hasCover = coverCache.containsKey(track.id) || track.albumId > 0L
            if (hasCover) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(getAlbumArtUri(track, coverCache))
                        .crossfade(true)
                        .build(),
                    contentDescription = track.album,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(shape),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.ic_music_note),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            // 曲名（封面下方）
            Text(
                text = track.title,
                style = itemStyle.titleSize?.let {
                    MaterialTheme.typography.bodyMedium.copy(fontSize = it)
                } ?: MaterialTheme.typography.bodyMedium,
                fontFamily = itemStyle.fontFamily ?: FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = itemStyle.titleColor
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 歌手名（曲名下方）
            Text(
                text = track.artist,
                style = itemStyle.subSize?.let {
                    MaterialTheme.typography.labelSmall.copy(fontSize = it)
                } ?: MaterialTheme.typography.labelSmall,
                fontFamily = itemStyle.fontFamily ?: FontFamily.Default,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = itemStyle.subColor
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

/**
 * 列表位置指示条：显示当前在列表中的位置。
 *
 * foundation 1.11 起旧 VerticalScrollbar API 已移除，改由 LazyListState.scrollIndicatorState
 * 提供标准滚动数据（scrollOffset / contentSize / viewportSize），此处据此自绘右侧细条。
 */
@Composable
private fun ListScrollIndicator(
    state: LazyListState,
    modifier: Modifier = Modifier,
) {
    val indicator = state.scrollIndicatorState
    val scrollOffset = indicator?.scrollOffset ?: 0
    val contentSize = indicator?.contentSize ?: 0
    val viewportSize = indicator?.viewportSize ?: 0

    val thumbFraction = if (contentSize <= 0 || viewportSize >= contentSize) 1f
    else viewportSize.toFloat() / contentSize
    val scrollFraction = if (contentSize <= viewportSize) 0f
    else (scrollOffset.toFloat() / (contentSize - viewportSize)).coerceIn(0f, 1f)

    val thumbColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.width(4.dp).fillMaxHeight()) {
        val trackHeight = size.height
        val thumbHeight = (trackHeight * thumbFraction).coerceAtLeast(24f)
        val thumbY = (trackHeight - thumbHeight) * scrollFraction
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(0f, thumbY),
            size = Size(size.width, thumbHeight),
            cornerRadius = CornerRadius(size.width / 2f),
        )
    }
}

/** 分类切换动画时长（毫秒）：先渐隐旧列表，再渐现新分类列表 */
private val CategoryFadeOutMillis = 200
private val CategoryFadeInMillis = 140

@Composable
fun MusicBrowserList(
    coverCache: Map<Long, String> = emptyMap(),
    onTrackClick: (Track, List<Track>) -> Unit = { _, _ -> },
    onTrackLongClick: (Track) -> Unit = {},
    itemStyle: ItemStyle = ItemStyle(),
) {
    val state = LocalBrowserState.current

    // 切换动画：点按 tab 切换分类 / 切换排序方式时，旧列表先淡出，再以新内容淡入。
    // visible* 状态滞后于 state 对应字段，动画完成前保持旧内容。
    var visibleCategory by remember { mutableStateOf(state.selectedCategory) }
    var visibleSortField by remember { mutableStateOf(state.sortField) }
    var visibleSortAsc by remember { mutableStateOf(state.sortAsc) }
    val listAlpha = remember { Animatable(1f) }
    LaunchedEffect(state.selectedCategory, state.sortField, state.sortAsc) {
        val categoryChanged = state.selectedCategory != visibleCategory
        val sortChanged = state.sortField != visibleSortField || state.sortAsc != visibleSortAsc
        if (categoryChanged || sortChanged) {
            listAlpha.animateTo(0f, tween(durationMillis = CategoryFadeOutMillis))
            visibleCategory = state.selectedCategory
            visibleSortField = state.sortField
            visibleSortAsc = state.sortAsc
            if (sortChanged) state.resetListScroll()
            listAlpha.animateTo(1f, tween(durationMillis = CategoryFadeInMillis))
        }
    }

    val unknownArtist = stringResource(R.string.unknown_artist)
    val unknownAlbum = stringResource(R.string.unknown_album)

    // 过滤 + 排序（基于 visible* 状态，切换动画完成前保持旧排序结果）
    val filteredTracks = remember(state.tracks, state.searchQuery, visibleSortField, visibleSortAsc) {
        val base = if (state.searchQuery.isBlank()) state.tracks
        else state.tracks.filter { track ->
            track.title.contains(state.searchQuery, ignoreCase = true) ||
                    track.artist.contains(state.searchQuery, ignoreCase = true) ||
                    track.album.contains(state.searchQuery, ignoreCase = true)
        }
        val sorted = when (visibleSortField) {
            1 -> base.sortedBy { it.duration }
            2 -> base.sortedBy { it.fileSize }
            3 -> base.sortedBy { it.dateAdded }
            else -> base.sortedBy { it.title }
        }
        if (visibleSortAsc) sorted else sorted.reversed()
    }

    val artistGroups = remember(filteredTracks, unknownArtist) {
        filteredTracks.groupBy { it.artist.ifBlank { unknownArtist } }.toSortedMap()
    }
    val albumGroups = remember(filteredTracks, unknownAlbum) {
        filteredTracks.groupBy { it.album.ifBlank { unknownAlbum } }.toSortedMap()
    }

    // 列表滚动状态由全局 MusicBrowserState 持有：backdrop-blur 毛玻璃镜像（双渲染）
    // 与主列表绑定同一实例，滚动实时同步；排序/数据变化时 resetListScroll() 重建实例回到顶部。
    val listState = state.listState

    // 数据加载完成（冷启动/刷新/权限变化）→ 重建滚动状态，回到顶部
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) state.resetListScroll()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = listAlpha.value },
    ) {
        when (visibleCategory) {
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
}

// ==================== 辅助函数 ====================

fun getAlbumArtUri(track: Track, coverCache: Map<Long, String>): Any? {
    // 优先本地压缩缩略图（thumb），回退原始无损封面；未缓存时用 MediaStore 缩略图临时兜底
    return com.winter.muplayer.ui.components.getAlbumArtUri(track, coverCache)
}