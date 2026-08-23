package com.winter.muplayer.ui

import android.app.Application
import com.winter.muplayer.core.CrashLogManager

/**
 * 应用入口：最早时机安装全局崩溃日志处理器，
 * 崩溃堆栈写入 {外部存储}/Android/data/com.winter.muplayer/files/logs/。
 */
class WMPlayerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashLogManager.install(this)
    }
}
