package com.winter.muplayer.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.roundToInt

/**
 * 组件配置加载器。
 *
 * `{filesDir}/config/main.json` 是唯一入口点，没有它则回退硬编码默认值。
 * 支持 `"include": ["style.json"]` 显式引用其他 JSON 文件，支持嵌套 include，
 * visited 集合检测循环引用。
 *
 * slot 排列顺序由 "main" 数组的索引决定，不依赖 JSON object key 顺序。
 * JSON 只描述结构（type / children / class），所有样式由 CSS class 控制。
 */
class StyleConfigLoader(private val context: Context) {

    private val _config: MutableStateFlow<ComponentLayout>
    val config: StateFlow<ComponentLayout>

    private val _cssRules: MutableStateFlow<CssRuleTable>
    val cssRules: StateFlow<CssRuleTable>

    private val configDir: File
        get() {
            val extDir = context.getExternalFilesDir(null)
            return if (extDir != null) File(extDir, "config")
            else File(context.filesDir, "config")
        }

    init {
        // 优先使用 ConfigPreload 后台缓存（大概率命中，零延迟）
        val cachedConfig = ConfigPreload.config
        if (cachedConfig != null) {
            _config = MutableStateFlow(cachedConfig)
            _cssRules = MutableStateFlow(ConfigPreload.css ?: CssRuleTable())
            config = _config.asStateFlow()
            cssRules = _cssRules.asStateFlow()
        } else {
            // 缓存未命中 → 跳同步读文件，用空默认值，initialize() 负责异步加载
            _config = MutableStateFlow(ComponentLayout())
            _cssRules = MutableStateFlow(CssRuleTable())
            config = _config.asStateFlow()
            cssRules = _cssRules.asStateFlow()
        }
    }

    /**
     * 写入默认配置文件（如需）+ 加载布局。
     * 通常在 LaunchedEffect 中调用，用于应用启动后的完整初始化。
     *
     * 策略：启动默认直接读缓存（[ConfigPreload] 已加载则直接用，不碰布局文件）；
     * 仅当缓存缺失（首次安装 / 系统清理了 cache 目录）时解析一次布局文件并写缓存。
     * 布局文件之后只在用户手动“重新读取配置”时重新解析。
     */
    fun initialize() {
        writeDefaultsIfMissing()

        // 存量迁移（幂等）：旧三层 overlay（backdrop-blur 兄弟镜像层）→ 两层（playbar 自包含模糊镜像）
        // 迁移成功说明磁盘布局已变 → 缓存失效，强制重新加载
        if (migrateOverlayLayout()) {
            ConfigPreload.config = null
            reload()
            return
        }

        // 线性默认布局 → 统一升级为 overlay 浮层（播放栏叠加在 app-center 内容之上，
        // none 也悬浮，与其他渲染样式布局一致）
        val mainFile = File(configDir, "main.json")
        if (mainFile.isFile && isLinearDefaultMain(try { mainFile.readText() } catch (_: Exception) { "" })) {
            if (ensureMiniLayout()) {
                ConfigPreload.config = null
                reload()
                return
            }
        }

        // 缓存已在启动预加载中命中 → 直接用，不解析布局文件
        if (ConfigPreload.config != null) return

        // 缓存未命中：再试一次（可能刚被后台 ConfigPreload 写入），仍无则解析一次并写缓存
        val cached = BinaryCache.tryRead(cacheDir())
        if (cached != null) {
            ConfigPreload.config = cached.first
            ConfigPreload.css = cached.second
            _config.value = cached.first
            _cssRules.value = cached.second
        } else {
            reload()
        }
    }

    /**
     * 保存完整布局到 main.json（布局编辑器使用）。
     * 编辑器负责提供扁平化后的全量布局文本（无 include 引用），
     * 保存后调用方应 [reload] 使改动生效。
     */
    fun saveLayoutJson(text: String) {
        try {
            val dir = configDir
            dir.mkdirs()
            File(dir, "main.json").writeText(text)
        } catch (e: Exception) {
            android.util.Log.w("StyleConfig", "saveLayoutJson failed: ${e.message}")
        }
    }

