package com.winter.muplayer.core

import android.content.Context
import android.content.SharedPreferences
import com.winter.muplayer.model.PlayMode

/**
 * 应用设置管理器 — 所有用户偏好配置的持久化存储。
 * 包装 SharedPreferences，提供类型安全的读写接口。
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== 播放设置 ====================

    /** 默认播放模式 */
    var defaultPlayMode: PlayMode
        get() = PlayMode.entries.getOrElse(
            prefs.getInt(KEY_DEFAULT_PLAY_MODE, PlayMode.SEQUENTIAL.ordinal)
        ) { PlayMode.SEQUENTIAL }
        set(value) = prefs.edit().putInt(KEY_DEFAULT_PLAY_MODE, value.ordinal).apply()

    /** 跨fade 淡入淡出时长（毫秒），0 = 关闭 */
    var crossfadeDurationMs: Int
        get() = prefs.getInt(KEY_CROSSFADE_MS, 0).coerceIn(0, 5000)
        set(value) = prefs.edit().putInt(KEY_CROSSFADE_MS, value.coerceIn(0, 5000)).apply()

    /** 音频焦点处理：true=暂停，false=降低音量 */
    var audioFocusDuck: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_FOCUS_DUCK, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_FOCUS_DUCK, value).apply()

    // ==================== 显示主题 ====================

    enum class ThemeMode { SYSTEM, LIGHT, DARK }

    /** 主题模式 */
    var themeMode: ThemeMode
        get() = ThemeMode.entries.getOrElse(
            prefs.getInt(KEY_THEME_MODE, ThemeMode.SYSTEM.ordinal)
        ) { ThemeMode.SYSTEM }
        set(value) = prefs.edit().putInt(KEY_THEME_MODE, value.ordinal).apply()

    /** 动态取色（Android 12+） */
    var dynamicColorEnabled: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, false)
        set(value) = prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, value).apply()

    /** 封面模糊背景 */
    var blurBackground: Boolean
        get() = prefs.getBoolean(KEY_BLUR_BG, false)
        set(value) = prefs.edit().putBoolean(KEY_BLUR_BG, value).apply()

    // ==================== 音乐扫描 ====================

    /** 启动时自动扫描 */
    var autoScanOnStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCAN, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCAN, value).apply()

    // ==================== 常量 ====================

    companion object {
        private const val PREFS_NAME = "winter_mu_player_settings"

        private const val KEY_DEFAULT_PLAY_MODE = "default_play_mode"
        private const val KEY_CROSSFADE_MS = "crossfade_ms"
        private const val KEY_AUDIO_FOCUS_DUCK = "audio_focus_duck"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_BLUR_BG = "blur_background"
        private const val KEY_AUTO_SCAN = "auto_scan"
    }
}
