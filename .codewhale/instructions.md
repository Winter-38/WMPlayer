# WinterMuPlayer 项目指令

## 项目概述
WinterMuPlayer（WMPlayer）是一个 Android 本地音乐播放器，使用 Jetpack Compose + Material3 构建。
- 包名：`com.winter.muplayer`，版本 0.2.2-SNAPSHOT
- compileSdk 36，minSdk 24，targetSdk 36
- Kotlin 2.2.10，AGP 9.1.1，Media3 ExoPlayer 1.10.1

## 模块架构
- `:app` — 壳应用，仅依赖其他模块
- `:core` — 核心业务：播放引擎（`core.engine` 包）、队列管理、本地音乐扫描
- `:model` — 纯数据模型（无 Android 依赖）
- `:plugin` — Shadow 插件框架（`plugin_runtime` 接口 + `plugin_loader` 加载器 + `plugin_manager` 管理器 + ShadowPluginHost 宿主）
- `:base-ui` — 所有 UI 组件（Compose + Material3）

## 全局记忆（跨会话生效）
每次会话开始时，必须阅读以下核心源文件以恢复上下文：
1. `core/src/main/java/com/winter/muplayer/core/MusicPlayerCore.kt` — 核心入口（实现 IPlayerHost）
2. `core/src/main/java/com/winter/muplayer/core/engine/PlayerEngine.kt` — 播放引擎接口
3. `plugin/src/main/java/com/winter/muplayer/plugin_manager/ShadowPluginHost.kt` — Shadow 插件宿主
4. `plugin/src/main/java/com/winter/muplayer/plugin_runtime/IPlugin.kt` — 插件生命周期接口
5. `plugin/src/main/java/com/winter/muplayer/plugin_runtime/IPlayerHost.kt` — 宿主 API 接口
6. `plugin/src/main/java/com/winter/muplayer/plugin_manager/PluginManager.kt` — 插件管理器
7. `base-ui/src/main/java/com/winter/muplayer/base_ui/MusicUIActivity.kt` — 主 UI

## 代码约定
- 包结构：`com.winter.muplayer.<模块名>`
- 数据模型在 `:model` 模块定义，其他模块引用
- 代码风格：官方 Kotlin 风格

## 已知问题
- `MusicUIActivity.kt:1384` 引用了 `R.drawable.ic_audio_track`，但 `base-ui` 模块的 drawable 资源中不存在此文件

## 变更记录
- `[2026-06-26] 基于 Shadow 架构重写插件系统：新增 plugin-runtime / plugin-loader / plugin-manager 三个模块；MusicPlayerCore 实现 IPlayerHost 接口；创建 ShadowPluginHost 替代旧 PluginHost；更新 PluginSlotRenderer / PluginManagerScreen / MusicUIActivity 适配新架构；标记旧 PluginHost / PluginContract 和 4 个存根文件为 @Deprecated。（涉及 15+ 文件）
- `[2026-06-27] 项目结构重构：engine 包统一为 `com.winter.muplayer.core.engine`（PlayerEngine / ExoPlayerEngine）；ShadowPluginHost 从 core 模块迁移至 plugin 模块的 `com.winter.muplayer.plugin_manager` 包；旧 PluginHost（249 行代码）替换为 @Deprecated 存根；移除 4 个废弃存根文件（CoreHostApi / PluginFileManager / PluginLoader / PluginManager）；plugin 模块 namespace 统一为 `com.winter.muplayer.plugin`；更新 .codewhale/instructions.md 模块路径。（涉及 16+ 文件）
---

*每次修改请在此文件末尾追加变更记录，格式：*
- `[YYYY-MM-DD] 修改内容（涉及文件）`
