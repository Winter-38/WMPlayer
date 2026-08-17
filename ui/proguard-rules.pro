# ==================== UI 子包 ====================
-keep class com.winter.muplayer.ui.** { *; }

# ==================== 主题系统 ====================
-keep class com.winter.muplayer.ui.theme.** { *; }

# ==================== 行号信息（保持与 app 模块一致）====================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
