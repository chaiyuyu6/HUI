# HUI GUI 贴图规范（给画师）

HUI 的容器/物品栏窗口已经改成自绘 UI（转义器 `VanillaMenuUiContent`）。贴图全部是**可选**的：文件不存在时
自动回退到深色平面样式，所以可以先画一部分、边画边生效。

所有贴图放在资源包命名空间 `hui` 下：

```
src/main/resources/assets/hui/textures/gui/sprites/<name>.png
```

对应的贴图 ID 是 `hui:<name>`（代码里用 `GuiTextures.xxx` 常量引用）。
GUI 贴图图集会自动收录 `textures/gui/sprites/` 下的所有文件，不需要额外的 atlas 配置。

> 改完贴图后游戏里按 `F3+T` 重载资源即可看到效果（或重启客户端）。

## 1. 必须的文件

| 文件 | 尺寸 | 说明 |
| --- | --- | --- |
| `window/frame.png` | 24x24（推荐） | 窗口边框，九宫格拉伸；边框 8px |
| `window/frame.png.mcmeta` | — | 见下方九宫格配置 |
| `slot.png` | 18x18 | 单个槽位底图，按 18x18 绘制 |
| `slot_hover.png` | 24x24 | 悬停槽位高亮，居中叠加在槽位外扩 3px 的位置 |
| `button.png` | 16x16（推荐） | 按钮底图，九宫格拉伸；边框 3px |
| `button.png.mcmeta` | — | 同上，见九宫格配置 |

### 九宫格 `.mcmeta` 格式（以 window/frame 为例）

```json
{
  "gui": {
    "scaling": {
      "type": "nine_slice",
      "width": 24,
      "height": 24,
      "border": 8
    }
  }
}
```

- `width`/`height`：原图尺寸；
- `border`：四边不拉伸的边框宽度（单值或 `[左, 上, 右, 下]`）；
- 绘制时 HUI 会调用 `blitSprite(...)` 按任意窗口大小自动九宫格拉伸。

### 透明与对齐

- 所有 PNG 用带 alpha 的 RGBA；
- 槽位图的 18x18 区域要和物品图标对齐（首格左上角对齐，物品 16x16 居中留 1px）；
- `slot_hover.png` 的 24x24 里，18x18 高亮应该在居中位置（四周各 3px 外扩），可以画成发光边框/半透明底；
- 窗口边框的 8px 边框里，标题栏区域可以画得更高，HUI 的窗口标题栏会盖在内容上方（标题栏本身由 HUI 绘制，
  目前使用纯色 + 强调色，后续也会走贴图）。

## 2. 配色（回退样式 / 文字）

| 用途 | 值 |
| --- | --- |
| 面板背景 | `#10161C`（不透明度 90%） |
| 强调色 | `#3FC7FF` |
| 槽位底 | `#0B0F14` |
| 槽位边框 | `#2F4353` |
| 悬停边框 | `#7FD9FF` |
| 高亮叠加 | `#7FD9FF`（约 33% 不透明度） |
| 文本 | `#E6EDF3` |

如果贴图里已经包含这些颜色，回退样式会自动被替换。

## 3. 后续阶段还需要（先知道即可，不用现在画）

| 文件 | 用途 |
| --- | --- |
| `tab_active.png` / `tab_inactive.png` | 创造物品栏页签（HUI 版页签控件） |
| `scroll_track.png` / `scroll_thumb.png` | 滚动条（列表/创造页签） |
| `text_input.png` + `.mcmeta` | 输入框 |
| `tooltip.png` + `.mcmeta` | 自定义 tooltip 背景 |
| `icon/close.png`、`icon/minimize.png`、`icon/pin.png` | 窗口按钮图标 |
| `arrow_right.png` 等 | 容器装饰图标（切石机箭头等） |

画好这些后再接入对应控件；当前 P4 控件库（`Ui`）使用的是代码绘制，接入贴图会作为一个单独的小步做。

## 4. 命名与目录约定

- 一律小写下划线；
- 窗口类：`window/*`；槽位类：`slot*`；按钮类：`button*`；图标类：`icon/*`；
- 每张需要拉伸的图都配套 `.mcmeta`（九宫格），不需要拉伸的直接 PNG。

## 5. 验收方式

1. 放好 `window/frame.png` + `slot.png` 后进游戏，右键箱子/切石机 → 窗口应立刻换成贴图样式；
2. 鼠标悬停槽位应有 `slot_hover.png` 的高亮；
3. 删除贴图文件后回退样式仍然正常（不会出现紫黑缺失贴图）。
