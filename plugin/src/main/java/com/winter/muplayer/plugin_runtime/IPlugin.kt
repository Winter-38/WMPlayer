package com.winter.muplayer.plugin_runtime

import android.content.Context
import android.view.View

/**
 * 插件生命周期接口。
 *
 * Shadow 架构中每个插件 APK 必须提供一个实现此接口的入口类。
 * 入口类的全限定名在插件 APK 的 AndroidManifest.xml 中通过
 * meta-data（key="shadow-plugin-entry"）声明。
 *
 * 宿主通过 [com.winter.muplayer.plugin_loader.PluginLoader]
 * 动态加载插件 APK，使用 DexClassLoader 加载入口类并实例化。
 *
 * 生命周期：
 * ```
 * onCreate → onStart → onResume → onPause → onStop → onDestroy
 * ```
 */
interface IPlugin {

    /**
     * 插件被加载时调用。
     * 在此方法中完成初始化：解析配置、准备资源、注册监听器等。
     *
     * @param context 插件上下文（插件 APK 内的 Context）
     * @param host 宿主 API 接口，插件可通过它读取状态和控制播放
     */
    fun onCreate(context: Context, host: IPlayerHost)

    /** 插件即将对用户可见 */
    fun onStart()

    /** 插件获得焦点，开始活跃 */
    fun onResume()

    /** 插件失去焦点 */
    fun onPause()

    /** 插件不再可见 */
    fun onStop()

    /** 插件被卸载/销毁，释放所有资源 */
    fun onDestroy()

    /**
     * 获取插件的 UI 组件 View。
     * 宿主会将此 View 嵌入到指定的 Slot 位置。
     *
     * @param slotName Slot 名称，如 "below_controls"、"above_queue"、"player_background"
     * @return 插件的 View，如果该 Slot 没有内容返回 null
     */
    fun getSlotView(slotName: String): View?

    /**
     * 获取插件元数据。
     */
    fun getMetadata(): PluginMetadata
}

/**
 * 插件元数据。
 * 对应旧架构中 ContentProvider 的 get_metadata 返回值。
 */
data class PluginMetadata(
    /** 插件唯一 ID */
    val id: String,
    /** 插件显示名称 */
    val name: String,
    /** 插件类型：lyrics / visualizer / recommend / custom */
    val type: String,
    /** 版本号 */
    val version: String,
    /** 所需权限：read_state / read_queue / playback_control / write_queue */
    val permissions: Set<String> = emptySet()
)
