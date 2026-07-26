# WinterMuPlayer (WMPlayer)

一个基于 Jetpack Compose + Material3 构建的 Android 本地音乐播放器。

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-7F52FF?logo=kotlin)]()
[![Compose BOM](https://img.shields.io/badge/Compose_BOM-2026.06-4285F4?logo=jetpackcompose)]()
[![Version](https://img.shields.io/badge/Version-0.6.0-blue)]()

## 功能

### ✅ 已实现
- **本地音乐扫描** — 扫描设备上的音频文件，按全部/歌手/专辑分类浏览
- **播放引擎** — 基于 Media3 ExoPlayer，支持播放/暂停/上一首/下一首
- **播放队列** — 管理当前播放队列，支持随机播放、单曲循环、列表循环
- **UI 布局配置** — 通过 `config/main.json` + `.css` 自定义界面布局和样式
- **组件系统** — 内置组件注册表 + 插槽渲染器，支持 JSON 声明式 UI 组装
- **CSS 引擎** — 支持 CSS class 属性（尺寸、颜色、间距、动画等），通过 Compose 渲染
- **全屏播放器** — 专辑封面、歌曲信息、控制按钮
- **顶部迷你播放栏** — 底部常驻迷你播放栏
- **搜索** — 本地曲库搜索
- **设置** — 基础设置页面
- **多语言** — 中文/英文资源支持

### 🚧 计划中
- 网络音频流支持
- 均衡器
- 歌词显示
- 主题系统增强

## 技术栈

| 组件 | 技术 |
|------|------|
| UI 框架 | Jetpack Compose + Material3 |
| 播放引擎 | Media3 ExoPlayer (1.10.1) |
| 音视频 | Coil (图片加载) |
| 构建工具 | Gradle + AGP 9.1.1 |
| 最低 SDK | Android 7.0 (API 24) |
| 目标 SDK | Android 16 (API 36) |

## 模块结构

```
:app                  # 壳应用
:base-ui              # UI 组件库（Compose 组件、配置引擎、主题）
  └─ ui/config/       # 组件配置系统（LayoutParser、CSS 引擎、渲染器）
:core                 # 核心业务（播放引擎、扫描器、队列管理）
  └─ core/engine/     # 播放引擎接口 + ExoPlayer 实现
  └─ core/scanner/    # 本地音乐扫描器
:model                # 纯数据模型（无 Android 依赖）
```

## UI 自定义

应用支持通过 JSON + CSS 配置文件自定义界面布局。

### 配置文件

配置文件存储在 `{外部存储}/config/` 目录：

- **main.json** — 入口文件，定义组件插槽布局
- **style.css** — 通过 CSS class 定义组件样式（尺寸、颜色、动画等）
- 支持多文件合并和 include 引用

### 插槽系统

```
app-top     → 顶部栏（应用名、搜索按钮、设置按钮、搜索栏）
app-center  → 主区域（播放列表）
app-bottom  → 底部栏（迷你播放栏）
```

每个插槽可配置任意内置组件，支持自定义组件定义。

## 构建

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建（需 local.properties 配置签名）
./gradlew assembleRelease
```

Release APK 自动复制到 `release/` 目录。

## 许可证

MIT License
