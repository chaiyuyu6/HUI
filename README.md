# HUI

Minecraft **26.3** / NeoForge **26.3.0.90** 的 GUI / HUD 修改模组。

- **模组名**：HUI
- **作者**：chai
- **Mod ID**：`hui`
- **包名**：`com.chai.hui`

项目基于 NeoForge 官方 MDK 模板 [`MDK-26.3-ModDevGradle`](https://github.com/NeoForgeMDKs/MDK-26.3-ModDevGradle)，并做了面向 26.3 新 API 的 HUD / GUI 实现。

## 功能

| 功能 | 说明 |
| --- | --- |
| HUD 信息面板 | 以 NeoForge `GuiLayer` 注册（`RegisterGuiLayersEvent`），显示帧率、坐标、维度、生物群系、游戏内天数与时钟 |
| 面板自定义 | 停靠角落（左上/右上/左下/右下）、偏移量、背景不透明度、强调色、每一行内容的开关 |
| 自定义设置界面 | `HuiConfigScreen`，26.3 的 `GuiGraphicsExtractor` 提取式渲染 + `pushScreenLayer` 屏幕层 API |
| 按键绑定 | `H` 切换面板，`K` 打开设置界面（可在“控制”里改键） |
| 配置持久化 | NeoForge CLIENT 配置 `config/hui-client.toml`，也可从 Mods 菜单打开设置 |

## 构建

```powershell
cd E:\NewJorney\HUI
.\gradlew.bat build --console=plain
```

产物：`build/libs/hui-0.1.0.jar`。

需要 JDK 25（Minecraft 26.3 使用的 Java 版本），Gradle Wrapper 会自动使用 9.2.1。

> 说明：`build.gradle` 里有一小段仓库重定向，把 `maven.neoforged.net` 指到 `neoforged.forgecdn.net` CDN 镜像，
> 用于在无法直连 NeoForged 官方 Maven 的网络环境下构建。能直连时删掉它即可。

## 开发环境

| 组件 | 版本 |
| --- | --- |
| Minecraft | 26.3 |
| NeoForge | 26.3.0.90 |
| ModDevGradle | 2.0.148 |
| Gradle | 9.2.1 |
| Java | 25 |

在 IDE 中打开后，可用 `gradlew runClient` 启动开发客户端。

## 关键类

- `com.chai.hui.HuiMod` — 模组入口，注册 CLIENT 配置。
- `com.chai.hui.HuiConfig` — 配置项定义（`ModConfigSpec`）。
- `com.chai.hui.HudCorner` — 面板停靠角落枚举。
- `com.chai.hui.client.HuiClient` — 客户端入口：HUD 层、按键、tick、设置界面接线。
- `com.chai.hui.client.hud.HuiHudLayer` — HUD 面板渲染（`GuiGraphicsExtractor`）。
- `com.chai.hui.client.screen.HuiConfigScreen` — 自定义设置界面。
- `com.chai.hui.client.HuiKeyMappings` — 按键绑定定义。

## 26.3 API 备注

- 旧 `GuiGraphics` 在 26.3 中由 **`GuiGraphicsExtractor`** 取代，GUI 改为“提取渲染状态”模型：
  `fill(...)`、`outline(...)`、`text(font, component, x, y, color, dropShadow)`、`centeredText(...)`、`pose()` 等。
- `ResourceLocation` 改名为 **`Identifier`**（`Identifier.fromNamespaceAndPath(...)`）。
- 屏幕通过 **`mc.gui.pushScreenLayer(screen)`** 打开、`popScreenLayer()` 关闭；渲染方法是 `extractRenderState` /
  `extractBackground`，不再有 `render(...)`。
- 按键分类使用 `KeyMapping.Category` 并在 `RegisterKeyMappingsEvent` 中 `registerCategory(...)`。

## 许可

All Rights Reserved（可在 `gradle.properties` 的 `mod_license` 中修改）。