    /**
     * 保存完整样式到 styles.css（布局编辑器使用）。
     * 编辑器写回的是全量合并表（含其他 .css 文件贡献的规则），因此把 config/ 下
     * 其余 .css 重命名为 .bak 隔离 —— 否则目录扫描会再次加载旧文件并可能覆盖编辑结果。
     * 保存后调用方应 [reload] 使改动生效。
     */
    fun saveStylesCss(text: String) {
        try {
            val dir = configDir
            dir.mkdirs()
            File(dir, "styles.css").writeText(text)
            dir.listFiles { f -> f.isFile && f.extension == "css" && f.name != "styles.css" }
                ?.forEach { f ->
                    val bak = File(dir, f.name + ".bak")
                    if (!bak.exists()) f.renameTo(bak)
                }
        } catch (e: Exception) {
            android.util.Log.w("StyleConfig", "saveStylesCss failed: ${e.message}")
        }
    }

    /**
     * 从磁盘重新解析 JSON + CSS 配置文件并写缓存。
     * 仅在用户手动“重新读取配置”或初始化兜底时调用；启动默认只读缓存。
     * `main.json` 是唯一的入口点，没有它则回退硬编码默认值。
     * 支持 `"include": ["relative/path.json"]` 显式引用其他文件。
     * 自动加载 `config/` 下所有 .css 文件。
     */
    fun reload() {
        val mainFile = File(configDir, "main.json")
        if (!mainFile.isFile) {
            // main.json 缺失（配置目录被清空等）→ 回退初始默认样式
            fallbackToDefaults()
            return
        }

        try {
            // 对象格式：{ "slot名": [...] }，支持 include
            val (merged, _) = resolveWithIncludes(mainFile, mutableSetOf())
            _config.value = parseConfigObject(merged)

            _cssRules.value = loadCssFiles()

            // 解析成功 → 写缓存，下次启动直接读取
            BinaryCache.write(cacheDir(), _config.value, _cssRules.value)
        } catch (e: Exception) {
            android.util.Log.w("StyleConfig", "Failed to parse config: ${e.message}")
            // 解析失败也落盘错误日志（logs/error_*.log），便于事后排查
            com.winter.muplayer.core.CrashLogManager.writeErrorLog(
                context, "StyleConfig", "Failed to parse config: ${e.message}", e,
            )
            android.widget.Toast.makeText(context, "配置解析失败，已回退默认样式", android.widget.Toast.LENGTH_LONG).show()
            // 解析失败 → 回退应用内置的初始默认样式，避免残缺配置导致布局一团糟
            fallbackToDefaults()
        }
    }

    /**
     * 修复存量布局（幂等）：将上次自动升级产生的 overlay 叠放变体（含 backdrop-blur 毛玻璃
     * 镜像）恢复为当前默认线性布局（app-center 线性 + app-bottom 迷你栏）。
     * 仅当 main.json 明确含 backdrop-blur 时才修复；用户自定义布局不受影响。返回是否发生修复。
     */
        /**
     * 存量迁移（幂等）：旧版 overlay 布局（含 backdrop-blur 兄弟镜像层，依赖 CSS height 限定区域）
     * 迁移为当前两层结构（content + playbar 自包含模糊镜像），并把 #backdrop-blur 的 blur-radius
     * 迁移到 #playbar —— 彻底消除“镜像层高度缺失 → 全屏模糊 → 无法滑动”。返回是否发生了迁移。
     */
    private fun migrateOverlayLayout(): Boolean {
        val mainFile = File(configDir, "main.json")
        if (!mainFile.isFile) return false
        val text = try { mainFile.readText() } catch (_: Exception) { return false }
        if (!text.contains("backdrop-blur")) return false // 线性或新两层 overlay 均无 backdrop-blur → 无需迁移
        return try {
            // 迁移 blur-radius（若旧 CSS 中 #backdrop-blur 有非 0 值 → 写 #playbar）
            val cssFile = File(configDir, "styles.css")
            val blurRadius = if (cssFile.isFile) {
                Regex("#backdrop-blur\\s*\\{[^}]*blur-radius\\s*:\\s*([^;}]+)")
                    .find(cssFile.readText())?.groupValues?.get(1)?.trim()
            } else null
            mainFile.writeText(overlayMainJson())
            if (cssFile.isFile && blurRadius != null && blurRadius != "0" && blurRadius != "0px") {
                var css = cssFile.readText()
                css = setCssProperty(css, "#playbar", "blur-radius", blurRadius)
                cssFile.writeText(css)
            }
            true
        } catch (_: Exception) { false }
    }

