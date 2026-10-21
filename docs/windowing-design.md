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
| P3 ✅ | 方块实体窗口 + 服务端多菜单 + 可达校验 + 跨窗口搬运（探索）                  | 打开箱子窗口可观可操作；离开范围自动关闭 |
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
- **已确认**：「默认物品栏」就是**裁剪后的真实物品栏**（不是独立虚拟容器），当前实现符合需求。
- **小问题**：创造窗口在 GUI 缩放 5、桌面高度 240 时内容高 214，底部页签行会被屏幕底边裁掉一点；P3 已把顶部边距
  从 50 调到 28、高度降到 192，问题解决。

## 16. P3 实施记录（增量一：方块实体窗口，已实测）

- **网络**：新增 `OpenBlockEntityPayload`（客户端→服务端，带 BlockPos）与 `HuiNetwork`，在 `HuiMod` 的 mod 总线
  `RegisterPayloadHandlersEvent` 注册（在 `HuiMod` 主类注册，客户端/服务端共用）。
- **打开流程**：
  1. 桌面空白右键 →「打开容器…」→ `HuiContainerListScreen`：扫描玩家周围 8 格的 `MenuProvider` 方块实体，
     按距离排序，带搜索框过滤（客户端扫描只是便利，安全校验在服务端）；
  2. 选中后发送 payload 并关闭列表；
  3. 服务端校验：区块已加载、`player.isWithinBlockInteractionRange(pos, 1.0)`、方块实体仍存在且是 `MenuProvider`，
     失败时给玩家发「该容器已不在手长范围内」；通过则 `player.openMenu(provider)`；
  4. 客户端 `ScreenEvent.Opening` 看到 pending 请求 + 新屏幕是 `AbstractContainerScreen` 时，不放行原版全屏界面，
     而是把它包成窗口（`HuiDesktopScreen.queueContainerWindow`）并让桌面保持为当前屏幕
     （`setScreen` 同实例不会触发 removed，只会重新 init，窗口状态不丢）。
- **窗口内容**：`EmbeddedScreenContent(screen, 0,0,imageWidth,imageHeight, null)` —— 背景走容器自己的
  `extractBackground`（箱子面板），槽位/标签/tooltip/手持物品与原版一致；已实测把钻石从玩家背包拖进箱子。
- **可达性**：服务端每 tick 检查 `containerMenu.stillValid`，离开范围会关闭菜单；客户端 `clientSideCloseContainer`
  会 `setScreen(null)`，因此**当前会连整个桌面一起关掉**（已知限制，下一增量处理）。
- **限制（下一增量）**：
  - 原版 `openMenu` 会先关闭上一个菜单，所以同一时间只有一个方块实体窗口；再打开另一个会先关桌面再重开新桌面；
  - 方块实体窗口是**临时窗口**（`ephemeral`）：不写入布局、切页/关桌面即消失，因为还没有「恢复时重新打开菜单」的流程；
  - 没有多菜单/跨窗口搬运。
- **下一步（增量二）**：自定义容器 id 跟踪 + 点击路由（复用旧项目的多菜单思路）→ 同时保留多个方块实体窗口、
  窗口持久化（维度+坐标，恢复时重新校验并打开）、关闭菜单时只关对应窗口而不是整个桌面、跨窗口搬运。

## 17. P3 实施记录（增量二：多菜单 + 裁掉物品栏，已实测）

- **多菜单**：新增 `HuiMenuManager`（服务端，按玩家 + containerId 跟踪菜单）与三个 payload：
  `ActivateMenuPayload`（客户端让某个窗口的菜单成为服务端当前菜单）、`CloseMenuPayload`（关某个窗口的菜单）、
  `CloseMenuClientPayload`（服务端通知客户端某个菜单已失效）。
  - 打开新容器时先把当前 `player.containerMenu` 挪到 `inventoryMenu` 但不发送关闭包（"park"），再调用原版
    `openMenu`，这样原版不会关闭已有菜单；新菜单登记进管理器；
  - 点击某个窗口前客户端先发 `ActivateMenuPayload`（包顺序保证先激活再点击），服务端把 `containerMenu` 切到该
    菜单并 `broadcastFullState`，随后原版点击处理（按 containerId 匹配）正常工作 → **多个方块实体窗口可同时使用**；
  - 激活时会把上一个菜单的 carried stack 转移到新菜单，因此「在 A 窗口拾取、再到 B 窗口点击」可以完成
    **跨窗口搬运**（已实测钻石从箱子 A 移到箱子 B）；
  - 服务端每 tick 校验所有跟踪菜单的 `stillValid`，失效时关闭该菜单并只通知客户端关掉对应窗口（桌面保持打开）；
  - 客户端鼠标移入窗口时触发激活，让非活动窗口也能刷新到最新内容（避免长时间显示旧数据）。
