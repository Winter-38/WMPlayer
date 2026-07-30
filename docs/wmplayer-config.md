# WMPlayer 配置文档

## 目录

1. [文件结构](#1-文件结构)
2. [JSON 配置详解](#2-json-配置详解)
   - [slots 数组](#21-slots-数组)
   - [组件引用](#22-组件引用)
   - [对象格式引用](#23-对象格式引用)
   - [自定义组件](#24-自定义组件)
   - [嵌套子 slot](#25-嵌套子-slot)
   - [全屏播放器 slot](#26-全屏播放器-slot)
   - [多文件合并](#27-多文件合并)
   - [注释支持](#28-注释支持)
3. [CSS 配置详解](#3-css-配置详解)
   - [选择器规则](#31-选择器规则)
   - [布局属性](#32-布局属性)
   - [视觉属性](#33-视觉属性)
   - [对齐属性](#34-对齐属性)
   - [动画属性](#35-动画属性)
   - [值单位](#36-值单位)
   - [CSS 属性速查表](#37-css-属性速查表)
4. [内置组件参考](#4-内置组件参考)
   - [界面组件](#41-界面组件)
   - [全屏播放器组件](#42-全屏播放器组件)
5. [完整示例](#5-完整示例)

---

## 1. 文件结构

配置文件存储在 `{外部存储}/config/` 目录。

```
config/
├── main.json              # 入口配置（必需）
├── styles.css             # 样式定义（可选，文件名任意）
└── components.json        # 可通过 include 拆分（可选）
```

**加载规则：**

- `main.json` 是唯一入口，不存在则回退硬编码默认布局
- `config/` 目录下所有 `.css` 文件按文件名升序合并，后加载覆盖同名 key
- JSON 通过 `include` 数组引用其他文件，支持嵌套

### 回退默认值

当 `main.json` 不存在时，系统使用硬编码默认布局：

| slot | 组件 |
|------|------|
| `app-top` | app-name, search-button, setting-button |
| `app-center` | tab-bar, sort, playlist |
| `app-bottom` | playbar |
| `main`（全屏） | track-info, progress-bar, controls-row |

---

## 2. JSON 配置详解

### 2.1 slots 数组

slot 的排列顺序由 `"slots"` 数组的索引决定，**不依赖 JSON object key 顺序**。这是唯一被接受的 slot 声明方式。

```json
{
  "slots": [
    { "app-top": ["app-name", "search-button", "setting-button"] },
    { "app-center": ["tab-bar", "sort", "playlist"] },
    { "app-bottom": ["playbar"] }
  ]
}
```

每个数组元素是一个单 key 对象，key 为 slot 名，value 为组件数组。slot 名的渲染顺序 = 数组索引。

### 2.2 组件引用

#### 原生组件（无 `#` 前缀）

```json
["app-name", "search-button", "setting-button"]
```

原生组件名不带 `#`，系统在 `ComponentRegistry` 中查找注册的渲染器。

#### 自定义组件（带 `#` 前缀）

```json
["#my-button", "#custom-header"]
```

自定义组件名必须以 `#` 开头，且需要在顶层定义该组件的属性（见 2.4 自定义组件）。

#### 实例标识（`@cid` 简写）

通过 `@` 附加可选实例标识，用于 CSS 精准定位特定实例：

```json
["#icon@search-icon", "#icon@settings-icon", "playbar"]
```

等价于：
```json
[{ "#icon@search-icon": { "icon": "ic_search" } }, { "#icon@settings-icon": { "icon": "ic_settings" } }, "playbar"]
```

**规则：**
- 原生组件不许写 `#`，否则会被视为未定义的自定义组件而跳过渲染
- 自定义组件必须写 `#`，否则会被视为原生组件而查找注册表

### 2.3 对象格式引用

当组件需要传递额外参数时，JSON key 直接作为组件名，value 对象作为参数：

```json
{ "#icon@search-icon": { "icon": "ic_search" } }
```

key 有 `#` 前缀 → 自定义组件；无 `#` 前缀 → 内置组件。
`cid` 只能从 key 的 `@` 语法取（见 2.2），value 对象中声明无效。

**支持字段（value 对象的保留 key）：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `class` | string | 已废弃（不再使用） |
| `children` | object | 嵌套子 slot 定义（见 2.5） |
| 其他 | 任意 | 作为 `extra` 参数传入组件，具体由各组件自行消费 |

**示例：带参数的内置组件**

```json
[{ "icon": { "icon": "ic_search" } }]
```

**示例：带参数的自定义组件**

```json
[{ "#my-button": { "action": "toggleSearch" } }]
```

### 2.4 自定义组件

自定义组件通过在顶层以 `#xxx` 为 key 定义：

```json
{
  "slots": [
    { "sidebar": ["#my-button", "#custom-header"] }
  ],
  "#my-button": {
    "icon": "ic_search",
    "onClick": "toggleSearch"
  },
  "#custom-header": {
    "icon": "ic_settings",
    "onClick": "openSettings"
  }
}
```

**支持属性：**

| 属性 | 类型 | 说明 |
|------|------|------|
| `icon` | string | drawable 资源名（如 `"ic_search"`），在 `R.drawable` 中查找 |
| `onClick` | string | 点击行为标识（由应用代码解释） |

自定义组件的外观（尺寸、颜色）通过 CSS 控制：

```css
#my-button { color: #ff6b6b; size: 24px; }
```

### 2.5 嵌套子 slot

通过 `children` 字段在组件内部定义子 slot：

```json
{
  "container": {
    "children": {
      "sidebar": ["tab-bar", "sort"],
      "main-content": ["playlist"]
    }
  }
}
```

children 的 value 与顶层 `slots` 数组元素格式一致——每个 key 对应一个子 slot，value 为组件数组。

也可以使用隐式子 slot（所有 value 都是数组的 JSON 对象）：

```json
[{ "sidebar": ["tab-bar"], "content": ["playlist"] }]
```

系统自动识别为子 slot 容器。

### 2.6 全屏播放器 slot

全屏播放器的组件用独立的 `main` key 定义：

```json
{
  "slots": [
    { "app-top": ["app-name"] },
    { "app-bottom": ["playbar"] }
  ],
  "main": ["track-info", "progress-bar", "controls-row"]
}
```

全屏播放器的样式由 `.main` CSS 选择器控制。

### 2.7 多文件合并

通过 `include` 数组引用其他 JSON 文件：

**main.json：**

```json
{
  "include": ["style.json"],
  "slots": [
    { "app-top": ["app-name", "search-button"] }
  ]
}
```

**style.json：**

```json
{
  "slots": [
    { "app-center": ["tab-bar", "playlist"] }
  ],
  "#my-button": { "icon": "ic_custom" }
}
```

**合并规则：**
- Include 文件先解析，主文件后解析，后加载的 `slots` 追加到已有数组末尾
- 后加载的非数组值覆盖先加载的同名 key
- `visited` 集合检测循环引用


## 3. CSS 配置详解

### 3.1 选择器规则

| 选择器 | 作用目标 | 示例 |
|--------|----------|------|
| `.layout` | 最外层容器 | 控制 slot 之间的排列方向 |
| `.slot-name` | 指定 slot（`slots` 数组中的 key） | 控制该 slot 的尺寸和内部排列 |
| `#component-id` | 指定组件类型 | 所有该类型的组件共享此样式 |
| `#cid-name` | 指定组件实例（`cid`） | 只作用于有该 `cid` 的单个实例 |

**CSS 层叠规则：**

`#<cid>` 的规则叠加覆盖 `#<type>` 的规则（同名属性以 cid 为准）：

```css
/* 所有 icon 默认 24px */
#icon { size: 24px; }

/* 搜索图标单独改颜色和尺寸 */
#search-icon { color: #ff6b6b; size: 28px; }
```

**优先级：** 后加载的 CSS 文件覆盖先加载的同名选择器。同名选择器内后出现的属性覆盖先出现的属性。

**CSS 选择器与 JSON 的关联方式：**

```
JSON:  { "sidebar": ["tab-bar", "sort"] }
          ↑ slot 名                 ↑ 组件 ID
CSS:   .sidebar { ... }          #tab-bar { ... }

带 cid：
JSON:  ["#icon@search-icon"]
                        ↑ cid
CSS:  #search-icon { ... }
```

### 3.2 布局属性

#### arrange

控制容器排列方向。

| 值 | 效果 |
|----|------|
| `column` | 垂直堆叠（默认） |
| `row` | 水平排列 |
| `vertical` | 同 `column` |
| `horizontal` | 同 `row` |

**使用层级：**

- `.layout { arrange: column/row }` — 控制 slot 之间的排列方向
- `.slot-name { arrange: column/row }` — 控制 slot 内部组件的排列方向

#### weight

控制 slot 或组件在主轴方向的比例。

| 值 | 效果 |
|----|------|
| `1` | 默认值，与其他 slot 平分空间 |
| `2`, `3`, ... | 按比例分配（如 2:1:1） |
| `0` | 包裹内容，不参与空间分配 |

**注意：** `weight: 0` 时内层容器不会撑满，子组件的 `width` / `height` 属性生效。

#### gap

控制子元素之间的间距。

```css
.app-top { gap: 8px; }
```

单位：`px` 或 `dp`，也可省略写纯数字。

### 3.3 视觉属性

| 属性 | 值格式                     | 说明 |
|------|-------------------------|------|
| `width` | `200px` / `200dp` / `200` | 固定宽度 |
| `height` | `56px` / `56dp` / `56`  | 固定高度 |
| `size` | `48px` / `48dp` / `48`  | 等宽高（同时设置 width 和 height） |
| `fillMaxWidth` | `true`                  | 在 Column 内撑满父容器宽度 |
| `background-color` | `#ff6b6b` / `#ff6b6bff` | 背景色，支持 6 位（RGB）或 8 位（ARGB）十六进制 |
| `border-radius` | `12px` / `12dp` / `12`  | 圆角大小 |
| `padding` | 8px                     | 内边距 |
| `padding-top` | `8px`                   | 单独覆盖上边距 |
| `padding-right` | `8px`                   | 单独覆盖右边距 |
| `padding-bottom` | `8px`                   | 单独覆盖下边距 |
| `padding-left` | `8px`                   | 单独覆盖左边距 |
| `opacity` | `0.5`                   | 透明度（0.0 ~ 1.0） |
| `scale` | `1.2`                   | 缩放比例 |
| `rotate` | `90deg` / `90`          | 旋转角度 |
| `overflow` | `hidden`                | 裁剪溢出内容 |
| `color` | `#ff6b6b`               | 文字/图标颜色（组件内部使用） |

#### padding 格式

```css
padding: 8px              /* 四边统一 */
padding: 8px 16px         /* 上下 8px，左右 16px */
padding: 8px 12px 16px 20px  /* 上 右 下 左 */
```

### 3.4 对齐属性

#### align-self

控制组件在 slot 内的对齐方式。使用方位名，不依赖 `arrange` 方向。

| 值 | 效果 | Compose Alignment |
|----|------|-------------------|
| `top` | 顶部居中 | TopCenter |
| `bottom` | 底部居中 | BottomCenter |
| `left` | 左边缘居中 | CenterStart |
| `right` | 右边缘居中 | CenterEnd |
| `top-left` | 左上角 | TopStart |
| `top-right` | 右上角 | TopEnd |
| `bottom-left` | 左下角 | BottomStart |
| `bottom-right` | 右下角 | BottomEnd |
| `center` | 绝对居中（双轴） | Center |
| `stretch` | 填满 slot 宽高 | fillMaxSize |

**注意：** `stretch` 和带填充的居中（`center`/`top`/`bottom`/`left`/`right`等）需要 slot 有 `weight` 提供可用空间。slot `weight: 0` 时包裹内容，无剩余空间可用。

### 3.5 动画属性

#### animation

```css
animation: <name> <duration> [easing] [count]
```

**内置动画名：**

| 名称 | 效果 |
|------|------|
| `spin` | 无限旋转 |
| `pulse` | 缩放脉冲（1.0 ↔ 1.15） |
| `bounce` | 垂直弹跳 |
| `fade-in` | 渐显（播放一次） |

**时长：** `3s`（秒）或 `3000ms`（毫秒），默认 `1000ms`

**缓动函数：**

| 值 | 说明 |
|----|------|
| `linear` | 线性 |
| `ease-in` | 缓入 |
| `ease-out` | 缓出 |
| `ease-in-out` | 缓入缓出（默认） |

**示例：**

```css
/* 3 秒线性无限旋转 */
#search-button { animation: spin 3s linear infinite; }

/* 2 秒脉冲 */
#play-button { animation: pulse 2s ease-in-out; }
```

### 3.6 值单位

| 单位 | 适用属性 | 示例 |
|------|----------|------|
| `px` | size, width, height, gap, padding, border-radius | `200px` |
| `dp` | 同上 | `16dp` |
| 无单位 | 同上（默认 px/dp） | `8` |
| `s` | animation duration | `3s` |
| `ms` | animation duration | `3000ms` |
| `deg` | rotate | `90deg` |

### 3.7 CSS 属性速查表

#### Slot 级属性（在 `.slot-name` 中设置）

| 属性 | 类型 | 说明 |
|------|------|------|
| `arrange` | `row` / `column` | 内部组件排列方向 |
| `weight` | 数字 | 空间分配比例，`0`=包裹内容 |
| `gap` | 长度 | 子元素间距 |
| `width` | 长度 | slot 固定宽度（需 `weight: 0`） |
| `height` | 长度 | slot 固定高度（需 `weight: 0`） |
| `background-color` | 颜色 | slot 背景色 |
| `padding` | 长度/组 | slot 内边距 |

#### 组件级属性（在 `#component-id` 中设置）

| 属性 | 类型 | 说明 |
|------|------|------|
| `weight` | 数字 | 组件在 slot 内的空间分配 |
| `width` | 长度 | 固定宽（需 slot `weight: 0`） |
| `height` | 长度 | 固定高 |
| `size` | 长度 | 等宽高 |
| `fillMaxWidth` | `true` | 撑满宽度 |
| `background-color` | 颜色 | 背景色 |
| `border-radius` | 长度 | 圆角 |
| `padding` | 长度/组 | 内边距 |
| `opacity` | 0.0~1.0 | 透明度 |
| `scale` | 数字 | 缩放 |
| `rotate` | 角度 | 旋转 |
| `overflow` | `hidden` | 裁剪 |
| `align-self` | `top`/`bottom`/`left`/`right`/`top-left`/`top-right`/`bottom-left`/`bottom-right`/`center`/`stretch` | 对齐方式 |
| `animation` | 动画值 | 动画效果 |
| `color` | 颜色 | 文字/图标颜色（组件内部使用） |

#### 根容器属性（在 `.layout` 中设置）

| 属性 | 类型 | 说明 |
|------|------|------|
| `arrange` | `row` / `column` | slot 之间的排列方向 |
| 其他 | 同上 | 应用到最外层容器 |

---

## 4. 内置组件参考

### 4.1 界面组件

#### app-name

显示应用名称文字。受 CSS `color` 控制。

```json
"app-name"
```

```css
.app-name { color: #ffffff; }
```

#### search-button

搜索按钮图标。

```json
"search-button"
```

```css
.search-button { color: #666666; size: 24px; }
```

#### setting-button

设置按钮图标。

```json
"setting-button"
```

#### tab-bar

音乐分类标签栏（全部/歌手/专辑），始终竖向排列为 FilterChip 列表。

```json
"tab-bar"
```

#### sort

音乐排序面板，显示曲目统计和排序选项。始终竖向排列。

```json
"sort"
```

#### playlist

音乐列表区域。包含长按菜单（添加到播放列表/删除歌曲）和播放列表选择器。

```json
"playlist"
```

#### playbar

底部迷你播放栏。显示当前歌曲封面、标题、上一首/播放暂停/下一首/列表按钮。

```json
"playbar"
```

#### icon

通用图标组件，通过 `extra` 参数指定图标资源。

```json
{ "icon": { "icon": "ic_search" } }
```

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `icon` | string | drawable 资源名，在 `R.drawable` 中查找 |

**CSS 支持：**

```css
.my-icon { color: #ff6b6b; size: 32px; }
```

找不到图标时渲染一个彩色圆点作为回退标记。

#### text

通用文本组件，通过 `extra` 参数指定显示内容。

```json
{ "text": { "content": "Hello World" } }
{ "#text@my-label": { "content": "我的标签" } }
```

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `content` | string | 显示文本，不设时显示 `(text)` |

**CSS 支持：**

| 属性 | 值示例 | 说明 |
|------|--------|------|
| `color` | `#ffffff` | 文字颜色 |
| `font-size` | `16px` / `20dp` | 字号 |
| `font-weight` | `bold` / `normal` / `600` / `light` | 字重，支持关键词和数字 |
| `font-style` | `italic` / `normal` | 斜体 |
| `text-align` | `left` / `center` / `right` | 对齐 |

```css
#text { color: #ffffff; font-size: 16px; font-weight: bold; }
```

#### old-playlist

向后兼容的旧版复合组件，包含 MusicBrowserTabs + MusicBrowserSort + MusicBrowserList。不推荐新配置使用。

```json
"old-playlist"
```

### 4.2 全屏播放器组件

全屏播放器的组件通过 JSON 的 `main` key 定义，CSS 样式由 `.main` 控制。

#### track-info

显示当前歌曲信息。支持自适应宽度响应式渲染：
- `>= 200dp`：标题 + 歌手/专辑
- `>= 120dp`：紧凑单行「标题 — 歌手」
- `< 120dp`：仅图标

#### progress-bar

播放进度条。支持自适应宽度：
- 水平 slot：紧凑行内布局（时间 + 滑块 + 时间）
- 垂直 slot：宽屏布局（滑块 + 底部时间）
- `< 180dp`：仅滑块

#### controls-row

播放控制按钮。支持自适应宽度：
- `< 160dp`：仅播放模式 + 播放暂停
- `< 200dp`：增加上下首按钮
- `>= 200dp`：显示全部按钮

---

## 5. 完整示例

### main.json

```json
{
  // 通用布局
  "slots": [
    { "top-bar": ["app-name", "search-button", "setting-button"] },
    { "sidebar": ["tab-bar", "sort"] },
    { "main-content": ["playlist"] },
    { "bottom-bar": ["playbar"] }
  ],
  // 全屏播放器
  "main": ["track-info", "progress-bar", "controls-row"],
  // 自定义组件
  "#favorite-btn": {
    "icon": "ic_favorite",
    "onClick": "toggleFavorite"
  }
}
```

### styles.css

```css
/* ═══════════════════════════════
   外层容器 — slot 排列方向
   ═══════════════════════════════ */
.layout { arrange: column; }

/* ═══════════════════════════════
   Slot 样式
   ═══════════════════════════════ */

/* 顶部栏：水平排列，固定高度 */
.top-bar {
  arrange: row;
  weight: 0;
  height: 56px;
  gap: 8px;
  padding: 0 16px;
  background-color: #1a1a2e;
}

/* 侧边栏：垂直排列，固定宽度 */
.sidebar {
  arrange: column;
  weight: 0;
  width: 200px;
  gap: 4px;
  background-color: #16213e;
}

/* 主内容区：填满剩余空间 */
.main-content { weight: 1; }

/* 底部播放栏：水平排列，固定高度 */
.bottom-bar {
  arrange: row;
  weight: 0;
  height: 72px;
  background-color: #0f3460;
}

/* 全屏播放器：垂直排列 */
.main { arrange: column; gap: 8px; }

/* ═══════════════════════════════
   组件样式
   ═══════════════════════════════ */

/* 侧边栏 tab 固定宽度 */
#tab-bar { weight: 0; }

/* 搜索按钮动画 */
#search-button { animation: spin 3s linear infinite; }

/* 自定义按钮样式 */
#favorite-btn { color: #e94560; size: 28px; }

/* 播放列表背景 */
#playlist { background-color: #1a1a2e; }
```
