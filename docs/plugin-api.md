# WMPlayer Lua 插件系统（:plugin）

基于 **LuaJ 3.0.1**（纯 Java，Android 全兼容）的插件模块，采用 **x+1 运行时** 模型：

- **x**：`dedicated` 类型插件（高频调用）各自独占一个**常驻运行时**，互不阻塞，可被宿主高频直调；
- **1**：`shared` 类型插件（事件驱动）共用一个**共享运行时**，由事件调度器按协程串行隔离执行。

## 需求 → 实现映射

| # | 需求 | 实现 |
|---|------|------|
| 1 | 预编译字节码缓存 | `BytecodeCompiler`（`compilePrototype` + `DumpState.dump`）把入口源码编译为 `.ljbc` 二进制 chunk；`BytecodeCache` 以 **SHA-256(源码)** 为键缓存，源码变更自动失效 |
| 2 | 字节码存入虚拟内存，按需使用物理内存 | `MappedBytecodeStore` 用 `FileChannel.map(READ_ONLY)` 把 `.ljbc` 文件 **mmap** 到虚拟地址空间，OS 分页机制只在执行访问时换入物理内存 |
| 3 | 安装时后台下载并解压 | `PluginDownloader`（OkHttp，挂起函数）+ `PluginInstaller`（`ZipInputStream` 解压，含 Zip Slip 路径穿越防护、`.tmp` 原子落盘） |
| 4 | 协程隔离安装与事件驱动执行 | 安装走 `CoroutineScope(SupervisorJob + IO)`；事件驱动插件在共享运行时内以 **Lua coroutine** 隔离（`yield`/`resume` 时间片），宿主侧 `SharedEventExecutor` 用无界 Channel 单协程串行派发 |
| 5 | 计算速度要求不高时用 Lua 原生计算 | 默认 `useNativeComputation = true`，直接走 LuaJ 解释执行（Lua 原生计算路径）；`false` 为高性能后端预留接入点 |
| 6 | 弱引用确保 GC 完成 | `PluginRegistry` 底层为 `WeakHashMap`（卸载即释放强引用）；`LuaRuntime` 持有 `WeakReference<Globals>` 检测回收，杜绝常驻泄漏 |
| 7 | 函数引用存入 map 由宿主快速读取 | `ExportedFunctionMap`：`pluginId → (函数名 → LuaFunction)` 快照，宿主 `invoke()` 查表直调，无需进入 Lua 环境做符号查找 |

## 模块结构

```
:plugin/src/main/java/com/winter/muplayer/plugin/
├── LuaPluginManager.kt        # 门面：安装/加载/事件/直调/生命周期/播放事件桥接
├── PluginConfig.kt            # 配置 + RuntimeKind（DEDICATED / SHARED）+ PluginException
├── model/                     # PluginManifest / PluginDescriptor / PluginEvent
├── bytecode/                  # BytecodeCompiler / BytecodeCache / MappedBytecodeStore
├── runtime/                   # LuaRuntimeFactory / LuaRuntime / RuntimeManager(x+1)
│                              # SharedEventExecutor / HostFunction / LuaConversions
├── install/                   # PluginDownloader / PluginInstaller
├── registry/                  # PluginRegistry(弱引用) / ExportedFunctionMap
├── PluginUiBridge.kt          # 插件 UI 桥接接口（由 ui 模块实现并注入，同步组件注册）
└── bridge/                    # 宿主 API 桥接（见下）
    ├── LuaApiBuilder.kt       # 组装 wm 全局表：log/plugin/app/eventBus/thread/timer/
    │                          # player/json/url/crypto/http/clipboard/ui/services
    ├── PluginSession.kt       # 每插件会话：调度器 + 事件总线 + 配置存储 + UI 注册表
    ├── PluginScheduler.kt     # thread.run/ui/sleep + timer.*
    ├── PluginEventBus.kt      # eventBus.on/emit + 播放事件 → 生命周期回调映射
    ├── PluginConfigStore.kt   # plugin.getConfig/setConfig（文件 JSON 持久化）
    ├── PluginUiRegistry.kt    # 插件 UI 注册数据（组件 / slot 组件 / 界面 / widget / 动作）
    ├── LuaJson.kt             # Lua ↔ JSON 转换（数组表 → JSON 数组）
    └── LuaValues.kt           # 宿主值 ↔ Lua 值、Track 双向映射
```

