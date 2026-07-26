package com.winter.muplayer.base_ui.ui.config

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

    private val _config = MutableStateFlow(ComponentLayout())
    val config: StateFlow<ComponentLayout> = _config.asStateFlow()

    private val _cssRules = MutableStateFlow(CssRuleTable())
    val cssRules: StateFlow<CssRuleTable> = _cssRules.asStateFlow()

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
            _cssRules.value = CssRuleTable()
            fileLastModified = 0L
            return
        }

        try {
            val raw = LayoutParser.readFileContent(mainFile).trim()
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
     *
     * @return Pair(已展开的 JSON 内容, 所有文件中最新修改时间)
     */
    private fun resolveWithIncludes(file: File, visited: MutableSet<String>): Pair<JSONObject, Long> {
        val canonical = file.canonicalPath
        if (canonical in visited) {
            android.util.Log.w("StyleConfig", "Circular include detected: $canonical")
            return JSONObject() to 0L
        }
        visited.add(canonical)

        // 使用 LayoutParser 读取文件（含注释剔除）
        val content = try {
            LayoutParser.readFileContent(file).trim()
        } catch (e: Exception) {
            android.util.Log.w("StyleConfig", "Failed to read ${file.name}: ${e.message}")
            return JSONObject() to 0L
        }

        if (content.isEmpty() || content == "{}") return JSONObject() to file.lastModified()

        val obj = JSONObject(content)
        var latestMod = file.lastModified()

        // 处理 include：解析所有被引用的文件，先合并进来
        val includeArray = obj.optJSONArray("include")
        if (includeArray != null) {
            val base = JSONObject()
            for (i in 0 until includeArray.length()) {
                val relPath = includeArray.getString(i)
                val includedFile = File(configDir, relPath)
                if (includedFile.exists() && includedFile.isFile) {
                    val (childJson, childMod) = resolveWithIncludes(includedFile, visited)
                    mergeJson(base, childJson)
                    if (childMod > latestMod) latestMod = childMod
                } else {
                    android.util.Log.w("StyleConfig", "Include file not found: $relPath")
                }
            }
            // 把当前文件除了 include 外的 key 覆盖到 base 上
            for (key in obj.keys()) {
                if (key == "include") continue
                base.put(key, obj.get(key))
            }
            return base to latestMod
        }

        return obj to latestMod
    }

    /**
     * 将默认配置写入磁盘（创建 config/main.json + style.json）。
     * 文件已存在时跳过。
     */
    fun writeDefaultsIfMissing() {
        val target = File(configDir, "main.json")
        if (target.exists()) return
        configDir.mkdirs()
        val json = buildString {
            appendLine("{")
            appendLine("  // 主入口文件 —— 可在此覆盖或编辑 style.json")
            appendLine("  \"include\": [\"style.json\"],")
            appendLine("}")
        }
        target.writeText(json)

        // 同时生成 style.json 作为默认布局配置
        val styleFile = File(configDir, "style.json")
        if (!styleFile.exists()) {
            val styleJson = buildString {
                appendLine("{")
                appendLine("  // 根级 key = slot 名，value = 组件列表")
                appendLine("  // 组件以 # 开头（# 可选），如 #playlist")
                appendLine("  // 样式全部在 styles.css 中定义")
                appendLine("  \"app-top\": [\"#app-name\", \"#search-button\", \"#setting-button\", \"#search-bar\"],")
                appendLine("  \"app-center\": [\"#playlist\"],")
                appendLine("  \"app-bottom\": [\"#playbar\"]")
                appendLine("}")
            }
            styleFile.writeText(styleJson)
        }

        // 同时生成默认 styles.css
        val cssFile = File(configDir, "styles.css")
        if (!cssFile.exists()) {
            cssFile.writeText("""
/* ── WMPlayer CSS 配置 ──
 * 每个 slot 默认平分屏幕高度（weight: 1）。
 * 要让 slot 只包裹内容不拉伸，设 weight: 0。
 * 要让子组件水平排列，设 arrange: horizontal 或 arrange: row。
 */
""".trimIndent())
        }
    }

    /** 从根级 JSON 对象解析 ComponentLayout。
     *  - 以 `#` 开头的 key → 自定义组件定义
     *  - 其他 key → slot 名称 */
    private fun parseConfigObject(root: JSONObject): ComponentLayout {
        val slots = linkedMapOf<String, List<ComponentEntry>>()
        val customComponents = linkedMapOf<String, Map<String, Any?>>()
        for (key in root.keys()) {
            if (key == "include") continue
            if (key.startsWith("#")) {
                // 自定义组件定义：#name → {"icon": "...", "on-click": "..."}
                val obj = root.optJSONObject(key)
                if (obj != null) {
                    val props = mutableMapOf<String, Any?>()
                    for (k in obj.keys()) props[k] = obj.get(k)
                    customComponents[key.removePrefix("#")] = props
                }
            } else {
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
        )
    }

    companion object {
        /**
         * 深度合并 JSON 对象：将 [source] 的 key 合并到 [target] 中。
         * 嵌套对象递归合并，非对象值直接覆盖。支持多文件分层配置。
         */
        private fun mergeJson(target: JSONObject, source: JSONObject) {
            for (key in source.keys()) {
                val srcVal = source.get(key)
                if (srcVal is JSONObject && target.has(key) && target.get(key) is JSONObject) {
                    mergeJson(target.getJSONObject(key), srcVal)
                } else {
                    target.put(key, srcVal)
                }
            }
        }
    }
}
