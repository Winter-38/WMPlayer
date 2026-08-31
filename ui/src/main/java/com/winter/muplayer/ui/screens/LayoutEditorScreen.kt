package com.winter.muplayer.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.winter.muplayer.config.ComponentEntry
import com.winter.muplayer.config.ComponentLayout
import com.winter.muplayer.config.ComponentRegistry
import com.winter.muplayer.config.ConfigWriter
import com.winter.muplayer.config.CssRuleTable
import com.winter.muplayer.config.LayoutParser
import com.winter.muplayer.config.NestedHop
import com.winter.muplayer.config.SlotContext
import com.winter.muplayer.config.SlotRenderer
import com.winter.muplayer.config.StyleConfigLoader
import com.winter.muplayer.config.nestedListOf
import com.winter.muplayer.config.parseCssColor
import com.winter.muplayer.config.replaceNestedRoot
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.core.SettingsManager
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.ui.R
import com.winter.muplayer.ui.browser.LocalBrowserState
import com.winter.muplayer.ui.browser.MusicBrowserState
import com.winter.muplayer.ui.components.LocalParticleBurstEnabled
import com.winter.muplayer.ui.components.LocalParticleBurstHost
import kotlin.math.roundToInt

/**
 * 布局与样式编辑器 —— 从设置页进入。
 *
 * 实时自定义当前布局与样式：
 * - 上半部分为实时预览（主界面 / 全屏播放器），任何修改立即写盘 + 热重载，预览同步刷新；
 * - 「布局」tab：主界面与全屏播放器的 slot / 组件增删、排序，点击组件或区域编辑其样式；
 * - 「样式」tab：编辑任意 CSS 选择器（区域 `.x` / 组件 `#x`）的属性，支持常用表单 + 高级自由属性；
 * - 「重置默认」恢复应用内置默认布局与样式。
 *
 * 所有修改立即生效并落盘（main.json / styles.css），退出编辑器即保持。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutEditorScreen(
    onBack: () -> Unit,
    configLoader: StyleConfigLoader,
    musicPlayerCore: MusicPlayerCore,
    browserState: MusicBrowserState,
    coverCache: MutableMap<Long, String>,
    playerState: PlayerStateData,
    playMode: PlayMode,
    blurBackground: Boolean = false,
    adaptiveTintStyle: SettingsManager.AdaptiveTintStyle = SettingsManager.AdaptiveTintStyle.MONOCHROME,
) {
    val configState by configLoader.config.collectAsState()
    val cssRules by configLoader.cssRules.collectAsState()

    // ── 编辑器状态 ──
    var tab by remember { mutableIntStateOf(0) }                 // 0=布局 1=样式
    var previewMode by remember { mutableIntStateOf(0) }         // 0=主界面 1=全屏
    // 平板端：双击预览进入全屏预览（再次双击退出）
    var previewExpanded by remember { mutableStateOf(false) }
    var styleTarget by remember { mutableStateOf<StyleTarget?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DeleteRequest?>(null) }
    // 添加组件对话框的目标：Main 主界面 slot / Full 全屏播放器子 slot
    var addComponentTarget by remember { mutableStateOf<AddComponentTarget?>(null) }
    // 添加区域（slot）对话框目标：主界面根 / 主界面嵌套 / 全屏嵌套
    var addAreaTarget by remember { mutableStateOf<AddAreaTarget?>(null) }
    var showAddSelector by remember { mutableStateOf(false) }

    // ── 提交：序列化 → 写盘 → 热重载（预览随 configState / cssRules 更新而刷新）──
    fun commitLayout(newLayout: ComponentLayout) {
        configLoader.saveLayoutJson(ConfigWriter.layoutToJson(newLayout))
        configLoader.reload()
    }

    fun commitCss(newTable: CssRuleTable) {
        configLoader.saveStylesCss(ConfigWriter.cssTableToText(newTable))
        configLoader.reload()
    }

    // ══════════════ 布局操作 ══════════════
    fun updateSlot(slotName: String, entries: List<ComponentEntry>) {
        commitLayout(configState.copy(slots = configState.slots + (slotName to entries)))
    }

    // ── 主界面 slot 任意层级列表操作（path 定位，空路径 = 根列表）──
    fun replaceSlotList(slotName: String, path: List<NestedHop>, newList: List<ComponentEntry>) {
        val root = configState.slots[slotName] ?: return
        val newRoot = replaceNestedRoot(root, path, newList) ?: return
        commitLayout(configState.copy(slots = configState.slots + (slotName to newRoot)))
    }

    fun addToList(slotName: String, path: List<NestedHop>, componentId: String) {
        val root = configState.slots[slotName] ?: return
        replaceSlotList(slotName, path, nestedListOf(root, path) + ComponentEntry(componentId))
    }

    fun moveInList(slotName: String, path: List<NestedHop>, from: Int, to: Int) {
        val root = configState.slots[slotName] ?: return
        val list = nestedListOf(root, path).toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        replaceSlotList(slotName, path, list)
    }

    /** 主界面 slot 顺序调整（拖拽）：按当前顺序将 [from] 位置插入到 [to]。 */
    fun moveSlotByIndex(from: Int, to: Int) {
        val keys = configState.slots.keys.toMutableList()
        if (from !in keys.indices || to !in keys.indices || from == to) return
        val key = keys.removeAt(from)
        keys.add(to, key)
        val newSlots = linkedMapOf<String, List<ComponentEntry>>()
        keys.forEach { k -> newSlots[k] = configState.slots[k].orEmpty() }
        commitLayout(configState.copy(slots = newSlots))
    }

    /**
     * 子 slot 序列内排序（拖拽）：调整容器条目 children 的顺序（from → to 插入）。
     * 子 slot 相互并列，可与同级子 slot 互换位置。
     */
    fun moveChildInList(
        fullPlayer: Boolean,
        slotName: String,
        path: List<NestedHop>,
        containerIndex: Int,
        from: Int,
        to: Int,
    ) {
        val root = (if (fullPlayer) configState.fullPlayerSlots[slotName] else configState.slots[slotName]) ?: return
        val list = nestedListOf(root, path).toMutableList()
        val entry = list.getOrNull(containerIndex) ?: return
        val children = entry.extra["children"] as? Map<*, *> ?: return
        val childItems = children.entries.toMutableList()
        if (from !in childItems.indices || to !in childItems.indices || from == to) return
        val item = childItems.removeAt(from)
        childItems.add(to, item)
        val newChildren = linkedMapOf<String, List<ComponentEntry>>()
        childItems.forEach { e ->
            @Suppress("UNCHECKED_CAST")
            val v = e.value as List<ComponentEntry>?
            if (v != null) newChildren[e.key.toString()] = v
        }
        list[containerIndex] = entry.copy(extra = entry.extra + ("children" to newChildren))
        if (fullPlayer) {
            val newRoot = replaceNestedRoot(root, path, list) ?: return
            commitLayout(configState.copy(fullPlayerSlots = configState.fullPlayerSlots + (slotName to newRoot)))
        } else {
            replaceSlotList(slotName, path, list)
        }
    }

    fun removeFromList(slotName: String, path: List<NestedHop>, index: Int) {
        val root = configState.slots[slotName] ?: return
        val list = nestedListOf(root, path).toMutableList()
        if (index in list.indices) list.removeAt(index)
        replaceSlotList(slotName, path, list)
    }

    fun addSlot(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank() || configState.slots.containsKey(trimmed)) return
        // 空 slot 会被解析器丢弃（parseConfigObjectStatic 仅保留非空条目），
        // 因此新建区域默认附带一个弹性空白组件，保证保存后区域可见可编辑。
        commitLayout(configState.copy(slots = configState.slots + (trimmed to listOf(ComponentEntry("spacer")))))
    }

    fun removeSlot(slotName: String) {
        if (!configState.slots.containsKey(slotName)) return
        // 移除布局中的区域；对应区域样式（.slotName）留待用户在样式 tab 手动清理
        commitLayout(configState.copy(slots = configState.slots - slotName))
    }

    /** 请求添加区域：弹出名称输入对话框（主界面根 / 嵌套 / 全屏统一入口）。 */
    fun requestAddArea(fullPlayer: Boolean, slotName: String, path: List<NestedHop>) {
        addAreaTarget = AddAreaTarget(fullPlayer, slotName, path)
    }

    /** 向主界面某列表（path）末尾添加一个命名子 slot 容器（{name, children: [spacer]}）。 */
    fun addNestedList(slotName: String, path: List<NestedHop>, name: String) {
        val root = configState.slots[slotName] ?: return
        val list = nestedListOf(root, path).toMutableList()
        list.add(
            ComponentEntry(
                LayoutParser.ANONYMOUS_CONTAINER,
                extra = mapOf("children" to mapOf(name to listOf(ComponentEntry("spacer")))),
            ),
        )
        replaceSlotList(slotName, path, list)
    }

    /** 向全屏播放器某列表（path）末尾添加一个命名子 slot 容器。 */
    fun addNestedFullList(slotName: String, path: List<NestedHop>, name: String) {
        val root = configState.fullPlayerSlots[slotName] ?: return
        val list = nestedListOf(root, path).toMutableList()
        list.add(
            ComponentEntry(
                LayoutParser.ANONYMOUS_CONTAINER,
                extra = mapOf("children" to mapOf(name to listOf(ComponentEntry("spacer")))),
            ),
        )
        val newRoot = replaceNestedRoot(root, path, list) ?: return
        commitLayout(configState.copy(fullPlayerSlots = configState.fullPlayerSlots + (slotName to newRoot)))
    }

    /** 确认添加区域：主界面根 → 新主 slot；嵌套 / 全屏 → 添加命名子 slot 容器。 */
    fun confirmAddArea(name: String) {
        val t = addAreaTarget ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        if (t.fullPlayer) {
            addNestedFullList(t.slotName, t.path, trimmed)
        } else if (t.path.isEmpty()) {
            addSlot(trimmed)
        } else {
            addNestedList(t.slotName, t.path, trimmed)
        }
        addAreaTarget = null
    }

    // ── 全屏播放器任意层级列表操作（path 相对 full-player 列表）──
    fun replaceFullPlayerList(path: List<NestedHop>, newList: List<ComponentEntry>) {
        val slotsMap = configState.fullPlayerSlots
        if (slotsMap.isEmpty()) return
        val slotName = slotsMap.keys.first()
        val root = slotsMap[slotName] ?: return
        val newRoot = replaceNestedRoot(root, path, newList) ?: return
        commitLayout(configState.copy(fullPlayerSlots = slotsMap + (slotName to newRoot)))
    }

    fun addToFullList(path: List<NestedHop>, componentId: String) {
        val slotsMap = configState.fullPlayerSlots
        if (slotsMap.isEmpty()) return
        val root = slotsMap[slotsMap.keys.first()].orEmpty()
        replaceFullPlayerList(path, nestedListOf(root, path) + ComponentEntry(componentId))
    }

    fun moveInFullList(path: List<NestedHop>, from: Int, to: Int) {
        val slotsMap = configState.fullPlayerSlots
        if (slotsMap.isEmpty()) return
        val root = slotsMap[slotsMap.keys.first()].orEmpty()
        val list = nestedListOf(root, path).toMutableList()
        if (from !in list.indices || to !in list.indices || from == to) return
        val item = list.removeAt(from)
        list.add(to, item)
        replaceFullPlayerList(path, list)
    }

    fun removeFromFullList(path: List<NestedHop>, index: Int) {
        val slotsMap = configState.fullPlayerSlots
        if (slotsMap.isEmpty()) return
        val root = slotsMap[slotsMap.keys.first()].orEmpty()
        val list = nestedListOf(root, path).toMutableList()
        if (index in list.indices) list.removeAt(index)
        replaceFullPlayerList(path, list)
    }

    // ══════════════ 样式操作 ══════════════
    fun updateProps(selector: String, props: Map<String, String>) {
        val rules = cssRules.rules.toMutableMap()
        if (props.isEmpty()) rules.remove(selector) else rules[selector] = props
        commitCss(CssRuleTable(rules))
    }

    fun removeSelector(selector: String) {
        val rules = cssRules.rules.toMutableMap()
        rules.remove(selector)
        commitCss(CssRuleTable(rules))
    }

    fun addSelector(rawName: String) {
        var name = rawName.trim()
        if (name.isBlank()) return
        // 未带 . / # 前缀时按组件（#）处理
        if (!name.startsWith(".") && !name.startsWith("#")) name = "#$name"
        if (cssRules.rules.containsKey(name)) return
        val rules = cssRules.rules.toMutableMap()
        rules[name] = emptyMap()
        commitCss(CssRuleTable(rules))
        styleTarget = StyleTarget(name, selectorTitle(name), emptyMap())
    }

    fun openComponentStyle(entry: ComponentEntry, ref: ComponentRef) {
        val selector = entry.cid?.let { "#$it" } ?: "#${entry.id}"
        styleTarget = StyleTarget(
            selector = selector,
            title = componentLabel(entry),
            props = cssRules.rules[selector].orEmpty(),
            componentRef = ref,
            componentId = entry.id,
            currentCid = entry.cid,
        )
    }

    /** 修改组件实例标识（cid）：更新布局模型并刷新编辑面板目标（选择器随 cid 变化）。 */
    fun updateComponentCid(ref: ComponentRef, componentId: String, newCid: String?) {
        val normalized = newCid?.trim()?.takeIf { it.isNotEmpty() }
        val rootList = if (ref.fullPlayer) {
            configState.fullPlayerSlots[ref.slotName]
        } else {
            configState.slots[ref.slotName]
        } ?: return
        val list = nestedListOf(rootList, ref.path).toMutableList()
        val entry = list.getOrNull(ref.index) ?: return
        if (entry.cid == normalized) return
        list[ref.index] = entry.copy(cid = normalized)
        val newRoot = replaceNestedRoot(rootList, ref.path, list) ?: return
        if (ref.fullPlayer) {
            commitLayout(configState.copy(fullPlayerSlots = configState.fullPlayerSlots + (ref.slotName to newRoot)))
        } else {
            commitLayout(configState.copy(slots = configState.slots + (ref.slotName to newRoot)))
        }
        // 刷新面板：选择器与标题随新 cid 更新（面板本地 props 重置为新选择器的样式）
        val newSelector = normalized?.let { "#$it" } ?: "#$componentId"
        styleTarget = StyleTarget(
            selector = newSelector,
            title = componentLabelOf(componentId),
            props = cssRules.rules[newSelector].orEmpty(),
            componentRef = ref,
            componentId = componentId,
            currentCid = normalized,
        )
    }

    fun openSlotStyle(slotName: String) {
        val selector = ".$slotName"
        styleTarget = StyleTarget(selector, slotName, cssRules.rules[selector].orEmpty())
    }

    fun resetDefaults() {
        configLoader.saveLayoutJson(StyleConfigLoader.defaultMainJson())
        configLoader.saveStylesCss(StyleConfigLoader.defaultStylesCss())
        configLoader.reload()
        styleTarget = null
        showResetDialog = false
    }

    // ── 预览上下文（真实数据，与主界面一致的 SlotContext）──
    val previewContext = remember(
        musicPlayerCore, playerState, playMode, coverCache, blurBackground,
        adaptiveTintStyle, browserState,
    ) {
        SlotContext(
            slotName = "main",
            onOpenSearch = {}, onOpenSettings = {},
            localMusicList = browserState.tracks,
            isLoadingLocal = browserState.isLoading,
            coverCache = coverCache,
            musicPlayerCore = musicPlayerCore,
            onPlayTrackSmart = { track, list -> musicPlayerCore.playTrackSmart(track, list) },
            playerState = playerState,
            onPlay = musicPlayerCore::play,
            onPause = musicPlayerCore::pause,
            onNext = musicPlayerCore::playNext,
            onPrevious = musicPlayerCore::playPrevious,
            onOpenFullPlayer = {}, onOpenQueue = {},
            onSeek = musicPlayerCore::seekTo,
            onPlayModeChange = musicPlayerCore::setPlayMode,
            playMode = playMode,
            adaptiveTint = Color.Unspecified,
            blurBackground = blurBackground,
        )
    }

    // ── 预览内容（手机全屏预览 / 平板右侧分栏共用）──
    val previewSlot: @Composable () -> Unit = {
        if (previewMode == 0) {
            // 主界面预览
            CompositionLocalProvider(LocalBrowserState provides browserState) {
                SlotRenderer(
                    slots = configState.slots,
                    css = cssRules,
                    customComponents = configState.customComponents,
                    context = previewContext,
                )
            }
        } else {
            // 全屏播放器预览（组件化内容，不含手势/返回处理）
            SlotRenderer(
                slots = configState.fullPlayerSlots,
                css = cssRules,
                customComponents = configState.customComponents,
                context = previewContext,
                outerArrange = cssRules.rules[".full-player"]?.get("arrange"),
            )
        }
    }

    // ── 编辑区（PrimaryTabRow + 布局 / 样式 tab）──
    val editSlot: @Composable () -> Unit = {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.layout_editor_tab_layout)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.layout_editor_tab_style)) })
        }
        when (tab) {
            0 -> LayoutTab(
                layout = configState,
                css = cssRules,
                onSlotStyle = ::openSlotStyle,
                onComponentStyle = ::openComponentStyle,
                onAddSlot = { requestAddArea(fullPlayer = false, slotName = "", path = emptyList()) },
                onAddNestedSlot = { fullPlayer, slotName, path ->
                    requestAddArea(fullPlayer = fullPlayer, slotName = slotName, path = path)
                },
                onMoveChildSlot = { fullPlayer, slotName, path, ci, from, to ->
                    moveChildInList(fullPlayer, slotName, path, ci, from, to)
                },
                onRemoveSlot = { slot ->
                    pendingDelete = DeleteRequest.Slot(
                        label = slot,
                        onConfirm = { removeSlot(slot) },
                    )
                },
                onMoveSlot = { from, to -> moveSlotByIndex(from, to) },
                onAddList = { slot, path -> addComponentTarget = AddComponentTarget.MainSlots(slot, path) },
                onMoveInList = { slot, path, from, to -> moveInList(slot, path, from, to) },
                onRemoveFromList = { slot, path, index ->
                    val target = nestedListOf(configState.slots[slot].orEmpty(), path).getOrNull(index)
                    pendingDelete = DeleteRequest.Component(
                        label = componentLabel(target ?: ComponentEntry(slot)),
                        onConfirm = { removeFromList(slot, path, index) },
                    )
                },
                onAddFullList = { path -> addComponentTarget = AddComponentTarget.FullPlayer(path) },
                onMoveInFullList = { path, from, to -> moveInFullList(path, from, to) },
                onRemoveFromFullList = { path, index ->
                    val root = configState.fullPlayerSlots.values.firstOrNull().orEmpty()
                    val target = nestedListOf(root, path).getOrNull(index)
                    pendingDelete = DeleteRequest.Component(
                        label = componentLabel(target ?: ComponentEntry("")),
                        onConfirm = { removeFromFullList(path, index) },
                    )
                },
            )
            1 -> StyleTab(
                css = cssRules,
                onEdit = { target -> styleTarget = target },
                onRemoveSelector = { selector ->
                    pendingDelete = DeleteRequest.Selector(
                        label = selector,
                        onConfirm = { removeSelector(selector) },
                    )
                },
                onAddSelector = { showAddSelector = true },
            )
        }
    }

    // ═══ 响应式布局：平板（宽屏）左右分栏，手机端预览 / 编辑按钮切换互斥显示 ═══
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp
        if (isWide) {
            // 预览放大过渡：沉浸全屏 ⇄ 分栏（淡入淡出）
            Crossfade(
                targetState = previewExpanded,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(400),
            ) { expanded ->
                if (expanded) {
                    // ── 平板：沉浸全屏预览（无头部，内容占满；双击退出）──
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        DoubleTapZoom(
                            onDoubleTap = { previewExpanded = false },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            previewSlot()
                        }
                    }
                } else {
                // ── 平板 / 宽屏：顶栏横跨，左侧实时预览 + 右侧编辑器 ──
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                ) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.layout_editor)) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    painterResource(R.drawable.ic_arrow_back),
                                    contentDescription = stringResource(R.string.back),
                                )
                            }
                        },
                        actions = {
                            TextButton(onClick = { showResetDialog = true }) {
                                Text(stringResource(R.string.layout_editor_reset), color = MaterialTheme.colorScheme.error)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        // 左侧：实时预览（占 38%，双击放大）
                        Column(
                            modifier = Modifier
                                .weight(0.38f)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        ) {
                            PreviewHeader(
                                mode = previewMode,
                                onModeChange = { previewMode = it },
                            )
                            DoubleTapZoom(
                                onDoubleTap = { previewExpanded = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            ) {
                                previewSlot()
                            }
                        }
                        VerticalDivider()
                        // 右侧：编辑器（占 62%）
                        Column(
                            modifier = Modifier
                                .weight(0.62f)
                                .fillMaxHeight(),
                        ) {
                            editSlot()
                        }
                    }
                }
            }
        }
        } else {
            // ── 手机：预览 / 编辑 通过按钮互斥切换（Crossfade 过渡）──
            var showPreview by remember { mutableStateOf(false) }
            Crossfade(
                targetState = showPreview,
                modifier = Modifier.fillMaxSize(),
                animationSpec = tween(400),
            ) { preview ->
                if (preview) {
                    // 预览视图：返回键先回到编辑视图
                    BackHandler { showPreview = false }
                    // 预览放大过渡：沉浸全屏 ⇄ 正常预览（淡入淡出）
                    Crossfade(
                        targetState = previewExpanded,
                        modifier = Modifier.fillMaxSize(),
                        animationSpec = tween(400),
                    ) { expanded ->
                        if (expanded) {
                            // 沉浸全屏预览：无头部，内容占满；双击退出到正常预览
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                DoubleTapZoom(
                                    onDoubleTap = { previewExpanded = false },
                                    modifier = Modifier.fillMaxSize(),
                                ) {
                                    previewSlot()
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.background),
                            ) {
                                TopAppBar(
                                    title = { Text(stringResource(R.string.layout_editor_preview)) },
                                    navigationIcon = {
                                        IconButton(onClick = { showPreview = false }) {
                                            Icon(
                                                painterResource(R.drawable.ic_arrow_back),
                                                contentDescription = stringResource(R.string.back),
                                            )
                                        }
                                    },
                                    colors = TopAppBarDefaults.topAppBarColors(
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                                    ),
                                )
                                PreviewHeader(
                                    mode = previewMode,
                                    onModeChange = { previewMode = it },
                                )
                                // 预览内容：双击放大（沉浸全屏）
                                DoubleTapZoom(
                                    onDoubleTap = { previewExpanded = true },
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                ) {
                                    previewSlot()
                                }
                            }
                        }
                    }
                } else {
                    // 编辑视图
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    ) {
                        TopAppBar(
                            title = { Text(stringResource(R.string.layout_editor)) },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        painterResource(R.drawable.ic_arrow_back),
                                        contentDescription = stringResource(R.string.back),
                                    )
                                }
                            },
                            actions = {
                                TextButton(onClick = { showPreview = true }) {
                                    Text(stringResource(R.string.layout_editor_show_preview))
                                }
                                TextButton(onClick = { showResetDialog = true }) {
                                    Text(stringResource(R.string.layout_editor_reset), color = MaterialTheme.colorScheme.error)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                titleContentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                        )
                        editSlot()
                    }
                }
            }
        }
    }

    // ══ 属性编辑面板（布局/样式两 tab 共用） ══
    styleTarget?.let { target ->
        StyleEditorSheet(
            target = target,
            onPropsChange = { selector, props -> updateProps(selector, props) },
            onClearAll = {
                updateProps(target.selector, emptyMap())
                styleTarget = null
            },
            onDismiss = { styleTarget = null },
            componentId = target.componentId,
            currentCid = target.currentCid,
            onCidChange = { newCid ->
                val ref = target.componentRef
                val id = target.componentId
                if (ref != null && id != null) updateComponentCid(ref, id, newCid)
            },
        )
    }

    // ══ 添加组件对话框 ══
    addComponentTarget?.let { target ->
        AddComponentDialog(
            onDismiss = { addComponentTarget = null },
            onPick = { componentId ->
                when (target) {
                    is AddComponentTarget.MainSlots -> addToList(target.slotName, target.path, componentId)
                    is AddComponentTarget.FullPlayer -> addToFullList(target.path, componentId)
                }
                addComponentTarget = null
            },
        )
    }

    // ══ 添加区域对话框（主界面根 / 嵌套 / 全屏统一） ══
    if (addAreaTarget != null) {
        NameInputDialog(
            title = stringResource(R.string.layout_editor_add_slot),
            hint = stringResource(R.string.layout_editor_slot_name_hint),
            onDismiss = { addAreaTarget = null },
            onConfirm = { name -> confirmAddArea(name) },
        )
    }

    // ══ 新建选择器对话框 ══
    if (showAddSelector) {
        NameInputDialog(
            title = stringResource(R.string.layout_editor_add_selector),
            hint = stringResource(R.string.layout_editor_selector_name_hint),
            onDismiss = { showAddSelector = false },
            onConfirm = { name -> addSelector(name); showAddSelector = false },
        )
    }

    // ══ 删除确认 ══
    pendingDelete?.let { req ->
        val message = when (req) {
            is DeleteRequest.Component -> stringResource(R.string.layout_editor_delete_component, req.label)
            is DeleteRequest.Slot -> stringResource(R.string.layout_editor_delete_slot, req.label)
            is DeleteRequest.Selector -> stringResource(R.string.layout_editor_delete_selector, req.label)
        }
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.layout_editor_delete_title)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { req.onConfirm(); pendingDelete = null }) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    // ══ 重置确认 ══
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.layout_editor_reset)) },
            text = { Text(stringResource(R.string.layout_editor_reset_confirm)) },
            confirmButton = {
                TextButton(onClick = ::resetDefaults) {
                    Text(stringResource(R.string.layout_editor_reset), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

// ════════════════════════════ 预览区 ════════════════════════════

/** 双击缩放包装：双击预览内容时切换（放大 / 退出）。 */
@Composable
private fun DoubleTapZoom(
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { onDoubleTap() })
        },
    ) {
        content()
    }
}

