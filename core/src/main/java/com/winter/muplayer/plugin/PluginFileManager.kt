package com.winter.muplayer.plugin

import android.content.Context
import android.util.Log
import com.winter.muplayer.plugin_api.PLUGIN_TAG
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object PluginFileManager {

    private const val PLUGIN_DIR = "plugins"

    fun getPluginDir(context: Context): File {
        return File(context.filesDir, PLUGIN_DIR).also { if (!it.exists()) it.mkdirs() }
    }

    /**
     * 将外部文件复制到插件目录，保留原始文件名。
     * 调用者应确保传入的文件具有 .dex 或 .json 扩展名。
     */
    fun copyPluginFile(context: Context, sourceFile: File): Boolean {
        return try {
            val destDir = getPluginDir(context)
            val destFile = File(destDir, sourceFile.name)
            FileInputStream(sourceFile).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(PLUGIN_TAG, "Copied plugin file: ${destFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(PLUGIN_TAG, "Failed to copy plugin file", e)
            false
        }
    }

    /**
     * 删除指定文件名的 dex，同时删除对应的同名 json 元数据文件。
     * @param fileName 文件名，如 "myplugin.dex"
     */
    fun deletePluginFile(context: Context, fileName: String): Boolean {
        val file = File(getPluginDir(context), fileName)
        val deleted = file.delete()
        if (deleted) {
            // 修正：使用 File 的 nameWithoutExtension 生成 json 文件名，避免多个 .dex 后缀错误替换
            val jsonFile = File(getPluginDir(context), File(fileName).nameWithoutExtension + ".json")
            jsonFile.delete()
            Log.d(PLUGIN_TAG, "Deleted plugin file: $fileName (and JSON if present)")
        } else {
            Log.e(PLUGIN_TAG, "Failed to delete plugin file: $fileName")
        }
        return deleted
    }

    fun listPluginFiles(context: Context): List<File> {
        return getPluginDir(context).listFiles()?.filter { it.extension == "dex" } ?: emptyList()
    }
}