# WMPlayer 配置文档

## 目录

1. [文件结构](#1-文件结构)
2. [JSON 配置详解](#2-json-配置详解)
   - [main 声明](#21-main-声明)
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

### 2.1 main 声明

支持两种形式：

**对象形式（新）**：界面的内容为对象 `{}`，里面不同的名字对应不同 slot：

```json
{
  "main": {
    "app-top": ["app-name", "search-button", "setting-button"],
    "app-center": ["tab-bar", "sort", "playlist"],
    "app-bottom": ["playbar"]
  }
}
```

**数组形式（旧，仍兼容）**：顺序由 `"main"` 数组的索引决定，不依赖 object key 顺序：

```json
{
  "main": [
    { "app-top": ["app-name", "search-button", "setting-button"] },
    { "app-center": ["tab-bar", "sort", "playlist"] },
    { "app-bottom": ["playbar"] }
  ]
}
```

每个数组元素是一个单 key 对象，key 为 slot 名，value 为组件数组。slot 名的渲染顺序 = 数组索引（对象形式按解析顺序）。

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
  "main": {
    "sidebar": ["#my-button", "#custom-header"]
  },
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

**命名子 slot（推荐）**：在组件数组内嵌 `{ "name": ..., "children": [...] }`：

```json
{
  "slot1": [
    "normal-compose1",
    { "name": "children-slot", "children": ["child-compose1", "child-compose2"] },
    "normal-compose2"
  ]
}
```

`name` 是该子 slot 的名字（CSS 用 `.name` 定位），`children` 为组件数组。

**slot 型组件**：若 slot / 容器名与已知 slot 型组件 id 一致（如 `fp-backdrop`），解析为 slot 型组件——自身渲染为背景层（fillMaxSize），children 作为前景层叠加：

```json
{
  "full-player": {
    "fp-backdrop": ["fp-title", "fp-cover"]
  }
}
```

- 数组形式的 children 自动包装为单个子 slot，子 slot 名 = 容器组件 id（如 `fp-backdrop` 的 children 用 `.fp-backdrop` 定位）
- 对象形式的 children 作为子 slot 字典（每个 key 一个子 slot）

也可以使用隐式子 slot（所有 value 都是数组的 JSON 对象）：

```json
[{ "sidebar": ["tab-bar"], "content": ["playlist"] }]
```

系统自动识别为子 slot 容器。

### 2.6 全屏播放器 slot

全屏播放器的组件用独立的 `full-player` key 定义。默认布局由细分组件组装：`fp-backdrop` 作为背景层容器（封面模糊背景），通过 `children` 将前景内容叠加其上。`full-player` 的值是对象，不同名字对应不同 slot；`fp-backdrop` 是 slot 型组件：

```json
{
  "main": {
    "app-top": ["app-name"],
    "app-bottom": ["playbar"]
  },
  "full-player": {
    "fp-backdrop": [
      "fp-title",
      "fp-subtitle",
      { "name": "main-cover", "children": ["fp-cover"] },
      "fp-progress",
      "controls-row"
    ]
  }
}
```

前景层布局：`fp-title`（标题）与 `fp-subtitle`（歌手 + 专辑）位于内容区**顶部、左对齐（左上角）**；随后依次为 `fp-cover`（主封面）、`fp-progress`（进度条）、`controls-row`（播放操控按钮组）。

**布局说明：**
- 带 `children` 的组件是「容器组件」：自身渲染为背景层（`fillMaxSize` 铺满），`children` 中的子 slot 作为前景层叠加在背景之上
- 容器组件未显式设置 `weight` 时默认铺满父容器（如 `fp-backdrop` 铺满整个面板）
- **全屏播放器与主界面样式互不影响**：全屏是独立渲染树，外层排列方向由 `.full-player { arrange }` 控制（不读取主界面的 `.main`）；容器组件 `children` 中多个子 slot 之间的排列方向由容器自身 CSS 控制（如 `#fp-backdrop { arrange: row }`），未设置时继承 `.full-player` 的方向
- 标题/副标题默认左对齐显示在内容区顶部；如需调整位置（居中、靠右）或让封面居中，可通过 CSS `align-self` / `weight` / `size` 定制，见下方示例
- 未配置 `full-player` 时使用内置默认布局（与上例相同的细分组件组装）

全屏播放器的样式由 `.full-player` CSS 选择器控制。

### 2.7 多文件合并

通过 `include` 数组引用其他 JSON 文件：

**main.json：**

```json
{
  "include": ["style.json"],
  "main": {
    "app-top": ["app-name", "search-button"]
  }
}
```

**style.json：**

```json
{
  "main": {
    "app-center": ["tab-bar", "playlist"]
  },
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
| `.main` | 主界面最外层容器 | 控制 slot 之间的排列方向 |
| `.slot-name` | 指定 slot（`main` 数组中的 key） | 控制该 slot 的尺寸和内部排列 |
| `#component-id` | 指定组件类型 | 所有该类型的组件共享此样式 |
| `#cid-name` | 指定组件实例（`cid`） | 只作用于有该 `cid` 的单个实例 |

**CSS 覆盖规则（重要）：**

规则按「整块替换」进行，不是标准 CSS 的逐属性级联：

- **跨文件**：`config/` 下所有 `.css` 按文件名升序加载，后加载文件中同名的选择器规则块会整体替换先加载文件中的同名规则块（不会逐属性合并）。
- **同一文件内**：同名选择器重复出现时，后出现的规则块整体替换先出现的规则块。
- **同一规则块内**：同名属性后写覆盖先写。

```css
/* a.css */
#icon { size: 24px; color: #ffffff; }

/* b.css —— 整块替换 a.css 的 #icon，color 会丢失 */
#icon { size: 28px; }
```

`#<cid>` 对 `#<type>` 是唯一例外：二者按**属性级叠加**，同名属性以 cid 为准，非同名属性保留。

```css
/* 所有 icon 默认 24px */
#icon { size: 24px; }

/* 搜索图标：size 覆盖为 28px，color 为新增 */
#search-icon { color: #ff6b6b; size: 28px; }
```

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

- `.main { arrange: column/row }` — 控制主界面 slot 之间的排列方向
- `.slot-name { arrange: column/row }` — 控制 slot 内部组件的排列方向
- `.full-player { arrange: column/row }` — 控制全屏播放器外层方向（独立渲染树，不读 `.main`）
- 容器组件 `#fp-backdrop { arrange: row }` — 控制其 children 多个子 slot 之间的排列方向

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

控制组件在 slot 交叉轴方向的对齐方式。仅支持 `start` / `center` / `end` 三个值，映射随 slot 的 `arrange` 方向变化：

| 值 | Row（水平排列）内 | Column（垂直排列）内 |
|----|------------------|---------------------|
| `start` | 顶部对齐 | 左边缘对齐 |
| `center` | 垂直居中 | 水平居中 |
| `end` | 底部对齐 | 右边缘对齐 |

未设置或填写了其他值（如 `top`、`left`、`stretch`）时，组件默认填满 slot 的交叉轴（等效 `stretch`）。

```css
/* 垂直排列的 slot 内，让组件靠左对齐 */
.sidebar { arrange: column; }
#tab-bar { align-self: start; }
```

### 3.5 动画属性

#### animation

```css
animation: <name> <duration> [easing]
```

**内置动画名：**

| 名称 | 效果 |
|------|------|
| `spin` | 旋转（无限循环） |
| `pulse` | 缩放脉冲 1.0 ↔ 1.15（无限循环） |
| `bounce` | 垂直弹跳（无限循环） |
| `fade-in` | 渐显（播放一次） |

`spin` / `pulse` / `bounce` 始终无限循环，`fade-in` 播放一次。当前实现不支持有限循环次数。

**时长：** `3s`（秒）或 `3000ms`（毫秒），默认 `1000ms`

**缓动函数：**

| 值 | 说明 |
|----|------|
| `linear` | 线性 |
| `ease-in` | 当前与 `ease-in-out` 等价 |
| `ease-out` | 当前与 `ease-in-out` 等价 |
| `ease-in-out` | 缓入缓出（默认） |

> 当前实现中 `ease-in` / `ease-out` / `ease-in-out` 三者映射到同一条缓动曲线，只有 `linear` 有实际区别。

**示例：**

```css
/* 3 秒线性旋转（无限循环） */
#search-button { animation: spin 3s linear; }

/* 2 秒脉冲（无限循环） */
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
| `justify-content` | `start`/`center`/`end`/`space-between`/`space-evenly`/`space-around` | 子组件在主轴方向的排列方式 |

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
| `align-self` | `start`/`center`/`end` | 交叉轴对齐（见 3.4） |
| `content-align` | `start`/`center`/`end` | 组件内容在自身 Box 内的对齐（默认 `center`） |
| `animation` | 动画值 | 动画效果 |
| `color` | 颜色 | 文字/图标颜色（组件内部使用） |

#### 根容器属性（在 `.main` 中设置）

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

音乐分类标签栏（全部/歌手/专辑）。CSS 属性控制形态：

- `display: column` → 竖向 FilterChip 胶囊堆叠
- `style: pills` → 横向胶囊 FilterChip 行（原默认样式）
- 缺省或 `style: tabs` → 横向 PrimaryTabRow 标签栏（默认样式，底部指示器）

```css
#tab-bar { }                /* 默认标签栏样式 */
#tab-bar { style: pills; }  /* 胶囊行样式 */
```

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

**条目样式（CSS `#playlist` 的 `item-*` 属性）：**

| 属性 | 说明 |
|------|------|
| `item-bg` / `item-background` | 条目背景色（hex / rgb() / rgba()） |
| `item-radius` | 条目圆角 |
| `item-color` / `item-text-color` | 歌名颜色 |
| `item-font-size` | 歌名字号 |
| `item-sub-color` | 歌手 • 专辑颜色 |
| `item-sub-size` | 副文字字号 |
| `item-font-family` | 字体族（serif / monospace / cursive） |
| `item-layout` | 条目布局：`list`（横向行，封面左/文字右，默认）或 `grid`（封面卡片：封面在上，下方曲名 + 歌手名） |
| `item-columns` | grid 布局列数（默认 2，范围 1..6） |

```css
/* 封面卡片网格：两列，封面在上、曲名与歌手名在下 */
#playlist { item-layout: grid; item-columns: 2; item-radius: 14px; }
```

#### playbar

底部迷你播放栏。显示当前歌曲封面、标题、上一首/播放暂停/下一首/列表按钮。

```json
"playbar"
```

**渲染样式（CSS `render-style`，四态，设置页可切换）：**

| 值 | 效果 |
|----|------|
| `none`（默认） | 不透明悬浮卡片（overlay 叠放，与其他样式布局一致） |
| `semi-tran` | 半透明浮层（露出下方内容，不模糊） |
| `blur` | 毛玻璃（内容实时模糊 + 半透明表面 + 常驻轻高光，无折射） |
| `liquid` | 液态玻璃（按原版示例组装：vibrancy + blur(8dp) + lens(28dp, 36dp) 折射/色散 + 常驻轻高光 + 轻阴影） |

```css
#playbar { render-style: liquid; blur-radius: 8dp; }
```

所有渲染样式（含 `none`）统一使用 overlay 布局（`app-center` 内叠放 playbar，播放栏悬浮叠加在内容之上）；设置页切换样式或启动初始化时自动改写 `main.json`，线性旧布局会自动升级为 overlay。

**玻璃参数（CSS `#playbar`，设置页滑块写入；毛玻璃与液态玻璃模式均显示参数块，模糊度/高光两模式共用，折射相关仅液态玻璃生效）：**

| 属性 | 默认 | 说明 |
|------|------|------|
| `blur-radius` | `8dp`（liquid）/ `12dp`（blur） | 模糊半径（dp 语义，设置页滑块可调，毛玻璃/液态玻璃共用） |
| `liquid-edge` | `28dp` | 边缘隆起宽度（折射带，dp 语义，边缘一圈折射、中心清晰） |
| `liquid-refraction` | `36dp` | 折射强度（dp 语义，原版示例值调高） |
| `liquid-opacity` | `0.15` | 表面基色不透明度（0..1，越小越透明） |
| `liquid-specular` | `0.45` | 高光强度（无单位） |
| `liquid-shininess` | `48` | 高光锐度（无单位） |
| `liquid-rim` | `0.3` | rim 边缘亮线强度（无单位） |
| `liquid-chromatic` | `0` | 色散强度 0..1（默认关闭，CSS 手配） |

液态玻璃（liquid）模式还常驻内阴影（玻璃厚度感）与顶部反光渐变，随参数块一并生效；`none` 模式为纯色卡片（无多余背景层）。

#### spacer

弹性空白占位组件，自身不渲染内容，`weight` 由 CSS 控制，用于把相邻组件推到两端。

```json
"spacer"
```

```css
/* 占据剩余空间，把两侧组件分开 */
#spacer { weight: 1; }
```

默认布局的 `app-top` 用它把应用名与右侧按钮分隔开。

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
| `bind` | string | 数据绑定键，引用播放器运行时状态，优先级高于 `content` |

**数据绑定（bind）：**

```json
{ "text": { "bind": "track.title" } }
{ "text": { "bind": "position" } }
```

| bind 键 | 说明 |
|---------|------|
| `track.title` | 当前歌曲标题 |
| `track.artist` | 当前歌曲歌手 |
| `track.album` | 当前歌曲专辑 |
| `track.artistAlbum` | "歌手 • 专辑" 组合文本 |
| `track.duration` | 当前歌曲总时长（MM:SS） |
| `position` | 当前播放位置（MM:SS） |
| `duration` | 播放总时长（MM:SS，来自进度追踪器） |

bind 未命中时显示 `(bind:<键>)` 便于排查。

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

#### icon-button

通用图标按钮，`icon` 指定 drawable 资源名，`action` 指定点击行为。

```json
{ "icon-button": { "icon": "ic_search", "action": "toggleSearch" } }
```

**参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| `icon` | string | drawable 资源名 |
| `action` | string | `toggleSearch` / `openSettings` / `previous` / `next` / `openQueue` / `openFullPlayer` / `togglePlay` |

**CSS 支持：** `color`（tint）/ `size`（尺寸）

#### cover

专辑封面缩略图，显示当前播放曲目封面，无曲目/无封面时回退占位图标。

```json
"cover"
```

**CSS 支持：** `size`（尺寸，默认 48dp）

#### progress-slider

进度条滑块，订阅播放进度，拖动时 seek。与 `text` + `bind position/duration` 组合即可拼出完整进度条。

```json
"progress-slider"
```

#### play-button / prev-button / next-button / playmode-button / queue-button

播放控制原子按钮，可独立放在任意 slot。

```json
["prev-button", "play-button", "next-button"]
```

**CSS 支持：** `color`（tint）；`prev-button` / `next-button` / `queue-button` 另支持 `size`。

### 4.2 全屏播放器组件

全屏播放器的组件通过 JSON 的 `full-player` key 定义，CSS 样式由 `.full-player` 控制。

#### fp-backdrop

全屏背景层容器。渲染当前曲目封面并做模糊处理铺满全屏（封面模糊背景）；设置中关闭模糊背景时退化为纯色背景。作为容器组件使用时，将前景组件放入其 `children` 即可叠加在背景之上。

`#fp-backdrop` 的 `arrange` 控制 children 中**多个子 slot 之间的排列方向**（如让 `fp-space-left` / `fp-main` / `fp-space-right` 横向排列）：

```css
#fp-backdrop { arrange: row; }
.fp-space-left, .fp-space-right { weight: 0; }
.fp-main { weight: 1; }
```

```json
{
  "fp-backdrop": {
    "children": {
      "fp-space-left": ["spacer"],
      "fp-main": ["..."],
      "fp-space-right": ["spacer"]
    }
  }
}
```

```json
{
  "fp-backdrop": {
    "children": {
      "fp-backdrop": ["fp-cover", "fp-title", "controls-row"]
    }
  }
}
```

#### fp-cover

全屏主封面。大尺寸方形封面（圆角 + 阴影），切歌时保持旧封面直到新封面加载完成，加载完成后 Crossfade 平滑过渡；无封面时显示占位图标。

```json
"fp-cover"
```

#### fp-title

全屏标题。显示当前曲目标题（粗体大字号），颜色跟随自适应色调（模糊背景开启时根据封面亮度自动调整）。

```json
"fp-title"
```

#### fp-subtitle

全屏歌手 + 专辑。显示「歌手 • 专辑」，颜色为自适应色调的半透明版本。

```json
"fp-subtitle"
```

#### fp-progress

全屏进度条。滑块 + 当前时间 + 总时长，拖动滑块即时 seek。

```json
"fp-progress"
```

**CSS 支持：** `color`（滑块主色）

---

以下为聚合/自适应变体组件（旧配置仍兼容，可继续使用）：

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
  // 通用布局（对象形式：不同名字对应不同 slot）
  "main": {
    "top-bar": ["app-name", "search-button", "setting-button"],
    "sidebar": ["tab-bar", "sort"],
    "main-content": ["playlist"],
    "bottom-bar": ["playbar"]
  },
  // 全屏播放器（fp-backdrop 为 slot 型组件：背景层 + 前景）
  "full-player": {
    "fp-backdrop": [
      "fp-title",
      "fp-subtitle",
      { "name": "main-cover", "children": ["fp-cover"] },
      "fp-progress",
      "controls-row"
    ]
  },
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
   主界面外层容器 — slot 排列方向
   ═══════════════════════════════ */
.main { arrange: column; }

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
.full-player { arrange: column; gap: 8px; }

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

---

## 6. 开发者调试

### 显示 slot / 组件边界

`ui/.../MusicUIActivity.kt` 中 `MusicPlayerApp` 内的 `isDebug` 开关设为 `true` 时，
SlotRenderer 会绘制调试信息：

- **slot 级**：3dp 彩色边框（红/绿/蓝/紫/黄轮询）+ 6% 透明度背景色块
- **组件级**：12% 透明度色块覆盖在组件上
- 嵌套子 slot（`.fp-backdrop` / 命名子 slot 等）同样生效
- 调试标签叠加在独立层（`matchParentSize`），**不参与布局测量**——开启 debug 不会改变实际布局（`weight: 0` 的 slot 不会被标签撑宽）

用于排查 slot 之间的边界与布局问题。调试完成后请将 `isDebug` 改回 `false`。

### 配置热重载

修改 `main.json` / `style.css` 后无需重启应用 —— 在设置页点击「重新读取配置」
即可立即从磁盘重新解析并应用（主界面与全屏播放器都会更新）。解析失败时会提示
错误信息并回退到默认布局。