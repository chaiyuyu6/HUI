# HUI UI 转义器设计（Vanilla → HUI UI Adapter）

> 目标：HUI 的所有窗口（物品栏、方块实体容器 …）都用 **HUI 自己的 UI 系统**渲染和交互；原版界面只作为
> "菜单/数据"来源。转义器负责把原版 `Screen + AbstractContainerMenu` 转换成 HUI UI 模型，并让行为等价：
> 点击、拖拽分堆、双击收集、Shift 快速移动、数字键/副手交换、Q 丢弃、tooltip/手持物品等都和原版一致。
>
> 依据：`docs/vanilla-ui-research.md`。

## 1. 术语

- **转义器（Adapter）**：把原版界面元素翻译成 HUI 元素/事件的中间层。
- **来源（Source）**：原版 `AbstractContainerScreen`（只用于取 `menu`、`title`、`imageWidth/Height`、槽位布局，
  **不 init、不渲染、不收输入**）。
- **HUI 内容（Content）**：实现 `WindowContent`/`MenuWindowContent` 的 HUI 自绘窗口内容。
- **描述器（Descriptor）**：可选的每类屏幕 UI 描述（面板贴图/标签/额外按钮），用于还原原版"外观"细节；
  没有描述器时使用 HUI 默认外观（深色面板 + 槽位网格），行为仍等价。

## 2. 分层架构

```
HuiDesktopScreen（窗口/页面管理）
   └─ WindowContent（HUI 内容）
        ├─ VanillaMenuUiContent ← 转义器主类
        │    ├─ MenuUiModel          // 槽位/分组/布局/尺寸
        │    ├─ MenuInputTranslator  // 原版点击状态机 → ContainerInput 序列
        │    ├─ MenuUiRenderer       // HUI 面板/槽位/物品/tooltip/手持
        │    └─ ScreenDescriptor     // 可选：贴图/标签/按钮（后续阶段）
        ├─ EmbeddedScreenContent     // 兼容兜底：确实无法转义的界面
        └─ UiPanelContent            // P4 控件库（第三方窗口）
```

### 2.1 MenuUiModel（数据/布局）

- 输入：`AbstractContainerMenu menu` + 可见槽位策略（`showPlayerInventory` 或 `Predicate<Slot>`）；
- 输出：
  - `visibleSlots`：渲染与命中的槽位列表（保持 `slot.index` 用于发包）；
  - `minX/minY/width/height`：槽位包围盒 + HUI 内边距；
  - 分组：默认按 `slot.container` 分（容器组/玩家背包组/快捷栏组），用于绘制分隔与标签（后续）；
  - 槽位屏幕坐标映射：`slotScreen(slot) = origin + PADDING + (slot.x - minX)`。
- 不复制物品数据：物品永远实时读 `slot.getItem()` / `menu.getCarried()`（单一数据源）。

### 2.2 MenuInputTranslator（行为等价核心）

把原版 `AbstractContainerScreen` 的私有状态机完整移植（字段同名同义）：

```
hoveredSlot, lastClickSlot, doubleclick, skipNextRelease,
isQuickCrafting, quickCraftingType, quickCraftingButton, quickCraftSlots, quickCraftingRemainder,
lastQuickMoved
```

事件映射：

| HUI 事件 | 转义结果 |
| --- | --- |
| 左键点槽位（空手） | `PICKUP` |
| 左键点槽位 + Shift | `QUICK_MOVE`（并记录 `lastQuickMoved`） |
| 右键点槽位（空手） | `PICKUP`（button 1） |
| 双击 + 右键 | `PICKUP_ALL`；Shift 双击 → 对同库存同类槽位批量 `QUICK_MOVE` |
| 手里有物品时按下左/右键 | 进入快速合成：记录按钮/类型，清空 `quickCraftSlots` |
| 拖拽经过槽位 | 加入 `quickCraftSlots`（`canItemQuickReplace`/`mayPlace`/`canDragTo`），重算剩余数量 |
| 松手（快速合成） | 依次发 `QUICK_CRAFT` 掩码 0/1/2（开始、逐槽、结束） |
| 点面板外 | `THROW`（整组/单个） |
| 数字键 1-9 / 副手键（悬停槽位、空手） | `SWAP`（button 0-8 / 40） |
| 拾取键（中键） | `CLONE`（创造模式） |
| 悬停槽位时按 Q / Ctrl+Q | `THROW`（button 0/1） |

