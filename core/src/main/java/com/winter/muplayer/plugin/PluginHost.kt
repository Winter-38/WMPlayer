package com.winter.muplayer.plugin

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.winter.muplayer.model.PlayMode
import com.winter.muplayer.model.PlayerState
import com.winter.muplayer.model.PlayerStateData
import com.winter.muplayer.model.Track
import com.winter.muplayer.plugin_api.PluginContract
import com.winter.muplayer.plugin_api.SlotWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * 插件宿主核心 — 通过 ContentProvider 发现和管理插件。
 *
 * ## 职责
 * 1. 扫描已安装的 APK，发现 authority 以 `.musicplugin` 结尾的 ContentProvider
 * 2. 调用 `get_metadata` 获取插件信息并校验权限声明
 * 3. 向所有已加载插件广播播放器事件
 * 4. 处理插件通过返回值发起的控制请求（play/pause/next 等）
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

        // 方案：遍历已安装 Package 的 Provider
        // 兼容 API 30+ 的包可见性限制
        val packages = try {
            pm.getInstalledPackages(PackageManager.GET_PROVIDERS)
        } catch (e: Exception) {
            Log.w(PluginContract.TAG, "getInstalledPackages failed", e)
            return result
        }

        for (pkg in packages) {
            val providers = pkg.providers ?: continue
            for (provider in providers) {
                val authority = provider.authority ?: continue
                if (!authority.endsWith(PluginContract.AUTHORITY_SUFFIX)) continue

                val meta = queryPluginMetadata(provider.authority)
                if (meta == null) {
                    Log.w(PluginContract.TAG,
                        "Plugin provider $authority failed get_metadata, skip")
                    continue
                }

                val plugin = PluginInfo(
                    id = meta.getString(PluginContract.KEY_PLUGIN_ID, ""),
                    name = meta.getString(PluginContract.KEY_PLUGIN_NAME, "未知"),
                    type = meta.getString(PluginContract.KEY_PLUGIN_TYPE, "unknown"),
                    version = meta.getString(PluginContract.KEY_PLUGIN_VERSION, "1.0"),
                    packageName = provider.packageName,
                    authority = authority,
                    permissions = meta.getStringArrayList(PluginContract.KEY_PERMISSIONS)
                        ?.toSet() ?: emptySet()
                )

                if (plugin.id.isBlank()) {
                    Log.w(PluginContract.TAG,
                        "Plugin $authority has empty id, skip")
                    continue
                }

                result.add(plugin)
            }
        }

        return result
    }

    /**
     * 扫描并加载所有插件。
     * 调用 [discover] 后对每个插件发送 [PluginContract.METHOD_ON_LOAD]。
     */
    fun loadAll() {
        val discovered = discover()
        _plugins.clear()
        _plugins.addAll(discovered)
        Log.i(PluginContract.TAG, "Discovered ${discovered.size} plugin(s)")

        for (plugin in _plugins) {
            sendSync(plugin, PluginContract.METHOD_ON_LOAD, Bundle())
        }
    }

    /**
     * 卸载所有插件（发送 [PluginContract.METHOD_ON_UNLOAD] 并清空列表）。
     */
    fun unloadAll() {
        for (plugin in _plugins) {
            sendSync(plugin, PluginContract.METHOD_ON_UNLOAD, Bundle())
        }
        _plugins.clear()
        Log.i(PluginContract.TAG, "All plugins unloaded")
    }

    // ==================== 事件广播 ====================

    /**
     * 向所有已加载插件广播事件（在 IO 线程执行，不阻塞调用方）。
     *
     * @param method  事件方法名，如 [PluginContract.METHOD_ON_STATE_CHANGE]
     * @param extras  附加参数 Bundle
     */
    suspend fun broadcast(method: String, extras: Bundle = Bundle()) =
            withContext(Dispatchers.IO) {
        for (plugin in _plugins) {
            try {
                val response = sendSync(plugin, method, extras)
                handlePluginResponse(plugin, response)
            } catch (e: Exception) {
                Log.w(PluginContract.TAG,
                    "Plugin ${plugin.id} $method failed", e)
            }
        }
    }

    // ==================== UI Slot ====================

    /**
     * 从所有已加载插件拉取 [PluginContract.METHOD_GET_SLOT] 内容，
     * 返回按 slot 名称分组的 [SlotWidget] 列表。
     */
    suspend fun refreshSlots() = withContext(Dispatchers.IO) {
        _slotWidgets.clear()
        for (plugin in _plugins) {
            try {
                val response = sendSync(plugin, PluginContract.METHOD_GET_SLOT, Bundle())
                if (response == null) continue
                val slotName = response.getString(PluginContract.KEY_SLOT_NAME, "")
                val widgetsJson = response.getString(PluginContract.KEY_WIDGETS_JSON, "")
                if (slotName.isBlank() || widgetsJson.isBlank()) continue

                val array = JSONArray(widgetsJson)
                val list = _slotWidgets.getOrPut(slotName) { mutableListOf() }
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    list.add(SlotWidget(
                        type = obj.optString(PluginContract.WF_TYPE, ""),
                        content = obj.optString(PluginContract.WF_CONTENT, ""),
                        align = obj.optString(PluginContract.WF_ALIGN, "center"),
                        size = obj.optString(PluginContract.WF_SIZE, "medium"),
                        color = obj.optString(PluginContract.WF_COLOR, "#FFFFFF"),
                        url = obj.optString(PluginContract.WF_URL, ""),
                        action = obj.optString(PluginContract.WF_ACTION, "")
                    ))
                }
            } catch (e: Exception) {
                Log.w(PluginContract.TAG, "Plugin ${plugin.id} get_slot failed", e)
            }
        }
    }

    // ==================== 私有方法 ====================

    private fun queryPluginMetadata(authority: String): Bundle? {
        return try {
            val uri = Uri.parse("content://$authority")
            context.contentResolver.call(
                uri, PluginContract.METHOD_GET_METADATA, null, null
            )
        } catch (e: Exception) {
            Log.w(PluginContract.TAG,
                "Failed to query metadata from $authority", e)
            null
        }
    }

    private fun sendSync(plugin: PluginInfo, method: String, extras: Bundle): Bundle? {
        val uri = Uri.parse("content://${plugin.authority}")
        return context.contentResolver.call(uri, method, null, extras)
    }

    private fun handlePluginResponse(plugin: PluginInfo, response: Bundle?) {
        val action = response?.getString(PluginContract.KEY_REQUEST_ACTION) ?: return
        // 权限校验：只有声明了 playback_control 的插件才能控制播放
        if (PluginContract.PERMISSION_PLAYBACK_CONTROL in plugin.permissions
            || plugin.permissions.isEmpty()) {
            onAction?.invoke(action, response)
        } else {
            Log.w(PluginContract.TAG,
                "Plugin ${plugin.id} lacks playback_control permission, " +
                "ignoring action=$action")
        }
    }
}
