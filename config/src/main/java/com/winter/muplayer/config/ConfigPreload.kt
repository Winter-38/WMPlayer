package com.winter.muplayer.config

import android.content.Context
import java.io.File

/**
 * 配置预加载缓存。
 *
 * 在 Activity.onCreate 中 setContent 之前调用 [start] 启动 IO 线程加载，
 * StyleConfigLoader 的 init 先检查此缓存，命中则直接使用，零文件 I/O 延迟。
 *
 * 线程安全：@Volatile 保证写线程对读线程可见，初始化后不修改。
 */
object ConfigPreload {

    @Volatile
    var config: ComponentLayout? = null

    @Volatile
    var css: CssRuleTable? = null

    /** 在 IO 线程上加载配置并缓存。可在 setContent 之前调用。 */
    fun start(context: Context) {
        Thread {
            try {
                load(context)
            } catch (_: Exception) {
                // 静默失败，StyleConfigLoader 会走同步回退
            }
        }.apply {
            name = "config-preload"
            isDaemon = true
            start()
        }
    }

    /** 同步加载配置到缓存。由 [start] 的线程调用。 */
    private fun load(context: Context) {
        val configDir: File = run {
            val extDir = context.getExternalFilesDir(null)
            if (extDir != null) File(extDir, "config")
            else File(context.filesDir, "config")
        }
        val mainFile = File(configDir, "main.json")
        if (!mainFile.isFile) return

        val (merged, _) = StyleConfigLoader.resolveWithIncludesStatic(
            mainFile, configDir, mutableSetOf()
        )
        val parsed = StyleConfigLoader.parseConfigObjectStatic(merged)
        val cssRules = loadCssStatic(configDir)

        config = parsed
        css = cssRules
    }

    private fun loadCssStatic(configDir: File): CssRuleTable {
        if (!configDir.isDirectory) return CssRuleTable()
        val cssFiles = configDir.listFiles { f -> f.extension == "css" }
            ?.sortedBy { it.name } ?: return CssRuleTable()
        val merged = mutableMapOf<String, Map<String, String>>()
        for (file in cssFiles) {
            try {
                merged.putAll(CssParser.parse(file.readText()))
            } catch (_: Exception) { }
        }
        return CssRuleTable(rules = merged)
    }
}
