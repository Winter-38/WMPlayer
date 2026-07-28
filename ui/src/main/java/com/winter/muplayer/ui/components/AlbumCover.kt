package com.winter.muplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winter.muplayer.model.Track
import com.winter.muplayer.ui.activity.MarqueeText
import com.winter.muplayer.ui.R

@Composable
fun AlbumCover(track: Track?, isPlaying: Boolean, coverCache: Map<Long, String>) {
    if (track != null) {
        val hasCover = coverCache.containsKey(track.id) || track.albumId > 0L
        if (hasCover) {
            val albumArtCorner = 24.dp
            val albumArtW = 0.dp
            val albumArtH = 0.dp
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(coverCache[track.id] ?: "content://media/external/audio/albumart/${track.albumId}")
                    .crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .then(if (albumArtW > 0.dp && albumArtH > 0.dp) Modifier.size(albumArtW, albumArtH) else Modifier)
                    .clip(RoundedCornerShape(albumArtCorner))
                    .shadow(16.dp, RoundedCornerShape(albumArtCorner)),
            )
        } else {
            val albumArtCorner = 24.dp
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(albumArtCorner))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .shadow(16.dp, RoundedCornerShape(albumArtCorner)),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_music_off),
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
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
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_music_off),
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
    }
}

@Composable
fun TrackInfoMarquee(track: Track?, isPlaying: Boolean) {
    if (track == null) {
        androidx.compose.material3.Text(
            text = com.winter.muplayer.ui.R.string.no_track_selected.let { "" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        return
    }
    val infiniteTransition = rememberInfiniteTransition(label = "marquee")
    val offset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
        ),
        label = "marqueeOffset",
    )
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Box(
            modifier = Modifier
                .graphicsLayer { translationX = offset }
                .padding(end = 16.dp),
        ) {
            com.winter.muplayer.ui.activity.MarqueeText(
                text = track.title,
                style = MaterialTheme.typography.titleLarge,
                isPlaying = isPlaying,
            )
        }
    }
}
