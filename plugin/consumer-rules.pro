# LuaJ 通过自身 VM 解释执行插件字节码，宿主侧仅通过公开 API 调用，
# 但 Globals/Prototype 等类型参与动态分派，禁止混淆以保证运行时兼容。
-keep class org.luaj.** { *; }
-dontwarn org.luaj.**

# 插件系统公共 API（门面/配置/模型），下游模块（ui/app）跨模块引用，整体保留。
-keep class com.winter.muplayer.plugin.** { *; }