/** 预览头部：模式切换（MD3 SegmentedButton，独占整行宽度）。 */
@Composable
private fun PreviewHeader(
    mode: Int,
    onModeChange: (Int) -> Unit,
) {
    val previewOptions = listOf(
        0 to stringResource(R.string.layout_editor_preview_main),
        1 to stringResource(R.string.layout_editor_preview_full),
    )
    // SegmentedButtonRow 独占整行宽度（fillMaxWidth）
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        previewOptions.forEachIndexed { index, (m, label) ->
            SegmentedButton(
                selected = mode == m,
                onClick = { onModeChange(m) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = previewOptions.size),
            ) {
                // 文字加内边距，避免“主界面”等较长标签挤到按钮边缘
                Text(
                    label,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

// ════════════════════════════ 布局 tab ════════════════════════════

@Composable
private fun LayoutTab(
    layout: ComponentLayout,
    css: CssRuleTable,
    onSlotStyle: (String) -> Unit,
    onComponentStyle: (ComponentEntry, ComponentRef) -> Unit,
    onAddSlot: () -> Unit,
    onRemoveSlot: (String) -> Unit,
    /** 添加嵌套子区域（fullPlayer 区分主界面/全屏，slotName + path 定位目标列表） */
    onAddNestedSlot: (Boolean, String, List<NestedHop>) -> Unit,
    /** 子 slot 序列内排序（fullPlayer 区分；slotName + path + 容器下标 + from/to） */
    onMoveChildSlot: (Boolean, String, List<NestedHop>, Int, Int, Int) -> Unit,
    /** 主界面 slot 顺序调整（拖拽）：from → to 插入 */
    onMoveSlot: (Int, Int) -> Unit,
    /** 添加组件到指定列表（path 定位任意层级，空路径 = 根）；弹选择对话框 */
    onAddList: (String, List<NestedHop>) -> Unit,
    onMoveInList: (String, List<NestedHop>, Int, Int) -> Unit,
    onRemoveFromList: (String, List<NestedHop>, Int) -> Unit,
    onAddFullList: (List<NestedHop>) -> Unit,
    onMoveInFullList: (List<NestedHop>, Int, Int) -> Unit,
    onRemoveFromFullList: (List<NestedHop>, Int) -> Unit,
) {
    // 主界面 slot 拖拽排序会话（长按区域卡片拖拽）
    val slotReorder = remember { DragReorderState() }
    // 普通 Column + verticalScroll：保证卡片间 zIndex 生效（LazyColumn 的 item zIndex 不可靠），
    // 拖拽中的卡片/条目才能稳定渲染在最上方不被遮挡
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            stringResource(R.string.layout_editor_tip_layout),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // ── 主界面区域 ──
        Text(
            stringResource(R.string.layout_editor_main_slots),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        // 主界面 slot 拖拽排序（长按区域卡片拖拽）
        val slotEntries = layout.slots.entries.toList()
        slotEntries.forEachIndexed { slotIndex, slotEntry ->
            val slotName = slotEntry.key
            val slotComponents = slotEntry.value
            SlotCard(
                title = slotName,
                entries = slotComponents,
                refPrefix = ComponentRef(fullPlayer = false, slotName = slotName, path = emptyList(), index = -1),
                onTitleClick = { onSlotStyle(slotName) },
                onComponentClick = onComponentStyle,
                onSlotStyle = onSlotStyle,
                onRemoveSlot = { onRemoveSlot(slotName) },
                onAddSlot = onAddSlot,
                onAddNestedSlot = { path -> onAddNestedSlot(false, slotName, path) },
                onAddList = { path -> onAddList(slotName, path) },
                onMoveInList = { path, from, to -> onMoveInList(slotName, path, from, to) },
                onRemoveFromList = { path, index -> onRemoveFromList(slotName, path, index) },
                onMoveChildSlot = { path, ci, from, to -> onMoveChildSlot(false, slotName, path, ci, from, to) },
                dragIndex = slotIndex,
                dragCount = slotEntries.size,
                dragState = slotReorder,
                onDragMove = onMoveSlot,
                // 被拖拽的 slot 卡片提升 zIndex（内部还会结合“组件拖拽中”一起置顶）
                isSlotDragging = slotReorder.dragging && slotReorder.from == slotIndex,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onAddSlot) {
                Text("+ " + stringResource(R.string.layout_editor_add_slot))
            }
        }

        // ── 全屏播放器 ──
        Text(
            stringResource(R.string.layout_editor_full_player),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        layout.fullPlayerSlots.forEach { (slotName, entries) ->
            FullPlayerCard(
                slotName = slotName,
                entries = entries,
                refPrefix = ComponentRef(fullPlayer = true, slotName = slotName, path = emptyList(), index = -1),
                onSlotStyle = onSlotStyle,
                onComponentClick = onComponentStyle,
                onAddNestedSlot = { path -> onAddNestedSlot(true, slotName, path) },
                onAddList = onAddFullList,
                onMoveInList = onMoveInFullList,
                onRemoveFromList = onRemoveFromFullList,
                onMoveChildSlot = { path, ci, from, to -> onMoveChildSlot(true, slotName, path, ci, from, to) },
            )
        }
    }
}

/** slot 卡片配色：统一黑色背景 + 亮色文字（与浅色 MD3 组件条目形成对比）。 */
private val SlotBg = Color(0xFF000000)
private val SlotFg = Color(0xFFE8E6EC)

/** slot 左侧标识竖线颜色（灰色，黑色背景上清晰可见）。 */
private val SlotBarColor = Color(0xFF9E9E9E)

/** 左侧竖线 Modifier：贯穿卡片内容的细灰线，标识这是一个 slot。
 *  用 drawWithContent 画在内容之上，避免被卡片背景遮挡。 */
private fun Modifier.slotLeftBar(color: Color): Modifier = drawWithContent {
    drawContent()
    drawLine(
        color = color,
        start = Offset(2.dp.toPx(), 0f),
        end = Offset(2.dp.toPx(), size.height),
        strokeWidth = 1.5.dp.toPx(),
    )
}

/** 主界面单个区域（slot）卡片：深色底 + 左侧竖线 + 组件列表（任意层级嵌套展开）+ 添加。 */
@Composable
private fun SlotCard(
    title: String,
    entries: List<ComponentEntry>,
    refPrefix: ComponentRef,
    onTitleClick: () -> Unit,
    onComponentClick: (ComponentEntry, ComponentRef) -> Unit,
    onSlotStyle: (String) -> Unit,
    onRemoveSlot: () -> Unit,
    onAddSlot: () -> Unit,
    onAddNestedSlot: (List<NestedHop>) -> Unit,
    onAddList: (List<NestedHop>) -> Unit,
    onMoveInList: (List<NestedHop>, Int, Int) -> Unit,
    onRemoveFromList: (List<NestedHop>, Int) -> Unit,
    onMoveChildSlot: (List<NestedHop>, Int, Int, Int) -> Unit,
    /** slot 拖拽定位：主界面 slot 序列中的下标/总数/共享会话（仅头部标题行长按触发） */
    dragIndex: Int = -1,
    dragCount: Int = 0,
    dragState: DragReorderState? = null,
    onDragMove: (Int, Int) -> Unit = { _, _ -> },
    /** 本卡片是否正在被整体拖拽（由外层列表状态提供，用于置顶渲染） */
    isSlotDragging: Boolean = false,
) {
    // 展开/收起（收起时仅显示标题行）；长按头部行 → 整个 slot 卡片（含组件）整体拖拽并置顶
    var expanded by remember { mutableStateOf(true) }
    // 根列表组件拖拽会话：与 SlotEntries 共享，感知“卡片内组件被拖拽”以同步提升卡片 zIndex
    val rootReorder = remember { DragReorderState() }
    // 卡片级置顶：整体拖拽中 或 内部组件拖拽中 → 整个卡片渲染在最上方（不被其他卡片遮挡）
    val liftCard = isSlotDragging || rootReorder.dragging
    Box(
        modifier = Modifier.zIndex(if (liftCard) 2f else 0f),
    ) {
        DragReorder(
            index = dragIndex,
            count = dragCount,
            state = dragState ?: remember { DragReorderState() },
            onMove = onDragMove,
            entryId = title,
        ) { dragMod ->
            // 卡片容器：不 clip（内部组件行拖拽时可拖出卡片边界显示，不被裁剪遮挡）
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
                    .background(SlotBg, RoundedCornerShape(16.dp))
                    .slotLeftBar(SlotBarColor),
            ) {
                Column {
                    // 头部行（标题 + 展开/收起 + “+” + 删除）：dragMod 使头部长按触发整个 slot 拖拽
                    Row(
                        modifier = dragMod
                            .fillMaxWidth()
                            .clickable(onClick = onTitleClick)
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = SlotFg,
                        )
                        Spacer(Modifier.weight(1f))
                        // 展开/收起：收起的 slot 只保留标题行；箭头旋转 + 内容淡入/高度过渡
                        val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200))
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_drop_down),
                                contentDescription = stringResource(
                                    if (expanded) R.string.layout_editor_collapse else R.string.layout_editor_expand,
                                ),
                                modifier = Modifier
                                    .size(20.dp)
                                    .graphicsLayer { rotationZ = arrowRotation },
                                tint = SlotFg,
                            )
                        }
                        AddMenu(
                            onAddComponent = { onAddList(emptyList()) },
                            onAddArea = onAddSlot,
                        )
                        IconButton(onClick = onRemoveSlot) {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                contentDescription = stringResource(R.string.delete),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    // 展开/收起过渡：内容淡入 + 垂直展开（收起时反向收缩）
                    AnimatedVisibility(visible = expanded) {
                        Column {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            // 组件列表（任意层级嵌套子 slot 递归展开）
                            SlotEntries(
                                entries = entries,
                                path = emptyList(),
                                refPrefix = refPrefix,
                                onComponentClick = onComponentClick,
                                onSlotStyle = onSlotStyle,
                                onAddNestedSlot = onAddNestedSlot,
                                onAddList = onAddList,
                                onMoveInList = onMoveInList,
                                onRemoveFromList = onRemoveFromList,
                                onMoveChildSlot = onMoveChildSlot,
                                rootReorder = rootReorder,
                            )
                            if (entries.isEmpty()) {
                                Text(
                                    stringResource(R.string.layout_editor_empty_slot),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SlotFg.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * slot 内组件列表（递归）：普通组件显示为可排序/删除的行；
 * 带 children 的容器条目（命名子 slot / slot 型组件）递归展开为子区域块。
 * [path] 为当前列表相对根列表的嵌套路径（空 = 根）。
 */
@Composable
private fun SlotEntries(
    entries: List<ComponentEntry>,
    path: List<NestedHop>,
    refPrefix: ComponentRef,
    onComponentClick: (ComponentEntry, ComponentRef) -> Unit,
    onSlotStyle: (String) -> Unit,
    onAddNestedSlot: (List<NestedHop>) -> Unit,
    onAddList: (List<NestedHop>) -> Unit,
    onMoveInList: (List<NestedHop>, Int, Int) -> Unit,
    onRemoveFromList: (List<NestedHop>, Int) -> Unit,
    /** 子 slot 序列内排序：path（到容器所在列表）+ 容器下标 + from/to */
    onMoveChildSlot: (List<NestedHop>, Int, Int, Int) -> Unit,
    /** 根列表拖拽会话（由调用方持有，用于感知“列表内有组件被拖拽”以提升容器 zIndex）；null 时内部创建 */
    rootReorder: DragReorderState? = null,
) {
    // 普通组件在 entries 中的真实下标（容器展开块不参与序号）
    val normalIndices = entries.mapIndexedNotNull { i, e ->
        if (e.extra["children"] is Map<*, *>) null else i
    }
    // 本列表的拖拽会话（长按行拖拽排序）
    val reorder = rootReorder ?: remember { DragReorderState() }
    entries.forEachIndexed { index, entry ->
        DragReorder(
            index = index,
            count = entries.size,
            state = reorder,
            onMove = { from, to -> onMoveInList(path, from, to) },
            entryId = entry.id,
        ) { dragMod ->
            val children = entry.extra["children"]
            if (children is Map<*, *>) {
                // 容器：子 slot 块垂直排列，每个子 slot **独立拖拽**（并列关系，
                // 可与同级子 slot 互换位置；容器本身不作为整体拖拽单元）
                val childEntries = children.entries.toList()
                // 同容器子 slot 序列共享一个拖拽会话（仅头部标题行长按触发）
                val childReorder = remember(entry) { DragReorderState() }
                Column(modifier = dragMod) {
                    childEntries.forEachIndexed { childIndex, childItem ->
                        @Suppress("UNCHECKED_CAST")
                        val childList = childItem.value as List<ComponentEntry>
                        val childSlotName = childItem.key.toString()
                        ChildSlotSection(
                            childSlotName = childSlotName,
                            childList = childList,
                            path = path + NestedHop(index, childSlotName),
                            refPrefix = refPrefix,
                            onComponentClick = onComponentClick,
                            onSlotStyle = onSlotStyle,
                            onAddNestedSlot = onAddNestedSlot,
                            onAddList = onAddList,
                            onMoveInList = onMoveInList,
                            onRemoveFromList = onRemoveFromList,
                            onMoveChildSlot = onMoveChildSlot,
                            dragIndex = childIndex,
                            dragCount = childEntries.size,
                            dragState = childReorder,
                        )
                    }
                }
            } else {
                // 普通组件
                val normalIndex = normalIndices.indexOf(index)
                ComponentRow(
                    index = normalIndex,
                    total = normalIndices.size,
                    entry = entry,
                    onClick = { onComponentClick(entry, refPrefix.copy(path = path, index = index)) },
                    onRemove = { onRemoveFromList(path, index) },
                    modifier = dragMod,
                )
            }
        }
    }
}

/**
 * 子区域（命名子 slot）展开块：标题（点击编辑样式）+ 子组件列表（递归渲染）+ 添加。
 * [path] 为本子 slot 列表相对根列表的嵌套路径。
 */
@Composable
private fun ChildSlotSection(
    childSlotName: String,
    childList: List<ComponentEntry>,
    path: List<NestedHop>,
    refPrefix: ComponentRef,
    onComponentClick: (ComponentEntry, ComponentRef) -> Unit,
    onSlotStyle: (String) -> Unit,
    onAddNestedSlot: (List<NestedHop>) -> Unit,
    onAddList: (List<NestedHop>) -> Unit,
    onMoveInList: (List<NestedHop>, Int, Int) -> Unit,
    onRemoveFromList: (List<NestedHop>, Int) -> Unit,
    onMoveChildSlot: (List<NestedHop>, Int, Int, Int) -> Unit,
    /** 子 slot 拖拽定位：所属容器序列中的下标/总数/共享会话（-1 = 不参与拖拽） */
    dragIndex: Int = -1,
    dragCount: Int = 0,
    dragState: DragReorderState? = null,
) {
    // 展开/收起（收起时仅显示标题行）；长按标题行 → 整个子 slot 块（含组件）整体拖拽并置顶
    var expanded by remember { mutableStateOf(true) }
    DragReorder(
        index = dragIndex,
        count = dragCount,
        state = dragState ?: remember { DragReorderState() },
        onMove = { from, to ->
            onMoveChildSlot(path, dragIndex, from, to)
        },
        entryId = childSlotName,
    ) { dragMod ->
        Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)) {
            // 子区域标题行（点击编辑样式 + 展开/收起 + 右侧“+”）：dragMod 使头部长按触发整个子 slot 拖拽
            Row(
                modifier = dragMod
                    .fillMaxWidth()
                    .clickable(onClick = { onSlotStyle(childSlotName) })
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    childSlotName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                // 展开/收起：收起的子 slot 只保留标题行；箭头旋转 + 内容淡入/高度过渡
                val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200))
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        painterResource(R.drawable.ic_arrow_drop_down),
                        contentDescription = stringResource(
                            if (expanded) R.string.layout_editor_collapse else R.string.layout_editor_expand,
                        ),
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = arrowRotation },
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(8.dp))
                AddMenu(
                    onAddComponent = { onAddList(path) },
                    onAddArea = { onAddNestedSlot(path) },
                )
            }
            // 展开/收起过渡：内容淡入 + 垂直展开（收起时反向收缩）
            AnimatedVisibility(visible = expanded) {
                // 子 slot 内容：黑色块 + 左侧竖线（不 clip：内部组件行拖拽可溢出显示）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SlotBg, RoundedCornerShape(12.dp))
                        .slotLeftBar(SlotBarColor),
                ) {
                    Column(modifier = Modifier.padding(bottom = 4.dp)) {
                        // 递归：子组件里可能还有容器（子 slot 内的子 slot）
                        SlotEntries(
                            entries = childList,
                            path = path,
                            refPrefix = refPrefix,
                            onComponentClick = onComponentClick,
                            onSlotStyle = onSlotStyle,
                            onAddNestedSlot = onAddNestedSlot,
                            onAddList = onAddList,
                            onMoveInList = onMoveInList,
                            onRemoveFromList = onRemoveFromList,
                            onMoveChildSlot = onMoveChildSlot,
                        )
                        if (childList.isEmpty()) {
                            Text(
                                stringResource(R.string.layout_editor_empty_slot),
                                style = MaterialTheme.typography.bodySmall,
                                color = SlotFg.copy(alpha = 0.55f),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** 列表拖拽排序会话状态（每个可拖拽列表一个实例）。 */
private class DragReorderState {
    /** 正在拖动的项下标（-1 = 未拖拽） */
    var from by mutableIntStateOf(-1)
    /** 当前插入目标下标 */
    var to by mutableIntStateOf(-1)
    /** 拖动累计偏移（px） */
    var offsetY by mutableFloatStateOf(0f)
    /** 拖动行高度（px），用于换算跨过几行 */
    var rowHeight by mutableFloatStateOf(56f)
    /** 是否处于拖拽手势中（长按已触发、尚未松手/取消） */
    var dragging by mutableStateOf(false)
    /** 松手时的手指位置（落位动画起点；reset 不清除，由落位动画用完后再清） */
    var lastOffsetY by mutableFloatStateOf(0f)
    /**
     * 本次拖拽被移动条目的身份（onDragEnd 记录）。
     * 落位/放大动画只应由渲染该条目的实例播放：列表重排后组合实例按位置复用，
     * 若按实例播放动画会落到错误条目上（导致整个 slot 的组件一起弹）。
     * reset 不清除，由 onDragStart 置空；被移动条目本身没有落位动画时保持 null。
     */
    var settleEntryId: String? = null

    fun reset() {
        from = -1
        to = -1
        offsetY = 0f
        dragging = false
    }
}

/**
 * 拖拽排序包装：长按进入拖拽（插入式，iOS 图标移动风格）。
 * - 拖拽中条目轻微透明，整体跟随手指并置顶（不被其他区域遮挡）；
 * - 目标位置的原条目以 tween 缓动平滑让位（无 spring 过冲，不会上下弹）；
 * - 松手后从手指位置直接“掉”到目标位（tween 缓动，无回弹、无瞬移闪现）；
 * - 落位时边缘爆出粒子（复用全局粒子宿主，提供高反馈）。
 *
 * [content] 接收 [Modifier] 形式的拖拽手势（长按触发）：调用方应把它应用到
 * “长按即拖拽整个条目”的区域（如 slot 头部行），而条目其余内容随 [content] 一起
 * 作为整体跟随手指移动。
 */
@Composable
private fun DragReorder(
    index: Int,
    count: Int,
    state: DragReorderState,
    onMove: (Int, Int) -> Unit,
    /** 当前渲染条目的身份（用于把落位动画绑定到正确条目上，防止重排后动画错位） */
    entryId: String,
    modifier: Modifier = Modifier,
    /** 条目内容；[dragModifier] 应应用到条目内“长按触发拖拽”的区域（如 slot 头部行） */
    content: @Composable (dragModifier: Modifier) -> Unit,
) {
    val isDragging = index == state.from

    // 兜底清理：手势已结束但会话未复位（异常中断）或下标越界时强制清理
    LaunchedEffect(state.dragging, state.from, count) {
        if (!state.dragging || state.from >= count) state.reset()
    }

    // ② 中间条目向空出位置（from）动态让位：
    //    向下拖（to > from）→ from..to 之间的条目上移让位；
    //    向上拖（to < from）→ to..from 之间的条目下移让位。
    //    平滑缓动（tween）无回弹：让位/回弹若带 spring 过冲，拖拽中会一直“上下弹”。
    //    松手后 snapTo 归位：重排瞬间让位条目已在新位置（与让位位置一致），
    //    若播放让位回弹动画，同样会因实例复用而落到错误条目上。
    val inRange = state.dragging && state.from >= 0 && index != state.from &&
        index in minOf(state.from, state.to)..maxOf(state.from, state.to)
    val targetShift = if (inRange) {
        (if (state.to > state.from) -state.rowHeight else state.rowHeight)
    } else 0f
    val shiftAnim = remember { Animatable(0f) }
    LaunchedEffect(targetShift, state.dragging) {
        if (state.dragging) {
            shiftAnim.animateTo(
                targetShift,
                tween(durationMillis = 220, easing = FastOutSlowInEasing),
            )
        } else {
            shiftAnim.snapTo(0f)
        }
    }

    // ③ 松手落位：从手指位置直接“掉”到目标位（不回原始位置）。
    //    仅由渲染“被移动条目”的实例播放（entryId == settleEntryId）；其余实例直接
    //    到位。否则重排后落位动画会播放到占用原位置实例的错误条目上。
    //    [settleStarted] 区分“动画未启动”与“已结束”：落位首帧（动画尚未启动）时
    //    直接以手指位置作为偏移，避免“先闪现到目标位、再跳回手指位置”的抖动。
    //    动画用 tween 缓动（FastOutSlowIn）：从手指位置平滑掉到目标位，无回弹过冲。
    val settle = remember { Animatable(0f) }
    var settleStarted by remember { mutableStateOf(false) }
    LaunchedEffect(state.dragging, entryId) {
        if (state.dragging) {
            settle.snapTo(0f)
            settleStarted = false
        } else if (state.lastOffsetY != 0f) {
            if (entryId == state.settleEntryId) {
                settleStarted = true
                settle.snapTo(state.lastOffsetY)
                settle.animateTo(
                    0f,
                    tween(durationMillis = 220, easing = FastOutSlowInEasing),
                )
                state.lastOffsetY = 0f
                settleStarted = false
            } else {
                settle.snapTo(0f)
            }
        }
    }

    // ③ 落位粒子依赖（组合时取值，pointerInput 内读取最新值）
    var itemCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val burstColor = MaterialTheme.colorScheme.primary
    val host = LocalParticleBurstHost.current
    val hostState by rememberUpdatedState(host)
    val enabled by rememberUpdatedState(LocalParticleBurstEnabled.current)
    val burstColorState by rememberUpdatedState(burstColor)
    val density = LocalDensity.current
    val densityState by rememberUpdatedState(density)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                // 拖拽条目跟随手指（实时）；松手后从手指位置直接“掉”到目标位；其余条目走让位动画
                translationY = when {
                    isDragging && state.dragging -> state.offsetY
                    else -> {
                        // 落位中的被移动条目：偏移只由 settle（手指位置 → 0）决定，
                        // 不再叠加让位偏移（其所在实例可能曾是让位条目，shiftAnim 残留会让首帧错位）
                        val settlingEntry = entryId == state.settleEntryId && state.lastOffsetY != 0f
                        val base = if (!settleStarted && settlingEntry) state.lastOffsetY else settle.value
                        if (settlingEntry) base else base + shiftAnim.value
                    }
                }
                alpha = if (isDragging && state.dragging) 0.9f else 1f
            }
            .zIndex(
                when {
                    isDragging && state.dragging -> 2f
                    inRange -> 1f
                    else -> 0f
                },
            )
            .onGloballyPositioned { if (isDragging && state.dragging) itemCoords = it }
            .onSizeChanged { if (isDragging) state.rowHeight = it.height.toFloat() },
    ) {
        content(
            Modifier.pointerInput(index, count) {
                // try/finally：无论手势正常结束、被取消，还是 pointerInput 因列表重组而
                // 重启（协程取消），finally 都会执行，确保会话状态必定清理
                try {
                    detectDragGesturesAfterLongPress(
                        onDragStart = {
                            state.from = index
                            state.to = index
                            state.offsetY = 0f
                            state.dragging = true
                            // 新一次拖拽：清除上次落位动画归属（lastOffsetY 由上次动画用完已清）
                            state.settleEntryId = null
                        },
                        onDrag = { change, amount ->
                            change.consume()
                            if (state.from != index) return@detectDragGesturesAfterLongPress
                            // 按累计偏移换算插入目标（条目本身不移动，由目标让位表达）
                            state.offsetY += amount.y
                            val step = state.rowHeight.takeIf { it > 0f } ?: 56f
                            val newTo = (state.from + (state.offsetY / step).roundToInt())
                                .coerceIn(0, count - 1)
                            if (newTo != state.to) state.to = newTo
                        },
                        onDragEnd = {
                            val from = state.from
                            val to = state.to
                            // 落位起点 = 松手时相对旧位置(from)的偏移 offsetY，换算成相对
                            // 重排后新位置(to)的偏移：offsetY - (to - from) * rowHeight。
                            // 重排瞬间条目已布局到目标位，只有这个换算后的偏移才等于“手指位置”，
                            // 直接用 offsetY 会让条目先出现在目标位下方(向上拖则为上方)再弹回。
                            state.lastOffsetY = state.offsetY - (to - from) * state.rowHeight
                            state.settleEntryId = entryId
                            state.reset()
                            if (from in 0 until count && to in 0 until count && from != to) {
                                // 落位：条目边缘爆出粒子（高反馈）
                                val coords = itemCoords
                                val burstHost = hostState
                                if (coords != null && burstHost != null && enabled) {
                                    burstHost.addBurst(
                                        color = burstColorState,
                                        center = coords.localToRoot(
                                            Offset(coords.size.width / 2f, coords.size.height / 2f),
                                        ),
                                        radiusPx = with(densityState) { 72.dp.toPx() },
                                        durationMillis = 480,
                                    )
                                }
                                onMove(from, to)
                            }
                        },
                        onDragCancel = { state.reset() },
                    )
                } finally {
                    state.reset()
                }
            },
        )
    }
}

/**
 * 单个组件行：playlist 条目式卡片，颜色遵从 Material Design 3
 * （secondaryContainer 底 + onSecondaryContainer 文字，与播放列表条目同源）。
 * 点击编辑样式、长按拖拽排序、删除。
 */
@Composable
private fun ComponentRow(
    index: Int,
    total: Int = 0,
    entry: ComponentEntry,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = MaterialTheme.colorScheme.secondaryContainer
    val fg = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = bg,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "${index + 1}",
                style = MaterialTheme.typography.labelMedium,
                color = fg.copy(alpha = 0.7f),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    componentLabel(entry),
                    style = MaterialTheme.typography.bodyMedium,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    buildString {
                        append(entry.id)
                        entry.cid?.let { append("@").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = fg.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** 全屏播放器卡片：fp-backdrop 容器递归展开其子 slot；其余条目按组件行显示。 */
@Composable
private fun FullPlayerCard(
    slotName: String,
    entries: List<ComponentEntry>,
    refPrefix: ComponentRef,
    onSlotStyle: (String) -> Unit,
    onComponentClick: (ComponentEntry, ComponentRef) -> Unit,
    onAddNestedSlot: (List<NestedHop>) -> Unit,
    onAddList: (List<NestedHop>) -> Unit,
    onMoveInList: (List<NestedHop>, Int, Int) -> Unit,
    onRemoveFromList: (List<NestedHop>, Int) -> Unit,
    onMoveChildSlot: (List<NestedHop>, Int, Int, Int) -> Unit,
) {
    // 展开/收起（收起时仅显示标题行）
    var expanded by remember { mutableStateOf(true) }
    // 根列表组件拖拽会话：感知卡片内组件被拖拽 → 提升整卡 zIndex（不被其他卡片遮挡）
    val rootReorder = remember { DragReorderState() }
    Box(modifier = Modifier.zIndex(if (rootReorder.dragging) 2f else 0f)) {
        // 卡片容器：不 clip（内部组件行拖拽时可拖出卡片边界显示，不被裁剪遮挡）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
                .background(SlotBg, RoundedCornerShape(16.dp))
                .slotLeftBar(SlotBarColor),
        ) {
            Column {
                // 头部：全屏播放器区域样式（MD3 ListItem + 展开/收起）
                ListItem(
                    modifier = Modifier.clickable(onClick = { onSlotStyle(slotName) }),
                headlineContent = {
                    Text(
                        slotName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = SlotFg,
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.layout_editor_edit_style),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                    )
                },
                trailingContent = {
                    // 展开/收起：收起的 slot 只保留标题行；箭头旋转 + 内容淡入/高度过渡
                    val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, tween(200))
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_drop_down),
                            contentDescription = stringResource(
                                if (expanded) R.string.layout_editor_collapse else R.string.layout_editor_expand,
                            ),
                            modifier = Modifier
                                .size(20.dp)
                                .graphicsLayer { rotationZ = arrowRotation },
                            tint = SlotFg,
                        )
                    }
                },
            )
            // 展开/收起过渡：内容淡入 + 垂直展开（收起时反向收缩）
            AnimatedVisibility(visible = expanded) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                    // 组件列表（path 相对 full-player 根；fp-backdrop 容器递归展开子 slot）
                    SlotEntries(
                        entries = entries,
                        path = emptyList(),
                        refPrefix = refPrefix,
                        onComponentClick = onComponentClick,
                        onSlotStyle = onSlotStyle,
                        onAddNestedSlot = onAddNestedSlot,
                        onAddList = onAddList,
                        onMoveInList = onMoveInList,
                        onRemoveFromList = onRemoveFromList,
                        onMoveChildSlot = onMoveChildSlot,
                        rootReorder = rootReorder,
                    )
                }
            }
            }
        }
    }
}

// ════════════════════════════ 样式 tab ════════════════════════════

@Composable
private fun StyleTab(
    css: CssRuleTable,
    onEdit: (StyleTarget) -> Unit,
    onRemoveSelector: (String) -> Unit,
    onAddSelector: () -> Unit,
) {
    val classRules = css.rules.filterKeys { it.startsWith(".") }
    val idRules = css.rules.filterKeys { it.startsWith("#") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Text(
                stringResource(R.string.layout_editor_tip_style),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        item {
            Text(
                stringResource(R.string.layout_editor_style_slots),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
        }
        items(classRules.keys.sorted(), key = { it }) { selector ->
            SelectorRow(
                selector = selector,
                props = css.rules[selector].orEmpty(),
                onClick = { onEdit(StyleTarget(selector, selectorTitle(selector), css.rules[selector].orEmpty())) },
                onRemove = { onRemoveSelector(selector) },
            )
        }
        item {
            Text(
                stringResource(R.string.layout_editor_style_components),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
            )
        }
        items(idRules.keys.sorted(), key = { it }) { selector ->
            SelectorRow(
                selector = selector,
                props = css.rules[selector].orEmpty(),
                onClick = { onEdit(StyleTarget(selector, selectorTitle(selector), css.rules[selector].orEmpty())) },
                onRemove = { onRemoveSelector(selector) },
            )
        }
        item {
            Row {
                TextButton(onClick = onAddSelector) {
                    Text("+ " + stringResource(R.string.layout_editor_add_selector))
                }
            }
        }
    }
}

@Composable
private fun SelectorRow(
    selector: String,
    props: Map<String, String>,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(selector, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    propsSummary(props),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = stringResource(R.string.delete),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

// ════════════════════════════ 属性编辑面板 ════════════════════════════

data class StyleTarget(
    val selector: String,
    val title: String,
    val props: Map<String, String>,
    /** 组件抽屉专属：组件在布局中的定位（用于编辑 cid），null = 区域/选择器抽屉 */
    val componentRef: ComponentRef? = null,
    val componentId: String? = null,
    val currentCid: String? = null,
)

/** 组件在布局中的定位：根列表（主界面 slot / 全屏播放器）+ 嵌套路径 + 条目下标。 */
data class ComponentRef(
    val fullPlayer: Boolean,
    val slotName: String,
    val path: List<NestedHop>,
    val index: Int,
)

/** “+” 菜单：按下弹出「添加组件」「添加区域」两个选项。 */
@Composable
private fun AddMenu(
    onAddComponent: () -> Unit,
    onAddArea: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 36.dp),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layout_editor_add_component)) },
                onClick = { expanded = false; onAddComponent() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.layout_editor_add_slot)) },
                onClick = { expanded = false; onAddArea() },
            )
        }
    }
}

