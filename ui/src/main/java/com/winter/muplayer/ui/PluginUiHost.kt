package com.winter.muplayer.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.winter.muplayer.config.ComponentRegistry
import com.winter.muplayer.config.CssParseResult
import com.winter.muplayer.config.CssParser
import com.winter.muplayer.config.CssRuleTable
import com.winter.muplayer.config.LayoutParser
import com.winter.muplayer.config.LocalComponentCss
import com.winter.muplayer.config.SlotContext
import com.winter.muplayer.config.StyleConfigLoader
import com.winter.muplayer.config.parseCssColor
import com.winter.muplayer.config.parseCssDp
import com.winter.muplayer.config.ComponentLayout
import com.winter.muplayer.plugin.PluginUiBridge
import com.winter.muplayer.plugin.LuaPluginManager
import com.winter.muplayer.plugin.bridge.PluginSession
import com.winter.muplayer.plugin.bridge.UiAction
import com.winter.muplayer.plugin.bridge.UiComponentConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 插件 UI 宿主：实现 [PluginUiBridge]，在插件加载/卸载时把插件注册的组件挂到全局
 * [ComponentRegistry]，并聚合"当前打开的页面/widget 浮层"状态（[overlay]）。
 *
 * - 普通组件：icon（内置图标名）或标题首字符 + 点按/长按/滑动手势，动作可执行 Lua
 *   函数、唤起界面或 widget；
 * - slot 型组件：圆角卡片背景层（children 由布局 JSON 承载，参考 fp-backdrop）；
 * - 页面/widget：布局 JSON 存放在插件 zip 内（格式与 main.json 一致），打开时由
 *   [PluginUiOverlay] 用 SlotRenderer 渲染，可引用 app 内置组件。
 */
object PluginUiHost : PluginUiBridge {

    enum class UiKind { PAGE, DROPDOWN, BOTTOM_SHEET, DIALOG, PLUGIN_INFO }

    /** 当前打开的页面/widget 浮层。 */
    data class UiOverlay(
        val session: PluginSession,
        val title: String,
        val layoutPath: String,
        val kind: UiKind,
    )

    private val sessions = ConcurrentHashMap<String, PluginSession>()

    /** 各插件已注册到 ComponentRegistry 的组件 id（供卸载/重同步清理）。 */
    private val registeredByPlugin = ConcurrentHashMap<String, MutableSet<String>>()

    /** 主线程 Handler：插件 UI 同步统一走主线程。 */
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** 去抖集合：已排队待同步的插件 id（同一插件的连续变更合并为一次全量同步）。 */
    private val pendingSync = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var manager: LuaPluginManager? = null

    private val _overlay = MutableStateFlow<UiOverlay?>(null)
    val overlay: StateFlow<UiOverlay?> = _overlay.asStateFlow()

    /** 由 PluginHost 在创建 LuaPluginManager 后调用。 */
    fun attach(manager: LuaPluginManager) {
        this.manager = manager
    }

    /** 某插件导出的函数名（服务插件默认界面展示 API 列表用）。 */
    fun exportedNames(pluginId: String): List<String> =
        manager?.exportedNames(pluginId)?.toList() ?: emptyList()

    // ==================== PluginUiBridge ====================

    override fun onPluginLoaded(session: PluginSession) {
        sessions[session.descriptor.id] = session
        syncComponents(session)
        android.util.Log.i(
            "LuaPlugin-UI",
            "onPluginLoaded ${session.descriptor.id}: 注册 ${session.ui.components.size} 组件 " +
                session.ui.components.keys.joinToString()
        )
    }

    /** 运行时 wm.ui 注册/反注册后调用：全量重新同步该插件组件渲染器。 */
    override fun onPluginUiChanged(pluginId: String) {
        val session = sessions[pluginId] ?: return
        // 去抖：同一插件的连续多次 register/unregister（如一次点击触发十几次）
        // 合并为一次主线程全量同步，避免重复的全量重注册。
        if (!pendingSync.add(pluginId)) return
        mainHandler.post {
            pendingSync.remove(pluginId)
            syncComponents(session)
        }
    }

    override fun onPluginUnloaded(pluginId: String) {
        // 若该插件正打开浮层 → 一并关闭
        _overlay.update { if (it?.session?.descriptor?.id == pluginId) null else it }
        // 反注册其已注册的组件并清理记录
        registeredByPlugin.remove(pluginId)?.forEach { ComponentRegistry.unregister(it) }
        sessions.remove(pluginId)
    }

