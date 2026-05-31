package com.winter.muplayer.plugin_api

import android.content.Context

const val PLUGIN_TAG = "MusicPlugin"

interface MusicPlugin {
    val id: String
    val name: String
    val type: String

    /**
     * 插件被加载时调用。
     * @param context Android Context，用于资源访问
     * @param hostApi 宿主提供的能力接口，插件可通过它与播放器核心交互
     */
    fun onLoad(context: Context, hostApi: HostApi)

    /** 插件被卸载时调用，应释放所有资源 */
    fun onUnload()
}