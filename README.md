# 简易飞行戒指 Simple Flight Ring

一个面向 Minecraft 的小型 MOD，添加 6 种材质的飞行戒指：
木 → 石 → 铁 → 金 → 钻石 → 下界合金。

本仓库同时维护两个版本（各自为独立的 Gradle 项目，互不干扰）：

| 目录 | 游戏版本 | NeoForge | 工具链 |
|------|---------|----------|--------|
| [`FlightRing/`](FlightRing) | Minecraft **1.21.1** | NeoForge 21.1.248 | Gradle 8.14.3 / JDK 21 / ModDevGradle 1.0.17 |
| [`FlightRing-26.1.2/`](FlightRing-26.1.2) | Minecraft **26.1.2** | NeoForge 26.1.2.97 | Gradle 9.2.1 / JDK 25 / NeoGradle userdev 7.1.38 |

## 功能特性

- **传统创造飞行**：佩戴（或背包内携带）有耐久的戒指时获得创造飞行能力，
  双击空格起飞/降落，按住空格上升、Shift 下降。
- **仅飞行时消耗耐久**：每飞行 1 秒消耗 1 点；耐久附魔每级让每点耐久多飞行 1 秒
  （耐久 III 时每 4 秒消耗 1 点）；经验修补（Mending）原生兼容。
- **戒指耐久耗尽后不消失**：失效但保留，可升级合成或修复。
- **嵌套合成升级**：3×3 工作台外圈 8 材料 + 中间羽毛（木戒）/低一级戒指；
  升级保留附魔/自定义名称/Lore，接受任意剩余耐久，产物为满耐久新戒指。
- **下界合金戒指**：锻造台升级（下界合金升级模板 + 钻石飞行戒指 + 下界合金锭）。
- **HUD 飞行时间倒计时**：默认左下角；多枚戒指剩余时间累加；打开聊天框时自动隐藏。
  位置与开关可在 `config/simpleflightring-client.toml` 配置，支持 Cloth Config 游戏内调整。
- **可选集成**（均非必须）：
  - **Curios API**：添加「飞行戒指」饰品槽位（可右键佩戴；未安装时背包内生效，背包始终生效）。
  - **Cloth Config API**：游戏内 Mod 列表 → Config 打开配置界面（开关 + 位置滑块）。
  - **精妙背包 Sophisticated Backpacks**：戒指放在精妙背包内同样生效
    （主物品栏/盔甲栏/副手/Curios 槽中的背包均可，支持嵌套背包）。

## 各材质飞行时间

| 材质 | 总飞行时间 | 耐久值 |
|------|-----------|--------|
| 木 | 5 分钟 | 300 |
| 石 | 15 分钟 | 900 |
| 铁 | 30 分钟 | 1800 |
| 金 | 60 分钟 | 3600 |
| 钻石 | 120 分钟 | 7200 |
| 下界合金 | 240 分钟 | 14400 |

## 构建

各自目录下运行（构建产物在 `build/libs/simpleflightring-1.0.0.jar`）：

```bat
:: 1.21.1 版（需要 JDK 21）
cd FlightRing
gradlew.bat build

:: 26.1.2 版（需要 JDK 25，Gradle 会自动下载 toolchain）
cd ..\FlightRing-26.1.2
gradlew.bat build
```

开发环境运行：`gradlew.bat runClient` / `runServer`。

> `libs/` 目录包含本地下载的可选依赖 jar（Curios / Cloth Config / Sophisticated Backpacks 及核心库），
> 已随仓库提交以便开箱即用。

## 安装

1. 安装对应版本的 NeoForge。
2. 将对应版本的 `simpleflightring-1.0.0.jar` 放入 `mods` 文件夹。
3. （可选）安装对应版本的 Curios API / Cloth Config / Sophisticated Backpacks 以获得额外集成。

## 许可

MIT（见 [LICENSE](LICENSE)）
