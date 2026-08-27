package com.winter.muplayer.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import com.winter.muplayer.config.ComponentLayout
import com.winter.muplayer.config.CssRuleTable
import com.winter.muplayer.config.SlotContext
import com.winter.muplayer.config.SlotRenderer
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.plugin.bridge.PluginSession


/**
 * 插件页面/widget 浮层：消费 [PluginUiHost.overlay] 状态，用 SlotRenderer 渲染
 * 插件 zip 内的布局 JSON（与 main.json 同格式，可引用 app 内置组件）。
 *
 * - bottom_sheet → ModalBottomSheet 底部面板
 * - dialog / page → 居中对话框
 * - dropdown → 居中圆角卡片浮层（简化实现）
 *
 * 在 Activity 内容顶部挂载一次：[PluginUiOverlay]()。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginUiOverlay() {
    val overlay by PluginUiHost.overlay.collectAsState()
    val context = LocalContext.current
    val slotContext = rememberPluginSlotContext(context)

    // ========== 全屏默认界面（PAGE）：进入/退出过渡动画 ==========
    // ========== 全屏默认界面（PAGE）：内容与状态一起过渡（进入/退出都有动画） ==========
    AnimatedContent(
        targetState = overlay?.takeIf { it.kind == PluginUiHost.UiKind.PAGE },
        transitionSpec = {
            (fadeIn(tween(220)) + slideInHorizontally(tween(300)) { it / 4 })
                .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(tween(260)) { it / 3 })
        },
        label = "plugin-page",
    ) { target ->
        if (target != null) {
            // 同步加载布局数据（组合期间），保证进入动画播放时内容已就绪
            val r = remember(target.session.descriptor.id, target.layoutPath) {
                PluginUiHost.loadLayout(target.session, target.layoutPath)
            }
            PluginFullscreenPage(
                session = target.session,
                layout = r.getOrNull()?.layout,
                css = r.getOrNull()?.css ?: CssRuleTable(),
                slotContext = slotContext,
                error = r.exceptionOrNull()?.message,
            )
        }
    }

    // ========== 非 PAGE 浮层（信息页 / BottomSheet / Dialog） ==========
    if (overlay != null && overlay!!.kind != PluginUiHost.UiKind.PAGE) {
        val o = overlay!!
        when (o.kind) {
            PluginUiHost.UiKind.PLUGIN_INFO -> PluginInfoDialog(o.session)
            else -> {
                val result = remember(o.session.descriptor.id, o.layoutPath) {
                    PluginUiHost.loadLayout(o.session, o.layoutPath)
                }
                if (result == null) return
                if (result.isFailure) {
                    Dialog(onDismissRequest = { PluginUiHost.close() }) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text("无法显示默认界面", style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = result.exceptionOrNull()?.message ?: "未知错误",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.align(Alignment.End),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    TextButton(onClick = { PluginUiHost.showInfo(o.session) }) {
                                        Text("查看插件信息")
                                    }
                                    TextButton(onClick = { PluginUiHost.close() }) {
                                        Text("关闭")
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val (layout, css) = result.getOrThrow()
                    if (o.kind == PluginUiHost.UiKind.BOTTOM_SHEET) {
                        ModalBottomSheet(onDismissRequest = { PluginUiHost.close() }) {
                            SlotRenderer(
                                slots = layout.slots,
                                context = slotContext,
                                css = css,
                                customComponents = layout.customComponents,
                                debug = false,
                                outerArrange = css.rules[".main"]?.get("arrange"),
                            )
                            Spacer(Modifier.padding(bottom = 32.dp))
                        }
                    } else {
                        Dialog(onDismissRequest = { PluginUiHost.close() }) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                            ) {
                                SlotRenderer(
                                    slots = layout.slots,
                                    context = slotContext,
                                    css = css,
                                    customComponents = layout.customComponents,
                                    debug = false,
                                    outerArrange = css.rules[".main"]?.get("arrange"),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 插件默认界面全屏页：顶部返回栏 + SlotRenderer 内容（覆盖整个界面，返回键退出）。 */
@Composable
private fun PluginFullscreenPage(
    session: PluginSession,
    layout: ComponentLayout?,
    css: CssRuleTable,
    slotContext: SlotContext,
    error: String?,
) {
    // 系统返回键同样退出
    BackHandler { PluginUiHost.close() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 顶部返回栏（避开状态栏）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { PluginUiHost.close() }) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_back),
                        contentDescription = stringResource(R.string.back),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = session.descriptor.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                )
            }
            // 内容区：SlotRenderer 填充剩余空间；布局缺失时显示错误
            if (layout != null && error == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    SlotRenderer(
                        slots = layout.slots,
                        context = slotContext,
                        css = css,
                        customComponents = layout.customComponents,
                        debug = false,
                        outerArrange = css.rules[".main"]?.get("arrange"),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = error ?: "无法显示默认界面",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** 插件信息页（无 main.json 时宿主生成）：名称/类别 + 导出函数 API + 注册 UI 元素。 */
@Composable
private fun PluginInfoDialog(session: PluginSession) {
    val pluginId = session.descriptor.id
    val typeLabel = when (session.descriptor.type) {
        com.winter.muplayer.plugin.model.PluginType.APP -> "基本插件"
        com.winter.muplayer.plugin.model.PluginType.COMPONENT -> "组件插件"
        com.winter.muplayer.plugin.model.PluginType.SERVICE -> "服务插件"
    }
    Dialog(onDismissRequest = { PluginUiHost.close() }) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(session.descriptor.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    "${session.descriptor.id} · v${session.descriptor.version} · $typeLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                val exported = PluginUiHost.exportedNames(pluginId)
                Text(
                    if (exported.isEmpty()) "（未导出函数）" else "导出函数 API",
                    style = MaterialTheme.typography.titleMedium,
                )
                LazyColumn(modifier = Modifier.heightIn(max = 160.dp)) {
                    items(exported) { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                val ui = session.ui
                if (ui.components.isNotEmpty() || ui.pages.isNotEmpty() || ui.widgets.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text("注册 UI 元素", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "组件 ${ui.components.size} · 界面 ${ui.pages.size} · widget ${ui.widgets.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { PluginUiHost.close() },
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text("关闭")
                }
            }
        }
    }
}

/** 构建插件页面使用的 SlotContext：桥接播放核心，供页面内 app 组件使用。 */
@Composable
private fun rememberPluginSlotContext(context: Context): SlotContext {
    val core = remember { MusicPlayerCore.getInstance(context) }
    return remember(core) {
        SlotContext(
            slotName = "plugin-page",
            onOpenSearch = {},
            onOpenSettings = {},
            localMusicList = emptyList(),
            isLoadingLocal = false,
            coverCache = emptyMap(),
            musicPlayerCore = core,
            onPlayTrackSmart = { _, _ -> },
            playerState = core.playerState.value,
            onPlay = { core.play() },
            onPause = { core.pause() },
            onNext = { core.playNext() },
            onPrevious = { core.playPrevious() },
            onOpenFullPlayer = {},
            onOpenQueue = {},
            onSeek = { core.seekTo(it) },
            onPlayModeChange = { core.setPlayMode(it) },
            playMode = core.playMode.value,
            adaptiveTint = Color.Unspecified,
            blurBackground = false,
        )
    }
}

// 内部数据结构别名（避免渲染处重复类型）
private typealias LayoutData = Pair<ComponentLayout, CssRuleTable>
