package com.winter.muplayer.plugin_manager

import android.content.Context
import android.net.Uri
import android.util.Log
import com.winter.muplayer.plugin_loader.PluginLoader
import com.winter.muplayer.plugin_runtime.IPlayerHost
import com.winter.muplayer.plugin_runtime.IPlugin
import com.winter.muplayer.plugin_runtime.PluginMetadata
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.FileOutputStream

private const val TAG = "Shadow-Manager"

/**
 * 插件管理器 —— Shadow 架构中的 Manager 层。
 *
 * 负责插件的存储、安装/卸载、加载/卸载生命周期统筹。
 * 宿主通过 Manager 管理插件，不直接操作 Loader。
 *
 * @param context 宿主 Context
 * @param host 播放器控制接口
 */
class PluginManager(
    private val context: Context,
    private val host: IPlayerHost
) {
    private val pluginDir = File(context.filesDir, "shadow_plugins").also {
        if (!it.exists()) it.mkdirs()
    }

    private val downloadDir = File(context.cacheDir, "plugin_downloads").also {
        if (!it.exists()) it.mkdirs()
    }

    private val loader = PluginLoader(context)
    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _installedPlugins = MutableStateFlow<List<PluginInstallInfo>>(emptyList())
    val installedPlugins: StateFlow<List<PluginInstallInfo>> = _installedPlugins.asStateFlow()

    private val _loadedPlugins = MutableStateFlow<Map<String, IPlugin>>(emptyMap())
    val loadedPlugins: StateFlow<Map<String, IPlugin>> = _loadedPlugins.asStateFlow()

    init {
        loadInstalledListSync()
    }

    // ==================== 安装 & 卸载 ====================

    suspend fun install(apkUri: Uri): PluginInstallInfo? = mutex.withLock {
        try {
            val tempFile = File(downloadDir, "install_${System.currentTimeMillis()}.apk")
            context.contentResolver.openInputStream(apkUri)?.use { input ->
                FileOutputStream(tempFile).use { output -> input.copyTo(output) }
            } ?: return@withLock null

            val info = extractPluginInfo(tempFile) ?: return@withLock null.also { tempFile.delete() }

            val targetFile = File(pluginDir, "${info.id}.apk")
            tempFile.copyTo(targetFile, overwrite = true)
            tempFile.delete()

            val installInfo = PluginInstallInfo(
                metadata = info, apkPath = targetFile.absolutePath,
                installTime = System.currentTimeMillis()
            )
            _installedPlugins.update { list ->
                list.filter { it.metadata.id != info.id } + installInfo
            }
            Log.i(TAG, "install: plugin ${info.id} (${info.name}) installed")
            installInfo
        } catch (e: Exception) {
            Log.e(TAG, "install: failed", e); null
        }
    }

    suspend fun installFile(apkFile: File): PluginInstallInfo? =
        if (apkFile.exists()) install(Uri.fromFile(apkFile)) else null

    suspend fun uninstall(pluginId: String) = mutex.withLock {
        loader.unload(pluginId)
        _loadedPlugins.update { it - pluginId }
        File(pluginDir, "$pluginId.apk").delete()
        _installedPlugins.update { list -> list.filter { it.metadata.id != pluginId } }
        Log.i(TAG, "uninstall: plugin $pluginId removed")
    }

    // ==================== 加载 & 卸载 ====================

    suspend fun load(pluginId: String): IPlugin? = mutex.withLock {
        val info = _installedPlugins.value.find { it.metadata.id == pluginId }
            ?: return@withLock null.also { Log.w(TAG, "load: plugin $pluginId not installed") }
        val plugin = loader.load(info.apkPath, host)
        if (plugin != null) _loadedPlugins.update { it + (pluginId to plugin) }
        plugin
    }

    suspend fun unload(pluginId: String) = mutex.withLock {
        loader.unload(pluginId)
        _loadedPlugins.update { it - pluginId }
    }

    suspend fun loadAll() = mutex.withLock {
        for (info in _installedPlugins.value) {
            val plugin = loader.load(info.apkPath, host)
            if (plugin != null) _loadedPlugins.update { it + (info.metadata.id to plugin) }
        }
    }

    suspend fun unloadAll() = mutex.withLock {
        loader.unloadAll()
        _loadedPlugins.value = emptyMap()
    }

    // ==================== 工具方法 ====================

    suspend fun refreshInstalledList() = mutex.withLock { loadInstalledListSync() }

    private fun loadInstalledListSync() {
        val apkFiles = pluginDir.listFiles { f -> f.extension == "apk" } ?: emptyArray()
        _installedPlugins.value = apkFiles.mapNotNull { file ->
            extractPluginInfo(file)?.let { meta ->
                PluginInstallInfo(meta, file.absolutePath, file.lastModified())
            }
        }
    }

    private fun extractPluginInfo(apkFile: File): PluginMetadata? {
        return try {
            val info = context.packageManager.getPackageArchiveInfo(
                apkFile.absolutePath, android.content.pm.PackageManager.GET_META_DATA
            ) ?: return null
            val meta = info.applicationInfo?.metaData ?: return null
            val id = meta.getString("plugin-id") ?: info.packageName ?: apkFile.nameWithoutExtension
            val name = meta.getString("plugin-name")
                ?: info.applicationInfo?.loadLabel(context.packageManager)?.toString() ?: id
            val type = meta.getString("plugin-type") ?: "custom"
            val version = meta.getString("plugin-version") ?: "1.0"
            val permStr = meta.getString("plugin-permissions") ?: ""
            val permissions = permStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            PluginMetadata(id, name, type, version, permissions)
        } catch (e: Exception) {
            Log.w(TAG, "extractPluginInfo: failed for ${apkFile.name}", e); null
        }
    }

    fun release() {
        scope.cancel()
        kotlinx.coroutines.runBlocking { unloadAll() }
    }
}

data class PluginInstallInfo(
    val metadata: PluginMetadata,
    val apkPath: String,
    val installTime: Long
)
