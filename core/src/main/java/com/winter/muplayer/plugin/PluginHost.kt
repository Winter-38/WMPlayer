package com.winter.muplayer.plugin

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import com.winter.muplayer.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// ==================== 常量内联（原 plugin-api 模块已废弃） ====================
private const val TAG = "WinterMuPlugin"
private const val AUTHORITY_SUFFIX = ".musicplugin"
private const val KEY_PLUGIN_ID = "plugin_id"
private const val KEY_PLUGIN_NAME = "plugin_name"
private const val KEY_PLUGIN_TYPE = "plugin_type"
private const val KEY_PLUGIN_VERSION = "plugin_version"
private const val KEY_PERMISSIONS = "permissions"
private const val METHOD_GET_METADATA = "get_metadata"
private const val METHOD_ON_LOAD = "on_load"
private const val METHOD_ON_UNLOAD = "on_unload"
private const val METHOD_GET_SLOT = "get_slot"
private const val KEY_SLOT_NAME = "slot"
private const val KEY_WIDGETS_JSON = "widgets_json"
private const val WF_TYPE = "type"
private const val WF_CONTENT = "content"
private const val WF_ALIGN = "align"
private const val WF_SIZE = "size"
private const val WF_COLOR = "color"
private const val WF_URL = "url"
private const val WF_ACTION = "action"
private const val KEY_REQUEST_ACTION = "request_action"
private const val PERMISSION_PLAYBACK_CONTROL = "playback_control"
private const val KEY_STATE_JSON = "state_json"
private const val KEY_TRACK_JSON = "track_json"
private const val KEY_PLAY_MODE = "play_mode"
private const val METHOD_ON_STATE_CHANGE = "on_state_change"
private const val METHOD_ON_TRACK_CHANGE = "on_track_change"
private const val METHOD_ON_PLAY_MODE_CHANGE = "on_play_mode_change"

/**
 * 插件 UI 小卡片（内联版，原 plugin-api 模块已废弃）。
 */
data class SlotWidget(
    val type: String,
    val content: String,
    val align: String = "center",
    val size: String = "medium",
    val color: String = "#FFFFFF",
    val url: String = "",
    val action: String = ""
)

/**
 * @deprecated 插件系统已重写为 Shadow 架构。
 * 请使用 [ShadowPluginHost] 替代。
 *
 * 旧的 ContentProvider IPC 方案已被 DexClassLoader 动态加载方案取代。
 * 此文件仅保留用于编译兼容，将在下个主版本中移除。
 *
 * ## 迁移指南
 * - `discover()` → [ShadowPluginHost.loadAll]
 * - `broadcast()` → 插件通过 [com.winter.muplayer.plugin_runtime.IPlayerHost] 直接访问
 * - `refreshSlots()` → [ShadowPluginHost.getSlotViews]
 */
