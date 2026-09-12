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
- **UI 布局配置** — 通过 `config/main.json` + `.css` 自定义界面布局和样式，对象化 slot 声明 + 命名子 slot 嵌套
- **组件系统** — 内置组件注册表 + 插槽渲染器，支持 JSON 声明式 UI 组装；全屏播放器细分组件（主封面/标题/歌手+专辑/进度条/控制按钮/封面模糊背景）可自由组合
- **slot 型组件** — 容器组件（如 `fp-backdrop`）可作为背景层承载 children 前景内容；children 子 slot 以容器 id 定位（`.fp-backdrop`），子 slot 之间排列方向由容器组件 `#fp-backdrop { arrange }` 控制
- **CSS 引擎** — 多级样式：主界面（`.main`）与全屏播放器（`.full-player`）方向独立互不影响，slot 级排列 + 组件级样式；属性覆盖尺寸、颜色、间距、对齐、动画等
- **全屏播放器** — 细分组件组装：主封面、标题、歌手+专辑、进度条、播放操控按钮、封面模糊背景
- **顶部迷你播放栏** — 底部常驻迷你播放栏
- **液态玻璃效果** — mini 播放栏支持四态渲染样式（`render-style`：不透明卡片 / 半透明浮层 / 毛玻璃（仅模糊）/ 液态玻璃），液态玻璃按原版 AndroidLiquidGlass 示例组装（vibrancy + blur + lens 折射/色散 + 高光 + 内阴影 + 边缘亮线，基于 Kyant backdrop 引擎 `:backdrop` 模块），毛玻璃与液态玻璃相互独立；模糊度、折射、表面不透明度、高光等均可在设置页滑块实时调节，CSS `liquid-*` 参数可调
- **搜索** — 本地曲库搜索
- **设置** — 基础设置页面，支持「重新读取配置」热重载布局配置
- **布局与样式编辑器** — 设置页内置可视化小编辑器：上半区实时预览（主界面 / 全屏播放器），下半区可视化编辑当前布局（区域与组件的增删、排序）与样式（常用 CSS 属性表单化编辑 + 高级自由属性），所有修改即时写盘生效，支持一键恢复默认
- **纯美化组件** — `divider`（实线 / 虚线 / 两端渐隐）、`image`（资源名 / 文件路径 / 网络）、`card`（填充 + 描边 + 高程）、`badge`、`dot`、`progress`（静态读数条）、`blur-layer`（渐变光斑 + 模糊）；每个组件在通用属性之外都有自己的特色属性（如文本类可自定义字体、背景、透明度、行高、字间距、大小写变换等）
- **动画系统** — CSS 风格动画：`enter`（组合/呼出界面时入场，slot 级 `stagger` 让内部组件依次错开位置）、`animation`（循环或指定次数）、自定义 `@keyframes`，内置 16 种预设（`fade-up` / `slide-in-*` / `zoom-in` / `pop` / `spin` / `pulse` / `bounce` / `shake` 等）；动画只作用于绘制层，不改变布局占位，也不触发重新布局
- **滚动惯性** — 播放列表滑动时，条目按「与视口中心的距离」错开不同间隔（越远拉开越大），停止后弹性回位；由 `item-inertia` 控制强度
- **性能与体积** — 播放进度订阅下沉到叶子组件（不再每 250ms 重组整棵树）、CSS 解析结果与 SlotContext 实例复用、惯性动画只触发重绘；release 同时裁剪非必要语言资源与 CPU 架构
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
:ui                   # UI 组件库（Compose 组件、配置引擎、主题、全屏播放器组件）
  └─ ui/components/   # 内置组件注册表 + 细分组件（fp-* 系列）
:config               # 配置系统（LayoutParser、CSS 引擎、SlotRenderer、BinaryCache）
:core                 # 核心业务（播放引擎、扫描器、队列管理）
  └─ core/engine/     # 播放引擎接口 + ExoPlayer 实现
  └─ core/scanner/    # 本地音乐扫描器
