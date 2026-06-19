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
import com.winter.muplayer.plugin_api.PluginContract
import com.winter.muplayer.plugin_api.SlotWidget

/**
 * 渲染一个插件 Slot 位置的所有 Widget。
 *
 * @param slotName  slot 名称，如 "below_controls"
 * @param widgets   该 slot 中的 widget 列表
 * @param background  是否包裹背景 Card
 */
@Composable
fun PluginSlot(
    slotName: String,
    widgets: List<SlotWidget>,
    background: Boolean = true
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
                SlotWidgetItem(widget = widget)
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

@Composable
private fun SlotWidgetItem(widget: SlotWidget) {
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
        PluginContract.WIDGET_TEXT -> {
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

        PluginContract.WIDGET_MARQUEE -> {
            MarqueeText(
                text = widget.content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                isPlaying = true
            )
        }

        PluginContract.WIDGET_IMAGE -> {
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

        PluginContract.WIDGET_BUTTON -> {
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
                    onClick = { /* 按钮点击由 MusicPlayerCore 处理 */ }
                ) {
                    Text(widget.content)
                }
            }
        }
    }
}
