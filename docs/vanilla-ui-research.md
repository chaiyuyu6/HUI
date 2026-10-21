# 原版 GUI 实现研究（Minecraft 26.3 / NeoForge）

> 目的：弄清原版 Screen / 容器 GUI 的渲染、输入、菜单数据同步方式，作为 HUI「转义器」的设计依据。
> 源码位置：`_refs/mc_src_26.3`（反编译源），行号会随版本变化，这里只记录关键机制。

## 1. 屏幕生命周期与渲染模型

- `Gui.setScreen(Screen)`：销毁旧屏幕（`ScreenEvent.Closing` → `removed()`），设置新屏幕，调用 `init(width, height)`，
  再 `added()`；见 `Gui.java`。NeoForge 在真正切换前发 `ScreenEvent.Opening`，可替换/取消——HUI 的窗口注入点。
- 渲染是"渲染状态提取"：`Screen.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partial)`，
  `GuiGraphicsExtractor` 只负责登记绘制指令（fill/blit/text/item/blitSprite/setTooltip…），由 GUI 渲染器统一提交。
- 输入：`GuiEventListener` 提供 `mouseClicked / mouseDragged / mouseReleased / mouseScrolled / keyPressed / charTyped`；
  `KeyboardHandler` 把 GLFW 事件转换成 `MouseButtonEvent / KeyEvent / CharacterEvent`。
- `Screen` 的 `renderables`/`children` 由 `ContainerEventHandler` 负责分发；`Screen.isInGameUi()` 决定背景是
  半透明压暗（容器界面）还是模糊全景（非游戏内界面）。

## 2. `AbstractContainerScreen`：容器 GUI 的骨架

字段（`AbstractContainerScreen.java` 顶部）：

```java
protected final T menu;                 // AbstractContainerMenu
protected final Component playerInventoryTitle;
protected int imageWidth, imageHeight;  // 面板尺寸，默认 176x166
protected int leftPos, topPos;          // 面板左上角
protected int titleLabelX/Y, inventoryLabelX/Y;
protected @Nullable Slot hoveredSlot;
private @Nullable Slot lastClickSlot;
protected final Set<Slot> quickCraftSlots;
protected boolean isQuickCrafting;
private int quickCraftingType;          // 0=左键均分 1=右键逐格 2=创造复制
private int quickCraftingButton;
private boolean skipNextRelease;
private int quickCraftingRemainder;
private boolean doubleclick;
private ItemStack lastQuickMoved = ItemStack.EMPTY;
```

渲染管线（`extractRenderState`）：

1. `extractBackground(...)`：面板背景（子类自己 blit 贴图；`Screen.extractBackground` 只负责压暗/模糊）；
2. `extractContents(...)`：`super.extractRenderState`（widgets）→ `pose.translate(leftPos, topPos)` →
   `extractLabels` → 计算 `hoveredSlot = getHoveredSlot(mouseX, mouseY)` → 槽位高亮（back） → `extractSlots`
   （每个 `slot.isActive()` 的槽位画槽位贴图 + 物品 + 数量角标 + 快速合成预览） → 槽位高亮（front）；
3. `ScreenEvent.Render.Foreground`（NeoForge，给 JEI 之类的 overlay）；
4. `extractCarriedItem`：把 `menu.getCarried()` 画在鼠标位置（快速合成时用剩余数量）；
5. `extractTooltip`：悬停槽位有物品时注册 tooltip（如果手里没物品，或物品的 tooltip 支持手持显示）。

槽位命中：`getHoveredSlot(x,y)` 遍历 `menu.slots`，`slot.isActive()` 且点落在 `(slot.x-1, slot.y-1, 18, 18)`；
`isHovering` 是 `xm >= left-1 && xm < left+w+1` 的 2px 容差。

## 3. 输入状态机（`AbstractContainerScreen`）

- `mouseClicked(event, doubleClick)`：
  - 先给 widgets 处理；
  - 计算 `cloning = keyPickItem.matchesMouse(event) && player.hasInfiniteMaterials()`；
  - `doubleclick = lastClickSlot == slot && doubleClick`；
  - 非 1/3 键：`checkHotbarMouseClicked`（副手交换 F、数字键 1-9 → `ContainerInput.SWAP`，button 40 = 副手）；
  - 1/3 键或 cloning：判断 `clickedOutside`（面板外 → slotId=-999，用于丢出）；
    - 手里没物品：shift→`QUICK_MOVE`、面板外→`THROW`、cloning→`CLONE`，否则 `PICKUP`；
    - 手里有物品：进入 `isQuickCrafting`，按按键设置 quickCraftingType（左 0 / 右 1 / 复制 2），清空 `quickCraftSlots`；
  - 所有点击最终走 `slotClicked(slot, slotId, button, ContainerInput)` →
    `minecraft.gameMode.handleContainerInput(menu.containerId, slotId, button, input, player)`。