每次发包前先 `activate()`（`ActivateMenuPayload` + 客户端 `player.containerMenu` 指向该菜单），保证服务端
`handleContainerClick` 的 containerId 校验通过；HUI 只发包，不在客户端复制点击算法，保持与服务器绝对一致。

### 2.3 MenuUiRenderer（HUI 外观）

- 面板：HUI 深色底 + 强调色边框（窗口 chrome 已显示标题）；
- 槽位：18x18 边框 + 物品 + 数量角标；`slot.isHighlightable()` 时悬停显示强调色描边；
- 快速合成预览：对 `quickCraftSlots` 用 `getQuickCraftPlaceCount/canItemQuickReplace` 计算预览数量并绘制
  （和原版 `extractSlot` 一致的规则），数量被截断时用黄色提示；
- 手持物品：overlay 层绘制 `menu.getCarried()`（快速合成时显示剩余数量），tooltip 在 overlay 层；
- 标签/贴图：第一版全部使用 HUI 外观；`ScreenDescriptor` 阶段再按屏幕类型补原版贴图/标签。

### 2.4 数据同步与激活

- 每个窗口持有自己的客户端菜单实例（`MenuScreens.create` 时由原版创建，转义器只取 `menu`）；
- 鼠标进入窗口 → `activate()` → 服务端切换 `containerMenu` 并全量同步 → 窗口内容实时；
- 点击前再次 `activate()`，随后发送原版点击包；
- 固定窗口恢复时按维度+坐标重新 `openMenu`（已有 `OpenBlockEntityPayload` + 占位窗口机制）。

## 3. 与现有 HUI 部件的整合

| 现有部件 | 变化 |
| --- | --- |
| `HuiDesktopScreen` | 内容类型从 `EmbeddedScreenContent` 抽象为 `MenuWindowContent`；激活/固定恢复/跨窗口/创造插入均按接口工作 |
| 物品栏窗口（生存/默认） | 改为转义器 + HUI 外观（`showPlayerInventory` 策略：默认物品栏只显示 27+9，生存显示全部槽位） |
| 方块实体容器窗口 | 改为转义器（只显示容器槽位），配默认物品栏窗口 |
| 创造物品栏窗口 | 第一阶段保留嵌入式原版界面（页签/搜索/翻页尚未转义），后续用描述器 + HUI 页签控件补齐 |
| 无法转义的自定义 Screen | `HuiWindowApi.openFullscreen` 全屏兜底 |
| JEI | 后续阶段：给 JEI 提供 `SlotView` 适配（转义器已有槽位索引/坐标映射） |

## 4. 分期

| 阶段 | 内容 | 验收 |
| --- | --- | --- |
| A（本轮） | `MenuWindowContent` 接口、`MenuUiModel`、`MenuInputTranslator`、`MenuUiRenderer`；方块实体/生存/默认物品栏窗口接入；配置开关可回退旧嵌入方案 | 与转义前行为一致：左/右/Shift/拖拽分堆/双击/数字键/Q 都等价；无幽灵物品 |
| B | `ScreenDescriptor` 注册：为原版常见容器提供贴图/标签/分组；HUI 页签/搜索控件实现创造模式 | 创造窗口转义后页签、搜索、翻页可用 |
| C | JEI `SlotView` 适配、配方书按钮等交互控件 | JEI 配方可拖入 HUI 窗口 |
| D | 自定义窗口 API 与转义器共用控件库，移除旧 `EmbeddedScreenContent` 依赖 | 全 HUI UI，原版屏幕只作为数据源 |

## 5. 风险

- 原版状态机字段/顺序出错会导致"幽灵物品/复制/丢物品"——转义器按原版逐行移植并保留 `skipNextRelease` 等细节；
- 客户端只发包不做预测，网络延迟下手持物品变化会晚一帧（可接受；原版也是服务器权威）；
- 方块实体/模组自定义控件无法自动转义（描述器/全屏兜底）；
- 配置开关必须保证关闭时完全回到旧的嵌入方案，便于回归。