- **裁掉物品栏**：方块实体窗口不再显示原版界面里的玩家物品栏部分。`EmbeddedScreenContent.blockEntity` 根据
  `menu.slots` 中不属于 `player Inventory` 的槽位计算包围盒（顶部裁到第一行槽位上方 `minY-3`，左/右留 3/20 px，
  底部 `maxY+18`），再用视口裁剪渲染；窗口标题由窗口 chrome 显示，面板标题被裁掉。已实测箱子窗口只显示
  27 个箱子格，不再显示玩家背包/快捷栏。
- **窗口位置/尺寸**：方块实体窗口按容器裁剪后的大小自动决定窗口大小（箱子约 167x57 内容 + 标题栏）。
- **限制（增量三）**：
  - 方块实体窗口仍是**临时窗口**：不写入布局、切页/关桌面消失（还没有"恢复时按维度+坐标重新打开"的流程）；
  - 非活动窗口内容可能短暂陈旧，鼠标移入会刷新；没有后台全量同步；
  - `Shift+点击` 只在当前菜单内快速移动，跨窗口的自动转移未做（跨窗口目前用两次点击搬运）；
  - 多菜单依赖单人/集成服务器语义（本项目本来就只支持单人）。

## 18. P3 增量三 + 问题修复（已实测）

### 修复
- **创造模式持物消失**：创造物品栏是纯客户端菜单，鼠标移到容器窗口时之前会清掉 carried stack。现在
  `EmbeddedScreenContent` 标记 `serverBacked`（创造为 false），只在切换到服务端菜单窗口时清除上一个窗口的
  carried；创造持物悬停/打开容器窗口不再丢失。
- **首次打开物品栏不居中**：物品栏窗口（生存/创造/默认）改为精确居中放置，不再用级联偏移。
- **铁砧/切石机/制图台等打不开**：容器发现与打开从「`BlockEntity instanceof MenuProvider`」改为
  `BlockState#getMenuProvider(level, pos)`，覆盖没有方块实体、直接用 `SimpleMenuProvider` 的方块。
- **方块实体窗口 = 方块 UI + 默认物品栏**：打开容器时自动保证页面上有配套物品栏窗口——生存模式用
  「默认物品栏」（36 格裁剪视图），创造模式用创造物品栏；页面里如果是旧的其他物品栏窗口会先关掉。
  容器窗口放在物品栏窗口左侧；左侧空间不够时自动放到右侧/上方，避免跑出屏幕。

### 增量三（容器窗口持久化）
- `WindowLayout` 新增 `pos`（BlockPos.asLong）与 `dimension`；固定的方块实体窗口在关闭桌面时写入布局。
- 恢复流程：`restoreWindow` 先创建「打开中…」占位窗口，把位置登记到 `pendingRestores`，并向服务端排队发送
  `OpenBlockEntityPayload`；服务端按顺序返回 `OpenBlockEntityResultPayload`，随后打开容器屏幕，客户端在
  `ScreenEvent.Opening` 里用请求队列把屏幕对回占位窗口（替换内容、更新标题、保留位置/固定状态）。
- 打开失败（方块被拆、离开手长）时服务端回失败结果，客户端移除占位窗口；维度不一致直接跳过。
- 恢复时同样会自动补齐配套物品栏窗口。
- 已实测：把铁砧窗口固定 → 关桌面 → `F8` 重开，铁砧窗口按原位置恢复并可继续交互。

### 仍未完成（后续）
- 跨窗口 `Shift+点击` 自动转移（目前仍是两次点击搬运）；
- 非活动窗口后台全量同步（当前鼠标移入刷新）；
- 页面/窗口重命名（需要文本输入控件，计划随 P4 元素树一起做）。

## 19. P4：窗口 API / 控件库 / 重命名 / 全屏兜底（已实现）

