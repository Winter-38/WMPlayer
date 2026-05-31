package com.winter.muplayer.plugin

import android.content.Context
import android.util.Log
import com.winter.muplayer.core.MusicPlayerCore
import com.winter.muplayer.plugin_api.MusicPlugin
import com.winter.muplayer.plugin_api.PLUGIN_TAG
import dalvik.system.DexClassLoader
import org.json.JSONObject
import java.io.File

object PluginLoader {

    private const val CURRENT_API_VERSION = 1

    data class PluginMetaData(
        val id: String,
        val name: String,
        val version: String,
        val entryClass: String,
        val minApiVersion: Int = 1,
        val type: String = "unknown"
    )

    fun loadPlugins(context: Context) {
        val pluginDir = PluginFileManager.getPluginDir(context)
        if (!pluginDir.exists()) {
            Log.d(PLUGIN_TAG, "Plugin directory does not exist, skip loading.")
            return
        }

        val dexFiles = PluginFileManager.listPluginFiles(context)
        if (dexFiles.isEmpty()) {
            Log.d(PLUGIN_TAG, "No plugin dex files found.")
            return
        }

        for (dexFile in dexFiles) {
            loadPluginFromDex(context, dexFile)
        }
    }

    private fun loadPluginFromDex(context: Context, dexFile: File) {
        val jsonFile = File(dexFile.parent, dexFile.nameWithoutExtension + ".json")
        if (!jsonFile.exists()) {
            Log.w(PLUGIN_TAG, "Missing metadata JSON for ${dexFile.name}, skipping.")
            return
        }

        val metadata: PluginMetaData
        try {
            val jsonStr = jsonFile.readText()
            val jsonObj = JSONObject(jsonStr)
            metadata = PluginMetaData(
                id = jsonObj.getString("id"),
                name = jsonObj.optString("name", "unknown"),
                version = jsonObj.optString("version", "1.0"),
                entryClass = jsonObj.getString("entryClass"),
                minApiVersion = jsonObj.optInt("minApiVersion", 1),
                type = jsonObj.optString("type", "unknown")
            )
        } catch (e: Exception) {
            Log.e(PLUGIN_TAG, "Failed to parse metadata for ${dexFile.name}", e)
            return
        }

        if (dexFile.length() == 0L) {
            Log.e(PLUGIN_TAG, "Plugin dex file is empty: ${dexFile.name}, skipping.")
            return
        }

        if (metadata.minApiVersion > CURRENT_API_VERSION) {
            Log.w(PLUGIN_TAG, "Plugin ${metadata.id} requires minApiVersion ${metadata.minApiVersion}, current is $CURRENT_API_VERSION, skipping.")
            return
        }

        try {
            val optimizedDir = context.getDir("plugin_dex", Context.MODE_PRIVATE)
            val classLoader = DexClassLoader(
                dexFile.absolutePath,
                optimizedDir.absolutePath,
                null,
                context.classLoader
            )
            val clazz = classLoader.loadClass(metadata.entryClass)
            val instance = clazz.getDeclaredConstructor().newInstance()
            if (instance !is MusicPlugin) {
                Log.e(PLUGIN_TAG, "Plugin entry class does not implement MusicPlugin: ${metadata.entryClass}")
                return
            }
            // 传入 Context 和 HostApi
            val core = MusicPlayerCore.getInstance(context)
            instance.onLoad(context, CoreHostApi(core))
            PluginManager.register(instance, dexFile.name)
            Log.i(PLUGIN_TAG, "Plugin loaded successfully: ${metadata.id} (${metadata.name})")
        } catch (e: Exception) {
            Log.e(PLUGIN_TAG, "Failed to load plugin: ${dexFile.name}", e)
        }
    }


    fun unloadAllPlugins() {
        PluginManager.clear()
    }
}