- `mouseDragged`：把鼠标经过的槽位加入 `quickCraftSlots`（校验 `canItemQuickReplace / mayPlace / canDragTo`），
  `recalculateQuickCraftRemaining()` 计算剩余数量用于预览。
- `mouseReleased`：
  - 双击 + 右键 + `canTakeItemForPickAll`：Shift 双击对同类槽位批量 `QUICK_MOVE`，否则 `PICKUP_ALL`；
  - 快捷合成：`quickCraftToSlots()` 依次发送 `QUICK_CRAFT` 掩码 0（开始）→ 每个槽位 1（放置）→ 2（结束）；
  - 手里有物品且在槽位上：Shift→`QUICK_MOVE`，否则 `PICKUP`；面板外 → 丢出；cloning → `CLONE`；
  - 处理 `skipNextRelease`、快速合成取消等情况。
- `keyPressed`：`keyInventory` 关闭界面；`checkHotbarKeyPressed`（数字键/副手交换 → `SWAP`）；
  悬停槽位按拾取键 → `CLONE`；`Q` → `THROW`（Ctrl+Q 整组）。
- `mouseScrolled`：交给 `ItemSlotMouseAction`（例如创造模式物品栏的翻页）。

## 4. 菜单数据与同步（`AbstractContainerMenu` / `ClientPacketListener`）

- `AbstractContainerMenu`：`slots: List<Slot>`、`carried: ItemStack`、`stateId`、`containerId`、`menuType`；
  槽位 `Slot` 有 `container`（数据容器）、`x/y`（面板内坐标）、`index`、`isActive()`、`mayPlace()` 等。
- 客户端点击：`MultiPlayerGameMode.handleContainerInput(containerId, slotNum, button, ContainerInput, player)`
  → `ServerboundContainerClickPacket(containerId, stateId, slotNum, button, input, carried, changedSlots)`。
- 服务端：`ServerGamePacketListenerImpl.handleContainerClick` 校验 `containerId == player.containerMenu.containerId`，
  再调用 `menu.clicked(...)`；`clicked` 内实现 PICKUP/QUICK_MOVE/SWAP/CLONE/THROW/QUICK_CRAFT/PICKUP_ALL 语义。
- 同步包：`ClientboundContainerSetContentPacket`（全量：items + carried，用于打开/全量纠正）、
  `ClientboundContainerSetSlotPacket`（单槽）、`ClientboundContainerSetDataPacket`（进度等）、
  `ClientboundContainerClosePacket`、`ClientboundOpenScreenPacket`。
- 客户端打开流程：`MenuScreens.create(type, minecraft, containerId, title)` → 工厂 `fromPacket` 里
  `minecraft.player.containerMenu = screen.getMenu()` 然后 `gui.setScreen(screen)`；客户端每个菜单的槽位更新只应用到
  `player.containerMenu`（按 containerId 匹配）。

## 5. 创造模式物品栏

`CreativeModeInventoryScreen` + `ItemPickerMenu`（客户端菜单，`containerId` 也是 0）：
- 45 个"物品选择"槽 + 9 个玩家快捷栏槽；翻页/搜索/页签都是屏幕自己绘制的控件；
- 拾取走本地 `CLONE`（`AbstractContainerMenu.clicked` 的 CLONE 分支在客户端预测），
  数值不在服务端菜单里；关闭界面时手里物品直接丢弃；
- 往容器里放创造物品必须先落到玩家背包/快捷栏，服务端才有对应物品。

## 6. 对 HUI 转义器的启示

1. **数据层不需要重写**：所有同步都通过 `AbstractContainerMenu` + 原版包完成；HUI 只需要维护"当前激活菜单"
   并在交互前让服务端把 `containerMenu` 切到目标菜单（已有 `ActivateMenuPayload`）。
2. **渲染层必须自绘**：原版屏幕的面板/槽位/高亮/物品/tooltip 都是 `GuiGraphicsExtractor` 指令，HUI 可以用自己的
   面板样式重画，槽位坐标直接取 `Slot.x/y`。
3. **输入层要完整移植状态机**：`quickCraft`、`doubleClick`、`skipNextRelease`、hotbar swap、drop、pick 等字段
   都是 `AbstractContainerScreen` 的私有状态，转义器必须自己保存并逐条还原 `ContainerInput` 序列。
4. **原版控件（页签/配方书/滚动条/自定义渲染）没有通用提取 API**：需要"每类屏幕的 UI 描述"或直接 HUI 风格重绘，
   不可能 100% 自动。转义器应支持：标准槽位网格 + 可选描述器（标签/按钮/贴图），否则全屏兜底。