:model                # 纯数据模型（无 Android 依赖）
:plugin               # Lua 插件系统（x+1 运行时：高频专用常驻 + 事件驱动共享；预编译字节码缓存 / mmap 虚拟内存 / 后台下载安装 / 协程隔离 / 导出函数 Map / UI 注册体系 / 三类插件（app·component·service））已接入 app（设置页提供插件管理入口：独立管理界面 / 默认界面 / 安装）
```

## Lua 插件系统

应用内置基于 LuaJ 的插件模块（`:plugin`），支持安装、加载、事件驱动、高频直调与 UI 注册：

- **x+1 运行时** — 高频插件独占常驻运行时，事件驱动插件共享单一运行时
- **预编译字节码缓存** — 入口源码编译为 `.ljbc` 字节码（键 = SHA-256 源码），命中时 mmap 虚拟内存按需换入物理内存
- **后台下载安装** — OkHttp + 协程下载 zip，校验 manifest 后原子解压落盘
- **协程隔离** — 安装/事件派发均在协程中串行执行，插件间以 Lua coroutine 隔离
- **导出函数 Map** — 插件导出表快照为函数引用，宿主查表直调
- **UI 注册体系** — 插件可注册普通/slot 型组件（图标 + 点按/长按/滑动手势，动作可执行函数、唤起界面或 widget）、界面与 widget（布局 JSON 存于 zip 内，格式与 main.json 一致，可引用 app 组件）
- **插件类别与服务调用** — 插件分 app/component/service 三类（manifest `type` 声明，zip 根目录 `main.json` 为默认界面）；任意插件可经 `wm.services.call` 调用其它插件（含服务插件）的导出函数

详见 [docs/plugin-api.md](docs/plugin-api.md)。

## UI 自定义

应用支持通过 JSON + CSS 配置文件自定义界面布局。

### 配置文件

配置文件存储在 `{外部存储}/config/` 目录：

- **main.json** — 入口文件，定义组件插槽布局（对象化 slot：不同名字对应不同 slot）
- **style.css** — 通过 CSS class 定义组件样式（尺寸、颜色、动画等）
- 支持多文件合并和 include 引用

### 插槽系统

```
app-top     → 顶部栏（应用名、搜索按钮、设置按钮、搜索栏）
app-center  → 主区域（播放列表）
app-bottom  → 底部栏（迷你播放栏）
full-player → 全屏播放器（fp-backdrop 背景层 + 细分组件前景）
```

每个插槽可配置任意内置组件，支持自定义组件定义。slot 可嵌套：组件数组内可用
`{ "name": "children-slot", "children": ["child-1", "child-2"] }` 声明命名子 slot；
slot 名与 slot 型组件 id（如 `fp-backdrop`）一致时解析为容器组件（自身作背景层，
children 作前景层）。

全屏播放器与主界面样式**互不影响**：全屏方向由 `.full-player` 控制（不读主界面的
`.main`），容器组件 children 中多个子 slot 之间的排列方向由 `#fp-backdrop { arrange }`
控制（未设时继承 `.full-player`）。

设置页提供「重新读取配置」入口，修改 `main.json` / `style.css` 后可热重载，
无需重启应用。

### 布局与样式编辑器（设置页内置）

设置页 →「布局编辑器」进入可视化编辑，无需手写 JSON / CSS：

- **实时预览**：编辑器上半区实时渲染当前布局（主界面 / 全屏播放器可切换），
  任何修改立即生效并写盘（`main.json` + `styles.css`）；
- **布局 tab**：主界面区域（顶部栏 / 主区域 / 底部栏）与全屏播放器子区域（如
  `fp-main`）的组件增删、上移 / 下移排序；点击区域或组件直接编辑其样式；
- **样式 tab**：列出全部区域（`.x`）与组件（`#x`）选择器，点击进入属性表单
  （排列方向、尺寸、颜色、圆角、间距、对齐、模糊、迷你栏渲染样式等），
  高级模式可手动添加任意 CSS 属性；
- **重置默认**：一键恢复应用内置默认布局与样式。

> 注意：编辑器保存时会**扁平化**配置 —— 布局写入单个 `main.json`（不再引用
> include 文件），样式合并写入 `styles.css`（其余 `.css` 文件内容并入后重命名
> 为 `.bak` 隔离，避免旧文件覆盖编辑结果）。

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

本项目使用 Kyant0/AndroidLiquidGlass（Apache License 2.0）实现液态玻璃效果：`backdrop/` 模块为其 backdrop 库移植版，`ui` 模块的液态玻璃渲染参考其 catalog 示例实现。第三方组件归属与许可证全文见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
