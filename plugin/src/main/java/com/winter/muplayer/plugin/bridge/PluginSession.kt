package com.winter.muplayer.plugin.bridge

import com.winter.muplayer.plugin.model.PluginDescriptor
import com.winter.muplayer.plugin.runtime.LuaRuntime
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * 一个已加载插件的宿主会话：聚合其调度器、配置存储、事件总线与运行目录。
 *
 * 生命周期由 [com.winter.muplayer.plugin.LuaPluginManager] 管理；
 * [close] 时释放定时器与事件订阅（配合 LuaRuntime 的弱引用完成 GC）。
 */
class PluginSession(
    val descriptor: PluginDescriptor,
    val runtime: LuaRuntime,
    val baseDir: File,
    scope: CoroutineScope
) {
    val scheduler: PluginScheduler = PluginScheduler(scope)
    val eventBus: PluginEventBus = PluginEventBus()
    val configStore: PluginConfigStore = PluginConfigStore(baseDir, descriptor.id)

    /** 插件注册的 UI 元素（组件/界面/widget），由宿主侧 PluginUiHost 消费渲染 */
    val ui: PluginUiRegistry = PluginUiRegistry()

    /** 插件私有数据目录。 */
    val dataDir: File get() = File(baseDir, "data")

    /** 插件私有缓存目录。 */
    val cacheDir: File get() = File(baseDir, "cache")

    /** 调用插件运行时全局作用域下的生命周期/事件处理函数（如 onLoad）。 */
    fun callGlobalFunction(name: String, vararg args: org.luaj.vm2.LuaValue) {
        runtime.withGlobals { globals ->
            val fn = globals.get(name)
            if (fn.isfunction()) {
                try {
                    fn.invoke(org.luaj.vm2.LuaValue.varargsOf(arrayOf(*args)))
                } catch (t: Throwable) {
                    android.util.Log.w("LuaPlugin", "插件回调 $name 失败: ${t.message}", t)
                }
            }
        }
    }

    /** 释放资源。 */
    fun close() {
        scheduler.clearAll()
        eventBus.clear()
    }
}
