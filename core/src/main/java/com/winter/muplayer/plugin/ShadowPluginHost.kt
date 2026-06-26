package com.winter.muplayer.plugin

import android.content.Context
import android.util.Log
import com.winter.muplayer.plugin_manager.PluginManager
import com.winter.muplayer.plugin_runtime.IPlayerHost
import com.winter.muplayer.plugin_runtime.IPlugin
import com.winter.muplayer.plugin_runtime.PluginMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

private const val TAG = "Shadow-Host"

/**
 * Shadow 插件宿主 —— 基于 Shadow 架构的插件管理中心。
 *
 * ## 与旧 PluginHost 的区别
 * - **旧**：通过 ContentProvider 扫描已安装 APK，使用 IPC 通信
 * - **新**：通过 [PluginManager] 管理插件 APK 的生命周期，使用 [IPlayerHost] 直接 API 调用
 *
 * ## Shadow 三层架构中的位置
 * ```
 * Host（MusicPlayerCore）
 *   └── ShadowPluginHost（本类）
 *         └── PluginManager（插件的安装/加载/卸载）
 *               └── PluginLoader（DexClassLoader 加载）
 *                     └── Plugin（功能实现）
 * ```
 *
 * @param context 宿主 Context
 * @param hostApi 播放器控制接口（由 MusicPlayerCore 实现）
 */
class ShadowPluginHost(
    context: Context,
    private val hostApi: IPlayerHost
) {
    /** 插件管理器——负责插件的存储、安装、加载、卸载 */
    private val pluginManager = PluginManager(context, hostApi)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ==================== 插件信息 ====================

    /** 已安装的插件信息 */
    val installedPlugins: StateFlow<List<com.winter.muplayer.plugin_manager.PluginInstallInfo>>
        get() = pluginManager.installedPlugins

    /** 已加载的插件实例 */
    val loadedPlugins: StateFlow<Map<String, IPlugin>>
        get() = pluginManager.loadedPlugins

    // ==================== 生命周期 ====================

    /** 启动时加载所有已安装的插件 */
    fun loadAll() {
        scope.launch {
            pluginManager.loadAll()
            Log.i(TAG, "loadAll: ${pluginManager.loadedPlugins.value.size} plugin(s) loaded")
        }
    }

    /** 卸载所有插件 */
    fun unloadAll() {
        scope.launch {
            pluginManager.unloadAll()
        }
    }

    // ==================== 安装 & 卸载 ====================

    /**
     * 安装一个插件 APK。
     *
     * @param apkUri 插件 APK 的 Uri（content:// 或 file://）
     * @param autoLoad 安装后是否自动加载
     */
    fun install(apkUri: android.net.Uri, autoLoad: Boolean = true) {
        scope.launch {
            val info = pluginManager.install(apkUri)
            if (info != null && autoLoad) {
                pluginManager.load(info.metadata.id)
            }
        }
    }

    /** 卸载插件（删除磁盘文件并卸载实例） */
    fun uninstall(pluginId: String) {
        scope.launch {
            pluginManager.uninstall(pluginId)
        }
    }

    // ==================== 加载 & 卸载 ====================

    /** 加载一个已安装的插件到内存 */
    fun load(pluginId: String) {
        scope.launch {
            pluginManager.load(pluginId)
        }
    }

    /** 从内存卸载插件（不删除磁盘文件） */
    fun unload(pluginId: String) {
        scope.launch {
            pluginManager.unload(pluginId)
        }
    }

    /** 刷新已安装插件列表 */
    fun refresh() {
        scope.launch {
            pluginManager.refreshInstalledList()
        }
    }

    // ==================== 插件 Slot 查询 ====================

    /**
     * 获取指定 Slot 位置的插件 View。
     *
     * @param slotName Slot 名称，如 "below_controls"、"above_queue"
     * @param pluginId 可选，限定到具体插件
     * @return 匹配的 View 列表
     */
    fun getSlotViews(slotName: String, pluginId: String? = null): List<android.view.View> {
        val result = mutableListOf<android.view.View>()
        for ((id, plugin) in pluginManager.loadedPlugins.value) {
            if (pluginId != null && id != pluginId) continue
            try {
                val view = plugin.getSlotView(slotName)
                if (view != null) result.add(view)
            } catch (e: Exception) {
                Log.w(TAG, "getSlotViews: plugin $id slot=$slotName error", e)
            }
        }
        return result
    }

    /**
     * 从插件获取 Widget 列表（用于 Compose 渲染）。
     *
     * 插件可以通过 [IPlugin.getSlotView] 返回一个 View，
     * 也可以返回元数据中的 Widget 描述。
     */
    fun getSlotWidgets(slotName: String): List<com.winter.muplayer.plugin_runtime.PluginWidget> {
        // 在当前实现中，插件通过 getSlotView 返回 View。
        // 如果未来需要 Compose-native Widget 描述，可在此扩展。
        return emptyList()
    }

    // ==================== 清理 ====================

    fun release() {
        unloadAll()
        pluginManager.release()
    }
}