    /** 全量同步：卸载旧渲染器 → 按当前 PluginUiRegistry 重新注册。 */
    private fun syncComponents(session: PluginSession) {
        val pluginId = session.descriptor.id
        registeredByPlugin.remove(pluginId)?.forEach { ComponentRegistry.unregister(it) }
        val ids = mutableSetOf<String>()
        for ((id, config) in session.ui.components) {
            ComponentRegistry.register(id) {
                if (config.isSlot) {
                    renderPluginSlotComponent(config)
                } else {
                    renderPluginComponent(config, session)
                }
            }
            ids += id
        }
        registeredByPlugin[pluginId] = ids
        android.util.Log.i("LuaPlugin-UI", "syncComponents $pluginId: ${ids.size} 个渲染器已挂载 -> ${ids.joinToString()}")
    }

    // ==================== 打开 / 关闭 ====================

    fun close() {
        _overlay.value = null
    }

    fun openPage(pluginId: String, pageId: String) {
        val session = sessions[pluginId] ?: return
        session.ui.pages[pageId]?.let { page ->
            _overlay.value = UiOverlay(session, page.title, page.layoutPath, UiKind.PAGE)
        }
    }

    fun openWidget(pluginId: String, widgetId: String) {
        val session = sessions[pluginId] ?: return
        session.ui.widgets[widgetId]?.let { widget ->
            val kind = when (widget.type) {
                "bottom_sheet" -> UiKind.BOTTOM_SHEET
                "dropdown" -> UiKind.DROPDOWN
                else -> UiKind.DIALOG
            }
            _overlay.value = UiOverlay(session, widget.title, widget.layoutPath, kind)
        }
    }

    /**
     * 打开插件默认界面：优先渲染 zip 根目录 main.json；
     * 缺失时生成信息页（名称/类别/导出函数/注册 UI 元素）。
     */
    fun openDefault(pluginId: String) {
        var session = sessions[pluginId]
        if (session == null) {
            // 兜底：会话缺失（如启动自动加载失败）时尝试加载
            runCatching { manager?.load(pluginId) }
            session = sessions[pluginId]
        }
        if (session == null) {
            android.util.Log.e("LuaPlugin-UI", "openDefault 失败：插件 $pluginId 会话缺失（load 失败）")
            return
        }
        val mainJson = session.descriptor.defaultLayoutFile
        android.util.Log.i(
            "LuaPlugin-UI",
            "openDefault $pluginId: main.json 存在=${mainJson.isFile} path=$mainJson " +
                "组件数=${session.ui.components.size}"
        )
        _overlay.value = if (mainJson.isFile) {
            UiOverlay(session, session.descriptor.name, "main.json", UiKind.PAGE)
        } else {
            UiOverlay(session, session.descriptor.name, "", UiKind.PLUGIN_INFO)
        }
    }

    // ==================== 组件注册 ====================

    // ==================== 手势动作 ====================

    private fun performAction(session: PluginSession, action: UiAction) {
        when (action) {
            is UiAction.Call -> session.scheduler.runBlock {
                action.fn.invoke()
            }
            is UiAction.OpenPage -> openPage(session.descriptor.id, action.pageId)
            is UiAction.OpenWidget -> openWidget(session.descriptor.id, action.widgetId)
        }
    }

    // ==================== 布局解析 ====================

    /** 插件布局解析结果（布局 + 可选内联 style CSS）。 */
    data class PluginLayout(
        val layout: ComponentLayout,
        val css: CssRuleTable,
    )

    /**
     * 解析插件 zip 内布局 JSON（与 main.json 同格式）。
     *
     * 样式来源（合并，后者优先级更高）：
     * 1. 布局 JSON 内联 `style` 字段（兼容旧写法，新插件不再使用）；
     * 2. 与布局 JSON **同目录的 `style.css`**（插件统一样式文件，JSON 只定义 slot 结构）。
     * 失败返回失败结果（含原因），并记录 Logcat（插件 id + 路径 + 异常），不再静默。
     */
    fun loadLayout(session: PluginSession, layoutPath: String): Result<PluginLayout> {
        return runCatching {
            val file = File(session.descriptor.dir, layoutPath)
            check(file.isFile) {
                "插件 ${session.descriptor.id} 的界面文件不存在或不是文件: $layoutPath（需放在插件 zip 的该路径，且不能是目录）"
            }
            val root = JSONObject(LayoutParser.removeComments(file.readText()))
            val layout = StyleConfigLoader.parseConfigObjectStatic(root)
            // JSON 内联 style（兼容） + 同目录 style.css（插件统一样式，覆盖同名规则）
            val jsonCss = root.optString("style").takeIf { it.isNotBlank() }
                ?.let { CssParser.parseAll(it) } ?: CssParseResult(emptyMap(), emptyMap())
            val cssFile = File(file.parentFile, "style.css")
            val fileCss = if (cssFile.isFile) CssParser.parseAll(cssFile.readText())
            else CssParseResult(emptyMap(), emptyMap())
            val merged = jsonCss.rules + fileCss.rules
            val mergedKeyframes = jsonCss.keyframes + fileCss.keyframes
            android.util.Log.i(
                "LuaPlugin-UI",
                "loadLayout ${session.descriptor.id}/$layoutPath: 规则 ${merged.size} 个" +
                    "（json ${jsonCss.rules.size} + style.css ${fileCss.rules.size}），" +
                    "关键帧 ${mergedKeyframes.size} 个"
            )
            PluginLayout(layout, CssRuleTable(merged, mergedKeyframes))
        }.onFailure { e ->
            android.util.Log.e(
                "LuaPlugin-UI",
                "插件 ${session.descriptor.id} 布局解析失败 ($layoutPath): ${e.message}",
                e
            )
        }
    }

