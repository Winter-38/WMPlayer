package com.winter.muplayer.plugin

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * 插件系统全局配置。
 *
 * @param pluginsDir      插件安装根目录（每个插件一个子目录，目录名 = 插件 id）
 * @param bytecodeCacheDir 预编译字节码缓存目录（.ljbc 文件）
 * @param cacheDir        安装包下载临时目录
 * @param useNativeComputation 为 true 时插件计算直接走 LuaJ 解释执行（Lua 原生计算路径，
 *        适用于对计算速度要求不高的插件）；为 false 时预留高性能后端接入点（见 LuaBackend）。
 * @param downloadDispatcher 安装流程使用的协程调度器
 */
data class PluginConfig(
    val pluginsDir: File,
    val bytecodeCacheDir: File,
    val cacheDir: File,
    val useNativeComputation: Boolean = true,
    val downloadDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    companion object {
        /** 使用应用私有目录构建默认配置。 */
        fun from(context: Context): PluginConfig = PluginConfig(
            pluginsDir = File(context.filesDir, "plugins"),
            bytecodeCacheDir = File(context.filesDir, "plugins/.bytecode"),
            cacheDir = File(context.cacheDir, "plugin-downloads")
        )
    }
}

/** 插件运行时类型：x+1 模型中的 x（高频常驻专用运行时）或 1（共享运行时）。 */
enum class RuntimeKind {
    /** 高频调用插件：独占一个常驻运行时，避免与其他插件相互阻塞。 */
    DEDICATED,

    /** 事件驱动插件：共享一个全局运行时，由事件调度器按协程隔离执行。 */
    SHARED
}

/** 插件系统异常。 */
class PluginException(message: String, cause: Throwable? = null) : Exception(message, cause)
