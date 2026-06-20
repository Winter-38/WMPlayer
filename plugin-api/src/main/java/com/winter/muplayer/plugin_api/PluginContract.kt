package com.winter.muplayer.plugin_api

/**
 * 喏，这就是咱家播放器和插件小可爱们聊天用的「通信协议」啦！
 *
 * 插件呢，是作为一个独立的 APK 安装在手机里的，只要在 AndroidManifest 里声明一个
 * exported 的 ContentProvider，authority 以 `.musicplugin` 结尾，宿主就能找到它。
 * 两者之间通过 [android.content.ContentResolver.call] 来传悄悄话～
 *
 * ## 想写个插件？超简单的，我教你～
 *
 * 1. 先在 AndroidManifest.xml 里注册 ContentProvider：
 * ```xml
 * <provider
 *     android:name=".MyPluginProvider"
 *     android:authorities="com.example.myplugin.musicplugin"
 *     android:exported="true" />
 * ```
 *
 * 2. 然后在 [android.content.ContentProvider.call] 里面处理下面这些 method：
 *    - `get_metadata` → 这个必须实现！不然宿主都不知道你是谁啦
 *    - `on_load` → 插件被加载的时候会调，可以在这做初始化
 *    - `on_state_change` / `on_track_change` / `on_progress` → 播放器有啥动静都会告诉你
 *    - `on_unload` → 要卸载了，记得打扫干净哦
 *
 * 3. 如果你想主动控制播放器（比如点个按钮切歌啥的），在 call 返回的 Bundle 里塞一个
 *    `request_action` 字段就行啦～
 */
object PluginContract {

    /** ContentProvider 的 authority 必须以这个后缀结尾，不然宿主不认你哦！ */
    const val AUTHORITY_SUFFIX = ".musicplugin"

    const val TAG = "WinterMuPlugin"

    // ==================== 插件的基本信息 ====================

    /** 宿主会调这个来问插件「你是谁呀？」，返回的信息都放在下面的 KEY_* 里 */
    const val METHOD_GET_METADATA = "get_metadata"

    const val KEY_PLUGIN_ID = "plugin_id"
    const val KEY_PLUGIN_NAME = "plugin_name"
    const val KEY_PLUGIN_TYPE = "plugin_type"
    const val KEY_PLUGIN_VERSION = "plugin_version"
    const val KEY_PERMISSIONS = "permissions"

    // ==================== 宿主→插件：各种事件通知 ====================

    /** 插件被加载啦～可以在这儿做点初始化的事情，比如准备资源啥的 */
    const val METHOD_ON_LOAD = "on_load"

    /** 播放器状态变了！包括播放/暂停/报错/进度/当前曲目/时长等等 */
    const val METHOD_ON_STATE_CHANGE = "on_state_change"

    /** 欸，切歌了！当前曲目换了～ */
    const val METHOD_ON_TRACK_CHANGE = "on_track_change"

    /** 进度条往前跳了一丢丢，想同步显示的话就监听这个 */
    const val METHOD_ON_PROGRESS = "on_progress"

    /** 播放模式变了哦～顺序播、随机播、还是单曲循环，都在这 */
    const val METHOD_ON_PLAY_MODE_CHANGE = "on_play_mode_change"

    /** 播放队列有变化，比如加了歌或者删了歌 */
    const val METHOD_ON_QUEUE_CHANGE = "on_queue_change"

    /** 插件要被卸载啦，赶紧把占用的资源释放掉，别留垃圾哦！ */
    const val METHOD_ON_UNLOAD = "on_unload"

    // ==================== Bundle 里塞的参数（事件详情） ====================

    /** 整个播放器的状态打包成 JSON 字符串，一次性告诉你 */
    const val KEY_STATE_JSON = "state_json"

    /** 当前曲目的详细信息，也是 JSON 字符串 */
    const val KEY_TRACK_JSON = "track_json"

    /** 播放进度，单位毫秒～现在播到哪儿了 */
    const val KEY_PROGRESS = "progress_ms"

    /** 这首歌的总时长，也是毫秒 */
    const val KEY_DURATION = "duration_ms"

    /** 当前播放模式的名字，字符串形式 */
    const val KEY_PLAY_MODE = "play_mode"

    /** 现在是不是正在播放呀？true 就是在播 */
    const val KEY_IS_PLAYING = "is_playing"

    // ==================== 插件→宿主：你想干嘛？ ====================

    /**
     * 插件在 call() 返回的 Bundle 里塞了这个字段，宿主就会乖乖执行对应的操作～
     * 不过要注意哦，你必须在元数据的 `permissions` 里声明 `playback_control` 权限，
     * 不然宿主会傲娇地不理你（安全第一嘛）！
     */
    const val KEY_REQUEST_ACTION = "request_action"

    const val ACTION_PLAY = "play"
    const val ACTION_PAUSE = "pause"
    const val ACTION_NEXT = "next"
    const val ACTION_PREV = "prev"
    const val ACTION_SEEK = "seek"
    const val ACTION_STOP = "stop"

    /** 和 ACTION_SEEK 一起用的，表示要跳到第几毫秒～类型是 Long */
    const val KEY_SEEK_POSITION = "seek_position_ms"

    // ==================== 权限声明：你能碰什么 ====================

    /** 允许读取播放器状态、进度、当前曲目这些信息 */
    const val PERMISSION_READ_STATE = "read_state"

    /** 允许看播放队列里都有啥歌 */
    const val PERMISSION_READ_QUEUE = "read_queue"

    /** 允许控制播放——就是 play/pause/next/prev/seek 这些啦 */
    const val PERMISSION_PLAYBACK_CONTROL = "playback_control"

    /** 允许修改播放队列，比如加歌删歌 */
    const val PERMISSION_WRITE_QUEUE = "write_queue"

    val ALL_PERMISSIONS = setOf(
        PERMISSION_READ_STATE,
        PERMISSION_READ_QUEUE,
        PERMISSION_PLAYBACK_CONTROL,
        PERMISSION_WRITE_QUEUE
    )

    // ==================== UI Slot：让插件在界面上刷存在感 ====================

    /**
     * 插件可以返回一些 UI 组件，宿主会在预定义的 Slot 位置渲染出来。
     * 返回的 Bundle 里要带上 [KEY_SLOT_NAME] 和 [KEY_WIDGETS_JSON]。
     */
    const val METHOD_GET_SLOT = "get_slot"

    /** Slot 的名字，比如 "below_controls"（控制按钮下方）、"above_queue"（队列上方）、"player_background"（播放器背景） */
    const val KEY_SLOT_NAME = "slot"

    /** 一个 JSON 字符串数组，每个元素描述一个 Widget 长啥样 */
    const val KEY_WIDGETS_JSON = "widgets_json"

    // ======== Widget 的类型，你想要啥样的组件？ ========
    const val WIDGET_TEXT = "text"
    const val WIDGET_MARQUEE = "marquee_text"
    const val WIDGET_IMAGE = "image"
    const val WIDGET_BUTTON = "button"

    // ======== Widget 的字段名（JSON 里的 key） ========
    const val WF_TYPE = "type"
    const val WF_CONTENT = "content"
    const val WF_ALIGN = "align"
    const val WF_SIZE = "size"
    const val WF_COLOR = "color"
    const val WF_URL = "url"
    const val WF_ACTION = "action"
}

/**
 * 插件塞给宿主的 UI 小卡片～
 * 插件通过 [PluginContract.METHOD_GET_SLOT] 返回一个 JSON 数组，
 * 宿主会按照这里的描述在界面上渲染出来。
 * 你可以放文字、图片、按钮……想放啥就放啥！
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
