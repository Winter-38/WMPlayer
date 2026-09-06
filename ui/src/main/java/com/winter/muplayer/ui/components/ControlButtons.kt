package com.winter.muplayer.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.winter.muplayer.ui.R
import com.winter.muplayer.model.PlayMode

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    iconTint: Color = MaterialTheme.colorScheme.onPrimary,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale = remember { Animatable(1f) }
    val burst = rememberParticleBurstState()

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

    ParticleBurstBox(
        state = burst,
        modifier = Modifier.size(72.dp),
        color = iconTint,
    ) {
        IconButton(
            onClick = {
                isPressed = true
                burst.burst()
                if (isPlaying) onPause() else onPlay()
            },
            modifier = Modifier
                .fillMaxSize()
                .scale(scale.value)
                .background(color = containerColor, shape = CircleShape),
        ) {
        // 加载 / 播放 / 暂停三态图标之间淡入淡出过渡（切歌、起播、暂停不再瞬时跳变）
        Crossfade(
            targetState = when {
                isLoading -> 0
                isPlaying -> 1
                else -> 2
            },
            animationSpec = tween(180),
            label = "playStateIcon",
        ) { state ->
            when (state) {
                0 -> CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = iconTint,
                    strokeWidth = 3.dp,
                )
                1 -> Icon(
                    painter = painterResource(R.drawable.ic_pause),
                    contentDescription = stringResource(R.string.pause),
                    modifier = Modifier.size(40.dp),
                    tint = iconTint,
                )
                else -> Icon(
                    painter = painterResource(R.drawable.ic_play),
                    contentDescription = stringResource(R.string.play),
                    modifier = Modifier.size(40.dp),
                    tint = iconTint,
                )
            }
        }
        }
    }
}

@Composable
fun PlayModeButton(
    playMode: PlayMode,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    val burst = rememberParticleBurstState()
    ParticleBurstBox(state = burst, color = tint) {
        IconButton(
            onClick = {
                burst.burst()
                onClick()
            },
        ) {
            // 模式图标淡入淡出切换（不再瞬时跳变）
            Crossfade(
                targetState = playMode,
                animationSpec = tween(180),
                label = "playModeIcon",
            ) { mode ->
                Icon(
                    painter = when (mode) {
                        PlayMode.SEQUENTIAL -> painterResource(R.drawable.ic_shuffle_disabled)
                        PlayMode.SHUFFLE -> painterResource(R.drawable.ic_shuffle)
                        PlayMode.SINGLE_LOOP -> painterResource(R.drawable.ic_repeat_one)
                        PlayMode.REPEAT_ALL -> painterResource(R.drawable.ic_repeat)
                    },
                    contentDescription = when (mode) {
                        PlayMode.SEQUENTIAL -> stringResource(R.string.mode_sequential)
                        PlayMode.SHUFFLE -> stringResource(R.string.mode_shuffle)
                        PlayMode.SINGLE_LOOP -> stringResource(R.string.mode_single_loop)
                        PlayMode.REPEAT_ALL -> stringResource(R.string.mode_repeat_all)
                    },
                    modifier = Modifier.size(28.dp),
                    tint = tint,
                )
            }
        }
    }
}

@Composable
fun ControlButton(icon: Painter, onClick: () -> Unit, size: Dp = 48.dp, tint: Color = MaterialTheme.colorScheme.onSurface) {
    val burst = rememberParticleBurstState()
    ParticleBurstBox(state = burst, modifier = Modifier.size(size), color = tint) {
        IconButton(
            onClick = {
                burst.burst()
                onClick()
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Icon(painter = icon, contentDescription = null, modifier = Modifier.size(size * 0.65f), tint = tint)
        }
    }
}
