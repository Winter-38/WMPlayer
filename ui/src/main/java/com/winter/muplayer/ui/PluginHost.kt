package com.winter.muplayer.ui

import android.content.Context
import com.winter.muplayer.plugin.LuaPluginManager

/**
 * 插件系统宿主：应用级单例持有 [LuaPluginManager]。
 *
 * 在 [WMPlayerApplication.onCreate] 中初始化；UI 各处通过 [get] 获取。
 * 初始化即开始桥接播放器事件（trackChanged / playStateChanged / progressUpdated）。
 */
object PluginHost {

    @Volatile
    private var manager: LuaPluginManager? = null

    /** 初始化（幂等），由 Application.onCreate 调用。 */
    fun init(context: Context) {
        if (manager == null) {
            synchronized(this) {
                if (manager == null) {
                    manager = LuaPluginManager(
                        context.applicationContext,
                        uiBridge = PluginUiHost,
                    ).also {
                        PluginUiHost.attach(it)
                        // 启动即恢复：自动加载全部已安装插件（列表出现 = 已加载）
                        it.loadAllInstalled()
                    }
                }
            }
        }
    }

    /** 获取插件管理器（未初始化则惰性初始化）。 */
    fun get(context: Context): LuaPluginManager {
        init(context)
        return manager ?: error("PluginHost init failed")
    }

    /** 是否已初始化。 */
    fun isInitialized(): Boolean = manager != null
}
