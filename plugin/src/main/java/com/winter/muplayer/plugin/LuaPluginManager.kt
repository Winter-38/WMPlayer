package com.winter.muplayer.plugin

import android.content.Context
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.plugin.bridge.LuaApiBuilder
import com.winter.muplayer.plugin.bridge.LuaValues
import com.winter.muplayer.plugin.bridge.PluginSession
import com.winter.muplayer.plugin.bytecode.BytecodeCache
import com.winter.muplayer.plugin.install.PluginInstaller
import com.winter.muplayer.plugin.model.PluginDescriptor
import com.winter.muplayer.plugin.model.PluginEvent
import com.winter.muplayer.plugin.registry.ExportedFunctionMap
import com.winter.muplayer.plugin.registry.PluginRegistry
import com.winter.muplayer.plugin.runtime.HostFunction
import com.winter.muplayer.plugin.runtime.RuntimeManager
import com.winter.muplayer.plugin.runtime.SharedEventExecutor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Lua 插件系统门面（x+1 运行时模型 + 宿主 API 桥接）。
 *
 * 在加载时通过 [LuaApiBuilder] 向插件注入宿主 API 表 `wm`（log / plugin / app /
 * eventBus / thread / timer / player / json / url / crypto / http / system /
 * clipboard），并：
 * - 调用生命周期回调（onLoad / onUnload / onConfigChanged / 播放事件回调）；
 * - 把播放核心的 playerState / progressState 桥接为事件
 *   （trackChanged / playStateChanged / progressUpdated），广播到所有已加载插件。
 */