class PluginHost(
    private val context: Context,
    private val onAction: ((action: String, params: Bundle) -> Unit)? = null
) {

    data class PluginInfo(
        val id: String,
        val name: String,
        val type: String,
        val version: String,
        val packageName: String,
        val authority: String,
        val permissions: Set<String>
    )

    private val _plugins = mutableListOf<PluginInfo>()
    val plugins: List<PluginInfo> get() = _plugins.toList()

    private val _slotWidgets = mutableMapOf<String, MutableList<SlotWidget>>()
    val slotWidgets: Map<String, List<SlotWidget>> get() = _slotWidgets

    // ==================== 发现 & 加载 ====================

    /**
     * 扫描已安装的 ContentProvider，筛选出符合插件协议的。
     */
    fun discover(): List<PluginInfo> {
        val result = mutableListOf<PluginInfo>()
        val pm = context.packageManager

        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_PROVIDERS)
        } catch (e: Exception) {
            Log.w(TAG, "getInstalledPackages failed", e)
            return result
        }

        for (pkg in packages) {
            val providers = pkg.providers ?: continue
            for (provider in providers) {
                val authority = provider.authority ?: continue
                if (!authority.endsWith(AUTHORITY_SUFFIX)) continue

                val meta = queryPluginMetadata(provider.authority)
                if (meta == null) {
                    Log.w(TAG, "Plugin provider $authority failed get_metadata, skip")
                    continue
                }

                val plugin = PluginInfo(
                    id = meta.getString(KEY_PLUGIN_ID, ""),
                    name = meta.getString(KEY_PLUGIN_NAME, "未知"),
                    type = meta.getString(KEY_PLUGIN_TYPE, "unknown"),
                    version = meta.getString(KEY_PLUGIN_VERSION, "1.0"),
                    packageName = provider.packageName,
                    authority = authority,
                    permissions = meta.getStringArrayList(KEY_PERMISSIONS)
                        ?.toSet() ?: emptySet()
                )

                if (plugin.id.isBlank()) {
                    Log.w(TAG, "Plugin $authority has empty id, skip")
                    continue
                }

                result.add(plugin)
            }
        }

        return result
    }

    /**
     * 扫描并加载所有插件。
     * 调用 [discover] 后对每个插件发送 [METHOD_ON_LOAD]。
     */
    fun loadAll() {
        val discovered = discover()
        _plugins.clear()
        _plugins.addAll(discovered)
        Log.i(TAG, "Discovered ${discovered.size} plugin(s)")

        for (plugin in _plugins) {
            sendSync(plugin, METHOD_ON_LOAD, Bundle())
        }
    }

    /**
     * 卸载所有插件（发送 [METHOD_ON_UNLOAD] 并清空列表）。
     */
    fun unloadAll() {
        for (plugin in _plugins) {
            sendSync(plugin, METHOD_ON_UNLOAD, Bundle())
        }
        _plugins.clear()
        Log.i(TAG, "All plugins unloaded")
    }

    // ==================== 事件广播 ====================

    /**
     * 向所有已加载插件广播事件（在 IO 线程执行，不阻塞调用方）。
     */
    suspend fun broadcast(method: String, extras: Bundle = Bundle()) =
            withContext(Dispatchers.IO) {
        for (plugin in _plugins) {
            try {
                val response = sendSync(plugin, method, extras)
                handlePluginResponse(plugin, response)
            } catch (e: Exception) {
                Log.w(TAG, "Plugin ${plugin.id} $method failed", e)
            }
        }
    }

    // ==================== UI Slot ====================

    /**
     * 从所有已加载插件拉取 [METHOD_GET_SLOT] 内容，
     * 返回按 slot 名称分组的 [SlotWidget] 列表。
     */
    suspend fun refreshSlots() = withContext(Dispatchers.IO) {
        _slotWidgets.clear()
        for (plugin in _plugins) {
            try {
                val response = sendSync(plugin, METHOD_GET_SLOT, Bundle())
                if (response == null) continue
                val slotName = response.getString(KEY_SLOT_NAME, "")
                val widgetsJson = response.getString(KEY_WIDGETS_JSON, "")
                if (slotName.isBlank() || widgetsJson.isBlank()) continue

                val array = JSONArray(widgetsJson)
                val list = _slotWidgets.getOrPut(slotName) { mutableListOf() }
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(SlotWidget(
                        type = obj.optString(WF_TYPE, ""),
                        content = obj.optString(WF_CONTENT, ""),
                        align = obj.optString(WF_ALIGN, "center"),
                        size = obj.optString(WF_SIZE, "medium"),
                        color = obj.optString(WF_COLOR, "#FFFFFF"),
                        url = obj.optString(WF_URL, ""),
                        action = obj.optString(WF_ACTION, "")
                    ))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Plugin ${plugin.id} get_slot failed", e)
            }
        }
    }

    // ==================== 私有方法 ====================

    private fun queryPluginMetadata(authority: String): Bundle? {
        return try {
            val uri = Uri.parse("content://$authority")
            context.contentResolver.call(
                uri, METHOD_GET_METADATA, null, null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query metadata from $authority", e)
            null
        }
    }

    private fun sendSync(plugin: PluginInfo, method: String, extras: Bundle): Bundle? {
        val uri = Uri.parse("content://${plugin.authority}")
        return context.contentResolver.call(uri, method, null, extras)
    }

    private fun handlePluginResponse(plugin: PluginInfo, response: Bundle?) {
        val action = response?.getString(KEY_REQUEST_ACTION) ?: return
        if (PERMISSION_PLAYBACK_CONTROL in plugin.permissions) {
            onAction?.invoke(action, response)
        } else {
            Log.w(TAG,
                "Plugin ${plugin.id} lacks playback_control permission, " +
                "ignoring action=$action")
        }
    }
}
