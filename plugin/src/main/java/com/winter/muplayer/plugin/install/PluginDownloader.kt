package com.winter.muplayer.plugin.install

import com.winter.muplayer.plugin.PluginException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * 插件包下载器（安装时后台下载）。
 *
 * 使用 OkHttp，超时与失败以 [PluginException] 上报，由调用方协程处理。
 */
object PluginDownloader {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * 下载插件 zip 到 [targetDir]，返回本地文件。
     *
     * 在 [Dispatchers.IO] 上执行（阻塞式 IO），由调用方协程挂起等待。
     */
    suspend fun download(url: String, targetDir: File): File = withContext(Dispatchers.IO) {
        targetDir.mkdirs()
        val fileName = url.substringAfterLast('/')
            .substringBefore('?')
            .ifBlank { "plugin.zip" }
        val target = File(targetDir, fileName)

        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw PluginException("插件下载失败: HTTP ${response.code} ($url)")
            }
            val body = response.body ?: throw PluginException("插件下载失败: 空响应体 ($url)")
            body.byteStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
        target
    }
}
