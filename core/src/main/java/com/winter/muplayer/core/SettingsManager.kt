package com.winter.muplayer.core

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置管理器 — 所有用户偏好配置的持久化存储。
 * 包装 SharedPreferences，提供类型安全的读写接口。
 */
class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ==================== 播放设置 ====================

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

    // ==================== 封面自适应取色 ====================

    /** 封面模糊背景模式下组件取色方式：正色 / 反色 / 黑白 */
    enum class AdaptiveTintStyle { COLOR, INVERT, MONOCHROME }

    /** 封面背景模式下组件取色方式 */
    var adaptiveTintStyle: AdaptiveTintStyle
        get() = AdaptiveTintStyle.entries.getOrElse(
            prefs.getInt(KEY_ADAPTIVE_TINT_STYLE, AdaptiveTintStyle.MONOCHROME.ordinal)
        ) { AdaptiveTintStyle.MONOCHROME }
        set(value) = prefs.edit().putInt(KEY_ADAPTIVE_TINT_STYLE, value.ordinal).apply()

    // ==================== 音乐扫描 ====================

    /** 启动时自动扫描 */
    var autoScanOnStart: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SCAN, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_SCAN, value).apply()

    // ==================== 语言设置 ====================

    enum class AppLanguage(val localeTag: String) {
        SYSTEM(""),    // 跟随系统
        ZH("zh"),      // 简体中文
        EN("en")       // English
    }

    /** 应用语言 */
    var appLanguage: AppLanguage
        get() = AppLanguage.entries.getOrElse(
            prefs.getInt(KEY_APP_LANGUAGE, AppLanguage.SYSTEM.ordinal)
        ) { AppLanguage.SYSTEM }
        set(value) = prefs.edit().putInt(KEY_APP_LANGUAGE, value.ordinal).apply()

    // ==================== 歌曲排序 ====================

    /** 排序字段：0=名称 1=时长 2=大小 3=日期 4=类型 */
    var sortField: Int
        get() = prefs.getInt(KEY_SORT_FIELD, 0)
        set(value) = prefs.edit().putInt(KEY_SORT_FIELD, value).apply()

    /** 排序方向：true=升序 false=降序 */
    var sortAsc: Boolean
        get() = prefs.getBoolean(KEY_SORT_ASC, true)
        set(value) = prefs.edit().putBoolean(KEY_SORT_ASC, value).apply()

    // ==================== 常量 ====================

    companion object {
        private const val PREFS_NAME = "winter_mu_player_settings"

        private const val KEY_CROSSFADE_MS = "crossfade_ms"
        private const val KEY_AUDIO_FOCUS_DUCK = "audio_focus_duck"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_BLUR_BG = "blur_background"
        private const val KEY_ADAPTIVE_TINT_STYLE = "adaptive_tint_style"
        private const val KEY_AUTO_SCAN = "auto_scan"
        private const val KEY_APP_LANGUAGE = "app_language"
        private const val KEY_SORT_FIELD = "sort_field"
        private const val KEY_SORT_ASC = "sort_asc"
    }
}