class LuaPluginManager(
    context: Context,
    private val config: PluginConfig = PluginConfig.from(context),
    private val uiBridge: PluginUiBridge? = null
) {
    /**
     * Lua 执行专用作用域：单线程调度器（limitedParallelism(1)）。
     * 所有 Lua 调用（SharedEventExecutor 事件派发、播放事件分发、timer/thread 回调）
     * 都运行在此作用域 —— LuaJ Globals 非线程安全，必须全局串行，避免
     * 播放事件桥接与事件调度并发进入同一运行时。
     */
    private val luaDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val scope = CoroutineScope(SupervisorJob() + luaDispatcher)

    private val exported = ExportedFunctionMap()
    private val runtimes = RuntimeManager()
    private val registry = PluginRegistry()
    private val installer = PluginInstaller(config)
    private val executor = SharedEventExecutor(runtimes.sharedRuntime, exported, scope)
    private val core = MusicPlayerCore.getInstance(context)

    private val sessions = ConcurrentHashMap<String, PluginSession>()
    private val apiBuilder = LuaApiBuilder(context, scope, exported, uiBridge)

    /** 插件运行时数据/缓存根目录（独立于安装目录）。 */
    private val runtimeRoot: File = File(context.filesDir, "plugin_runtime")

    init {
        bridgePlayerEvents()
    }

    // ==================== 安装 / 卸载 ====================

    /** 后台下载并安装插件（挂起；在 config.downloadDispatcher 上执行 IO）。 */
    suspend fun installFromUrl(url: String): PluginDescriptor = installer.installFromUrl(url)

    /** 从本地 zip 安装。 */
    fun installZip(zip: File): PluginDescriptor = installer.installZip(zip)

    /** 卸载插件（移除磁盘 + 运行时 + 导出函数）。按 id 直接删除目录，不依赖 manifest 解析。 */
    fun uninstall(pluginId: String) {
        unload(pluginId)
        installer.uninstallById(pluginId)
    }

    // ==================== 加载 / 卸载运行时 ====================

    /**
     * 加载插件：创建会话 → 注入宿主 API → 执行入口（字节码缓存优先，mmap）→
     * 注册导出函数 → 调用 onLoad()。
     */
    fun load(pluginId: String) {
        if (registry.isLoaded(pluginId)) return
        val descriptor = installer.descriptorFor(pluginId)
            ?: throw PluginException("插件未安装: $pluginId")

        val runtime = runtimes.runtimeFor(descriptor)
        val globals = runtime.weakGlobals.get()
            ?: throw PluginException("插件运行时已被 GC 回收: $pluginId")

        // 宿主 API 注入（HostFunction 桥，无反射依赖）
        val session = PluginSession(
            descriptor, runtime,
            File(runtimeRoot, pluginId), scope
        )
        session.dataDir.mkdirs()
        session.cacheDir.mkdirs()
        runtime.inject("wm", apiBuilder.build(session))

        // 入口 chunk：字节码缓存命中走 mmap，未命中预编译并写缓存
        val chunk = BytecodeCache.obtainChunk(globals, descriptor, config)
        val result = try {
            chunk.call()
        } catch (e: Exception) {
            throw PluginException("插件入口执行失败 ${descriptor.id}: ${e.message}", e)
        }

        // 导出表约定：入口 `return { fnName = function ... end }`，或全局 `_exports`
        val exports = result.arg1().opttable(null)
            ?: globals.get("_exports").opttable(null)
            ?: throw PluginException("插件 ${descriptor.id} 未导出函数表（入口需 return 表或设置 _exports）")

        exported.registerAll(descriptor.id, exports)
        registry.register(descriptor, runtime)
        sessions[pluginId] = session

        // 生命周期：onLoad()
        session.callGlobalFunction("onLoad")

        // 通知宿主侧同步插件 UI（组件/界面/widget 注册）
        uiBridge?.onPluginLoaded(session)
    }

    /** 卸载插件运行时：调用 onUnload() → 释放会话 → 移除导出函数与注册条目 → 释放专用运行时。 */
    fun unload(pluginId: String) {
        sessions.remove(pluginId)?.let { session ->
            session.callGlobalFunction("onUnload")
            session.close()
        }
        uiBridge?.onPluginUnloaded(pluginId)
        exported.unregisterPlugin(pluginId)
        registry.unregister(pluginId)
        runtimes.release(pluginId)
    }

    /** 是否已加载且运行时存活。 */
    fun isLoaded(pluginId: String): Boolean = registry.isLoaded(pluginId)

    /** 已加载插件列表。 */
    fun loadedPlugins(): List<PluginDescriptor> = registry.loadedPlugins()

    /** 已安装插件 id 列表。 */
    fun installedPlugins(): List<String> = installer.installedIds()

    /** 加载全部已安装插件（应用启动时恢复会话；单个失败跳过不影响其它）。 */
    fun loadAllInstalled() {
        for (id in installer.installedIds()) {
            runCatching { load(id) }
        }
    }

    /** 查询已安装插件的描述（名称/类别/版本等），未安装返回 null。 */
    fun pluginDescriptor(pluginId: String): PluginDescriptor? = installer.descriptorFor(pluginId)

    // ==================== 事件驱动（共享运行时，1） ====================

    /** 广播事件：由共享运行时内的插件按导出函数名响应（协程串行调度）。 */
    fun emit(event: PluginEvent) = executor.emit(event)

    // ==================== 高频直调（专用运行时，x） ====================

    /** 宿主快速调用插件导出函数（查表直调，不进入 Lua 符号查找）。 */
    fun invoke(pluginId: String, functionName: String, args: Varargs): Varargs? =
        exported.invoke(pluginId, functionName, args)

    /** 查询插件导出函数名集合。 */
    fun exportedNames(pluginId: String): Set<String> = exported.names(pluginId)

    // ==================== 播放器事件桥接 ====================

    private fun bridgePlayerEvents() {
        var lastTrackId = -1L
        var lastState = ""
        scope.launch {
            core.playerState.collect { state ->
                val track = state.currentTrack
                if (track != null && track.id != lastTrackId) {
                    lastTrackId = track.id
                    dispatchToAll("trackChanged", LuaValues.toTrackTable(track))
                }
                val stateName = state.state.name.lowercase()
                if (stateName != lastState) {
                    lastState = stateName
                    dispatchToAll("playStateChanged", LuaValues.toLuaValue(stateName))
                }
            }
        }
        scope.launch {
            core.progressState.collect { p ->
                if (p.progress > 0L || p.duration > 0L) {
                    dispatchToAll("progressUpdated", LuaValues.toLuaValue(
                        mapOf("position" to p.progress, "duration" to p.duration)
                    ))
                }
            }
        }
    }

    private fun dispatchToAll(event: String, data: LuaValue) {
        for (session in sessions.values) {
            session.runtime.withGlobals { globals ->
                session.eventBus.dispatchExternal(event, data, globals)
            }
        }
    }

    // ==================== 生命周期 / 诊断 ====================

    /** 释放全部资源：调用 onUnload、释放定时器/订阅、取消作用域。 */
    fun close() {
        for (session in sessions.values) {
            session.callGlobalFunction("onUnload")
            session.close()
        }
        sessions.clear()
        runtimes.releaseAll()
        scope.cancel()
    }

    /** 清理孤儿字节码缓存。 */
    fun pruneBytecode() = installer.pruneBytecode()

    /** 诊断：专用运行时数量（x）、共享运行时、已加载插件与导出函数数。 */
    fun diagnostics(): String =
        "dedicated runtimes(x) = ${runtimes.dedicatedCount}, " +
            "shared alive = ${runtimes.sharedRuntime.isAlive}, " +
            "loaded plugins = ${registry.size}, " +
            "exported functions = ${exported.size}"
}
