# HUI UI 框架与注册通道（设计 + API）

> 目标：HUI 提供一个"网页式"的 UI 框架（布局、样式、事件、动画），本模组用它覆盖原版 GUI，同时把
> "方块实体 + GUI"的收录/开发通道开放给其他模组。
>
> 相关文档：原版 GUI 研究 `docs/vanilla-ui-research.md`、转义器设计 `docs/ui-adapter-design.md`、
> 贴图规范 `docs/gui-texture-spec.md`。

## 1. 框架总览

```
UiNode                       // 类似 DOM 节点：style + children + 事件 + 动画
 ├─ UiStyle                  // 类似 CSS：宽高/百分比、flex 行/列、gap、padding、对齐、颜色、边框、滚动裁剪
 ├─ UiAnimation              // 关键帧：平移/缩放/旋转/透明度，缓动 + loop/pingpong + delay
 └─ UiWindowContent          // 窗口内容：measure → layout → render，负责命中测试/悬停/点击/滚轮/键盘
      └─ HuiDesktopScreen    // 已有的窗口/页面/桌面管理
```

- **布局**：内建 flex 式的 row/column、gap、padding、百分比尺寸、justify/align、自动测量（文本按字体宽度、
  图片按贴图尺寸、按钮按文字宽度 + padding）、`overflowClip` 滚动容器；
- **动画**：`UiAnimation.of(0.25f).translate(0, -8, 0, 0).scale(0.9f, 1.0f).alpha(0, 1).easing(EASE_OUT)`，
  窗口打开/悬停/按钮反馈都可以做过渡；
- **交互**：点击/右键/悬停进出/滚轮滚动/键盘输入/字符输入，事件从命中节点向父级冒泡；
- **输入框**：`Ui.textInput(placeholder, value, onChange)`，点击聚焦、输入、退格、回车失焦、闪烁光标；
- **数据绑定**：节点文本/控件回调直接读 `Supplier` / 调 `Consumer`，配合自定义 `UiState`（mod 自己维护即可），
  窗口每帧重绘，不需要手动 invalidate。

## 2. 窗口内容 API（本模组 & 其它模组通用）

```java
UiNode root = Ui.panel(
    new UiStyle().size(240, 160).gap(8),
    Ui.label("熔炉状态：运行中"),
    Ui.row(new UiStyle().gap(6),
        Ui.button("开始", node -> sendAction("start")),
        Ui.button("停止", node -> sendAction("stop"))),
    Ui.textInput("搜索…", () -> query, value -> query = value),
    Ui.image(Identifier.parse("hui:icon/fire"), 16, 16)
).animate(UiAnimation.of(0.25f).translate(0, 10, 0, 0).alpha(0, 1).easing(EASE_OUT));

UiWindowContent content = new UiWindowContent(root);
```

## 3. 注册通道

### 3.1 给"菜单类 GUI"注册 HUI UI（原版覆盖 / 其它模组菜单）

```java
// 客户端初始化时
HuiUiRegistry.registerMenu(MyMenus.MACHINE.get(), ctx -> {
    AbstractContainerMenu menu = ctx.menu();
    UiNode root = Ui.column(...);
    // 槽位示例：点击时通过 ctx.click(slotIndex, button, ContainerInput.PICKUP) 走原版点击协议
    for (Slot slot : menu.slots) { ... }
    return root;
});
```

- 菜单数据仍然走原版 `AbstractContainerMenu` 同步（槽位、carried、stateId），HUI 只负责 UI；
- 点击前 HUI 自动 `activate()`（服务端把 `containerMenu` 切到这个菜单），然后发送原版 `ContainerInput`；
- 未注册的**模组**菜单保持原版界面；原版（`minecraft:` 命名空间）菜单默认全部由 HUI 覆盖
  （`HuiUiRegistry.coverVanillaMenus()` 自动执行，也可以注册自己的 UI 覆盖某个原版菜单）。

### 3.2 给"方块实体"注册 HUI UI（含没有原版 GUI 的方块实体）

客户端 UI 构建（`HuiUiRegistry`）：

```java
HuiUiRegistry.registerBlockEntity(MyBlockEntities.MACHINE.get(), ctx -> {
    CompoundTag data = ctx.data();          // 服务端 Provider.writeData 发来的数据
    UiNode root = Ui.column(...);
    root.add(Ui.button("启动", node -> ctx.sendAction("start", new CompoundTag())));
    return root;
});
```

服务端数据/动作（`HuiBlockUiServer`，在公共初始化里注册）：

```java
HuiBlockUiServer.register(MyBlockEntities.MACHINE.get(), new HuiBlockUiServer.Provider() {
    @Override
    public CompoundTag writeData(ServerPlayer player, BlockEntity blockEntity) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("running", ((MyMachine) blockEntity).isRunning());
        return tag;
    }

    @Override
    public void onAction(ServerPlayer player, BlockEntity blockEntity, String action, CompoundTag payload) {
        if ("start".equals(action)) { ((MyMachine) blockEntity).start(); }
    }
});
```

流程：

1. 客户端右键**已注册**的方块实体 → 发送 `OpenBlockUiPayload(pos)`，并取消客户端的使用/交互；
2. 服务端校验距离、方块实体、注册的 Provider → `writeData` → `BlockUiDataPayload`；
3. 客户端用 `BlockUiFactory` 构建 UI，在 HUI 桌面里打开/刷新窗口（按坐标复用同一窗口）；
4. UI 里 `ctx.sendAction(...)` → `BlockUiActionPayload` → 服务端 `onAction` → 重新 `writeData` 并回发，
   客户端自动重建窗口内容。