- **窗口注册 API**（`com.chai.hui.client.windowing.api`）：
  ```java
  HuiWindowApi.register(Identifier.fromNamespaceAndPath("mymod", "machine"))
      .title(() -> Component.translatable("mymod.window.machine"))
      .content(() -> new UiPanelContent(Ui.column()
          .gap(4)
          .child(Ui.label(Component.literal("Hello")))
          .child(Ui.button(Component.literal("OK"), () -> {}))))
      .register();
  ```
  `HuiWindowApi.open(minecraft, id)` 在桌面里打开该类型窗口；`HuiWindowApi.openFullscreen(minecraft, screen)` 是
  「全屏兜底」入口：不能嵌入的自定义 GUI 直接用原版全屏界面，保证功能可用。
- **控件库**（`client/windowing/ui`）：`Ui` 声明式构建 + `PanelWidget`（column/row、padding/gap）、`LabelWidget`、
  `ButtonWidget`、`TextInputWidget`（可聚焦、支持输入/退格/回车）、`UiPanelContent`（负责渲染和输入分发）。
  示例窗口已改成 Ui 控件展示（标签 + 文本输入）。
- **窗口重命名**：窗口右键菜单新增「重命名窗口…」，用 Ui 文本输入修改名称；自定义名称以字面量写入布局
  （`WindowLayout.title`），重开存档后保留；固定窗口恢复时同样保留自定义名称。
- **JEI 兼容（未完成，明确说明）**：JEI 的配方面板/拖拽依赖 `AbstractContainerScreen` 与它注册的 overlay 流程，
  而 HUI 的窗口是嵌进桌面的独立 Screen 实例，需要给 JEI 提供 `SlotView` 适配层并接管它的 overlay 渲染，
  工作量比其余 P4 项大。本轮没有加入 JEI 依赖，避免半成品；作为后续独立步骤处理。
- **小项状态**：
  - 跨窗口 `Shift+点击`：未单独做——由于容器窗口已裁掉玩家背包、物品栏窗口显示真实背包，`Shift+点击` 在容器
    菜单内快速移动到玩家背包本身可用，配合物品栏窗口的自动刷新；真正「Shift 从一个窗口转到另一个窗口的容器」
    仍未实现（目前两次点击搬运）。
  - 非活动窗口后台同步：未做，仍靠鼠标移入激活刷新。

## 20. 直接交互 / 持物图层 / 跨窗口拖拽 修复（P4 后）

- **创造模式下右键方块实体没进入 HUI**：之前只拦截「打开容器」列表请求和 `E` 键；现在 HUI 物品栏开启时，
  从游戏里打开的任何 `AbstractContainerScreen`（箱子、酿造台、铁砧、熔炉、模组容器……）都会替换成 HUI 桌面窗口，
  并自动配好默认物品栏/创造物品栏；关闭原版开关则保持原版界面。
- **酿造台窗口盖住手持物品**：carried stack 与 tooltip 从各窗口的 `extract` 移到新的 `extractOverlay`，
  在所有窗口与右键菜单之后统一绘制，永远不会被别的窗口覆盖。
- **拖拽到另一窗口物品消失**：
  - 拖拽期间（contentDragging 非空）不再因鼠标悬停切换活动菜单，避免物品被"激活"提前转移走；
  - 松手时如果落在另一个窗口的槽位上，先激活目标菜单（服务端会把 carried 转移到该菜单），再把点击转发给
    目标窗口，物品落入对应槽位；落在窗口空白处则保持手持，不会丢失；
  - **创造物品栏是纯客户端菜单**：从创造物品栏直接拖到容器窗口无法通过服务端校验（物品不在任何服务端菜单里），
    因此这种拖拽会保持手持而不是"放置后消失"；创造模式请先把物品放进快捷栏/物品栏，再拖入容器（原版也是这样）。
- 已实测：创造模式右键酿造台直接进入 HUI 桌面并显示「酿造台 + 创造物品栏」组合（见截图）。
- 仍待你实测确认：从酿造台拖到物品栏的完整链路（自动化点击坐标不稳，没跑出成功截图）。

## 21. 容器裁剪 / 悬停提示 / 拖拽叠放 修复

- **切石机窗口不完整**：容器裁剪从「非玩家背包槽位的包围盒」改成「面板完整宽度 + 从标题下方到玩家背包标签上方」：
  `left = 4, right = imageWidth - 4, top = 12, bottom = playerInventoryTop - 13`，并保留无容器槽位时整屏回退。
  这样切石机、酿造台、铁砧等把配方网格/装饰画在槽位区域之外的界面也能完整显示；窗口标题由 chrome 显示，
  面板标题与玩家背包标签被裁掉。