`:plugin` 依赖 `:core`（播放核心）与 `:model`（Track 等数据模型）。
插件 UI 的**渲染层在 `:ui` 模块**：`PluginUiHost`（实现 [PluginUiBridge]，把插件注册的组件挂到全局
`ComponentRegistry`、聚合页面/widget 打开状态）与 `PluginUiOverlay`（Dialog / ModalBottomSheet /
居中卡片浮层渲染插件布局 JSON），挂载于 `MusicUIActivity` 顶层。

## 插件包格式

zip 包内：

```
plugin.json      # 清单：身份、类别、入口、运行方式（必须）
main.json        # 默认界面布局（可选，格式与 main.json 一致；缺失时宿主生成信息页）
main.lua         # entry 指定的入口脚本
...              # 可选资源
```

`plugin.json`：

```json
{
  "id": "com.example.lyrics",
  "name": "歌词助手",
  "version": "1.0.0",
  "type": "app",
  "entry": "main.lua",
  "runtime": "shared",
  "events": ["onTrackChanged"]
}
```

- `type`: 插件类别 —— `app`（基本插件，由组件和服务构成，`main.json` 为默认 GUI）、
  `component`（组件插件，主要提供组件扩展 app UI，默认界面常列出可用组件及效果）、
  `service`（服务插件，提供函数 API 供其它插件调用，默认界面常列出 API 及作用）；缺省 `app`
- `runtime`: `shared`（默认，事件驱动）或 `dedicated`（高频，独占常驻运行时）
- `events`: 声明监听的事件名（约定与导出函数名一致）

**默认界面**：管理界面点击插件条目时展示 —— 优先渲染 zip 根目录 `main.json`；
不存在时宿主生成信息页（名称/类别/导出函数 API/注册 UI 元素统计）。

## 插件类别

三类插件都以 `type` 字段声明，宿主不强制行为差异（名称不严谨，按实际而定）：

- **基本插件（`app`）**：由组件与服务构成，zip 根目录 `main.json` 是可直接使用的默认 GUI；
- **组件插件（`component`）**：主要提供组件（`wm.ui.registerComponent` 等）扩展 app UI，
  默认界面常列出可用组件及其效果；
- **服务插件（`service`）**：不提供界面功能，通过导出表暴露一个或多个函数 API
  （`return { api = function() end }`），供其它插件调用，默认界面常列出 API 及作用。

**服务调用**：任意插件可调用其它插件（含服务插件）的导出函数：

```lua
wm.services.list("com.example.service")          -- 服务发现：函数名数组
wm.services.call("com.example.service", "api", arg1, arg2)  -- 调用，返回值完整透传
```

调用在宿主单线程 Lua 调度器上同步执行（目标 shared 或 dedicated 均安全），未找到函数返回 nil。

## Lua 插件约定

入口脚本**必须返回导出表**（或设置全局 `_exports`）：

```lua
-- 生命周期回调（可选实现，均为全局函数）
function onLoad() end
function onUnload() end
function onConfigChanged(key, newVal, oldVal) end

-- 播放事件回调（可选实现，由播放器事件桥接触发）
function onTrackChanged(track) end
function onPlayStateChanged(state) end   -- "playing"/"paused"/"idle"/"loading"/"error"
function onProgressUpdated(position, duration) end

local exports = {}

-- 宿主可直调的函数
function exports.getLyrics()
    return "La la la..."
end

return exports
```

## 宿主 API 参考（已实现）

