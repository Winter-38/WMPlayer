# WinterMuPlayer ProGuard/R8 规则
# R8 已启用（isMinifyEnabled=true），以下为必需的 keep 规则。

# ==================== 数据模型 ====================
# 反射 / 序列化
-keep class com.winter.muplayer.model.** { *; }
-keep class com.winter.muplayer.ui.** { *; }

# ==================== Shadow 插件框架 ====================
-keep class com.winter.muplayer.plugin_runtime.** { *; }
-keep class com.winter.muplayer.plugin_loader.** { *; }
-keep class com.winter.muplayer.plugin_manager.** { *; }

# ==================== Media3 / ExoPlayer ====================
-keep class androidx.media3.** { *; }

# ==================== Coil ====================
# 不再整包 keep：coil-base / coil-compose 的 AAR 自带 consumer rules（会保留
# ComponentRegistry 等需要运行时发现的组件），整包 -keep 只会阻止 R8
# 裁掉未使用的解码器（GIF / SVG / 视频等本项目不用的部分）。

# ==================== Kotlin 协程 ====================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ==================== Compose ====================
# 保留 Compose 编译器生成的合成参数（$composer 和 $changed），防止 R8 错误移除
-keepclassmembers class * {
    *** $composer;
    *** $changed;
}
# 保留 @Composable 注解标记的方法（运行时通过注解反射查找）
-keep,allowobfuscation @androidx.compose.runtime.Composable class * { *; }
# 保留 @Composable 方法的成员
-keepclassmembers class * {
    @androidx.compose.runtime.Composable <methods>;
}
# 保留 Composable 函数的默认参数实现
-keepclassmembers class * {
    *** $default(...);
}
# 注意：故意不设 -dontwarn，确保 R8 编译时任何 Compose 相关警告都会暴露出来

# ==================== 日志移除（Release 零开销）====================
# AppLogger 是 Kotlin object，方法在字节码中是 public void（实例方法）。
# 同时保留静态签名为 R8 可能自动生成的前置函数做准备。
-assumenosideeffects class com.winter.muplayer.core.AppLogger {
    public void d(java.lang.String, java.lang.String);
    public static void d(java.lang.String, java.lang.String);
    public void i(java.lang.String, java.lang.String);
    public static void i(java.lang.String, java.lang.String);
    public void w(java.lang.String, java.lang.String);
    public static void w(java.lang.String, java.lang.String);
    public void e(java.lang.String, java.lang.String);
    public static void e(java.lang.String, java.lang.String);
}

# ==================== 嵌套数据类保留 ====================
# ProgressData 是 ProgressTracker 的嵌套 data class，-keep ProgressTracker 不覆盖嵌套类，
# 它是公开 API progressState 的类型（MusicPlayerCore 暴露给 UI 层），必须保留防止运行时 NoClassDefFoundError
-keep class com.winter.muplayer.core.ProgressTracker$ProgressData { *; }

# ==================== 压缩优化（保守）====================
-optimizationpasses 2
-dontpreverify

# 保留行号信息用于 Crash 分析（不保留源文件名）
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile