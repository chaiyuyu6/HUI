# HUI 窗口化 GUI 设计（草案 v0.1）

> 依据：`gui及窗口化.txt`（需求）+ 讨论确认的范围。目标版本：Minecraft 26.3 / NeoForge 26.3.0.90 / Java 25。
>
> 状态：P0（嵌入渲染）、P1（页面/多窗口/最小化固定/窗口管理器/存档持久化/原版开关）已实现并实测，  
> 详见第 13、14 节；P2 起为设计。

## 1. 目标

把物品栏改造成一个「窗口化桌面」：玩家可以创建多个物品栏页面，页面里放可拖拽、可最小化、可固定的窗口；  
窗口内容可以是虚拟界面、玩家物品栏、方块实体容器或原版 Screen（全屏兜底）。提供一套 Java builder 风格的  
窗口/元素 API，让其他 mod“像写网页一样”扩展窗口，并尽量兼容 JEI。

## 2. 已确认的范围决策

| 决策点       | 结论                                            |
| --------- | --------------------------------------------- |
| 多人        | 不支持多人服务器，只保证单人（集成服务器）可用；不承诺原版服务器兼容            |
| 持久化       | 存档内数据（跟随存档/玩家数据，跨存档不共享）                       |
| 与原版物品栏的关系 | 不完全替代；空白处右键菜单里提供「切换原版/HUI 物品栏」开关              |
| 「打开」菜单    | 列出范围内所有容器，支持搜索                                |
| 默认物品栏窗口   | **独立虚拟容器**（36 格 + 快捷栏，不是玩家真实物品栏的可视化）          |
| 全屏窗口      | 打开原版 `Screen`（兼容兜底）                           |
| 窗口 API    | Java builder / 声明式                            |
| JEI       | 按建议后置（P4），前期统一 `SlotView` 抽象；不承诺 EMI/REI      |
| 旧项目       | 复用 `windowedguis-26.3` 的服务端多菜单思路，客户端窗口/渲染框架重写 |
| 跨窗口搬运     | 可以尝试（窗口内拆分、Shift 跨窗口、拖出丢弃），作为 P3 的探索项         |

## 3. 非目标（本设计不覆盖）

- 多人服务器上的权限/反作弊/延迟补偿；
- 原版服务器（未装 HUI）上的多容器窗口；
- 数据包/资源包驱动的窗口定义（API 为 Java）；
- 自定义渲染的 mod 容器 GUI 的“像素级”移植（走全屏兜底）。

## 4. 总体架构

```
┌──────────────────────────── 客户端 ────────────────────────────┐
│  HuiDesktopScreen（桌面/页面容器 + 输入分发 + 窗口绘制）          │
│   ├─ page：物品栏页面（命名、空页面删除、页面栏）                │
│   ├─ window：位置/大小/z-order/聚焦/最小化/固定/关闭              │
│   └─ content（可插拔）：VirtualContent / InventoryContent /      │
│                         ContainerContent / EmbeddedScreenContent │
│  UI 框架：元素树 + 布局 + 样式 + 事件 + 控件库（"像网页一样"）    │
│  兼容层：JEI 适配、全屏兜底、屏幕黑名单                          │
└───────────────────────────────┬─────────────────────────────────┘
                                │ 只在单人时也走“客户端→服务端”边界
┌───────────────────────────────┴─────────────────────────────────┐
│ 服务端（集成服务器）                                             │
│   ├─ 窗口数据持久化（玩家附件/存档数据）                          │
│   ├─ 多菜单跟踪：菜单 id、containerMenu 切换、广播/关闭防护       │
│   ├─ 方块实体窗口可达性校验（手长/同维度/方块实体仍存在）         │
│   └─ 槽位点击/跨窗口搬运的合法性校验                              │
└─────────────────────────────────────────────────────────────────┘
```

代码包（计划）：

```
com.chai.hui.client.windowing
├── HuiDesktopScreen        桌面界面：页面、窗口绘制、输入分发
├── HuiWindow               窗口模型（位置/大小/标题/内容/z-order）
├── page/PageModel          页面与窗口布局数据
├── content/                WindowContent 实现
│   ├── VirtualContent      元素树内容（默认物品栏等）
│   ├── ContainerContent    方块实体/物品栏窗口（嵌入标准容器外观）
│   └── EmbeddedScreenContent 嵌入一个 Screen 的内容（P0 已实现）
├── ui/                     元素树、布局、样式、控件
└── api/HuiWindows          对外注册 SPI
com.chai.hui.server.windowing
├── WindowSessionManager    每个玩家的窗口/页面状态
├── MenuTracker             多菜单跟踪与 id 分配
└── ReachValidator          方块实体可达性校验
```

## 5. 数据模型与持久化

```java
record WindowState(
    Identifier windowId,
    Identifier type,           // 窗口类型，决定内容工厂
    String title,
    int x, int y, int width, int height,
    boolean minimized,
    boolean pinned,
    int zOrder,
    @Nullable BlockEntityRef blockEntity   // 仅方块实体窗口；维度 + 坐标
) {}

record PageState(String name, List<WindowState> windows) {}
record DesktopState(List<PageState> pages, int activePage, boolean vanillaInventory) {}
```

- **持久化位置**：服务端玩家数据附件（NeoForge `AttachmentType`，序列化用 Codec），随存档保存、随玩家登录加载；  
  单人时由集成服务器持有，客户端通过同步报文获取。
- **方块实体窗口**：持久化只存 `维度 + BlockPos + 类型`；重开时若方块实体存在且玩家在交互距离内则重建菜单，  
  否则标记为失效窗口（显示名称 + “方块实体不可用”，可一键关闭）。
- **页面**：`PageState` 列表；打开页面时清理空页面；`vanillaInventory` 记录“当前用原版还是 HUI 物品栏”的开关。

## 6. 窗口/页面行为

- **聚焦与堆叠**：点击窗口 → 置顶并成为焦点；其余窗口保持上次交互顺序（zOrder 递增重排）。
- **最小化**：从绘制链移除、保留在窗口管理器中；恢复时回到原位置。
- **固定**：固定窗口在再次打开桌面时重建（位置/大小/最小化状态保留）；未固定的窗口在关闭桌面时丢弃。
- **关闭**：销毁内容、释放菜单（服务端菜单跟踪同步关闭）。
- **窗口属性面板**：类型、名称、方块实体 id、是否固定；可改名。
- **窗口管理器**：列表展示所有窗口（含最小化项），双击恢复/聚焦，可搜索。
- **页面栏/任务栏**：页面切换、新建页面、重命名、删除。
- **空白右键菜单**：打开（容器列表+搜索）/ 窗口管理器 / HUI 配置 / 切换原版物品栏。

## 7. 内容类型与嵌入策略

### 7.1 嵌入渲染（核心可行性验证点）

26.3 的 GUI 是“提取渲染状态”模型，没有“把 Screen 画进矩形”的官方 API。可行方案（P0 已跑通）：

1. 创建内容对应的 `Screen` 实例（如 `InventoryScreen`），用桌面尺寸 `init(width,height)`，得到 `leftPos/topPos`；
2. 背景由窗口内容自己绘制（标准容器用原版贴图）；
3. 用 `pose().translate(origin - leftPos, origin - topPos)` 平移后调用 `screen.extractContents(...)`；
4. `enableScissor` 裁剪到窗口内容区，避免越界；
5. 鼠标坐标反向变换后转发给 `screen.mouseClicked/mouseDragged/mouseReleased`；
6. 手持物品（carried item）和 tooltip 在 scissor 之外、用桌面鼠标坐标绘制。

已知取舍：tooltip/拖拽物品会跟随桌面鼠标；mod 自定义绘制走「全屏窗口」兜底。

### 7.2 内容类型

| 类型                    | 实现                           | 交互              |
| --------------------- | ---------------------------- | --------------- |
| VirtualContent        | 元素树渲染                        | 由元素树事件处理        |
| InventoryContent      | 嵌入玩家 `inventoryMenu` 的标准容器外观 | 槽位点击转发（P2）      |
| ContainerContent      | 方块实体菜单代理 + 裁剪物品栏区域           | 槽位点击 + 可达校验（P3） |
| EmbeddedScreenContent | 任意 Screen（P0 原型用）            | 全屏窗口或嵌入（实验性）    |
| FullscreenWindow      | `HuiScreens.open(原版 Screen)` | 原版行为            |

## 8. 服务端容器层（单人也要走）

- **多菜单并存**：复用 `windowedguis-26.3` 的思路（`MenuTracker` 分配容器 id、切换 `containerMenu`、  
  拦截 `broadcastChanges`/`closeContainer` 触发的关闭）；单人时集成服务器同进程，仍走同一套逻辑，方便调试。
- **打开/关闭协议**：客户端请求打开某窗口 → 服务端校验（方块实体存在、可达、类型允许）→ 打开菜单并把  
  `containerId + 内容摘要` 同步给客户端 → 客户端创建窗口内容。
- **可达性**：打开时 + 每 10 tick 校验 `player.isWithinBlockInteractionRange(pos)`、维度一致、方块实体未移除；  
  失败 → 关闭窗口并提示。
- **跨窗口搬运**：同一菜单内沿用原版点击；跨菜单采用「服务端暂存（carried）+ 两次模拟点击」，  
  需要额外的点击合法性校验；先做可行性验证，失败则降级为“仅窗口内搬运”。

## 9. 客户端 UI 框架（“像写网页一样”）

```java
HuiWindows.register(Identifier.of("mymod", "machine_window"), registry -> registry
    .title(() -> Component.translatable("mymod.window.machine"))
    .size(200, 160)
    .content(context -> Ui.column()
        .gap(4)
        .child(Ui.label(() -> Component.literal("进度")))
        .child(Ui.progressBar(() -> context.data("progress")))
        .child(Ui.button(Component.translatable("gui.done"), ctx -> ctx.close()))
        .build()));
```

- 元素树：不可变节点 + 每帧 diff 或直接重建（先做直接重建，性能不够再缓存）；
- 布局：MVP 支持 absolute / linear（row/column，gap/padding）/ 锚点（left/right/top/bottom/center）；
- 样式：颜色、内边距、边框、贴图九宫格、字体缩放；
- 事件：鼠标点击/拖拽/滚轮、键盘、焦点链；
- 控件：Label、Button、Image、SlotGrid、ProgressBar、List/Scroll、TextInput、Tabs；
- 数据绑定：`Supplier<T>` + 每帧取值（简单可靠，先不做响应式）。

## 10. 兼容策略

1. 标准 `AbstractContainerScreen` 子类：走嵌入渲染（背景由窗口类型注册的贴图提供；未注册贴图时用中性底板）。
2. 自定义 Screen / 特殊渲染：全屏窗口（`HuiScreens.open(screen)`），窗口记录仅作为“启动器”。
3. 屏幕黑名单/白名单配置（例如某些 mod 的 GUI 强制全屏）。
4. JEI：提供 `SlotView` 适配（槽位坐标、配方转移、ghost 物品、overlay 层级），JEI 插件在 P4 接入；  
   未接入前不影响窗口本身。

## 11. 分期与验收

| 阶段   | 内容                                                  | 验收标准                 |
| ---- | --------------------------------------------------- | -------------------- |
| P0 ✅ | 嵌入渲染原型：单窗口 + 原版物品栏嵌入 + 拖拽 + 裁剪                      | 可构建；窗口能拖动；槽位可点；无崩溃   |
| P1 ✅ | 页面/窗口模型、多个窗口、z-order、最小化/固定/关闭、窗口管理器、持久化、页面栏、切换原版开关 | 重开桌面后固定窗口恢复；空页面自动删除  |
| P2 ✅ | 物品栏窗口（生存/创造/默认 36+快捷栏）、槽位交互、tooltip、carried item    | 可正常存取物品；与原版行为一致      |
| P3   | 方块实体窗口 + 服务端多菜单 + 可达校验 + 跨窗口搬运（探索）                  | 打开箱子窗口可观可操作；离开范围自动关闭 |
| P4   | 窗口注册 API + 控件库 + 全屏兜底 + JEI 适配                      | 第三方可注册窗口；JEI 配方可拖入窗口 |

## 12. 风险

- **多菜单同步**（P3）：原版菜单生命周期耦合强，容易出现幽灵菜单/物品复制/广播风暴；需要逐项校验与回滚。
- **嵌入渲染**：不同 Screen 的绘制假设、tooltip/overlay 层级、scissor 与 render-state 的交互；P0 已验证标准容器可行。
- **输入一致性**：嵌入 Screen 的鼠标坐标/焦点/键盘需要完整转发，且有“桌面级”和“窗口级”两套快捷键冲突。
- **性能**：多窗口 + 多菜单每帧渲染与同步；必要时对窗口内容做离屏缓存。
- **存档兼容**：数据格式需带版本号与迁移。

## 13. P0 原型结论（已验证）

- 用 `EmbeddedScreenContent` 把 `InventoryScreen` 嵌入可拖拽窗口：背景贴图、槽位、标签、高亮框、tooltip、  
  手持物品均正常；拖拽窗口、点击槽位（空槽位点击无异常）可用；ESC 关闭桌面恢复原版。
- 结论：**「平移 + 裁剪 + 转发输入」可行**，P3 的方块实体窗口可以按同一方案实现，不需要逐像素重画。

（原型入口：游戏内按 `F8` 打开 HUI 桌面；代码位于 `client/windowing`，后续会在此基础上升级为正式页面/窗口系统。）

## 14. P1 实施记录（已实测）

- **持久化位置**：P1 直接由客户端写 `<存档目录>/hui/desktop.json`（单人存档目录），符合"存档内数据"的决策；  
  设计中的"服务端玩家附件"推迟到 P3 与多菜单一起做（单人下两者等价，但 P3 需要服务端参与）。
- **页面**：`DesktopLayout.PageLayout`，`name` 为空时显示默认名"物品栏页面N"；空页面在关闭桌面时自动移除。
- **窗口**：`HuiWindow`（运行时）+ `DesktopLayout.WindowLayout`（持久化）；z-order 即列表顺序；固定与最小化状态随布局保存；  
  未固定窗口在关闭桌面时丢弃（符合需求"未被固定的窗口下次打开会消失"）。
- **窗口管理器**：P1 只列当前页面前 8 个（含最小化），点击恢复并聚焦；搜索与跨页面分组待 P4。
- **原版开关**：`DesktopLayout.huiInventory`，`ScreenEvent.Opening` 拦截"从游戏里打开 `InventoryScreen`"并替换为桌面；  
  开关在空白处右键菜单，切换即保存。
- **嵌入物品栏**：P1 的"物品栏窗口"仍是**玩家真实物品栏**的嵌入渲染（`EmbeddedScreenContent`）；  
  需求里的"独立虚拟容器（36 格 + 快捷栏）"放在 P2 实现。
- **已验证**：多页面切换、新建示例窗口、拖动/z-order、右键窗口菜单（最小化/关闭/固定/属性）、窗口管理器恢复、  
  固定窗口重开恢复、未固定窗口丢弃、`E` 在开启时打开桌面 / 关闭时打开原版物品栏、布局 JSON 落盘。
- **页面/窗口重命名**：需要文本输入控件，随 P4 的元素树一起做，P1 暂不支持。

## 15. P2 实施记录（已实测）

- **通用嵌入容器**：`EmbeddedScreenContent` 现在可嵌入任意 `AbstractContainerScreen`，用视口（`srcX/srcY/srcW/srcH`，相对
  `leftPos/topPos`）决定显示哪一块，并用同一个视口变换转发鼠标/键盘：
  - 生存物品栏：完整 `InventoryScreen`（合成/盔甲/副手/玩家模型），背景自绘，无窗口内变暗；
  - 创造物品栏：`CreativeModeInventoryScreen`，视口向上多留 50px、向下 28px 以容纳顶部/底部页签行，
    背景走 screen 自己的 `extractBackground`（容器界面 `isInGameUi()` 为真，只会画半透明底，不会模糊/脏土）；
  - 默认物品栏：把原版物品栏面板裁到「27 格存储 + 快捷栏」共 36 格（`srcX=7, srcY=83, 162x76`）。
- **`E` 按键**：生存打开生存物品栏窗口，创造打开创造物品栏窗口（不再经过 `InventoryScreen.init` 里切创造界面的逻辑，
  否则会递归打开屏幕）；原版开关关闭时仍打开原版界面。每页只保留一个物品栏窗口（生存/创造/默认互相替换）。
- **空桌面右键菜单**新增「打开默认物品栏窗口」，「打开物品栏窗口」按当前游戏模式选择生存/创造。
- **输入转发**：桌面把 `keyPressed`/`charTyped`/`mouseScrolled` 转发给聚焦窗口；因此槽位数字键换位、Q 丢弃、
  创造搜索框输入、滚轮翻页都可用；`Shift+点击`、拖拽分堆、手持物品、tooltip 与原版一致（已实测）。
- **已实测**：`/give` 物品 → 左键拾取/放置、Shift 快速移动、数字键与快捷栏交换、创造窗口取物放入快捷栏后
  切回生存物品仍在、默认物品栏窗口只显示 36 格并正确反映真实物品栏。
- **待确认 / 后续**：需求里「默认物品栏」若指的是**独立虚拟容器**（自己的 36 格存储），而不是裁剪后的真实物品栏，
  则要在 P3 增加自定义菜单 + 存档持久化（可复用方块实体窗口的菜单层）。当前实现是「裁剪的真实物品栏视图」，
  因为它在 P2 内即可完整交互，且不需要服务端菜单。
- **小问题**：创造窗口在 GUI 缩放 5、桌面高度 240 时内容高 214，底部页签行会被屏幕底边裁掉一点；P3 调窗口钳制。