所有宿主 API 注入在全局表 `wm` 下。**名称与参数已按当前应用实际能力裁剪**，
未照搬参考规格，避免脱离应用。

### 通用

| API | 说明 |
|-----|------|
| `wm.log.d(tag, msg)` / `.i()` / `.w()` / `.e()` | 日志输出 |
| `wm.plugin.getConfig(key)` | 读取插件持久化配置（string/number/boolean/table） |
| `wm.plugin.setConfig(key, value)` | 写入配置并触发 `onConfigChanged`，返回布尔 |
| `wm.plugin.getDataDir()` / `wm.plugin.getCacheDir()` | 插件私有数据/缓存目录 |
| `wm.app.version` / `.platform` / `.locale` | 应用信息（只读字符串） |
| `wm.eventBus.on(event, cb)` | 订阅事件，回调 `(event, data)`；含播放器事件 |
| `wm.eventBus.emit(event, data)` | 触发本插件订阅的事件 |
| `wm.thread.run(fn)` | 异步排队执行（单线程 Lua 调度器，不阻塞当前调用） |
| `wm.thread.ui(fn)` | 主线程（UI）执行 |
| `wm.thread.sleep(ms)` | 阻塞当前线程（仅在异步上下文内使用） |
| `wm.timer.setTimeout(ms, fn)` / `setInterval(ms, fn)` / `clear(id)` | 定时器（返回 id 用于 clear） |

### 播放器交互（桥接 MusicPlayerCore）

| API | 说明 |
|-----|------|
| `wm.player.play([track])` | 无参播当前；传 Track 表立即播放（入队顶部） |
| `wm.player.pause()` / `stop()` / `next()` / `previous()` | 播放控制 |
| `wm.player.seekTo(ms)` | 跳转 |
| `wm.player.setVolume(l, r)` | 音量（0~1） |
| `wm.player.setSpeed(speed)` | 播放倍速（≥0.05） |
| `wm.player.isPlaying()` | 是否播放中 |
| `wm.player.getPosition()` / `getDuration()` | 进度（ms） |
| `wm.player.getCurrentTrack()` | 当前 Track 表 |
| `wm.player.getPlaylist()` | 当前队列（Track 数组表） |
| `wm.player.addToQueue(track)` / `removeFromQueue(index)` | 队列增删 |
| `wm.player.shuffle()` | 随机开关（toggle） |
| `wm.player.setRepeatMode(mode)` | `"none"` / `"one"` / `"all"` |

### 数据 / 网络

| API | 说明 |
|-----|------|
| `wm.json.parse(str)` / `stringify(tbl)` | JSON 序列化（Lua 数组表 → JSON 数组，对象表 → JSON 对象） |
| `wm.url.encode(str)` / `decode(str)` / `parse(str)` | URL 编解码与 query 解析 |
| `wm.crypto.md5/sha1/sha256(data)` | 哈希（hex） |
| `wm.crypto.base64Encode/Decode(data)`、`hexEncode/Decode(data)` | 编码 |
| `wm.http.get(url[, headers], cb)` / `post(url, body[, headers], cb)` / `postForm(url, form, cb)` | **全异步回调模式**（不阻塞 Lua 线程），结果经回调返回 `{ statusCode, body, headers, error? }` |
| `wm.http.download(url, savePath[, onProgress])` | 下载文件（进度回调 `(ratio)`） |

HTTP 请求在 IO 线程执行，回调在单线程 Lua 调度器上触发；失败时回调携带 `error` 字段。

### 剪贴板

| API | 说明 |
|-----|------|
| `wm.clipboard.get()` / `set(text)` | 剪贴板读/写 |

### 服务调用（插件间互调）

| API | 说明 |
|-----|------|
| `wm.services.list(pluginId)` | 服务发现：目标插件导出函数名数组 |
| `wm.services.call(pluginId, fnName, ...)` | 调用目标插件导出函数，返回值完整透传（未找到返回 nil） |

