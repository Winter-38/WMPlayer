# WinterMuPlayer 项目指令

## 项目概述
WinterMuPlayer（WMPlayer）是一个 Android 本地音乐播放器，使用 Jetpack Compose + Material3 构建。
- 包名：`com.winter.muplayer`，版本 0.2.2-SNAPSHOT
- compileSdk 36，minSdk 24，targetSdk 36
- Kotlin 2.2.10，AGP 9.1.1，Media3 ExoPlayer 1.10.1

## 模块架构
- `:app` — 壳应用，仅依赖其他模块
- `:core` — 核心业务：播放引擎、队列管理、插件宿主、本地音乐扫描
- `:model` — 纯数据模型（无 Android 依赖）
- `:plugin-api` — 插件协议定义（ContentProvider 通信）
- `:base-ui` — 所有 UI 组件（Compose + Material3）

## 全局记忆（跨会话生效）
每次会话开始时，必须阅读以下核心源文件以恢复上下文：
1. `core/src/main/java/com/winter/muplayer/core/MusicPlayerCore.kt` — 核心入口
2. `core/src/main/java/com/winter/muplayer/plugin/PluginHost.kt` — 插件宿主
3. `base-ui/src/main/java/com/winter/muplayer/base_ui/MusicUIActivity.kt` — 主 UI
4. `plugin-api/src/main/java/com/winter/muplayer/plugin_api/PluginContract.kt` — 插件协议

## 代码约定
- 包结构：`com.winter.muplayer.<模块名>`
- 数据模型在 `:model` 模块定义，其他模块引用
- 代码风格：官方 Kotlin 风格

## 已知问题
- `MusicUIActivity.kt:1384` 引用了 `R.drawable.ic_audio_track`，但 `base-ui` 模块的 drawable 资源中不存在此文件

## 变更记录
---

*每次修改请在此文件末尾追加变更记录，格式：*
- `[YYYY-MM-DD] 修改内容（涉及文件）`
