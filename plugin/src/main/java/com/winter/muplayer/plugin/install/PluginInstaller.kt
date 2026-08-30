package com.winter.muplayer.plugin.install

import com.winter.muplayer.plugin.PluginConfig
import com.winter.muplayer.plugin.PluginException
import com.winter.muplayer.plugin.bytecode.BytecodeCache
import com.winter.muplayer.plugin.model.PluginDescriptor
import com.winter.muplayer.plugin.model.PluginManifest
import java.io.File
import java.util.zip.ZipInputStream

/**
 * 插件安装器：后台下载 → 校验 manifest → 解压 → 原子落盘。
 *
 * 安装流程（调用方协程内）：
 * 1. [installFromUrl] 下载 zip（PluginDownloader，IO 协程）；
 * 2. 读取并校验 `plugin.json`（id 必填、entry 存在、无路径穿越）；
 * 3. 先解压到 `.tmp-{id}` 临时目录，成功后 rename 为正式目录 —— 失败不污染旧版本。
 */
class PluginInstaller(private val config: PluginConfig) {

    /** 从 URL 安装（后台协程中调用）。 */
    suspend fun installFromUrl(url: String): PluginDescriptor {
        val zip = PluginDownloader.download(url, config.cacheDir)
        return installZip(zip)
    }

    /** 从本地 zip 安装（也用于测试与本地导入）。 */
    fun installZip(zip: File): PluginDescriptor {
        if (!zip.exists()) throw PluginException("插件包不存在: $zip")
        val manifest = readManifest(zip)
        validateEntry(manifest, zip)

        config.pluginsDir.mkdirs()
        val targetDir = File(config.pluginsDir, manifest.id)
        val tmpDir = File(config.pluginsDir, ".tmp-${manifest.id}")
        tmpDir.deleteRecursively()
        unzip(zip, tmpDir, manifest)

        targetDir.deleteRecursively()
        if (!tmpDir.renameTo(targetDir)) {
            tmpDir.deleteRecursively()
            throw PluginException("插件目录落盘失败: ${manifest.id}")
        }
        // 安装成功即删除安装包（下载 / 导入的 zip 均为临时件，避免永久占盘）
        try { zip.delete() } catch (_: Exception) { }
        return manifest.toDescriptor(targetDir)
    }

    /** 读取已安装插件的描述（从插件目录内 plugin.json）。 */
    fun descriptorFor(pluginId: String): PluginDescriptor? {
        val dir = File(config.pluginsDir, pluginId)
        val manifestFile = File(dir, "plugin.json")
        if (!manifestFile.exists()) return null
        return try {
            PluginManifest.parse(manifestFile.readText()).toDescriptor(dir)
        } catch (e: Exception) {
            null
        }
    }

    /** 已安装插件 id 列表（过滤隐藏缓存目录如 .bytecode、.tmp-*）。 */
    fun installedIds(): List<String> =
        config.pluginsDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()

    /** 卸载插件：移除磁盘目录（按 id 直接删除，不依赖 manifest 解析）+ 清理字节码缓存。 */
    fun uninstallById(pluginId: String) {
        val dir = File(config.pluginsDir, pluginId)
        var removed = !dir.exists()
        if (!removed) {
            removed = dir.deleteRecursively()
            if (!removed) {
                // 文件句柄/瞬时占用等导致失败：短暂让步后重试一次
                try {
                    Thread.sleep(50)
                } catch (_: InterruptedException) {
                }
                removed = dir.deleteRecursively()
            }
        }
        if (!removed && dir.exists()) {
            android.util.Log.e("PluginInstaller", "卸载目录删除失败: $dir")
        }
        // 清理该插件可能遗留的孤儿字节码缓存
        pruneBytecode()
    }

    /** 卸载：删除插件目录并清理其字节码缓存。 */
    fun uninstall(descriptor: PluginDescriptor) {
        uninstallById(descriptor.id)
    }

    /** 清理孤儿字节码缓存。 */
    fun pruneBytecode() {
        val sources = installedIds().mapNotNull { descriptorFor(it) }
            .filter { it.entryFile.exists() }
            .map { it.entryFile.readText() }
            .toSet()
        BytecodeCache.prune(config, sources)
    }

    private fun readManifest(zip: File): PluginManifest {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "plugin.json") {
                    val text = zis.readBytes().toString(Charsets.UTF_8)
                    return PluginManifest.parse(text)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        throw PluginException("插件包缺少 plugin.json: $zip")
    }

    private fun validateEntry(manifest: PluginManifest, zip: File) {
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == manifest.entry) return
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        throw PluginException("插件入口不存在: ${manifest.entry}")
    }

    private fun unzip(zip: File, targetDir: File, manifest: PluginManifest) {
        targetDir.mkdirs()
        ZipInputStream(zip.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = safeName(entry.name)
                if (name != null) {
                    val dest = File(targetDir, name)
                    if (entry.isDirectory) {
                        // 目录条目：若同路径已有文件（zip 内文件/目录同名冲突）则先移除文件
                        if (dest.exists() && !dest.isDirectory) dest.delete()
                        dest.mkdirs()
                    } else {
                        // 文件条目：若同路径已是目录（如 zip 内含 main.json/ 目录导致）则先移除目录
                        if (dest.exists() && dest.isDirectory) dest.delete()
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { output -> zis.copyTo(output) }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    /** Zip Slip 防护：拒绝绝对路径与 `..` 穿越。 */
    private fun safeName(name: String): String? {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/")) return null
        if (normalized.split('/').any { it == ".." }) return null
        return normalized
    }
}
