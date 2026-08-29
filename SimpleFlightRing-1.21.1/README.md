# 简易飞行戒指 Simple Flight Ring

一个面向 **Minecraft 1.21.1 / NeoForge** 的小型 MOD，添加 6 种材质的飞行戒指：
木 → 石 → 铁 → 金 → 钻石 → 下界合金。

## 特性

- **传统创造飞行**：佩戴（或背包内携带）有耐久的戒指时，玩家获得创造模式的飞行能力，
  双击空格起飞/降落，按住空格上升、Shift 下降，与创造模式手感完全一致。
- **仅飞行时消耗耐久**：每飞行 1 秒消耗 1 点耐久；站着不动或落地后不消耗。
- **耐久附魔延长飞行时间**：耐久附魔每级让每点耐久多飞行 1 秒
  （耐久 III 时每 4 秒才消耗 1 点耐久）。
- **经验修补（Mending）**：直接兼容原版机制，拾取经验球即可修复戒指。
- **戒指耐久耗尽后不会消失**：变为失效状态（无法飞行），仍可参与升级合成，
  也可通过经验修补或铁砧修复。
- **嵌套合成升级**：3x3 工作台外圈 8 格为对应材料，中间放羽毛（木戒指）或低一级戒指。
  - 升级时**保留附魔、自定义名称和 Lore**；
  - 升级配方**忽略输入戒指的剩余耐久**（哪怕耐久为 0 也能升级）；
  - 升级产物为满耐久的新戒指。
- **下界合金戒指**：通过**锻造台**升级——下界合金升级锻造模板 + 钻石飞行戒指 + 下界合金锭。
- **Curios API 可选集成**（非必须）：
  - 安装 Curios 后自动添加一个额外的**「飞行戒指」饰品槽位**，戒指可右键直接佩戴；
  - 未安装 Curios 时，戒指放在背包内即可生效；
  - 即使安装了 Curios，放在背包内同样生效（优先检测饰品槽位，其次背包）。
- **HUD 飞行时间倒计时**：携带可用戒指时在游戏界面显示剩余飞行时间（默认左下角），
  可在配置文件中开关与调整位置。
- **Cloth Config API 可选适配**（非必须）：安装后可在游戏内 Mod 列表点击「Config」打开配置界面，
  用滑块条实时调整 HUD 位置；未安装时直接编辑配置文件同样生效。
- **精妙背包 Sophisticated Backpacks 可选适配**（非必须）：戒指放在精妙背包内同样生效——
  背包在主物品栏、**盔甲栏（穿在背上）**、副手或 Curios 槽位中均可，支持**嵌套背包**（最深 3 层）；
  HUD 倒计时同样累加背包内戒指。

## HUD 与配置

配置文件位于 `config/simpleflightring-client.toml`（客户端配置）：

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `showFlightTimer` | `true` | 是否显示飞行时间倒计时 |
| `hideWhileChatOpen` | `true` | 打开聊天框输入时是否隐藏倒计时（默认位置与聊天框重叠，建议保持开启） |
| `hudX` | `4` | 倒计时水平位置：距屏幕左边缘的像素（滑块范围 0~4096） |
| `hudY` | `4` | 倒计时垂直位置：距屏幕底部边缘的像素（滑块范围 0~4096） |

- 默认显示在**左下角**（`hudX=4, hudY=4`），调大 `hudY` 可向上移动。
- 倒计时显示格式为「飞行时间：X 分 Y 秒」；持有**多枚戒指**（饰品槽位 + 背包 + 副手）时，
  剩余飞行时间会**累加**显示。
