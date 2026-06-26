package com.winter.muplayer.base_ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.winter.muplayer.plugin_runtime.PluginWidget
import com.winter.muplayer.plugin_runtime.WidgetType

/**
 * 渲染插件塞过来的 UI 小部件～（Shadow 架构版）
 *
 * 使用 [PluginWidget] 数据类替代旧架构的 [com.winter.muplayer.plugin_api.SlotWidget]。
 * Widget 类型定义在 plugin-runtime 模块中，宿主和插件共享同一个数据契约。
 *
 * @param slotName  Slot 的名字，如 "below_controls"（控制按钮下面）
 * @param widgets   这个 Slot 里要展示的小部件列表
 * @param background  要不要在外面包一层 Card 背景呀？
 * @param onWidgetAction  插件按钮被点击的时候会调这个回调，告诉你 action 是啥
 */
@Composable
fun PluginSlot(
    slotName: String,
    widgets: List<PluginWidget>,
    background: Boolean = true,
    onWidgetAction: (String) -> Unit = {}
) {
    if (widgets.isEmpty()) return

    val content = @Composable {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            widgets.forEach { widget ->
                SlotWidgetItem(widget = widget, onAction = onWidgetAction)
            }
        }
    }

    if (background) {
        androidx.compose.material3.Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = androidx.compose.material3.CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            content()
        }
    } else {
        content()
    }
}

/**
 * 单个 Widget 的渲染逻辑～
 * 根据 type 不同，可以渲染成文字、跑马灯、图片或者按钮。
 */
@Composable
private fun SlotWidgetItem(widget: PluginWidget, onAction: (String) -> Unit = {}) {
    val color = try {
        Color(android.graphics.Color.parseColor(widget.color))
    } catch (_: Exception) {
        MaterialTheme.colorScheme.onSurface
    }

    val align = when (widget.align) {
        "start" -> TextAlign.Start
        "end" -> TextAlign.End
        else -> TextAlign.Center
    }

    when (widget.type) {
        WidgetType.TEXT -> {
            val size = when (widget.size) {
                "small" -> 12.sp
                "large" -> 18.sp
                else -> 14.sp
            }
            Text(
                text = widget.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = size,
                    fontWeight = FontWeight.Medium
                ),
                color = color,
                textAlign = align,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        WidgetType.MARQUEE_TEXT -> {
            MarqueeText(
                text = widget.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                isPlaying = true
            )
        }

        WidgetType.IMAGE -> {
            if (widget.url.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                            .data(widget.url)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        WidgetType.BUTTON -> {
            val buttonAlign: Alignment.Horizontal = when (widget.align) {
                "start" -> Alignment.Start
                "end" -> Alignment.End
                else -> Alignment.CenterHorizontally
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = when (buttonAlign) {
                    Alignment.Start -> Arrangement.Start
                    Alignment.End -> Arrangement.End
                    else -> Arrangement.Center
                }
            ) {
                androidx.compose.material3.Button(
                    onClick = { onAction(widget.action) }
                ) {
                    Text(widget.content)
                }
            }
        }
    }
}
