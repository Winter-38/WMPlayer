package com.winter.muplayer.config

import android.content.Context
import android.util.Log
import java.io.File

/**
 * 配置预加载缓存。
 *
 * 在 Activity.onCreate 中 setContent 之前调用 [start] 启动 IO 线程加载，
 * StyleConfigLoader 的 init 先检查此缓存，命中则直接使用，零文件 I/O 延迟。
 *
 * 启动流程：
 *   1. 检查 layout.cache 是否存在且最新 → 反序列化，跳过 JSON/CSS 解析
 *   2. 否则读 JSON + CSS 解析 → 写 layout.cache（下次启动直接走 1）
 *
 * 线程安全：@Volatile 保证写线程对读线程可见，初始化后不修改。
 */
object ConfigPreload {

    private const val TAG = "ConfigPreload"

    @Volatile
    var config: ComponentLayout? = null

    @Volatile
    var css: CssRuleTable? = null

    /**
     * 同步尝试加载缓存。必须在 setContent 之前调用。
     * true = 缓存命中，首帧即用真实配置。
     * false = 没有缓存，需要 [start] 异步加载。
     */
    fun loadIfCached(context: Context): Boolean {
        val configDir = configDir(context)
        if (!BinaryCache.isFresh(configDir)) return false

        val t0 = System.nanoTime()
        val cached = BinaryCache.tryRead(configDir) ?: return false

        val elapsedMs = (System.nanoTime() - t0) / 1_000_000.0
        config = cached.first
        css = cached.second
        Log.d(TAG, "缓存命中，%.2fms 反序列化完成".format(elapsedMs))
        return true
    }

    /** 在 IO 线程上加载配置并缓存。可在 setContent 之前调用。 */
    fun start(context: Context) {
        if (config != null) {
            Log.d(TAG, "缓存已在 loadIfCached 中加载，跳过")
            return
        }
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

    /** 同步加载配置到缓存（慢速路径）。 */
    private fun load(context: Context) {
        val t0 = System.nanoTime()
        val configDir = configDir(context)

        // ── 1. 优先尝试二进制缓存（可能在 start→load 之间已被写入） ──
        if (BinaryCache.isFresh(configDir)) {
            val cached = BinaryCache.tryRead(configDir)
            if (cached != null) {
                config = cached.first
                css = cached.second
                val ms = (System.nanoTime() - t0) / 1_000_000.0
                Log.d(TAG, "后台缓存命中，%.2fms".format(ms))
                return
            }
        }

        // ── 2. 缓存不存在/已过期 → 解析 JSON + CSS ──
        val mainFile = File(configDir, "main.json")
        if (!mainFile.isFile) return

        val (merged, _) = StyleConfigLoader.resolveWithIncludesStatic(
            mainFile, configDir, mutableSetOf()
        )
        val parsedLayout = StyleConfigLoader.parseConfigObjectStatic(merged)
        val cssRules = loadCssStatic(configDir)

        config = parsedLayout
        css = cssRules

        val parseMs = (System.nanoTime() - t0) / 1_000_000.0
        Log.d(TAG, "JSON+CSS 解析完成，%.2fms".format(parseMs))

        // ── 3. 写二进制缓存（下次启动走快速路径） ──
        try {
            val t1 = System.nanoTime()
            BinaryCache.write(configDir, parsedLayout, cssRules)
            val writeMs = (System.nanoTime() - t1) / 1_000_000.0
            Log.d(TAG, "缓存写入完成，%.2fms".format(writeMs))
        } catch (_: Exception) { }
    }

    private fun configDir(context: Context): File {
        val extDir = context.getExternalFilesDir(null)
        return if (extDir != null) File(extDir, "config")
        else File(context.filesDir, "config")
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