/** 添加组件对话框的目标：主界面 slot（path 定位任意层级）/ 全屏播放器子列表（path 定位）。 */
private sealed interface AddComponentTarget {
    data class MainSlots(val slotName: String, val path: List<NestedHop>) : AddComponentTarget
    data class FullPlayer(val path: List<NestedHop>) : AddComponentTarget
}

/** 添加区域（slot）对话框目标：fullPlayer=true 时操作全屏列表，path 空 = 主界面根（新主 slot）。 */
private data class AddAreaTarget(
    val fullPlayer: Boolean,
    val slotName: String,
    val path: List<NestedHop>,
)

/** 删除确认请求：只存数据与操作，提示文案在 Composable 作用域内格式化。 */
private sealed interface DeleteRequest {
    val onConfirm: () -> Unit

    data class Component(val label: String, override val onConfirm: () -> Unit) : DeleteRequest
    data class Slot(val label: String, override val onConfirm: () -> Unit) : DeleteRequest
    data class Selector(val label: String, override val onConfirm: () -> Unit) : DeleteRequest
}

/** 属性编辑底部面板：通用属性 + 当前组件特有属性 + 高级自由属性。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleEditorSheet(
    target: StyleTarget,
    onPropsChange: (String, Map<String, String>) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
    /** 组件抽屉专属：组件 id / 当前 cid / cid 修改回调（null = 非组件抽屉） */
    componentId: String? = null,
    currentCid: String? = null,
    onCidChange: ((String) -> Unit)? = null,
) {
    // 面板内本地状态：滑块拖动只改这里，松手/点选才提交，避免连续重写文件
    var props by remember(target) { mutableStateOf(target.props) }
    // 高级属性：新 key / 新 value 输入框
    var newKey by remember(target) { mutableStateOf("") }
    var newValue by remember(target) { mutableStateOf("") }

    // 当前目标类型：区域选择器（.x）或组件选择器（#x）
    val isSlot = target.selector.startsWith(".")
    // 当前组件特有的属性（如迷你栏 render-style、playlist item-*、tab-bar 样式组合）；
    // 未登记组件 → 只有通用属性
    val specificProps = COMPONENT_SPECIFIC_PROPS[target.selector.removePrefix("#")].orEmpty()
    // 面板内展示的属性 = 通用 + 类型专属（slot 显示主轴对齐；组件显示交叉轴对齐）+ 组件特有
    val visibleProps = remember(specificProps, isSlot) {
        if (isSlot) {
            (COMMON_PROPS + SLOT_ONLY_PROPS + specificProps).distinctBy { it.key }
        } else {
            (COMMON_PROPS + COMPONENT_ONLY_PROPS + specificProps).distinctBy { it.key }
        }
    }
    // 已由面板管理的属性（含组合预设写到的 style/display 等）不再进“高级属性”区
    val managedKeys = remember(visibleProps) {
        visibleProps.flatMap { spec ->
            listOf(spec.key) + spec.presetValues.values.flatMap { it.keys }
        }.toSet()
    }

    fun commit() = onPropsChange(target.selector, props)

    // skipPartiallyExpanded：一次弹出到全高（不做两段展开）；下滑（dragHandle）即可收回
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            // 标题
            Text(target.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                target.selector,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            // 组件抽屉：编辑实例标识（cid）——修改后选择器随 cid 变化，用于精确匹配 CSS
            if (componentId != null && onCidChange != null) {
                var cidText by remember(target.componentRef) { mutableStateOf(currentCid.orEmpty()) }
                OutlinedTextField(
                    value = cidText,
                    onValueChange = { cidText = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    label = { Text(stringResource(R.string.layout_editor_cid)) },
                    placeholder = { Text(stringResource(R.string.layout_editor_cid_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onCidChange(cidText.trim()) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .onFocusChanged {
                            if (!it.isFocused) {
                                if (cidText.isBlank()) cidText = currentCid.orEmpty()
                                else if (cidText.trim() != currentCid) onCidChange(cidText.trim())
                            }
                        },
                )
            }
            Row {
                TextButton(onClick = onClearAll) {
                    Text(
                        stringResource(R.string.layout_editor_clear_all),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            HorizontalDivider()

            // ── 通用 + 组件特有属性 ──
            Text(
                stringResource(R.string.layout_editor_common_props),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            visibleProps.forEach { spec ->
                when (spec.type) {
                    PropType.SEGMENT -> PropSegments(
                        spec = spec,
                        current = props[spec.key],
                        allProps = props,
                        onChange = { v -> props = props + (spec.key to v); commit() },
                        onPresetChange = { opt ->
                            // 组合预设：同时设置/清除多个 CSS 属性（如 tab 栏样式）
                            val preset = spec.presetValues[opt]
                            if (preset != null) {
                                val newProps = props.toMutableMap()
                                preset.forEach { (k, v) ->
                                    if (v == null) newProps.remove(k) else newProps[k] = v
                                }
                                props = newProps
                                commit()
                            }
                        },
                        onClear = { props = props - spec.key; commit() },
                    )
                    PropType.NUMBER -> PropNumberInput(
                        spec = spec,
                        current = props[spec.key],
                        onCommit = { v -> props = props + (spec.key to v); commit() },
                        onClear = { props = props - spec.key; commit() },
                    )
                    PropType.TEXT -> PropTextInput(
                        spec = spec,
                        current = props[spec.key],
                        onCommit = { v -> props = props + (spec.key to v); commit() },
                        onClear = { props = props - spec.key; commit() },
                    )
                    PropType.COLOR -> PropColor(
                        spec = spec,
                        current = props[spec.key],
                        onChange = { v -> props = props + (spec.key to v); commit() },
                        onClear = { props = props - spec.key; commit() },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(top = 12.dp))

            // ── 高级属性 ──
            Text(
                stringResource(R.string.layout_editor_advanced_props),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            Text(
                stringResource(R.string.layout_editor_advanced_hint),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            val knownKeys = managedKeys
            val advancedKeys = props.keys.filter { it !in knownKeys }.sorted()
            advancedKeys.forEach { key ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = key,
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.4f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = props[key].orEmpty(),
                        onValueChange = { v -> props = props + (key to v); commit() },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(0.6f),
                    )
                    IconButton(onClick = { props = props - key; commit() }) {
                        Icon(
                            painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            // 新增属性
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    placeholder = { Text(stringResource(R.string.layout_editor_prop_key)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.4f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = newValue,
                    onValueChange = { newValue = it },
                    placeholder = { Text(stringResource(R.string.layout_editor_prop_value)) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(0.6f),
                )
                TextButton(
                    onClick = {
                        val k = newKey.trim()
                        if (k.isNotEmpty() && newValue.trim().isNotEmpty()) {
                            props = props + (k to newValue.trim())
                            commit()
                            newKey = ""; newValue = ""
                        }
                    },
                ) { Text("+") }
            }
        }
    }
}

// ════════════════════════════ 属性控件 ════════════════════════════

private enum class PropType { SEGMENT, NUMBER, TEXT, COLOR }

private data class PropSpec(
    val key: String,
    val label: String,
    val type: PropType,
    /** SEGMENT：单选选项（直接选择，不提供输入）；COLOR：预设色板快捷。 */
    val options: List<String> = emptyList(),
    /** NUMBER：true=纯数字键盘（weight / opacity），false=文本键盘（可输 px / dp 单位）。 */
    val numeric: Boolean = false,
    /**
     * 组合预设：option → {CSS 属性 → 值}（值为 null 表示移除该属性）。
     * 用于一个选项同时设置/清除多个 CSS 属性（如 tab 栏 3 种样式组合）。
     */
    val presetValues: Map<String, Map<String, String?>> = emptyMap(),
)

/** 枚举型属性：直接单选（FilterChip）。支持组合预设（一个选项映射多个 CSS 属性）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropSegments(
    spec: PropSpec,
    current: String?,
    allProps: Map<String, String> = emptyMap(),
    onChange: (String) -> Unit,
    onPresetChange: (String) -> Unit = {},
    onClear: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(spec.label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            if (current != null && spec.presetValues.isEmpty()) {
                Text(
                    current,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.layout_editor_clear), style = MaterialTheme.typography.labelSmall) }
            }
        }
        // 单选 chips（FlowRow 自动换行）
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            spec.options.forEach { opt ->
                val selected = if (spec.presetValues.isNotEmpty()) {
                    // 组合预设：当前 props 与该选项的预设完全一致即视为选中
                    spec.presetValues[opt]?.all { (k, v) ->
                        if (v == null) allProps[k] == null else allProps[k] == v
                    } ?: false
                } else {
                    current == opt
                }
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (spec.presetValues.isNotEmpty()) onPresetChange(opt) else onChange(opt)
                    },
                    label = { Text(opt, fontSize = 12.sp) },
                )
            }
        }
    }
}

/** 开放数值型属性：直接输入框（回车 / 失焦提交），可带单位（px / dp / 无单位）。 */
@Composable
private fun PropNumberInput(
    spec: PropSpec,
    current: String?,
    onCommit: (String) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember(current) { mutableStateOf(current.orEmpty()) }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(spec.label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            if (current != null) {
                Text(
                    current,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.layout_editor_clear), style = MaterialTheme.typography.labelSmall) }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            placeholder = { Text(stringResource(R.string.layout_editor_input_hint)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = if (spec.numeric) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onCommit(text.trim()) }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (!it.isFocused) {
                        // 回车/失焦提交；清空后失焦恢复显示原值（清除请用“清除”按钮）
                        if (text.isBlank()) text = current.orEmpty()
                        else if (text.trim() != current) onCommit(text.trim())
                    }
                },
        )
    }
}

/** 自由文本属性：输入框（回车 / 失焦提交）+ 可选快捷选项 chips（仅辅助填充）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropTextInput(
    spec: PropSpec,
    current: String?,
    onCommit: (String) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember(current) { mutableStateOf(current.orEmpty()) }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(spec.label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            if (current != null) {
                Text(
                    current,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.layout_editor_clear), style = MaterialTheme.typography.labelSmall) }
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            placeholder = { Text(stringResource(R.string.layout_editor_input_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onCommit(text.trim()) }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (!it.isFocused) {
                        // 回车/失焦提交；清空后失焦恢复显示原值（清除请用“清除”按钮）
                        if (text.isBlank()) text = current.orEmpty()
                        else if (text.trim() != current) onCommit(text.trim())
                    }
                },
        )
        // 快捷选项（仅辅助填充，不限制输入）
        if (spec.options.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                spec.options.forEach { opt ->
                    FilterChip(
                        selected = current == opt,
                        onClick = { text = opt; onCommit(opt) },
                        label = { Text(opt, fontSize = 12.sp) },
                    )
                }
            }
        }
    }
}

private val PRESET_COLORS: List<String> = listOf(
    "#000000", "#FFFFFF", "#F5F5F5", "#E0E0E0", "#9E9E9E", "#616161", "#424242",
    "#F44336", "#E91E63", "#9C27B0", "#673AB7", "#3F51B5", "#2196F3", "#03A9F4",
    "#00BCD4", "#009688", "#4CAF50", "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107",
    "#FF9800", "#FF5722", "#795548", "#607D8B", "#2962FF",
)

/** 颜色属性：自由输入任意 CSS 颜色（hex / rgb() / rgba() / 颜色名）+ 预设色板快捷。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PropColor(
    spec: PropSpec,
    current: String?,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember(current) { mutableStateOf(current.orEmpty()) }
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(spec.label, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            if (current != null) {
                Text(
                    current,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                TextButton(onClick = onClear) { Text(stringResource(R.string.layout_editor_clear), style = MaterialTheme.typography.labelSmall) }
            }
        }
        // 自由输入任意 CSS 颜色值（回车 / 失焦提交）
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall,
            placeholder = { Text(stringResource(R.string.layout_editor_color_hint)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onChange(text.trim()) }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged {
                    if (!it.isFocused) {
                        // 回车/失焦提交；清空后失焦恢复显示原值
                        if (text.isBlank()) text = current.orEmpty()
                        else if (text.trim() != current) onChange(text.trim())
                    }
                },
        )
        // 预设色板（FlowRow 自动换行）：选中项为 primary 描边 + 对比色内点
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRESET_COLORS.forEach { hex ->
                val color = parseCssColor(hex) ?: Color.Transparent
                val selected = current.equals(hex, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant,
                            shape = CircleShape,
                        )
                        .background(color, CircleShape)
                        .clickable { text = hex; onChange(hex) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        // 选中指示：与底色形成对比的内点
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(
                                    if (isDarkColor(color)) Color.White else Color.Black,
                                    CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

/** 简单亮度判断：用于在色块上叠加对比色选中指示点。 */
private fun isDarkColor(color: Color): Boolean =
    color.luminance() < 0.5f

// ════════════════════════════ 对话框 ════════════════════════════

@Composable
private fun AddComponentDialog(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
) {
    val components = remember { ComponentRegistry.registeredIds() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.layout_editor_add_component)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                LazyColumn {
                    items(components, key = { it }) { id ->
                        ListItem(
                            modifier = Modifier.clickable { onPick(id) },
                            headlineContent = { Text(componentLabelOf(id), style = MaterialTheme.typography.bodyLarge) },
                            supportingContent = {
                                Text(
                                    id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
private fun NameInputDialog(
    title: String,
    hint: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(hint) },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text(stringResource(R.string.confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

// ════════════════════════════ 工具 ════════════════════════════

private fun componentLabel(entry: ComponentEntry): String = componentLabelOf(entry.id) +
    (entry.cid?.let { "（$it）" } ?: "")

private fun componentLabelOf(id: String): String = COMPONENT_LABELS[id] ?: id

private val COMPONENT_LABELS: Map<String, String> = mapOf(
    "app-name" to "应用名",
    "search-button" to "搜索按钮",
    "setting-button" to "设置按钮",
    "tab-bar" to "分类栏",
    "sort" to "排序",
    "track-count" to "曲目统计",
    "playlist" to "播放列表",
    "playbar" to "迷你播放栏",
    "pb-backdrop" to "播放栏背景层",
    "pb-cover" to "播放栏封面",
    "pb-title" to "播放栏标题",
    "pb-subtitle" to "播放栏副标题",
    "pb-track-info" to "播放栏曲目信息",
    "pb-controls" to "播放栏控制按钮",
    "icon" to "图标",
    "icon-button" to "图标按钮",
    "text" to "文本",
    "rect" to "色块",
    "spacer" to "弹性空白",
    "cover" to "封面",
    "progress-slider" to "进度滑块",
    "play-button" to "播放/暂停",
    "prev-button" to "上一首",
    "next-button" to "下一首",
    "playmode-button" to "播放模式",
    "queue-button" to "播放队列",
    "track-info" to "曲目信息",
    "progress-bar" to "进度条",
    "controls-row" to "控制按钮组",
    "fp-backdrop" to "播放器背景层",
    "fp-cover" to "大封面",
    "fp-title" to "歌曲标题",
    "fp-subtitle" to "歌手/专辑",
    "fp-progress" to "播放器进度条",
    "__slot__" to "子区域容器",
)

private fun selectorTitle(selector: String): String = if (selector.startsWith(".")) {
    selector.removePrefix(".") + "（区域）"
} else {
    componentLabelOf(selector.removePrefix("#")) + "（组件）"
}

private fun propsSummary(props: Map<String, String>): String {
    if (props.isEmpty()) return "（无样式）"
    return props.entries.joinToString("  ") { (k, v) -> "$k: $v" }
}

/** 常用属性定义表（所有组件编辑抽屉共用的通用属性）。
 *  SEGMENT：有明确取值集合 → 直接选择（FilterChip 单选）；
 *  NUMBER：开放数值 → 输入框（可带单位）；
 *  TEXT：自由文本 → 输入框（+ 可选快捷选项）；
 *  COLOR：颜色 → 色板快捷 + 输入框（任意 CSS 颜色）。
 *
 *  归属划分：
 *  - justify-content（分布方式）→ [SLOT_ONLY_PROPS]（仅 slot 决定内部组件排列）；
 *  - align-self（对齐方式）→ [COMPONENT_ONLY_PROPS]（仅组件决定在父 slot 交叉轴位置）；
 *  - align（叠放定位）→ 通用（overlay 叠放时 slot 与组件都按其 9 宫格定位）；
 *  - 组件特有属性（render-style / item-* 等）见 [COMPONENT_SPECIFIC_PROPS]，仅对应抽屉显示。 */
private val COMMON_PROPS: List<PropSpec> = listOf(
    PropSpec("arrange", "排列方向", PropType.SEGMENT, options = listOf("row", "column", "overlay")),
    PropSpec("weight", "空间占比", PropType.NUMBER, numeric = true),
    PropSpec("size", "尺寸", PropType.NUMBER),
    PropSpec("width", "宽度", PropType.NUMBER),
    PropSpec("height", "高度", PropType.NUMBER),
    PropSpec("color", "文字/图标颜色", PropType.COLOR),
    PropSpec("background-color", "背景颜色", PropType.COLOR),
    PropSpec("border-radius", "圆角", PropType.NUMBER),
    PropSpec("padding", "内边距", PropType.NUMBER),
    PropSpec("gap", "间距", PropType.NUMBER),
    PropSpec("font-size", "字号", PropType.NUMBER),
    PropSpec("align", "叠放定位", PropType.SEGMENT, options = listOf("top-start", "top-center", "top-end", "center-start", "center", "center-end", "bottom-start", "bottom-center", "bottom-end")),
    PropSpec("opacity", "不透明度", PropType.NUMBER, numeric = true),
)

/** slot 独有属性：仅区域选择器（.x）的编辑抽屉显示。
 *  justify-content（分布方式）：决定 slot 内组件沿排列方向（row=水平 / column=垂直）的分布。
 *  组件自身不读取此属性，因此组件抽屉不显示。 */
private val SLOT_ONLY_PROPS: List<PropSpec> = listOf(
    PropSpec("justify-content", "分布方式", PropType.SEGMENT, options = listOf("start", "center", "end", "space-between", "space-evenly", "space-around")),
)

/** 组件独有属性：仅组件选择器（#x）的编辑抽屉显示。
 *  align-self（对齐方式）：决定组件在父 slot 交叉轴（与排列方向垂直的轴）上的对齐（未设 = stretch 填满）。
 *  slot 自身不读取此属性，因此区域抽屉不显示。 */
private val COMPONENT_ONLY_PROPS: List<PropSpec> = listOf(
    PropSpec("align-self", "对齐方式", PropType.SEGMENT, options = listOf("start", "center", "end", "stretch")),
)

/** 迷你播放栏玻璃属性（playbar / pb-backdrop 共用）。 */
private val GLASS_PROPS: List<PropSpec> = listOf(
    PropSpec("render-style", "渲染样式", PropType.SEGMENT, options = listOf("none", "semi-tran", "blur", "liquid")),
    PropSpec("blur-radius", "模糊半径", PropType.NUMBER),
    PropSpec("liquid-edge", "边缘隆起", PropType.NUMBER),
    PropSpec("liquid-refraction", "折射强度", PropType.NUMBER),
    PropSpec("liquid-opacity", "表面不透明度", PropType.NUMBER, numeric = true),
    PropSpec("liquid-specular", "高光强度", PropType.NUMBER, numeric = true),
    PropSpec("liquid-shininess", "高光锐度", PropType.NUMBER, numeric = true),
    PropSpec("liquid-rim", "边缘亮线", PropType.NUMBER, numeric = true),
    PropSpec("liquid-chromatic", "色散强度", PropType.NUMBER, numeric = true),
)

/**
 * 组件特有属性表 —— 仅在该组件的编辑抽屉中显示（key = 组件 id，不含 # 前缀）。
 * 覆盖各组件实际读取的 CSS 属性：
 * - 迷你播放栏（playbar / pb-backdrop）：玻璃渲染样式与液态玻璃参数
 * - 播放列表（playlist）：条目 item-* 样式（含封面卡片网格）
 * - 分类栏（tab-bar）：标签栏（默认）/ `style: pills` 胶囊行 / `display: column` 竖向堆叠
 * - 文本（text）：content / font-weight / font-style / text-align
 */
private val COMPONENT_SPECIFIC_PROPS: Map<String, List<PropSpec>> = mapOf(
    "playbar" to GLASS_PROPS,
    "pb-backdrop" to GLASS_PROPS,
    "playlist" to listOf(
        PropSpec("item-layout", "条目布局", PropType.SEGMENT, options = listOf("list", "grid")),
        PropSpec("item-columns", "网格列数", PropType.NUMBER, numeric = true),
        PropSpec("item-bg", "条目背景色", PropType.COLOR),
        PropSpec("item-radius", "条目圆角", PropType.NUMBER),
        PropSpec("item-color", "歌名颜色", PropType.COLOR),
        PropSpec("item-font-size", "歌名字号", PropType.NUMBER),
        PropSpec("item-sub-color", "歌手/专辑颜色", PropType.COLOR),
        PropSpec("item-sub-size", "副文字字号", PropType.NUMBER),
        PropSpec("item-font-family", "字体", PropType.TEXT, options = listOf("serif", "monospace", "cursive")),
    ),
    "tab-bar" to listOf(
        // 3 种样式组合：标签栏（默认）/ 胶囊行（style: pills）/ 竖向堆叠（display: column）
        PropSpec(
            key = "__tab_style__",
            label = "标签栏样式",
            type = PropType.SEGMENT,
            options = listOf("标签栏", "胶囊行", "竖向堆叠"),
            presetValues = mapOf(
                "标签栏" to mapOf("style" to null, "display" to null),
                "胶囊行" to mapOf("style" to "pills", "display" to null),
                "竖向堆叠" to mapOf("style" to null, "display" to "column"),
            ),
        ),
    ),
    "text" to listOf(
        PropSpec("content", "文本内容", PropType.TEXT),
        PropSpec("font-weight", "字重", PropType.NUMBER, numeric = true),
        PropSpec("font-style", "字体风格", PropType.SEGMENT, options = listOf("normal", "italic")),
        PropSpec("text-align", "文本对齐", PropType.SEGMENT, options = listOf("start", "center", "end")),
    ),
)
