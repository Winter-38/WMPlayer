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
            return
        }

        try {
            // 对象格式：{ "slot名": [...] }，支持 include
            val (merged, _) = resolveWithIncludes(mainFile, mutableSetOf())
            _config.value = parseConfigObject(merged)

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
     * 将默认配置写入磁盘（创建 config/main.json + style.css）。
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
                appendLine("  // main 数组决定主界面 slot 渲染顺序，数组索引即位置")
                appendLine("  // 样式在 styles.css 中定义")
                appendLine("  \"main\": [")
                appendLine("    {")
                appendLine("      \"app-top\": [")
                appendLine("        \"app-name\",")
                appendLine("        \"spacer\",")
                appendLine("        \"search-button\",")
                appendLine("        \"setting-button\"")
                appendLine("      ]")
                appendLine("    },")
                appendLine("    {")
                appendLine("      \"app-center\": [")
                appendLine("        \"tab-bar\",")
                appendLine("        \"sort\",")
                appendLine("        \"playlist\"")
                appendLine("      ]")
                appendLine("    },")
                appendLine("    {")
                appendLine("      \"app-bottom\": [")
                appendLine("        \"playbar\"")
                appendLine("      ]")
                appendLine("    }")
                appendLine("  ],")
                appendLine("  // 全屏播放器 slot，独立于界面 main")
                appendLine("  \"full-player\": [")
                appendLine("    \"track-info\",")
                appendLine("    \"progress-bar\",")
                appendLine("    \"controls-row\"")
                appendLine("  ]")
                appendLine("}")
            })
        }

        // 默认 styles.css
        val cssFile = File(configDir, "styles.css")
        if (!cssFile.exists()) {
            cssFile.writeText(buildString {
                appendLine("/* 主界面外层容器方向 — slot 之间的排列方式 */")
                appendLine("/*   arrange: column — 垂直堆叠（默认） */")
                appendLine("/*   arrange: row    — 水平排列 */")
                appendLine(".main { arrange: column; }")
                appendLine("")
                appendLine("/* slot 内部组件排列方向：arrange: row | column */")
                appendLine("/* slot 比例：weight: 1（默认均分）| 0（包裹内容）| 2、3... */")
                appendLine("")
                appendLine(".full-player { arrange: column; gap: 8px; }")
                appendLine(".app-top    { arrange: row;    weight: 0; }")
                appendLine("#search-button  { size: 40px; }")
                appendLine("#setting-button { size: 40px; }")
                appendLine("#spacer     { weight: 1; }")
                appendLine(".app-center { arrange: column; weight: 1; }")
                appendLine(".app-bottom { arrange: row;    weight: 0; }")
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
