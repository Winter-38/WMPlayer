# WinterMuPlayer — Core 模块 ProGuard / R8 规则
# Release 构建中 core 模块 isMinifyEnabled=true，以下规则确保核心类不被混淆。

# ==================== Manifest 声明的 Service ====================
# 必须保留全限定类名，否则系统启动 Service 时 ClassNotFoundException
-keep class com.winter.muplayer.core.MusicPlaybackService { *; }
-keep class com.winter.muplayer.core.MusicPlaybackService$Companion { *; }

# ==================== 核心播放类（单例）====================
# MusicPlayerCore 通过 companion object 的 getInstance() 单例模式访问
-keep class com.winter.muplayer.core.MusicPlayerCore { *; }
-keep class com.winter.muplayer.core.MusicPlayerCore$Companion { *; }

# ==================== 播放引擎 ====================
-keep class com.winter.muplayer.core.engine.PlayerEngine { *; }
-keep class com.winter.muplayer.core.engine.ExoPlayerEngine { *; }

# ==================== 核心工具类 ====================
-keep class com.winter.muplayer.core.ProgressTracker { *; }
# ProgressData 是 ProgressTracker 的嵌套 data class，被 config/ui 模块跨模块引用（progressState API）。
# 必须保留全限定名，否则 core 自身 R8 会将其混淆（如 -> a.e0），下游引用原始名导致 missing class / 运行时 NoClassDefFoundError
-keep class com.winter.muplayer.core.ProgressTracker$ProgressData { *; }
-keep class com.winter.muplayer.core.PlayQueueManager { *; }
# 保留 QueueEntry 数据类（PlayerQueueManager 的内部数据类，被 base-ui 跨模块引用）
-keep class com.winter.muplayer.core.QueueEntry { *; }
-keep class com.winter.muplayer.core.PlaylistManager { *; }
-keep class com.winter.muplayer.core.SettingsManager { *; }
-keep class com.winter.muplayer.core.SettingsManager$ThemeMode { *; }
-keep class com.winter.muplayer.core.SettingsManager$AppLanguage { *; }
-keep class com.winter.muplayer.core.SettingsManager$AdaptiveTintStyle { *; }

# ==================== 日志器（Kotlin object + 内部数据结构）====================
# AppLogger 是跨模块公共 API，base-ui 中直接调用 d/i/w/e 等方法，
# 必须保留所有方法才能通过 core 模块自身 R8 的独立混淆。
-keep class com.winter.muplayer.core.AppLogger { *; }
# 保留 LogEntry 数据类（被 getEntries() 返回，base-ui 中直接引用）
-keep class com.winter.muplayer.core.AppLogger$LogEntry { *; }

# ==================== 本地音乐扫描器 ====================
-keep class com.winter.muplayer.core.scanner.LocalMusicScanner { *; }

# ==================== Media3 / ExoPlayer ====================
-keep class androidx.media3.** { *; }

# ==================== 行号信息（用于 Crash 分析）====================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
