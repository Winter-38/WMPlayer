package com.winter.muplayer.plugin_api

/**
 * ContentProvider 插件通信协议。
 *
 * 插件以独立 APK 形式安装，在 AndroidManifest 中声明一个 exported ContentProvider，
 * authority 以 `.musicplugin` 结尾。宿主通过 [android.content.ContentResolver.call] 与插件通信。
 *
 * ## 插件开发快速指引
 *
 * 1. 在 AndroidManifest.xml 中注册 ContentProvider：
 * ```xml
 * <provider
 *     android:name=".MyPluginProvider"
 *     android:authorities="com.example.myplugin.musicplugin"
 *     android:exported="true" />
 * ```
 *
 * 2. 在 [android.content.ContentProvider.call] 中处理以下 method：
 *    - `get_metadata` → 必须实现，返回插件信息
 *    - `on_load` → 初始化
 *    - `on_state_change` / `on_track_change` / `on_progress` → 播放器事件
 *    - `on_unload` → 清理
 *
 * 3. 如需主动控制播放器，在 call 的返回 Bundle 中放入 `request_action` 字段。
 */
object PluginContract {

    /** ContentProvider authority 必须以此后缀结尾 */
    const val AUTHORITY_SUFFIX = ".musicplugin"

    const val TAG = "WinterMuPlugin"

    // ==================== 插件元数据 ====================

    /** 获取插件信息，返回 Bundle 包含以下 KEY_* 字段 */
    const val METHOD_GET_METADATA = "get_metadata"

    const val KEY_PLUGIN_ID = "plugin_id"
    const val KEY_PLUGIN_NAME = "plugin_name"
    const val KEY_PLUGIN_TYPE = "plugin_type"
    const val KEY_PLUGIN_VERSION = "plugin_version"
    const val KEY_PERMISSIONS = "permissions"

    // ==================== 事件推送（宿主 → 插件） ====================

    /** 插件被加载时调用，可做初始化 */
    const val METHOD_ON_LOAD = "on_load"

    /** 播放器状态变化：state / progress / currentTrack / duration / error */
    const val METHOD_ON_STATE_CHANGE = "on_state_change"

    /** 当前曲目切换 */
    const val METHOD_ON_TRACK_CHANGE = "on_track_change"

    /** 播放进度更新 */
    const val METHOD_ON_PROGRESS = "on_progress"

    /** 播放模式变化（顺序/随机/单曲循环） */
    const val METHOD_ON_PLAY_MODE_CHANGE = "on_play_mode_change"

    /** 播放队列变更 */
    const val METHOD_ON_QUEUE_CHANGE = "on_queue_change"

    /** 插件被卸载时调用，应释放所有资源 */
    const val METHOD_ON_UNLOAD = "on_unload"

    // ==================== Bundle Key（事件参数） ====================

    /** JSON 字符串，包含完整的播放器状态 */
    const val KEY_STATE_JSON = "state_json"

    /** JSON 字符串，曲目信息 */
    const val KEY_TRACK_JSON = "track_json"

    /** Long，当前进度（毫秒） */
    const val KEY_PROGRESS = "progress_ms"

    /** Long，总时长（毫秒） */
    const val KEY_DURATION = "duration_ms"

    /** String，播放模式名 */
    const val KEY_PLAY_MODE = "play_mode"

    /** Boolean，是否正在播放 */
    const val KEY_IS_PLAYING = "is_playing"

    // ==================== 插件主动请求（插件 → 宿主） ====================

    /**
     * 插件在 call() 返回 Bundle 中放入此字段，宿主会执行对应操作。
     * 需在元数据的 `permissions` 中声明 `playback_control` 权限。
     */
    const val KEY_REQUEST_ACTION = "request_action"

    const val ACTION_PLAY = "play"
    const val ACTION_PAUSE = "pause"
    const val ACTION_NEXT = "next"
    const val ACTION_PREV = "prev"
    const val ACTION_SEEK = "seek"
    const val ACTION_STOP = "stop"

    /** 配合 ACTION_SEEK 使用，Long 类型 */
    const val KEY_SEEK_POSITION = "seek_position_ms"

    // ==================== 权限声明 ====================

    /** 读取播放器状态、进度、当前曲目 */
    const val PERMISSION_READ_STATE = "read_state"

    /** 读取播放队列 */
    const val PERMISSION_READ_QUEUE = "read_queue"

    /** 控制播放（play/pause/next/prev/seek） */
    const val PERMISSION_PLAYBACK_CONTROL = "playback_control"

    /** 修改播放队列（addTrack/removeTrack） */
    const val PERMISSION_WRITE_QUEUE = "write_queue"

    val ALL_PERMISSIONS = setOf(
        PERMISSION_READ_STATE,
        PERMISSION_READ_QUEUE,
        PERMISSION_PLAYBACK_CONTROL,
        PERMISSION_WRITE_QUEUE
    )

    // ==================== UI Slot（插件 UI 嵌入） ====================

    /**
     * 插件返回 UI 内容。宿主在预定义 Slot 位置渲染。
     * 返回 Bundle 包含 [KEY_SLOT_NAME] 和 [KEY_WIDGETS_JSON]。
     */
    const val METHOD_GET_SLOT = "get_slot"

    /** Slot 名称，如 "below_controls" / "above_queue" / "player_background" */
    const val KEY_SLOT_NAME = "slot"

    /** JSON 字符串数组，每个元素是一个 Widget 描述 */
    const val KEY_WIDGETS_JSON = "widgets_json"

    // ======== Widget 类型 ========
    const val WIDGET_TEXT = "text"
    const val WIDGET_MARQUEE = "marquee_text"
    const val WIDGET_IMAGE = "image"
    const val WIDGET_BUTTON = "button"

    // ======== Widget 字段名（JSON key） ========
    const val WF_TYPE = "type"
    const val WF_CONTENT = "content"
    const val WF_ALIGN = "align"
    const val WF_SIZE = "size"
    const val WF_COLOR = "color"
    const val WF_URL = "url"
    const val WF_ACTION = "action"
}

/**
 * 插件提供的 UI 组件描述。
 * 插件通过 [PluginContract.METHOD_GET_SLOT] 返回 JSON 数组，宿主反序列化后渲染。
 */
data class SlotWidget(
    val type: String,
    val content: String,
    val align: String = "center",
    val size: String = "medium",
    val color: String = "#FFFFFF",
    val url: String = "",
    val action: String = ""
)