## 4. 回退规则（已实现）

| 情况 | 行为 |
| --- | --- |
| 已注册菜单（含所有原版容器菜单） | HUI 窗口（转义器/HUI 自绘 UI） |
| 已注册方块实体（无论有没有原版 GUI） | HUI 强制接管，右键不再打开原版界面 |
| 未注册的模组菜单 | 原版界面（原样打开） |
| 未注册的方块实体 | 原版行为 |
| 自定义 / 无法转义的 Screen | `HuiWindowApi.openFullscreen(mc, screen)` 全屏兜底 |

## 5. 代码位置

| 内容 | 文件 |
| --- | --- |
| 框架节点/样式/动画 | `client/ui/framework/UiNode.java`、`UiStyle.java`、`UiAnimation.java` |
| 布局/渲染/事件 | `client/ui/framework/UiWindowContent.java` |
| 控件工厂 | `client/ui/framework/Ui.java` |
| 注册表 | `client/ui/HuiUiRegistry.java`、`server/HuiBlockUiServer.java` |
| 上下文 | `client/ui/MenuUiContext.java`、`client/ui/BlockUiContext.java` |
| 网络通道 | `network/OpenBlockUiPayload/BlockUiDataPayload/BlockUiActionPayload` |
| 客户端接线 | `client/network/HuiBlockUiClient.java`、`client/HuiClient.java` |

## 6. 分期

- A（本轮）：框架核心（布局/样式/动画/事件/输入框）、注册表、方块实体通道、回退规则；
- B：把转义器的容器 UI 迁移到框架节点（SlotGrid 等控件），并接入 GUI 贴图（`GuiTextures`）；
- C：创造物品栏页签/搜索用框架控件重写，JEI 适配；
- D：文档站 + 示例 mod（或在 `hui-example` 包内提供示例注册）。

## 7. 已知限制

- 创造物品栏窗口仍是嵌入原版界面（页签/搜索未转义）；
- 方块实体通道需要客户端 UI 工厂和服务端 Provider 都注册，只注册一边不会打开窗口；
- `UiWindowContent` 每帧重新布局（不缓存脏标记），复杂 UI 需要注意节点数量；
- 动画目前是节点级关键帧，没有 CSS 式 transition/选择器；需要更复杂的动效可扩展 `UiAnimation` 通道。

## 8. 创造物品栏（HUI 自绘，已实现）

`HuiCreativeUiContent` 用框架实现了创造物品栏，不再嵌入原版 `CreativeModeInventoryScreen`：

- **标签页**：遍历 `CreativeModeTabs.allTabs()`（客户端可用），因此**其它模组通过原版
  `Registries.CREATIVE_MODE_TAB` / NeoForge 注册的页签会自动出现在 HUI 里**，页签名、图标、物品列表都用原版数据；
- **物品列表**：`tab.getDisplayItems()`（原版同步到客户端的列表），左侧可滚动分类列表 + 右侧 9 列物品网格；
- **搜索**：输入框跨全部页签按物品名过滤（去重）；
- **拿取语义**：复刻原版创造物品栏的点击规则（左键拿整组、Shift 整组、右键减一、空手点物品拿取、
  同物品左键加一/右键减一），物品放在 `player.inventoryMenu` 的 carried 上，overlay 层跟随鼠标绘制；
- **快捷栏**：底部 9 格显示玩家快捷栏；有 carried 时点击放入（`handleCreativeModeItemAdd` + 服务端
  `ServerboundSetCreativeModeSlotPacket`），空手点击把物品拿在手上并从快捷栏清掉；
- **数据同步**：创造物品栏是纯客户端菜单，HUI 直接使用原版创造模式 helper（`handleCreativeModeItemAdd` /
  `handleCreativeModeItemDrop`），不依赖 `ItemPickerMenu`；关闭窗口时清空 carried，避免幽灵物品。

已实现但可继续完善（阶段 B/C）：

| 未做 | 说明 |
| --- | --- |
| 分类页签分页（原版 tab 分两排/翻页） | HUI 用左侧可滚动列表代替，页签多时也能用 |
| 销毁物品槽 | 原版右下角垃圾桶，HUI 暂用"点空白/右键减一"控制 carried |
| 页签专属搜索框 | 统一搜索框跨页签过滤 |
| 页签拖拽排序 | 使用原版注册顺序 |

由用户实测验证（本轮未启动游戏）。

## 9. 默认策略调整：照搬原版（复用原版代码）

截图暴露了 HUI 自绘创造物品栏的布局 bug（`Ui.copy` 把 row 的 direction 覆盖成 column，导致页签行全宽/物品网格
不显示）。按"完全照搬原版、可复用原版代码"的要求：

- `windowing.nativeUi` 默认改为 **false**：容器窗口、生存/默认物品栏窗口、创造物品栏窗口全部**嵌入原版
  Screen**（原版代码原样渲染，像素/行为一致），HUI 只负责窗口化（桌面/页面/拖动/固定/关闭）；
- `windowing.nativeUi=true` 可切回 HUI 自绘 UI 做实验（创造栏布局 bug 已修，但整体仍不如原版完整）；
- HUI 框架（`UiNode/UiStyle/UiAnimation/UiWindowContent`）与注册通道保留，用于其它模组的自定义 HUI 界面、
  以及后续逐步替换原版 GUI 的实验。

布局 bug 修复：`UiStyle` 增加 `directionSet/justifySet/alignSet`，只有显式调用时 `Ui.copy` 才覆盖基础方向，
`Ui.row()`/`Ui.column()` 不再被默认值覆盖。
