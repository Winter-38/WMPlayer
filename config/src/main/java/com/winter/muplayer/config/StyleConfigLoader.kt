package com.winter.muplayer.config

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

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
