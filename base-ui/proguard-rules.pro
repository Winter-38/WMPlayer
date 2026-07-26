# WinterMuPlayer — Base-UI 模块 ProGuard / R8 规则
# Release 构建中 base-ui 模块 isMinifyEnabled=true，以下规则确保 UI 核心类不被混淆。

# ==================== Manifest 引用的 Activity（必须保留全限定名）====================
-keep class com.winter.muplayer.base_ui.MusicUIActivity { *; }

# ==================== UI 界面类 ====================
-keep class com.winter.muplayer.base_ui.SettingsScreen { *; }
-keep class com.winter.muplayer.base_ui.PluginManagerScreen { *; }
-keep class com.winter.muplayer.base_ui.CategorizedMusicSheet { *; }


# ==================== UI 子包 ====================
-keep class com.winter.muplayer.base_ui.ui.** { *; }

# ==================== 主题系统 ====================
-keep class com.winter.muplayer.base_ui.ui.theme.** { *; }

# ==================== 行号信息（保持与 app 模块一致）====================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