    /**
     * 设置迷你播放栏渲染样式（写入 styles.css + 按需切换布局），设置页三态切换：
     * - `none`      无效果：overlay 浮层（不透明卡片悬浮，与其他样式布局一致）；
     * - `semi-tran` 半透明：overlay 浮层 + 半透明表面（露出下方内容，不模糊）；
     * - `blur`      毛玻璃：overlay 浮层 + 液态玻璃引擎（AndroidLiquidGlass：内容实时 blur +
     *                lens 折射/色散 + 高光 + 阴影）+ 半透明表面。
     * 规则块不存在时追加；调用后需 [reload] 重新解析才生效。
     */
    fun setMiniRenderStyle(style: String) {
        val normalized = if (style == "semi-tran" || style == "blur" || style == "liquid") style else "none"
        // 布局：所有样式（含 none）统一 overlay 浮层，播放栏始终叠加在内容之上
        ensureMiniLayout()
        // 2) CSS：render-style + 模糊半径（写在 #playbar，由 playbar 组件读取）
        val cssFile = File(configDir, "styles.css")
        if (!cssFile.isFile) return
        var text = try { cssFile.readText() } catch (_: Exception) { return }
        listOf("#playbar", "#pb-backdrop").forEach { selector ->
            text = setCssProperty(text, selector, "render-style", normalized)
        }
        // blur-radius：毛玻璃 12dp（更明显的模糊）/ 液态玻璃 8dp（原版示例值）/ 其他 0
        text = setCssProperty(
            text, "#playbar", "blur-radius",
            when (normalized) {
                "blur" -> "12dp"
                "liquid" -> "8dp"
                else -> "0"
            }
        )
        // 液态玻璃折射参数（dp 语义，原版示例 LiquidBottomTabs: lens(24dp, 24dp)）：
        // 切换到 liquid 时若 #playbar 块尚未配置 liquid-edge / liquid-refraction 则写入默认值
        if (normalized == "liquid") {
            if (!Regex("#playbar\\s*\\{[^}]*liquid-edge").containsMatchIn(text)) {
                text = setCssProperty(text, "#playbar", "liquid-edge", "40dp")
            }
            if (!Regex("#playbar\\s*\\{[^}]*liquid-refraction").containsMatchIn(text)) {
                text = setCssProperty(text, "#playbar", "liquid-refraction", "32dp")
            }
        }
        try { cssFile.writeText(text) } catch (_: Exception) { }
    }

    /**
     * 设置玻璃视觉参数（写入 styles.css 的 `#playbar` 属性，设置页滑块调用）：
     * - blur-radius（模糊半径，毛玻璃/液态玻璃共用）与长度类（边缘隆起 / 折射）写 dp；
     * - 表面不透明度 / 强度类写无单位数值。
     * 规则块不存在时追加；调用后需 [reload] 重新解析才生效。
     */
    fun setLiquidGlassParams(
        blurRadiusDp: Float,
        edgeWidthDp: Float,
        refractionDp: Float,
        surfaceAlpha: Float,
        specular: Float,
        shininess: Float,
        rimStrength: Float,
    ) {
        android.util.Log.d(
            "LiquidGlass",
            "setLiquidGlassParams: blur=${blurRadiusDp}dp edge=${edgeWidthDp}dp refraction=${refractionDp}dp " +
                "opacity=$surfaceAlpha specular=$specular shininess=$shininess rim=$rimStrength",
        )
        val cssFile = File(configDir, "styles.css")
        if (!cssFile.isFile) return
        var text = try { cssFile.readText() } catch (_: Exception) { return }
        text = setCssProperty(text, "#playbar", "blur-radius", "${blurRadiusDp.roundToInt()}dp")
        text = setCssProperty(text, "#playbar", "liquid-edge", "${edgeWidthDp.roundToInt()}dp")
        text = setCssProperty(text, "#playbar", "liquid-refraction", "${refractionDp.roundToInt()}dp")
        text = setCssProperty(text, "#playbar", "liquid-opacity", formatFloat(surfaceAlpha))
        text = setCssProperty(text, "#playbar", "liquid-specular", formatFloat(specular))
        text = setCssProperty(text, "#playbar", "liquid-shininess", shininess.roundToInt().toString())
        text = setCssProperty(text, "#playbar", "liquid-rim", formatFloat(rimStrength))
        try { cssFile.writeText(text) } catch (_: Exception) { }
    }

