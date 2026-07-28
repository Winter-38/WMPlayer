package com.winter.muplayer.config

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
 * JSON 根级 key 即为 slot 名，value 为布局树。
 * JSON 只描述结构（type / children / class），所有样式由 CSS class 控制。
 */
class StyleConfigLoader(private val context: Context) {

    private val _config: MutableStateFlow<ComponentLayout>
    val config: StateFlow<ComponentLayout>

    private val _cssRules: MutableStateFlow<CssRuleTable>
    val cssRules: StateFlow<CssRuleTable>

    /** 所有已加载配置文件中最新的修改时间，用于外部监听热重载 */
    var fileLastModified by mutableStateOf(0L)
        private set

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
            fileLastModified = System.currentTimeMillis()
        } else {
            // 缓存未命中 → 同步预加载（回退方案）
            val mainFile = File(configDir, "main.json")
            if (mainFile.isFile) {
                var preloadedCss = CssRuleTable()
                var preloadedMod = mainFile.lastModified()
                val preloadedCfg = try {
                    val (merged, latestMod) = resolveWithIncludes(mainFile, mutableSetOf())
                    if (latestMod > preloadedMod) preloadedMod = latestMod
                    preloadedCss = loadCssFiles()
                    fileLastModified = preloadedMod
                    parseConfigObject(merged)
                } catch (e: Exception) {
                    android.util.Log.w("StyleConfig", "Preload failed: ${e.message}")
                    fileLastModified = 0L
                    ComponentLayout()
                }
                _config = MutableStateFlow(preloadedCfg)
                _cssRules = MutableStateFlow(preloadedCss)
                config = _config.asStateFlow()
                cssRules = _cssRules.asStateFlow()
            } else {
                _config = MutableStateFlow(ComponentLayout())
                _cssRules = MutableStateFlow(CssRuleTable())
                config = _config.asStateFlow()
                cssRules = _cssRules.asStateFlow()
            }
        }
    }

    /**
     * 写入默认配置文件（如需）+ 从磁盘重载。
     * 通常在 LaunchedEffect 中调用，用于应用启动后的完整初始化。
     */
    fun initialize() {
        writeDefaultsIfMissing()
        reload()
    }

    /**
     * 从磁盘重新加载 JSON + CSS 配置文件。
     * `main.json` 是唯一的入口点，没有它则回退硬编码默认值。
     * 支持 `"include": ["relative/path.json"]` 显式引用其他文件。
     * 自动加载 `config/` 下所有 .css 文件。
     */
    fun reload() {
        val mainFile = File(configDir, "main.json")
        if (!mainFile.isFile) {
            _config.value = ComponentLayout()
            _cssRules.value = loadCssFiles()
            fileLastModified = 0L
            return
        }

        try {
            fileLastModified = mainFile.lastModified()

            // 对象格式：{ "slot名": [...] }，支持 include
            val (merged, latestMod) = resolveWithIncludes(mainFile, mutableSetOf())
            _config.value = parseConfigObject(merged)
            if (latestMod > fileLastModified) fileLastModified = latestMod

            _cssRules.value = loadCssFiles()
        } catch (e: Exception) {
            android.util.Log.w("StyleConfig", "Failed to parse config: ${e.message}")
            android.widget.Toast.makeText(context, "配置解析失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            _config.value = ComponentLayout()
            _cssRules.value = CssRuleTable()
        }
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
     * 将默认配置写入磁盘（创建 config/main.json + style.json）。
     * 文件已存在时跳过。
     */
    fun writeDefaultsIfMissing() {
        if (configDir.isDirectory && File(configDir, "main.json").exists()) return
        configDir.mkdirs()

        // 单个 main.json，无多余间接引用。include 机制保留给高级用户自行拆分。
        val mainFile = File(configDir, "main.json")
        if (!mainFile.exists()) {
            mainFile.writeText(buildString {
                appendLine("{")
                appendLine("  // slots 数组定义界面 slot 及排列顺序，数组元素位置即渲染顺序")
                appendLine("  // 样式在 styles.css 中定义")
                appendLine("  \"slots\": [")
                appendLine("    { \"app-top\": [\"#app-name\", \"#search-button\", \"#setting-button\", \"#search-bar\"] },")
                appendLine("    { \"app-center\": [\"#tab-bar\", \"#sort\", \"#playlist\"] },")
                appendLine("    { \"app-bottom\": [\"#playbar\"] }")
                appendLine("  ],")
                appendLine("  // 全屏播放器 slot")
                appendLine("  \"main\": [\"#track-info\", \"#progress-bar\", \"#controls-row\"]")
                appendLine("}")
            })
        }

        // 默认 styles.css
        val cssFile = File(configDir, "styles.css")
        if (!cssFile.exists()) {
            cssFile.writeText(buildString {
                appendLine("/* 外层容器方向 — slot 之间的排列方式 */")
                appendLine("/*   arrange: column — 垂直堆叠（默认） */")
                appendLine("/*   arrange: row    — 水平排列 */")
                appendLine(".layout { arrange: column; }")
                appendLine("")
                appendLine("/* slot 内部组件排列方向：arrange: row | column */")
                appendLine("/* slot 比例：weight: 1（默认均分）| 0（包裹内容）| 2、3... */")
                appendLine("")
                appendLine(".main { arrange: column; gap: 8px; }")
                appendLine(".app-top { arrange: row; weight: 0; }")
                appendLine(".app-center { arrange: column; }")
                appendLine(".app-bottom { arrange: row; weight: 0; }")
            })
        }
    }

    /** 从根级 JSON 对象解析 ComponentLayout。委托给 companion 静态方法。 */
    private fun parseConfigObject(root: JSONObject): ComponentLayout =
        parseConfigObjectStatic(root)

    companion object {
        // ── 以下静态方法同时被 StyleConfigLoader（实例）和 ConfigPreload（后台线程）调用 ──

        /** 从根级 JSON 对象解析 ComponentLayout。 */
        internal fun parseConfigObjectStatic(root: JSONObject): ComponentLayout {
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
            if (root.has("main")) {
                val entries = LayoutParser.parseSlotValue(root.get("main"))
                if (entries.isNotEmpty()) {
                    fullPlayerSlots = mapOf("main" to entries)
                }
            }

            // ── 主界面 slots ──
            val slotsArray = root.optJSONArray("slots")
            if (slotsArray != null) {
                for (i in 0 until slotsArray.length()) {
                    val element = slotsArray.getJSONObject(i)
                    val keys = element.keys()
                    if (!keys.hasNext()) continue
                    val slotName = keys.next()
                    val entries = LayoutParser.parseSlotValue(element.get(slotName))
                    if (entries.isNotEmpty()) {
                        slots[slotName] = entries
                    }
                }
            } else {
                for (key in root.keys()) {
                    if (key == "include" || key == "main" || key.startsWith("#")) continue
                    val value = root.get(key)
                    val entries = LayoutParser.parseSlotValue(value)
                    if (entries.isNotEmpty()) {
                        slots[key] = entries
                    }
                }
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
                    if (key == "slots" && base.has("slots") && base.get("slots") is JSONArray && obj.get("slots") is JSONArray) {
                        val baseArr = base.getJSONArray("slots")
                        val objArr = obj.getJSONArray("slots")
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
                if (key == "slots" && srcVal is JSONArray && target.has("slots") && target.get("slots") is JSONArray) {
                    val targetArr = target.getJSONArray("slots")
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
