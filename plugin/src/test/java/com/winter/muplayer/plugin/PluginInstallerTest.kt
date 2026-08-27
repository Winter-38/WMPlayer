package com.winter.muplayer.plugin

import com.winter.muplayer.plugin.install.PluginInstaller
import com.winter.muplayer.plugin.model.PluginDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 验证安装流程：zip 包 → manifest 校验 → 解压落盘 → 描述符 → 卸载清理。
 */
class PluginInstallerTest {

    private fun tmpDir(): File = File.createTempFile("plugin-install-test", "").apply {
        delete()
        mkdirs()
    }

    private fun createPluginZip(file: File, extraEntry: String? = null) {
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("plugin.json"))
            zip.write(
                """
                {
                  "id": "test.lyrics",
                  "name": "Lyrics",
                  "version": "1.2.0",
                  "entry": "main.lua",
                  "runtime": "shared",
                  "events": ["onTrackChanged"]
                }
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("main.lua"))
            zip.write("return { onTrackChanged = function() end }".toByteArray())
            zip.closeEntry()

            if (extraEntry != null) {
                zip.putNextEntry(ZipEntry(extraEntry))
                zip.write("x".toByteArray())
                zip.closeEntry()
            }
        }
    }

    @Test
    fun `安装 zip 生成描述符并落盘`() {
        val root = tmpDir()
        val config = PluginConfig(
            pluginsDir = File(root, "plugins"),
            bytecodeCacheDir = File(root, ".bytecode"),
            cacheDir = File(root, "downloads")
        )
        val zip = File(root, "test-lyrics.zip")
        createPluginZip(zip)

        val installer = PluginInstaller(config)
        val descriptor: PluginDescriptor = installer.installZip(zip)

        assertEquals("test.lyrics", descriptor.id)
        assertEquals(RuntimeKind.SHARED, descriptor.runtime)
        assertEquals(listOf("onTrackChanged"), descriptor.events)
        assertTrue("入口应已解压", File(descriptor.dir, "main.lua").exists())

        assertEquals(listOf("test.lyrics"), installer.installedIds())
        assertNotNull(installer.descriptorFor("test.lyrics"))
        assertNull(installer.descriptorFor("not-exist"))
    }

    @Test
    fun `缺少 manifest 的包被拒绝`() {
        val root = tmpDir()
        val config = PluginConfig(
            pluginsDir = File(root, "plugins"),
            bytecodeCacheDir = File(root, ".bytecode"),
            cacheDir = File(root, "downloads")
        )
        val zip = File(root, "bad.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("readme.txt"))
            z.write("no manifest".toByteArray())
            z.closeEntry()
        }

        val installer = PluginInstaller(config)
        try {
            installer.installZip(zip)
            throw AssertionError("缺少 plugin.json 应抛异常")
        } catch (e: PluginException) {
            assertTrue(e.message!!.contains("plugin.json"))
        }
    }

    @Test
    fun `Zip Slip 路径穿越被拦截`() {
        val root = tmpDir()
        val config = PluginConfig(
            pluginsDir = File(root, "plugins"),
            bytecodeCacheDir = File(root, ".bytecode"),
            cacheDir = File(root, "downloads")
        )
        val zip = File(root, "evil.zip")
        ZipOutputStream(zip.outputStream()).use { z ->
            z.putNextEntry(ZipEntry("plugin.json"))
            z.write("""{"id":"evil","entry":"main.lua"}""".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("main.lua"))
            z.write("return {}".toByteArray())
            z.closeEntry()
            z.putNextEntry(ZipEntry("../escape.lua"))
            z.write("evil".toByteArray())
            z.closeEntry()
        }

        val installer = PluginInstaller(config)
        val descriptor = installer.installZip(zip)
        // 穿越条目被忽略：插件目录内与根目录下都不应出现越界文件
        assertTrue(!File(root, "escape.lua").exists())
        assertTrue(!File(descriptor.dir, "escape.lua").exists())
    }

    @Test
    fun `installedIds 过滤隐藏缓存目录`() {
        val root = tmpDir()
        val config = PluginConfig(
            pluginsDir = File(root, "plugins"),
            bytecodeCacheDir = File(root, "plugins/.bytecode"),
            cacheDir = File(root, "downloads")
        )
        // 模拟插件目录 + 内部缓存目录
        File(config.pluginsDir, "test.lyrics").mkdirs()
        File(config.pluginsDir, ".bytecode").mkdirs()
        File(config.pluginsDir, ".tmp-x").mkdirs()

        val installer = PluginInstaller(config)
        assertEquals("隐藏目录不应作为插件列出", listOf("test.lyrics"), installer.installedIds())
    }

    @Test
    fun `卸载删除目录与缓存`() {
        val root = tmpDir()
        val config = PluginConfig(
            pluginsDir = File(root, "plugins"),
            bytecodeCacheDir = File(root, ".bytecode"),
            cacheDir = File(root, "downloads")
        )
        val zip = File(root, "test-lyrics.zip")
        createPluginZip(zip)

        val installer = PluginInstaller(config)
        val descriptor = installer.installZip(zip)
        assertTrue(File(config.pluginsDir, "test.lyrics").exists())

        installer.uninstall(descriptor)
        assertTrue(!File(config.pluginsDir, "test.lyrics").exists())
        assertEquals(emptyList<String>(), installer.installedIds())
    }

    @Test
    fun `uninstallById 不依赖 manifest 解析，直接删除目录`() {
        val root = tmpDir()
        val config = PluginConfig(
            pluginsDir = File(root, "plugins"),
            bytecodeCacheDir = File(root, ".bytecode"),
            cacheDir = File(root, "downloads")
        )
        val zip = File(root, "test-lyrics.zip")
        createPluginZip(zip)

        val installer = PluginInstaller(config)
        installer.installZip(zip)
        assertTrue(File(config.pluginsDir, "test.lyrics").exists())

        // 即使 plugin.json 被破坏（manifest 解析失败），卸载仍应删除目录
        File(config.pluginsDir, "test.lyrics/plugin.json").writeText("{ broken json")
        installer.uninstallById("test.lyrics")

        assertTrue("目录应被删除", !File(config.pluginsDir, "test.lyrics").exists())
        assertEquals(emptyList<String>(), installer.installedIds())
    }
}