- 打开聊天框输入文字时倒计时自动隐藏（可在配置中关闭此行为），避免遮挡聊天输入。
- 安装 [Cloth Config API](https://modrinth.com/mod/cloth-config)（15.x，1.21.1）后，
  打开游戏内 Mod 列表 → 简易飞行戒指 → 「Config」即可用开关与滑块条调整上述选项并保存。
- 安装 [Curios API](https://modrinth.com/mod/curios) 与 Cloth Config 均为可选项，MOD 单独运行不受影响。

## 各材质飞行时间

| 材质 | 总飞行时间 | 耐久值 | 附魔能力 |
|------|-----------|--------|---------|
| 木 | 5 分钟 | 300 | 10 |
| 石 | 15 分钟 | 900 | 5 |
| 铁 | 30 分钟 | 1800 | 14 |
| 金 | 60 分钟 | 3600 | 22 |
| 钻石 | 120 分钟 | 7200 | 10 |
| 下界合金 | 240 分钟 | 14400 | 15 |

> 耐久值 = 飞行分钟数 × 60（每飞行 1 秒消耗 1 点耐久）；
> 耐久 III 时各戒指实际可用时间约为上表的 4 倍（再加上经验修补可无限续航）。

## 合成配方

### 木飞行戒指（基础戒指）
```
橡木木板 橡木木板 橡木木板
橡木木板  羽毛   橡木木板
橡木木板 橡木木板 橡木木板
```

### 升级合成（石 / 铁 / 金 / 钻石）
外圈 8 格为对应材料，中间为低一级戒指（任意耐久均可）：

| 目标 | 外圈材料 | 中间 |
|------|---------|------|
| 石飞行戒指 | 石头 | 木飞行戒指 |
| 铁飞行戒指 | 铁锭 | 石飞行戒指 |
| 金飞行戒指 | 金锭 | 铁飞行戒指 |
| 钻石飞行戒指 | 钻石 | 金飞行戒指 |

### 下界合金飞行戒指（锻造台）
- 槽位 1：下界合金升级锻造模板
- 槽位 2：钻石飞行戒指（任意耐久）
- 槽位 3：下界合金锭

## 安装

1. 安装 [NeoForge 21.1.x](https://neoforged.net/)（需 21.1.228 或更高）。
2. 将 `build/libs/simpleflightring-1.0.0.jar` 放入 `mods` 文件夹。
3. （可选）安装 [Curios API 9.5.1+1.21.1](https://modrinth.com/mod/curios) 以获得饰品槽位。
4. （可选）安装 [Cloth Config API 15.x](https://modrinth.com/mod/cloth-config) 以获得游戏内配置界面。
5. （可选）安装 [Sophisticated Backpacks](https://modrinth.com/mod/sophisticated-backpacks)
   及其前置 [Sophisticated Core](https://modrinth.com/mod/sophisticated-core)，戒指放在精妙背包内也能生效。

## 从源码构建

环境要求：JDK 21、Gradle 8.14+（项目内已包含 wrapper）。

```bat
gradlew.bat build
```

产物位于 `build/libs/simpleflightring-1.0.0.jar`。

开发环境运行（含 Curios，来自本地 `libs/` 目录的 jar）：

```bat
gradlew.bat runClient
```

## 项目结构

```
FlightRing/
├── build.gradle / settings.gradle / gradle.properties   # NeoForge 21.1.228 + ModDevGradle 1.0.17
├── libs/                                                # 本地 Curios jar（API/完整版/源码）
└── src/main/
    ├── java/com/simpleflightring/
    │   ├── FlightRingMod.java        # 主类：注册物品/配方序列化器/创造标签/配置，可选加载 Curios 与 Cloth Config
    │   ├── RingTier.java             # 6 种材质：飞行分钟数、附魔能力（耐久 = 分钟 × 60）
    │   ├── FlightRingItem.java       # 戒指物品（耐久=飞行分钟数×60、Tooltip 显示剩余分/秒）
    │   ├── FlightRingConfig.java     # 客户端配置（HUD 开关与位置，config/simpleflightring-client.toml）
    │   ├── FlightHud.java            # 客户端 HUD：飞行时间倒计时渲染（多戒指累加）
    │   ├── ClothConfigCompat.java    # Cloth Config 可选集成（游戏内配置界面）
    │   ├── BackpackCompat.java       # 精妙背包可选集成（扫描背包内戒指，支持嵌套）
    │   ├── ModItems.java             # 物品注册
    │   ├── ModRecipeSerializers.java # 自定义配方序列化器注册
    │   ├── ModCreativeTabs.java      # 创造模式物品栏标签
    │   ├── RingUpgradeRecipe.java    # 工作台升级配方（保留附魔/名称/Lore，接受任意耐久）
    │   ├── RingSmithingRecipe.java   # 锻造台升级配方（继承 SmithingTransformRecipe，兼容 JEI 显示）
    │   ├── RingUpgradeHelper.java    # 组件复制逻辑
    │   ├── FlightHandler.java        # 飞行逻辑：授予创造飞行、按秒消耗耐久
    │   └── CuriosCompat.java         # Curios 可选集成（槽位查询、右键佩戴）
    └── resources/
        ├── META-INF/neoforge.mods.toml
        ├── assets/simpleflightring/        # 语言(zh_cn/en_us)、模型、贴图
        └── data/
            ├── simpleflightring/recipe/    # 6 个配方
            ├── simpleflightring/curios/    # 槽位与实体分配（数据包方式注册槽位）
            └── curios/tags/item/     # 飞行戒指物品标签
```

## 调整数值

各材质飞行分钟数与附魔能力定义在 `RingTier.java` 中（耐久值自动等于分钟数 × 60），修改后重新构建即可。

## 已知设计说明

- 创造/旁观模式的玩家不会消耗戒指耐久（本来就能飞）。
- 戒指耐久耗尽后保留在背包/槽位中（不碎裂），方便升级与修复。
- Curios 槽位采用数据包 JSON 方式注册（Curios 9.x 推荐方式），
  槽位 id 为 `flight_ring`，物品需属于标签 `curios:flight_ring` 才能放入。

## 许可

MIT
