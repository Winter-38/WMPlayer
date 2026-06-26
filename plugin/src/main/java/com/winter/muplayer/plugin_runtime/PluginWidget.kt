package com.winter.muplayer.plugin_runtime

/**
 * 插件 UI Widget 描述（纯数据，不依赖 Android View）。
 *
 * 替代旧架构中的 [com.winter.muplayer.plugin_api.SlotWidget]，
 * 放在 Runtime 层让宿主和插件共享同一个数据结构。
 *
 * @param type Widget 类型：text / marquee_text / image / button
 * @param content 文本内容
 * @param align 对齐方式：start / center / end
 * @param size 大小：small / medium / large
 * @param color 颜色，如 "#FFFFFF"
 * @param url 图片 URL（type=image 时使用）
 * @param action 按钮点击的 action 名称
 */
data class PluginWidget(
    val type: String,
    val content: String,
    val align: String = "center",
    val size: String = "medium",
    val color: String = "#FFFFFF",
    val url: String = "",
    val action: String = ""
)

/** Widget 类型常量 */
object WidgetType {
    const val TEXT = "text"
    const val MARQUEE_TEXT = "marquee_text"
    const val IMAGE = "image"
    const val BUTTON = "button"
}

/** Slot 名称常量 */
object SlotName {
    /** 播放控制按钮下方 */
    const val BELOW_CONTROLS = "below_controls"
    /** 播放队列上方 */
    const val ABOVE_QUEUE = "above_queue"
    /** 播放器背景 */
    const val PLAYER_BACKGROUND = "player_background"
    /** 主界面 - 音乐列表上方 */
    const val ABOVE_MUSIC_LIST = "above_music_list"
}
