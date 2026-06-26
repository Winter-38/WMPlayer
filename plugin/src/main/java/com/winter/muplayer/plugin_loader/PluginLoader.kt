package com.winter.muplayer.plugin_loader

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.util.Log
import com.winter.muplayer.plugin_runtime.IPlugin
import dalvik.system.DexClassLoader
import java.io.File

private const val TAG = "Shadow-Loader"

/**
 * 插件加载器 —— Shadow 架构中的 Loader 层。
 *
 * ## 职责
 * 1. 使用 [DexClassLoader] 动态加载插件 APK 中的代码
 * 2. 通过预定义的 [IPlugin] 接口实例化插件入口类（零反射）
 * 3. 管理插件资源（[Resources]）和类加载器（[ClassLoader]）
 * 4. 处理插件的加载/卸载生命周期
 *
 * @param context 宿主 Context
 */
class PluginLoader(private val context: Context) {

    data class LoadedPlugin(
        val plugin: IPlugin,
        val classLoader: DexClassLoader,
        val apkPath: String
    )

    private val _loadedPlugins = mutableMapOf<String, LoadedPlugin>()
    val loadedPlugins: Map<String, LoadedPlugin> get() = _loadedPlugins

    /**
     * 加载一个插件 APK。
     *
     * 1. 创建独立的 [DexClassLoader]
     * 2. 读取插件 APK 的 AndroidManifest，获取入口类名
     * 3. 通过 [ClassLoader.loadClass] 加载入口类
     * 4. 强制转换为 [IPlugin] 接口（零反射）
     * 5. 调用 [IPlugin.onCreate] 初始化插件
     */
    fun load(apkPath: String, host: com.winter.muplayer.plugin_runtime.IPlayerHost): IPlugin? {
        val pluginId = extractPluginId(apkPath) ?: run {
            Log.w(TAG, "load: failed to extract plugin id from $apkPath")
            return null
        }

        _loadedPlugins[pluginId]?.let {
            Log.i(TAG, "load: unloading existing plugin $pluginId")
            unload(pluginId)
        }

        return try {
            val optimizedDir = File(context.cacheDir, "plugin_dex/$pluginId").also { it.mkdirs() }
            val classLoader = DexClassLoader(
                apkPath, optimizedDir.absolutePath, null, context.classLoader
            )

            val entryClassName = getPluginEntryClass(apkPath)
                ?: "com.winter.muplayer.plugin.Entry"

            val clazz = classLoader.loadClass(entryClassName)
            val plugin = clazz.getDeclaredConstructor().newInstance() as? IPlugin
                ?: throw ClassCastException(
                    "Plugin entry class $entryClassName does not implement IPlugin"
                )

            val pluginContext = createPluginContext(context, apkPath, classLoader)
            plugin.onCreate(pluginContext, host)
            plugin.onStart()

            _loadedPlugins[pluginId] = LoadedPlugin(plugin, classLoader, apkPath)
            Log.i(TAG, "load: plugin $pluginId loaded successfully")
            plugin
        } catch (e: Exception) {
            Log.e(TAG, "load: failed to load plugin from $apkPath", e)
            null
        }
    }

    fun unload(pluginId: String) {
        val loaded = _loadedPlugins.remove(pluginId) ?: return
        try {
            loaded.plugin.onPause()
            loaded.plugin.onStop()
            loaded.plugin.onDestroy()
        } catch (e: Exception) {
            Log.w(TAG, "unload: error unloading plugin $pluginId", e)
        }
        File(context.cacheDir, "plugin_dex/$pluginId").deleteRecursively()
    }

    fun unloadAll() {
        _loadedPlugins.keys.toList().forEach { unload(it) }
    }

    private fun extractPluginId(apkPath: String): String? {
        val file = File(apkPath)
        return if (file.exists()) {
            "plugin_${file.nameWithoutExtension.hashCode().toUInt().toString(16)}"
        } else null
    }

    private fun getPluginEntryClass(apkPath: String): String? {
        return try {
            val info = context.packageManager.getPackageArchiveInfo(
                apkPath, PackageManager.GET_META_DATA
            ) ?: return null
            info.applicationInfo?.metaData?.getString("shadow-plugin-entry")
        } catch (e: Exception) {
            Log.w(TAG, "getPluginEntryClass: failed to parse manifest", e)
            null
        }
    }
}

/**
 * 为插件创建隔离的 Context（含独立 Resources + ClassLoader）。
 */
private fun createPluginContext(
    hostContext: Context,
    apkPath: String,
    classLoader: ClassLoader
): Context {
    val pluginResources: Resources = try {
        val am = AssetManager::class.java.getDeclaredConstructor().newInstance()
        AssetManager::class.java.getDeclaredMethod("addAssetPath", String::class.java)
            .invoke(am, apkPath)
        Resources(am, hostContext.resources.displayMetrics, hostContext.resources.configuration)
    } catch (e: Exception) {
        Log.w(TAG, "Failed to create plugin Resources, using host", e)
        hostContext.resources
    }

    return hostContext.createConfigurationContext(hostContext.resources.configuration).also {
        try {
            val resourcesField = it.javaClass.getDeclaredField("mResources")
            resourcesField.isAccessible = true
            resourcesField.set(it, pluginResources)
        } catch (_: Exception) { }
    }
}