    /** 浮点转紧凑字符串（圆整到两位小数避免步进累积误差）：0.55 → "0.55"，1 → "1" */
    private fun formatFloat(v: Float): String {
        val rounded = (v * 100).roundToInt() / 100f
        return if (rounded % 1f == 0f) rounded.toInt().toString() else rounded.toString()
    }

    /**
     * 确保 main.json 布局与所选样式匹配：
     * 所有渲染样式（含 none）统一使用 overlay 浮层布局 —— 播放栏叠加在 app-center 内容之上，
     * 保持悬浮观感（none 为不透明悬浮卡片，semi-tran/blur/liquid 为玻璃浮层）。
     * 仅当 main.json 是默认变体（线性 / 新两层 overlay / 旧三层 overlay）时自动切换；
     * 旧三层 overlay（含 backdrop-blur 兄弟镜像层）**强制迁移**为两层 —— 否则残留的
     * 全屏镜像层会盖住列表导致无法滑动、毛玻璃渲染异常。用户自定义布局不动。
     * 返回是否发生了切换。
     */
    private fun ensureMiniLayout(): Boolean {
        val mainFile = File(configDir, "main.json")
        if (!mainFile.isFile) return false
        val text = try { mainFile.readText() } catch (_: Exception) { return false }
        val isLinear = isLinearDefaultMain(text)
        val isLegacyOverlay = text.contains("backdrop-blur") // 旧三层（含 backdrop-blur 兄弟镜像层）
        val isNewOverlay = !isLegacyOverlay && isDefaultOverlay(text) // 新两层（content + playbar）
        if (!isLinear && !isLegacyOverlay && !isNewOverlay) return false // 用户自定义布局：不自动改
        // 所有渲染样式（含 none）都使用 overlay 浮层布局：播放栏始终叠加在内容之上
        if (isNewOverlay) return false // 已是最新两层 overlay
        return try {
            mainFile.writeText(overlayMainJson())
            // 同步 styles.css：.app-center 方向 + playbar 浮层定位
            val cssFile = File(configDir, "styles.css")
            if (cssFile.isFile) {
                var css = cssFile.readText()
                css = setCssProperty(css, ".app-center", "arrange", "overlay")
                css = setCssProperty(css, "#playbar", "align", "bottom-center")
                cssFile.writeText(css)
            }
            true
        } catch (_: Exception) { false }
    }

    /** main.json 是否为新两层 overlay 结构（app-center = [content 子 slot, playbar]，无 backdrop-blur）。 */
    private fun isDefaultOverlay(text: String): Boolean = try {
        val root = JSONObject(LayoutParser.removeComments(text))
        val main = root.optJSONObject("main") ?: return false
        val center = main.optJSONArray("app-center") ?: return false
        center.length() == 2 &&
            center.optJSONObject(0)?.optString("name") == "content" &&
            center.optString(1) == "playbar"
    } catch (_: Exception) { false }

    /** main.json 是否为线性默认结构（app-center = [tab-bar, sort, playlist] + app-bottom = [playbar]）。 */
    private fun isLinearDefaultMain(text: String): Boolean = try {
        val root = JSONObject(LayoutParser.removeComments(text))
        val main = root.optJSONObject("main") ?: return false
        val center = main.optJSONArray("app-center")
        val bottom = main.optJSONArray("app-bottom")
        center != null && center.length() == 3 &&
            (0 until 3).all { center.optString(it) == listOf("tab-bar", "sort", "playlist")[it] } &&
            bottom != null && bottom.length() == 1 && bottom.optString(0) == "playbar"
    } catch (_: Exception) { false }

