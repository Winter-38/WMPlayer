-keep class org.luaj.** { *; }
-dontwarn org.luaj.**

# 插件系统公共 API：被 ui/app 跨模块引用（LuaPluginManager 门面、PluginConfig、
# model 描述符等），plugin 自身 R8 必须保留，否则下游链接 Missing class。
# 插件系统对混淆敏感（宿主桥接/运行时派发），整体保留最稳妥。
-keep class com.winter.muplayer.plugin.** { *; }