调用在单线程 Lua 调度器上同步执行，目标为 shared 或 dedicated 均安全。

### UI 扩展（插件注册组件 / 界面 / widget）

| API | 说明 |
|-----|------|
| `wm.ui.registerComponent(id, config)` | 注册普通组件：`{ title?, icon?, onClick?, onLongClick?, onSwipe? }` |
| `wm.ui.registerSlotComponent(id, config)` | 注册 slot 型组件（自身作背景层，children 由布局 JSON 承载，参考 `fp-backdrop`） |
| `wm.ui.registerPage(id, config)` | 注册界面：`{ title?, layout = "zip 内布局 JSON 路径" }`，格式与 main.json 一致，可引用 app 组件 |
| `wm.ui.registerWidget(id, config)` | 注册 widget：`{ title?, type = "dropdown" / "bottom_sheet" / "dialog", layout }` |
| `wm.ui.unregister(id)` | 移除该插件注册的组件 / 页面 / widget |

**手势动作**（onClick / onLongClick / onSwipe）取值：

- Lua 函数：`onClick = function() end`
- 唤起界面：`onClick = { page = "pageId" }`
- 唤起 widget：`onClick = { widget = "widgetId" }`
- 显式调用：`onClick = { call = function() end }`

**组件 icon** 使用内置图标名：`play` / `pause` / `prev` / `next` / `shuffle` / `repeat` / `repeat_one` / `queue` / `search` / `settings` / `music` / `library` / `clear` / `delete` / `person` / `disc`；无 icon 时显示标题首字符。

**页面/widget 布局 JSON** 与 main.json 同格式（`main` / `full-player` 结构），存放在插件 zip 内，可引用 app 内置组件（`fp-*`、`play-button`、`text`、`rect` 等）与插件注册的组件。渲染由 ui 模块的 `PluginUiHost` / `PluginUiOverlay` 完成（页面用 Dialog、`bottom_sheet` 用 ModalBottomSheet、`dropdown` 简化为居中圆角卡片浮层）。

**样式统一放在与布局 JSON 同目录的 `style.css`**（与 app 的 main.json + style.css 约定一致）；JSON 只负责定义 slot 结构与组件包含关系，不再写样式。加载顺序：布局 JSON 内联 `style` 字段（兼容旧写法）→ `style.css`（后者覆盖同名规则，为权威样式源）。

**插件组件纯色块**：插件注册的组件在布局/CSS 中命中 `color` 属性时渲染为纯色块（此时不显示 icon / 首字），支持 `size`（默认 48px）与 `border-radius`（默认圆形）。

**`text` 组件 CSS 支持 `content`**（字符串，设置文本内容）：`content` 值带引号（`content: "我的标签"`），优先级为 bind（运行时状态） > CSS `content` > JSON `content`。其余 CSS：`color` / `font-size` / `font-weight` / `font-style` / `text-align`。

**`rect` 组件**：纯展示矩形色块（无内容/无手势）。CSS 支持 `color`（填充色）/ `size`（默认 48px）/ `border-radius`（默认 0，纯矩形）：

```json
{ "rect": {} }
```

```css
#rect-demo { color: #F44336; size: 40px; border-radius: 8px; }
```

**运行中动态生效**：`wm.ui.registerComponent` / `registerSlotComponent` / `registerPage` / `registerWidget` / `unregister` 在插件加载后的任意时刻调用都会即时同步宿主组件注册表（旧 id 自动反注册、新 id 全量重注册），无需重启应用或重进插件界面。

## 播放器事件

`wm.eventBus` 可订阅以下预定义事件（由播放核心的 playerState / progressState 桥接，
广播到所有已加载插件）：

- `trackChanged` — 当前曲目变化，data 为标准 Track 表
- `playStateChanged` — 播放状态变化，data 为 `"playing"`/`"paused"`/`"idle"`/`"loading"`/`"error"`
- `progressUpdated` — 播放进度（约 250ms），data 为 `{ position, duration }`

## 宿主用法