    /** overlay 浮层模板（半透明 / 毛玻璃用）：
     *  - content：前景列表（正常渲染，铺满；毛玻璃时通过 captureLiquidGlassContent
     *    把列表层录制为液态玻璃内容源 —— AndroidLiquidGlass backdrop 引擎）；
     *  - playbar：浮层，blur 模式下 drawBackdrop 引用内容层绘制模糊 + 折射副本。
     */
    private fun overlayMainJson(): String {
        val root = JSONObject(LayoutParser.removeComments(defaultMainJson()))
        val main = root.getJSONObject("main")
        main.remove("app-bottom")
        val contentContainer = org.json.JSONObject().apply {
            put("name", "content")
            put("children", org.json.JSONArray().apply { put("tab-bar"); put("sort"); put("playlist") })
        }
        main.put("app-center", org.json.JSONArray().apply {
            put(contentContainer)
            put("playbar")
        })
        return root.toString(2)
    }

    /** 在 styles.css 文本中设置指定选择器块的属性值（块内无该属性则插入，无该块则追加）。 */
    private fun setCssProperty(text: String, selector: String, prop: String, value: String): String {
        // 定位选择器规则块：{ ... }（CSS 属性块无嵌套）
        val blockRegex = Regex("$selector\\s*\\{([^}]*)\\}")
        val block = blockRegex.find(text)
        if (block != null) {
            val inner = block.groupValues[1]
            val propRegex = Regex("$prop\\s*:\\s*[^;}]+(?=[;} ])")
            val newInner = if (propRegex.containsMatchIn(inner)) {
                // 已有该属性 → 仅替换值，保留块内其他属性
                propRegex.replace(inner) { "$prop: $value" }
            } else {
                // 块内无该属性 → 在块尾插入，避免新增块覆盖原规则
                inner.trimEnd() + " $prop: $value;"
            }
            return text.replaceRange(block.range, "$selector { $newInner }")
        }
        // 无该选择器规则 → 追加新块
        return text + "\n$selector { $prop: $value; }\n"
    }

    /**
     * 回退到应用内置的初始默认配置（与首次启动写入磁盘的模板一致）：
     * `defaultMainJson()` 解析出的 slot 布局 + `defaultStylesCss()` 解析出的样式。
     * 双重 try-catch 兜底：最坏情况退化为空默认值，保证界面始终可用。
     */
    private fun fallbackToDefaults() {
        _config.value = try {
            parseConfigObjectStatic(JSONObject(defaultMainJson()))
        } catch (_: Exception) {
            ComponentLayout()
        }
        _cssRules.value = try {
            CssRuleTable(rules = CssParser.parse(defaultStylesCss()))
        } catch (_: Exception) {
            CssRuleTable()
        }
    }

    /** 缓存目录：应用专属外部缓存目录（Android/data/<package>/cache，与 files 同级）；不可用时回退内部 cacheDir */
    private fun cacheDir(): File {
        val extCache = context.getExternalCacheDir()
        return if (extCache != null) extCache
        else File(context.cacheDir, "config-cache")
    }

    /** 加载 config/ 下所有 .css 文件，按文件名升序合并 */
    private fun loadCssFiles(): CssRuleTable {
        if (!configDir.isDirectory) return CssRuleTable()
        val cssFiles = configDir.listFiles { f -> f.extension == "css" }
            ?.sortedBy { it.name } ?: return CssRuleTable()
        val merged = mutableMapOf<String, Map<String, String>>()
        var latestMod = 0L
        for (file in cssFiles) {
            try {
                val rules = CssParser.parse(file.readText())
                merged.putAll(rules)
                if (file.lastModified() > latestMod) latestMod = file.lastModified()
            } catch (e: Exception) {
                android.util.Log.w("StyleConfig", "Failed to parse CSS ${file.name}: ${e.message}")
            }
        }
        return CssRuleTable(rules = merged)
    }

    /**
     * 递归解析一个 JSON 文件及其 include 引用。
     * 委托给 companion 静态方法。
     */
    private fun resolveWithIncludes(file: File, visited: MutableSet<String>): Pair<JSONObject, Long> =
        resolveWithIncludesStatic(file, configDir, visited)

