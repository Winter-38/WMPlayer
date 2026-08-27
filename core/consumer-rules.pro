# WinterMuPlayer — Core 模块消费者 ProGuard 规则
# 引用 core 模块的模块（base-ui、app）在自身 R8 处理时会应用以下规则

# 跨模块引用的数据类必须保留
-keep class com.winter.muplayer.core.AppLogger { *; }
-keep class com.winter.muplayer.core.AppLogger$LogEntry { *; }
-keep class com.winter.muplayer.core.CrashLogManager { *; }
-keep class com.winter.muplayer.core.QueueEntry { *; }
-keep class com.winter.muplayer.core.MusicPlayerCore { *; }
-keep class com.winter.muplayer.core.MusicPlayerCore$Companion { *; }
-keep class com.winter.muplayer.core.SettingsManager$AppLanguage { *; }
-keep class com.winter.muplayer.core.SettingsManager$ThemeMode { *; }