    /** 切换到插件信息页（默认界面加载失败时的兜底展示）。 */
    fun showInfo(session: PluginSession) {
        _overlay.value = UiOverlay(session, session.descriptor.name, "", UiKind.PLUGIN_INFO)
    }

    // ==================== 插件组件渲染 ====================

    /** 内置图标名 → drawable 资源。 */
    private val ICONS: Map<String, Int> = mapOf(
        "play" to R.drawable.ic_play,
        "pause" to R.drawable.ic_pause,
        "prev" to R.drawable.ic_skip_previous,
        "next" to R.drawable.ic_skip_next,
        "shuffle" to R.drawable.ic_shuffle,
        "repeat" to R.drawable.ic_repeat,
        "repeat_one" to R.drawable.ic_repeat_one,
        "queue" to R.drawable.ic_playlist_music,
        "search" to R.drawable.ic_search,
        "settings" to R.drawable.ic_settings,
        "music" to R.drawable.ic_music_note,
        "library" to R.drawable.ic_library_music,
        "clear" to R.drawable.ic_clear,
        "delete" to R.drawable.ic_delete,
        "person" to R.drawable.ic_person,
        "disc" to R.drawable.ic_disc,
    )

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun SlotContext.renderPluginComponent(config: UiComponentConfig, session: PluginSession) {
        // 布局 CSS（组件内联 style / 页面 CSS）：color 渲染纯色块（background-color 兼容旧写法），size / border-radius 控制形状
        val css = LocalComponentCss.current
        val bg = (css["color"] ?: css["background-color"])?.let { parseCssColor(it) }
        val size = css["size"]?.let { parseCssDp(it) } ?: 48.dp
        val radius = css["border-radius"]?.let { parseCssDp(it) } ?: size / 2
        val iconRes = config.icon?.let { ICONS[it] }
        var dragging by mutableStateOf(false)
        // 诊断：记录组件 CSS 命中情况（首次渲染），tag LuaPlugin-UI 可过滤
        remember(config.id, css["color"], css["background-color"]) {
            android.util.Log.d(
                "LuaPlugin-UI",
                "render ${config.id}: color=${css["color"]} bg-color=${css["background-color"]} " +
                    "解析=${bg != null} size=${css["size"]} radius=${css["border-radius"]} css=$css"
            )
        }

        // 手势：点按 / 长按 / 滑动（动作可执行 Lua 函数、唤起界面或 widget）
        val gestureMod: Modifier = Modifier
            .then(
                if (config.onClick != null || config.onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { config.onClick?.let { performAction(session, it) } },
                        onLongClick = config.onLongClick?.let { { performAction(session, it) } },
                    )
                } else Modifier
            )
            .pointerInput(config.onSwipe) {
                val swipe = config.onSwipe
                if (swipe != null) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = {
                            if (dragging) {
                                dragging = false
                                performAction(session, swipe)
                            }
                        },
                        onDragCancel = { dragging = false },
                    ) { _, _ -> }
                }
            }

        if (bg != null) {
            // 纯色块（布局指定背景色，如色板选择器）：不显示 icon/首字符
            Column(
                modifier = Modifier.padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(size)
                        .clip(RoundedCornerShape(radius))
                        .background(bg)
                        .then(gestureMod),
                    contentAlignment = Alignment.Center,
                ) {}
                Text(
                    text = config.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            return
        }

        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(radius))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .then(gestureMod),
                contentAlignment = Alignment.Center,
            ) {
                if (iconRes != null) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    // 无 icon：显示标题首字符
                    Text(
                        text = config.title.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
            Text(
                text = config.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }

    /** slot 型插件组件：圆角卡片背景层（children 前景由布局 JSON 承载，参考 fp-backdrop）。 */
    @Composable
    private fun SlotContext.renderPluginSlotComponent(config: UiComponentConfig) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
        ) {}
    }
}