- **鼠标在 UI 下方浮现隐藏槽位的 tooltip**（如铁砧/快捷栏物品名）：嵌入屏幕的 `extractContents` 之前会拿到
  换算后的鼠标坐标，即使鼠标在裁剪视口之外也会命中被裁掉的玩家背包槽位。现在鼠标不在视口内时传 `-10000`
  给内嵌屏幕，隐藏槽位不再产生悬停和 tooltip。
- **恢复的固定容器窗口抢焦点**：固定容器窗口的打开结果比新打开的窗口晚到，之前会把新窗口盖住；现在恢复窗口
  不再主动置顶（除非当前没有聚焦窗口）。
- **拖拽叠放/丢失**：
  - 手持物品现在在所有窗口之后绘制（`extractOverlay`），拖到别的窗口上方也不会被盖住（已实测截图）；
  - 拖拽期间不切换活动菜单；松手跨窗口时仅当源和目标都是服务端菜单窗口才做激活+点击放置，其他情况保持手持；
  - 创造物品栏是纯客户端菜单：从它拖到容器窗口会**保持手持**（已实测），不会像之前那样放进去后消失。
- 仍建议实测：从容器窗口（服务端菜单）拖到默认物品栏窗口的槽位，确认服务端放置链路。

## 22. 跨窗口拖拽后的 carried 同步修复

- **现象**：把物品从物品栏窗口拖向容器窗口，松手后物品"消失"，在物品栏里再点一下又出现。
- **原因**：跨窗口松手时会把菜单激活到目标窗口（服务端把 carried 转移过去），随后把点击转发给目标窗口。
  如果目标菜单不接受该物品（例如把木板拖到酿造台/切石机的输入槽），服务端拒绝这次点击，但客户端之前做了
  放置预测，两侧 carried 状态不一致，客户端看起来物品没了；下一次点击任意物品栏窗口触发激活/全量同步，
  物品才重新出现。
- **修复**：
  - 服务端 `ActivateMenuPayload` 处理改为**每次都 `broadcastFullState()`**（原来只有切换菜单时才同步），
    这样客户端可以随时请求一次完整状态来纠正 carried/槽位；
  - 客户端跨窗口松手后无论点击是否被接受，都会：
    1. 在目标窗口客户端菜单上保留 carried（保证物品一直可见）；
    2. 再发一次 `ActivateMenuPayload` 强制全量同步——被拒绝的物品会回到光标上，而不是消失到下次点击。
- **已实测（生存模式）**：在「默认物品栏 + 酿造台」组合里，把 16 个橡木木板从物品栏拖到酿造台输入槽
  （木板不是酿造材料，必被拒绝）→ 松手后木板仍显示在光标上、物品栏对应槽位为空、没有丢失；
  再点物品栏即可正常放回。

## 23. 直接打开的容器未登记菜单（点击拾取后悬停另一窗口物品消失）

- **复现**：在物品栏窗口点一下物品（拾取到光标）→ 鼠标移到容器窗口上 → 物品消失；再点物品栏又出现。
- **原因**：`HuiMenuManager` 只在「打开容器」列表请求（`OpenBlockEntityPayload`）里登记菜单；之后改成
  「右键方块直接进 HUI」后，这类菜单是原版 `player.openMenu` 打开的，**没有登记**。
  鼠标悬停到容器窗口会触发 `ActivateMenuPayload`：客户端把上一个窗口的 carried 清掉并切换到目标菜单，
  但服务端 `HuiMenuManager.get` 找不到该菜单 → 直接 return，服务端容器菜单仍是原来的（物品还在服务端原菜单的
  carried 里，客户端却已清空）→ 视觉上物品消失。下次点击物品栏会发 `Activate(0)`：
  服务端把 carried 从当前菜单转回 `inventoryMenu` 并全量同步 → 物品重新出现。
- **修复**：监听 NeoForge 的 `PlayerContainerEvent.Open/Close`，把所有非 0 号容器菜单登记/注销到 `HuiMenuManager`，
  不再只依赖列表打开路径。这样悬停激活时服务端能正确把 carried 转移到目标菜单并全量同步。
- 由用户实测验证（本轮按用户要求未启动游戏自动化测试）。

## 24. 创造模式无法点击拿取物品（激活逻辑回归修复）

- **现象**：创造模式的创造物品栏窗口里点击任何物品都拿不起来。
- **原因**：窗口点击前会 `activate()` 把该窗口的菜单设为当前菜单。创造物品栏的 `ItemPickerMenu` 是**纯客户端**
  菜单（`containerId` 也是 0，服务端从来没有这个菜单），激活它会把客户端 `player.containerMenu` 换成服务端
  不认识的对象；同时服务端 `ActivateMenuPayload(0)` 会回一次 `inventoryMenu` 的全量同步，客户端把这份数据
  套到创造菜单上，刚拾取的 carried 立刻被覆盖 → 看起来完全无法拿取。
- **修复**：
  - `EmbeddedScreenContent.activate()` 对 `serverBacked == false`（创造物品栏）直接 return：不发激活包、不改
    `player.containerMenu`，恢复原版创造界面的拾取逻辑；
  - 桌面 `activateContent()` 在当前窗口是「非服务端菜单且 carried 非空」（创造模式拿着物品）时，不再把其它窗口
    设为活动窗口，避免创造持物在鼠标经过容器窗口时从光标上消失。
- 创造物品栏仍不能直接把物品"放"进容器窗口（它是客户端菜单，服务端没有对应物品），需要先放到快捷栏/背包，
  与原版一致。由用户实测验证（本轮未启动游戏自动化）。

## 25. 创造物品放入容器窗口（CreativeInsertPayload）

- **问题**：创造物品栏的 `ItemPickerMenu` 是纯客户端菜单，物品在服务端不存在；即使用 HUI 同时开着创造窗口和容器
  窗口，点击容器槽位也只会走原版点击（服务端 carried 为空）→ 石头放不进切石机。
- **方案**：新增客户端→服务端 `CreativeInsertPayload(containerId, slotIndex, ItemStack)`：
  - 客户端在「手中拿着创造物品」且点击的是服务端容器窗口的槽位时，计算该位置对应的槽位索引，
    发 payload（并把创造窗口的 carried 清空、切换客户端当前菜单到目标菜单）；
  - 服务端校验：创造模式、菜单已登记且 `stillValid`、槽位索引有效、`slot.isActive()` 且 `slot.mayPlace(stack)`，
    通过则把物品副本放进槽位并 `broadcastFullState()`；
  - 槽位拒绝时物品不会出现在服务端（创造模式物品无限，重新拿即可），不会再出现"客户端有、服务端没有"的幽灵物品。
- 限制：目前只处理左键单击放置（整组复制），不含右键分堆/数量选择；创造模式仍建议常规流程
  （先放进快捷栏再搬运），此 payload 是为了让 HUI 里"创造 + 容器"组合能直接放东西。
- 由用户实测验证（本轮未启动游戏）。

## 26. HUI UI 与原版 UI 隔离（方块实体窗口改为 HUI 自绘）

按需求改为「体素实体窗口用 HUI 自己的 UI，原版界面只做数据同步」：

- **数据同步**：原版 `AbstractContainerScreen` 只在收到开屏包时由 `MenuScreens.create` 创建一次，用作
  `AbstractContainerMenu` 的载体；它**不会被 init/渲染/接收输入**。HUI 只通过 `menu.slots`、`menu.getCarried()`、
  `handleContainerInput` 读写数据。
- **渲染**：新增 `HuiContainerContent`（实现 `MenuWindowContent`）：HUI 自绘深色面板 + 槽位边框 + 物品 +
  数量角标 + 悬停高亮；标题由窗口 chrome 显示；只显示容器自己的槽位（玩家背包不显示）；tooltip 与手持物品
  由 HUI 在 overlay 层绘制。
- **交互**：点击先 `activate()`（服务端切菜单 + 客户端 containerMenu 指向它），再
  `gameMode.handleContainerInput(containerId, slotIndex, button, input, player)`：
  左键 PICKUP、右键 PICKUP(button 1)、Shift+左键 QUICK_MOVE。拖拽分堆/滚轮/配方书等原版专属控件不再提供。
- **一致性**：`MenuWindowContent` 接口统一了嵌入式（背包类窗口仍在用）与 HUI 自绘容器窗口，桌面层的激活、
  固定恢复、跨窗口拖拽、创造模式插入等逻辑对两者都适用。
- 窗口大小由容器槽位包围盒 + 6px 内边距决定；固定恢复仍按维度 + 坐标重新打开菜单。
- 由用户实测验证（本轮未启动游戏）。
