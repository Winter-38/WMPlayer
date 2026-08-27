package com.winter.muplayer.ui

import android.app.Application
import com.winter.muplayer.core.CrashLogManager

/**
 * 应用入口：最早时机安装全局崩溃日志处理器，并初始化插件系统宿主。
 * 崩溃堆栈写入 {外部存储}/Android/data/com.winter.muplayer/files/logs/。
 */
class WMPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogManager.install(this)
        PluginHost.init(this)
    }
}
