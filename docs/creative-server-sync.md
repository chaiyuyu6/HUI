# 创造物品栏服务端同步方案（根治客户端菜单 bug）

## 结论

把创造物品栏从"纯客户端 `ItemPickerMenu`"改成 **HUI 自己的服务端菜单**：`HuiCreativeMenu`。
这样 carried、槽位、放置/取出全部由服务端权威同步，`containerId` 唯一（不再是 0），
彻底消除：containerId 撞号、carried 被全量同步清空、服务端不知道物品、跨窗口复制/消失。

## 菜单结构（服务端 + 客户端同构）

`HuiCreativeMenu extends AbstractContainerMenu`：

| 槽位 | 内容 | 规则 |
| --- | --- | --- |
| 0–44 | 当前页签的展示物品（45 格） | `mayPickup=false`、`mayPlace=false`，`ContainerInput.CLONE` 时由原版 `AbstractContainerMenu.clicked` 克隆到 carried |
| 45–71 | 玩家背包 27 格 | 普通 `Slot` |
| 72–80 | 快捷栏 9 格 | 普通 `Slot` |

- 服务端在 `menu.clicked` 之前不需要特殊处理：CLONE 分支已经要求 `player.hasInfiniteMaterials()`，
  拾取后 carried 由服务端菜单权威持有，放进背包就是普通 `PICKUP`，客户端只收到同步结果。
- 展示物品由服务端 `SimpleContainer` 填充；页签切换/搜索通过 small payload（`CreativeTabPayload` /
  `CreativeSearchPayload`）请求服务端重新填充 + `broadcastChanges`。
- 页签列表来自服务端 `CreativeModeTabs.allTabs()`（包含所有 mod 注册页签），与客户端列表一致；
  页签名/图标通过普通 slot 数据或 `ClientboundContainerSetDataPacket` 同步（tab index / page）。

## 客户端表现

- HUI 打开创造窗口时不再使用原版 `CreativeModeInventoryScreen`，而是：
  1. 客户端发 `OpenCreativePayload`；
  2. 服务端 `player.openMenu(HuiCreativeMenu)`（自定义 `MenuType` 注册进 `BuiltInRegistries.MENU`）；
  3. 客户端 `MenuScreens` 为这个类型注册一个轻量 `AbstractContainerScreen` 存根，HUI 的 `ScreenEvent.Opening`
     把它替换成 HUI 窗口（与容器窗口同一路径，复用 `VanillaMenuUiContent`/框架）；
  4. 页签/搜索/滚动条由 HUI UI 绘制（可复用原版 `CreativeModeInventoryScreen` 的贴图/布局代码逐屏移植），
     点击后发对应 payload，服务端更新展示槽位。

## 迁移步骤

1. 注册 `hui:creative` MenuType + `HuiCreativeMenu`（服务端填充当前页签展示物品）；
2. `RegisterMenuScreensEvent` 注册客户端存根 Screen，HUI 拦截成窗口；
3. `OpenCreativePayload` / `CreativeTabPayload` / `CreativeSearchPayload` + 服务端处理；
4. HUI 创造窗口 UI：页签列表（含 mod 页签）、搜索框、9 列网格、滚动、快捷栏/背包槽位（用菜单真实槽位渲染）；
5. 删除 `ClientMenuSession` / `CreativeInsertPayload` / `ClearCarriedPayload` 等过渡 hack（服务端菜单不再需要）；
6. 回归：拿取/放置/跨窗口/关闭/退出/多人（服务端权威）。

## 与"照搬原版"的关系

外观可以照搬原版（贴图/布局/页签位置从 `CreativeModeInventoryScreen` 代码移植），但数据层改为服务端菜单，
这才是"完全照搬原版行为"而不复制其客户端菜单缺陷的做法。

## 已确认的视觉要求

**像素级照搬原版**：创造窗口外观必须与原版 `CreativeModeInventoryScreen` 一致，包括：

- 面板背景贴图（`selectedTab.getBackgroundTexture()`，256x256 纹理按 195x136 区域绘制）；
- 上下两排页签按钮（`tab_top/bottom_selected/unselected_1..7` 精灵，含分页逻辑与页签位置）；
- 滚动条（`getScrollerSprite()`，175,18 起 12x15，含 hover 光标类型）；
- 搜索框（原版 `EditBox` 外观：背景、光标、占位符、高亮）；
- 玩家预览（`InventoryScreen.extractEntityInInventoryFollowsMouse`，INVENTORY 页签）；
- 销毁物品槽（垃圾桶）与页签分页、翻页行为。

实现方式：在 HUI 窗口内容里**复用/移植这些原版渲染代码**（骨架用服务端同步菜单的数据），
输入事件转换成原版 `ContainerInput` 或页签/搜索 payload；不改变原版外观像素。

数据层仍按本方案：`HuiCreativeMenu` 服务端权威 + 页签/搜索 payload。