插件系统已接入 `:app`：`WMPlayerApplication` 初始化，全局通过 `PluginHost` 获取。
设置页「插件」分区提供**插件管理入口**，点入独立管理界面（Material 3：
TopAppBar + 已安装插件列表 + 右下角 FAB 安装）：

- 列表条目：插件名 / id / 类别，右侧删除按钮（点击弹确认框后卸载并即时从列表移除）；
- 点击条目打开插件**默认界面**（zip 根目录 `main.json`，缺失时回退到信息页：名称/类别/导出函数/注册 UI 元素）；
- FAB 安装：SAF 选择 zip，安装并自动加载，结果 Snackbar 提示。

```kotlin
// 应用内任意处获取宿主单例（惰性初始化）
val manager = com.winter.muplayer.ui.PluginHost.get(context)

// 安装（挂起函数，后台协程）
val plugin = manager.installFromUrl("https://example.com/plugins/lyrics.zip")

// 加载（注入宿主 API → 执行入口 → 注册导出函数 → 调用 onLoad）
manager.load(plugin.id)

// 宿主高频直调（查表，不进入 Lua 符号查找）
manager.invoke(plugin.id, "getLyrics", LuaValue.varargsOf())

// 卸载（调用 onUnload → 释放定时器/订阅 → 释放专用运行时 → 交还 GC）
manager.unload(plugin.id)
```

线程模型：所有 Lua 执行（事件派发 / 播放事件桥接 / 定时器回调）统一在单线程调度器
（`Dispatchers.IO.limitedParallelism(1)`）上串行执行，保证 LuaJ 环境不被并发访问。

## 协程隔离模型

- **安装协程**：`installFromUrl` 为挂起函数，IO 在 `Dispatchers.IO`，不阻塞 UI；
- **共享运行时调度**：`emit()` 将事件投递到无界 Channel，单一消费者协程串行派发 —— Lua 环境（非线程安全）永不被并发进入；
- **插件间隔离**：同一共享运行时的多个插件可各自持有 Lua 协程栈（`LuaRuntime.createCoroutine`），宿主交替 `resume` 实现时间片；单个插件抛异常不影响其他插件（`SharedEventExecutor` 捕获并记日志）。

## 已实现 / 暂未实现边界

**已实现**：通用基础设施、播放器交互、json/url/crypto(哈希·base64·hex)/http（全异步回调）、剪贴板、UI 扩展（插件组件 / slot 型组件 / 界面 / widget；统一样式文件 style.css；text 组件 CSS `content`；`rect` 色块组件）。

**暂未实现（避免脱离应用）**：

| 参考规格 | 原因 |
|---------|------|
| `audio.*`（FFT/波形/均衡器/音频帧） | 应用当前无 PCM/频谱基础设施（均衡器在计划中），需先建设音频分析管线 |
| `buffer` / `iconv`（GBK/Big5） | GBK 等字符集在 Android 上支持不完整；`buffer` 可按需补 utf8/hex/base64 |
| `crypto` AES/RSA/HMAC、`randomBytes` | Java 可支持，但当前应用无远程音源加密场景 |
| `system` 媒体键/通知/壁纸/电池/屏幕、`cache/cookie/session` | 媒体键需桥接 MediaSession 回调；其余为边缘能力，可后续按需接入 |

## 已知技术边界

- LuaJ 无 JIT，`useNativeComputation = true` 时为纯解释执行；高性能场景需接入其他后端（接口预留）。
- Android 上不可用 `JsePlatform` / luajava（依赖 `java.beans` / JSR-223），运行时由 `LuaRuntimeFactory` 显式装配标准库，宿主 API 走 `HostFunction` 桥。
- mmap 映射区域的回收由 GC 管理；插件卸载后 `BytecodeCache.prune()` 可清理孤儿缓存。
- HTTP 全部为异步回调模式（IO 线程执行、单线程 Lua 调度器回调），不阻塞任何 Lua 调用。