    /**
     * 将默认配置写入磁盘（创建 config/main.json + styles.css）。
     * 文件已存在时跳过。模板内容见 companion 的 [defaultMainJson] / [defaultStylesCss]。
     */
    fun writeDefaultsIfMissing() {
        if (configDir.isDirectory && File(configDir, "main.json").exists()) return
        configDir.mkdirs()

        // 单个 main.json，无多余间接引用。include 机制保留给高级用户自行拆分。
        val mainFile = File(configDir, "main.json")
        if (!mainFile.exists()) {
            mainFile.writeText(defaultMainJson())
        }

        // 默认 styles.css
        val cssFile = File(configDir, "styles.css")
        if (!cssFile.exists()) {
            cssFile.writeText(defaultStylesCss())
        }
    }

    /** 从根级 JSON 对象解析 ComponentLayout。委托给 companion 静态方法。 */
    private fun parseConfigObject(root: JSONObject): ComponentLayout =
        parseConfigObjectStatic(root)

    companion object {
        // ── 以下静态方法同时被 StyleConfigLoader（实例）和 ConfigPreload（后台线程）调用 ──

        /**
         * 默认 main.json 模板（首次启动写入磁盘）——回退到上一个 commit 的布局配置
         * （来源：手机实测配置，playbar 聚合迷你播放栏 + fp-backdrop 背景层容器
         *   + 命名子 slot fp-space1 / fp-main / fp-space2）。
         *
         * - main 为对象形式（slot 名 → 组件数组）
         * - full-player：fp-backdrop 为 slot 型容器（背景层），children 为命名子 slot
         *   （CSS 用 .fp-space1 / .fp-main / .fp-space2 定位）
         */
        fun defaultMainJson(): String = """
{
  // ═══════════════════════════════════════════════════
  // WMPlayer 默认布局（回退版：与上一个 commit 设备实测配置一致）
  // main —— 对象形式：slot 名 → 组件数组
  // full-player —— fp-backdrop 背景层容器 + 命名子 slot（fp-space1/fp-main/fp-space2）
  // ═══════════════════════════════════════════════════
  "main": {
    "app-top": [
      "app-name",
      "spacer",
      "search-button",
      "setting-button"
    ],
    "app-center": [
      "tab-bar",
      "sort",
      "playlist"
    ],
    "app-bottom": [
      "playbar"
    ]
  },
  "full-player": {
    "fp-backdrop": {
      "fp-space1": [
        "spacer@fp-spacer"
      ],
      "fp-main": [
        "spacer@fp-top-spacer",
        "fp-title",
        "fp-subtitle",
        "spacer",
        "fp-cover",
        "spacer",
        "fp-progress",
        "spacer@fp-progress-spacer",
        {
          "name": "button",
          "children": [
            "playmode-button",
            "prev-button",
            "play-button",
            "next-button",
            "queue-button"
          ]
        },
        "spacer@fp-bottom-spacer"
      ],
      "fp-space2": [
        "spacer@fp-spacer"
      ]
    }
  }
}
""".trimIndent()

        /** 默认 styles.css 模板（首次启动写入磁盘）——回退到上一个 commit 的布局样式（来源：手机实测配置）。 */
        fun defaultStylesCss(): String = """
/* 外层容器方向 — slot 之间的排列方式 */
/*   arrange: column — 垂直堆叠（默认） */
/*   arrange: row    — 水平排列 */
.main { arrange: column; }

/* slot 内部组件排列方向：arrange: row | column */
/* slot 比例：weight: 1（默认均分）| 0（包裹内容）| 2、3... */

.full-player { arrange: column; gap: 8px; }
.app-top    { arrange: row;    weight: 0; }
#search-button  { size: 40px; }
#setting-button { size: 40px; }
#spacer     { weight: 1; }
.app-center { arrange: column; weight: 1; }
.app-bottom { arrange: row;    weight: 0; }

/* 迷你播放栏渲染样式（与设置页四态切换双向同步）：
   render-style: none       无效果：不透明卡片（默认，线性独立栏）
   render-style: semi-tran  半透明：浮层 + 半透明表面（露出下方内容，不模糊）
   render-style: blur       毛玻璃：浮层 + 内容实时模糊 + 半透明表面（仅模糊，无折射/高光）
   render-style: liquid     液态玻璃：浮层 + AndroidLiquidGlass 引擎，按原版示例组装 ——
                             vibrancy（增饱和）+ blur(8dp) + lens(24dp,24dp)（AGSL 折射/色散）
                             + 常驻轻高光 + 轻阴影 + 半透明基色
   blur-radius: 模糊半径（dp 语义；blur 模式由设置写为 12dp，liquid 模式为 8dp，其他 0）
   liquid-*: 液态玻璃视觉参数（仅 liquid 模式生效，由设置页滑块写入）：
     liquid-edge        边缘隆起宽度（折射带，dp 语义；3x 屏 1dp=3px）
     liquid-refraction  折射强度（dp 语义）
     liquid-opacity     表面基色不透明度（0..1，越小越透明；默认 0.1 高透）
     liquid-specular    高光强度（无单位）
     liquid-shininess   高光锐度（无单位）
     liquid-rim         rim 边缘亮线强度（无单位，默认 0 关闭，需时滑块/CSS 调回）
     liquid-chromatic   色散强度 0..1（默认 0 关闭，可选，CSS 手配）

   折射可见性：折射只发生在距卡片边缘 < liquid-edge 的带状区域内，强度随
   circleMap(1 - 距离/edge) 从边缘向内部衰减。mini 播放栏内容内边距为 8~16dp，
   因此 edge 必须 ≥ 内容内边距（默认 24dp，原版 LiquidBottomTabs 同值）；
   refraction 默认 32dp（在原版 24dp 基础上调高，视觉更明显）。 */
#playbar     { render-style: none; blur-radius: 0; liquid-edge: 28dp; liquid-refraction: 36dp; liquid-opacity: 0.15; liquid-specular: 0.45; liquid-shininess: 48; liquid-rim: 0.3; }
#pb-backdrop { render-style: none; }

/* ── 全屏播放器（fp-backdrop 背景层 + 前景） ── */
/* #fp-backdrop 控制 children 子 slot 之间的排列方向（row = 左右布局） */
#fp-backdrop { arrange: row; padding: 24px 16px 0; }
#fp-spacer { weight: 0; width: 16px; }
.fp-space1 { weight: 0; }
.fp-space2 { weight: 0; }
.fp-main { arrange: column; }
/* 按钮组（命名子 slot .button）：横向居中 */
.button { arrange: row; justify-content: center; gap: 16px; padding: 8px 0 24px; }

/* 封面尺寸 */
#fp-cover { size: 320px; align-self: center;}
#fp-top-spacer { weight: 0; height: 32px; }
#fp-progress-spacer { weight: 0; height: 16px; }
#fp-bottom-spacer { weight: 0; height: 100px; }
""".trimIndent()

        /** 从根级 JSON 对象解析 ComponentLayout。 */
        /** 从根级 JSON 对象解析 ComponentLayout（公开：供 ui 模块复用，如插件页面 JSON 解析）。 */
        fun parseConfigObjectStatic(root: JSONObject): ComponentLayout {
            val slots = linkedMapOf<String, List<ComponentEntry>>()
            val customComponents = linkedMapOf<String, Map<String, Any?>>()
            var fullPlayerSlots: Map<String, List<ComponentEntry>>? = null

            // ── 自定义组件定义（#xxx key）──
            for (key in root.keys()) {
                if (!key.startsWith("#")) continue
                val obj = root.optJSONObject(key)
                if (obj != null) {
                    val props = mutableMapOf<String, Any?>()
                    for (k in obj.keys()) props[k] = obj.get(k)
                    customComponents[key.removePrefix("#")] = props
                }
            }

            // ── 全屏播放器 slot ──
            if (root.has("full-player")) {
                val entries = LayoutParser.parseSlotValue(root.get("full-player"))
                if (entries.isNotEmpty()) {
                    fullPlayerSlots = mapOf("full-player" to entries)
                }
            }

            // ── 主界面 main（支持数组（旧）与对象（新）两种形式）──
            // 旧：{ "main": [{ "app-top": [...] }] }
            // 新：{ "main": { "app-top": [...], "app-center": [...] } }
            when (val slotsValue = root.opt("main")) {
                is JSONArray -> {
                    for (i in 0 until slotsValue.length()) {
                        val element = slotsValue.getJSONObject(i)
                        val keys = element.keys()
                        if (!keys.hasNext()) continue
                        val slotName = keys.next()
                        val entries = LayoutParser.parseSlotValue(element.get(slotName))
                        if (entries.isNotEmpty()) {
                            slots[slotName] = entries
                        }
                    }
                }
                is JSONObject -> {
                    for (key in slotsValue.keys()) {
                        val entries = LayoutParser.parseSlotValue(slotsValue.get(key))
                        if (entries.isNotEmpty()) {
                            slots[key] = entries
                        }
                    }
                }
                null -> { /* 未配置 main → 使用默认值 */ }
                else -> throw IllegalArgumentException("'main' must be a JSON array or object")
            }

            return ComponentLayout(
                slots = if (slots.isNotEmpty()) slots else ComponentLayout.defaultSlots,
                customComponents = customComponents,
                fullPlayerSlots = fullPlayerSlots ?: ComponentLayout.defaultFullPlayerSlots,
            )
        }

        /** 递归解析 JSON 文件及其 include 引用。 */
        internal fun resolveWithIncludesStatic(
            file: File, configDir: File, visited: MutableSet<String>
        ): Pair<JSONObject, Long> {
            val canonical = file.canonicalPath
            if (canonical in visited) {
                android.util.Log.w("StyleConfig", "Circular include detected: $canonical")
                return JSONObject() to 0L
            }
            visited.add(canonical)

            val content = try {
                LayoutParser.readFileContent(file).trim()
            } catch (e: Exception) {
                android.util.Log.w("StyleConfig", "Failed to read ${file.name}: ${e.message}")
                return JSONObject() to 0L
            }
            if (content.isEmpty() || content == "{}") return JSONObject() to file.lastModified()

            val obj = JSONObject(content)
            var latestMod = file.lastModified()

            val includeArray = obj.optJSONArray("include")
            if (includeArray != null) {
                val base = JSONObject()
                for (i in 0 until includeArray.length()) {
                    val relPath = includeArray.getString(i)
                    val includedFile = File(configDir, relPath)
                    if (includedFile.exists() && includedFile.isFile) {
                        val (childJson, childMod) = resolveWithIncludesStatic(includedFile, configDir, visited)
                        mergeJson(base, childJson)
                        if (childMod > latestMod) latestMod = childMod
                    } else {
                        android.util.Log.w("StyleConfig", "Include file not found: $relPath")
                    }
                }
                for (key in obj.keys()) {
                    if (key == "include") continue
                    if (key == "main" && base.has("main") && base.get("main") is JSONArray && obj.get("main") is JSONArray) {
                        val baseArr = base.getJSONArray("main")
                        val objArr = obj.getJSONArray("main")
                        for (i in 0 until objArr.length()) {
                            baseArr.put(objArr.get(i))
                        }
                    } else {
                        base.put(key, obj.get(key))
                    }
                }
                return base to latestMod
            }
            return obj to latestMod
        }

        /**
         * 深度合并 JSON 对象：将 [source] 的 key 合并到 [target] 中。
         * 嵌套对象递归合并，非对象值直接覆盖。
         * - `slots` 数组特殊处理：后加载的 slot 追加到前面 slot 之后（不覆盖）
         * - 支持多文件分层配置。
         */
        private fun mergeJson(target: JSONObject, source: JSONObject) {
            for (key in source.keys()) {
                if (key == "include") continue
                val srcVal = source.get(key)
                if (key == "main" && srcVal is JSONArray && target.has("main") && target.get("main") is JSONArray) {
                    val targetArr = target.getJSONArray("main")
                    for (i in 0 until srcVal.length()) {
                        targetArr.put(srcVal.get(i))
                    }
                } else if (srcVal is JSONObject && target.has(key) && target.get(key) is JSONObject) {
                    mergeJson(target.getJSONObject(key), srcVal)
                } else {
                    target.put(key, srcVal)
                }
            }
        }
    }
}
