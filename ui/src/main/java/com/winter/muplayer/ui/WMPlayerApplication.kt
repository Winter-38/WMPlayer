package com.winter.muplayer.ui

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import com.winter.muplayer.core.CrashLogManager
import java.io.File

/**
 * 应用入口：最早时机安装全局崩溃日志处理器，并初始化插件系统宿主。
 * 崩溃堆栈写入 {外部存储}/Android/data/com.winter.muplayer/files/logs/。
 */
class WMPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogManager.install(this)

        // 限制 Coil 磁盘缓存容量：默认上限为内部存储 2%（可达数百 MB），
        // 而封面已有本地文件缓存（album_covers_hi），Coil 磁盘缓存仅作解码结果加速，
        // 64MB 足以覆盖常用封面 / 缩略图的解码加速，避免无上限占盘。
        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .diskCache {
                    DiskCache.Builder()
                        .directory(File(cacheDir, "image_cache"))
                        .maxSizeBytes(64L * 1024 * 1024)
                        .build()
                }
                .build()
        )

        PluginHost.init(this)
    }
}
