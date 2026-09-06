package com.winter.muplayer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Dialog 内容进入动画：淡入 + 轻微放大（0.92→1）。
 * 内容始终参与布局（Dialog 尺寸稳定不跳变），仅透明度 / 缩放做一次性过渡。
 */
@Composable
private fun DialogEnter(content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(200)) }
        launch { scale.animateTo(1f, tween(240)) }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            this.alpha = alpha.value
            scaleX = scale.value
            scaleY = scale.value
        }
    ) {
        content()
    }
}

/**
 * 带点击粒子特效的底部面板：封装 Material3 [ModalBottomSheet] + [ParticleLayer]，
 * 面板内任意点击都会在手指位置播放粒子（样式 / 颜色 / 总开关继承全局配置）。
 * 可直接替换普通 ModalBottomSheet 使用；[sheetState] 传 null 时内部自建（全展开）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticleModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit,
) {
    // 内部自建状态：一次弹出到全高（skipPartiallyExpanded），下滑拖拽可收回
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = state,
        modifier = modifier,
        shape = shape,
    ) {
        ParticleLayer(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/**
 * 带点击粒子特效的普通对话框：内部即 [ParticleLayer] 包裹的 Dialog，弹层内任意点击
 * 都会在手指位置播放粒子（样式 / 颜色 / 总开关继承全局配置）。可直接替换普通
 * `androidx.compose.ui.window.Dialog(onDismissRequest = ...) { ... }` 使用。
 */
@Composable
fun ParticleDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        ParticleLayer {
            DialogEnter { content() }
        }
    }
}

/**
 * 带点击粒子特效的确认对话框：外观与 Material3 [androidx.compose.material3.AlertDialog]
 * 一致（28dp 圆角 Surface、24dp 内边距、按钮右对齐），但内容整体由 [ParticleLayer]
 * 包裹 —— 独立窗口内任意点击（含确定 / 取消按钮、空白处）都会在手指位置播放粒子，
 * 样式 / 颜色 / 总开关继承全局配置。可直接替换普通 AlertDialog 使用。
 */
@Composable
fun ParticleAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismissRequest) {
        ParticleLayer(modifier = modifier) {
            DialogEnter {
                Surface(
                    shape = shape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.Start,
                    ) {
                        if (icon != null) {
                            Box(modifier = Modifier.padding(bottom = 16.dp)) { icon() }
                        }
                        if (title != null) {
                            val hasBelow = text != null || icon != null
                            Box(modifier = if (hasBelow) Modifier.padding(bottom = 16.dp) else Modifier) {
                                title()
                            }
                        }
                        if (text != null) {
                            Box(modifier = Modifier.padding(bottom = 24.dp)) { text() }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (dismissButton != null) {
                                dismissButton()
                                Spacer(Modifier.width(8.dp))
                            }
                            confirmButton()
                        }
                    }
                }
            }
        }
    }
}
