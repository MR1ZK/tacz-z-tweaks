# 附属调用的 TACZ 内部 API 与 mixin 目标盘点

> ticket: #12「附属调用的 TACZ 内部 API 与 mixin 目标盘点」（`wayfinder:research`）
> 工作树：`.zt-research/tacz-internal-api`，分支 `research/tacz-internal-api`
> 目标 TACZ 版本：**1.1.8-hotfix**（Forge 1.20.1-47.3.19）
> 参考：TACZ 本体只读仓库 `c:/Users/YTH/Documents/myprojects/TACZ`
> 本文件是 ticket #13「内部 API 适配层（compat/）该不该建、怎么收口」的输入。

---

## 0. 结论 gist

- 附属共 **9 个 Java 类**，其中 **只有 3 个**碰了 TACZ：`ZtRefitScreen.java`（绝对主力）、`ZtRefitTakeover.java`、两个 mixin。
- TACZ 侧依赖分两类：**公开 API**（`com.tacz.guns.api.*`，8 个 import，安全）与 **内部包**（22 个 import，脆弱）。
- 内部依赖高度集中在 **三类咽喉**：
  1. **数据/求值**：`AttachmentDataUtils`（8 个静态方法）、`AttachmentPropertyManager.getModifiers()/eval`、`AttachmentCacheProperty.eval`、`IAttachmentModifier`/`DiagramsData`；
  2. **界面/渲染状态**：`GunRefitScreen`（父类）、`RefitTransform`（静态状态机）、`HSVSliderGroup`、`GunPropertyDiagrams`、两处 mixin；
  3. **网络**：`NetworkHandler.CHANNEL` + `ClientMessageRefitGun` / `ClientMessageUnloadAttachment`。
- **两处 mixin 都是 `require = 0` 静默降级**：注入失败时相机 / 虚拟装配失效，但界面其余功能照常 —— 玩家若不看诊断读数不会知道。
- 票面点名要确认的 `CommonAssetsManager`、`CommonNetworkCache`、`ClientMessagePlayerZoom`、`ClientGunIndex` **在本分支与主工作树代码中均不存在引用**（见 §5），属票面预设项，不是实际依赖。
- `compat/` 若要建：**必须收口 11 类**（清单见 §7），其中 `GunRefitScreen` 父类继承与两处 mixin 是「收口收益最大、但也最难收」的两块。

---

## 1. 盘点范围与证据方式

| 项 | 值 |
| --- | --- |
| 扫描目录 | `src/main/java/com/ztweaks/`（9 个类） |
| 匹配方式 | `com.tacz.` 全量 import 扫描 + 逐符号 grep + TACZ 本源签名核对 |
| 证据格式 | `相对路径:行号`，行号以本分支当前 HEAD 为准 |
| 未发现项 | 已对**两个工作树**（本分支 + 主工作树 `tacz-z-tweaks/src`）交叉搜索确认 |

9 个类中涉及 TACZ 的文件：

| 文件 | 大小 | 是否碰 TACZ | 内部包依赖数 |
| --- | --- | --- | --- |
| `src/main/java/com/ztweaks/client/ZtRefitScreen.java` | 79 KB | 是（重度） | 21 import |
| `src/main/java/com/ztweaks/client/ZtRefitTakeover.java` | 1.8 KB | 是（1 处） | 1 |
| `src/main/java/com/ztweaks/mixin/FirstPersonRenderGunEventMixin.java` | 2.6 KB | 是 | 3 |
| `src/main/java/com/ztweaks/mixin/GunItemRendererWrapperMixin.java` | 2.0 KB | 是 | 1 |
| `src/main/java/com/ztweaks/client/VirtualAssembly.java` | 7.2 KB | 是（仅 API） | 0 |
| `src/main/java/com/ztweaks/client/OrbitCamera.java` | 7.5 KB | 否 | 0 |
| `src/main/java/com/ztweaks/client/ZtUi.java` | 5.9 KB | 否 | 0 |
| `src/main/java/com/ztweaks/client/ZtConfigScreen.java` | 7.7 KB | 否 | 0 |
| `src/main/java/com/ztweaks/config/ZtConfig.java` | 6.8 KB | 否（仅注释提及） | 0 |

> 例外：`ZtConfig.java:45/72/92` 只在注释里提到 `GunPropertyDiagrams` / `GunRefitScreen`，不是代码依赖，不计入。

---

## 2. 按包分类：公开 API（安全）vs 内部包（脆弱）

### 2.1 公开 API —— `com.tacz.guns.api.*`（安全）

| 符号 | import 位置 | 本分支调用点 |
| --- | --- | --- |
| `TimelessAPI` | `ZtRefitScreen.java:3` | `:302,317,372,463,503,594,1374,1417` |
| `IGunOperator` | `ZtRefitScreen.java:4` | `:589` |
| `IAttachment` | `ZtRefitScreen.java:5`、`VirtualAssembly.java:3` | `ZtRefitScreen:429,430,1371,1601`；`VirtualAssembly:166` |
| `IGun` | `ZtRefitScreen.java:6`、`VirtualAssembly.java:4` | 多处 |
| `AttachmentType` | `ZtRefitScreen.java:7`、`VirtualAssembly.java:5` | 多处 |
| `AmmoItemBuilder` | `ZtRefitScreen.java:8` | `:1438` |
| `AttachmentItemBuilder` | `ZtRefitScreen.java:9` | `:379` |
| `IAttachmentModifier` | `ZtRefitScreen.java:13` | `:516,517,520,521` |

> 结论：API 层无需收口。**唯一例外**：`IAttachmentModifier` 虽是 API 包，但它暴露的 `DiagramsData` 记录体来自内部渲染语义（见 §3.4），收口时要连带处理。

### 2.2 内部包 —— 非 `com.tacz.guns.api`（脆弱）

按 TACZ 包归类：

| TACZ 包 | 类 | 用途类别 |
| --- | --- | --- |
| `com.tacz.guns.util` | `AttachmentDataUtils` | 数据/求值 |
| `com.tacz.guns.resource.modifier` | `AttachmentPropertyManager`、`AttachmentCacheProperty` | 数据/求值 |
| `com.tacz.guns.resource.index` | `CommonGunIndex`、`CommonAttachmentIndex` | 数据读取 |
| `com.tacz.guns.resource.pojo.data.gun` | `GunData`、`ExplosionData` | 数据读取 |
| `com.tacz.guns.resource.pojo.data.attachment` | `AttachmentData` | 数据读取 |
| `com.tacz.guns.config.sync` | `SyncConfig` | 配置常量 |
| `com.tacz.guns.client.gui` | `GunRefitScreen` | 父类继承 |
| `com.tacz.guns.client.gui.components.refit` | `GunPropertyDiagrams`、`HSVSliderGroup` | 渲染 |
| `com.tacz.guns.client.animation.screen` | `RefitTransform` | 渲染/状态 |
| `com.tacz.guns.client.resource` | `GunDisplayInstance` | 数据读取 |
| `com.tacz.guns.client.resource.index` | `ClientAttachmentIndex` | 数据读取 |
| `com.tacz.guns.client.resource.pojo.display` | `LaserConfig` | 数据读取 |
| `com.tacz.guns.client.sound` | `SoundPlayManager` | 音效 |
| `com.tacz.guns.sound` | `SoundManager` | 音效常量 |
| `com.tacz.guns.network` | `NetworkHandler` | 网络 |
| `com.tacz.guns.network.message` | `ClientMessageRefitGun`、`ClientMessageUnloadAttachment` | 网络 |
| `com.tacz.guns.client.event` | `FirstPersonRenderGunEvent` | 渲染（mixin 目标） |
| `com.tacz.guns.client.model` | `BedrockGunModel` | 渲染（mixin 目标） |
| `com.tacz.guns.client.model.bedrock` | `BedrockPart` | 渲染（mixin 目标） |
| `com.tacz.guns.client.renderer.item` | `GunItemRendererWrapper` | 渲染（mixin 目标） |

---

## 3. 内部依赖逐条明细

### 3.1 数据 / 求值类

#### 3.1.1 `AttachmentDataUtils`（`com.tacz.guns.util`，离线全量重算）

- import：`ZtRefitScreen.java:12`
- 说明：TACZ 注释自述「不应该频繁调用」，附属因此按 `枪 id + NBT` 缓存（`ZtRefitScreen.java:165-169`，缓存键注释在 `:165`）。

| 调用点 | 调用 | TACZ 完整签名 | 用途 |
| --- | --- | --- | --- |
| `ZtRefitScreen.java:1459` | `getDamageWithAttachment(gun, gunData)` | `public static double getDamageWithAttachment(ItemStack, GunData)` | 面板伤害数值 |
| `ZtRefitScreen.java:1463` | `isExplodeEnabled(gun, gunData)` | `public static boolean isExplodeEnabled(ItemStack, GunData)` | 是否显示爆炸附伤 |
| `ZtRefitScreen.java:1472` | `getArmorIgnoreWithAttachment(gun, gunData)` | `public static double getArmorIgnoreWithAttachment(ItemStack, GunData)` | 护甲穿透 |
| `ZtRefitScreen.java:1476` | `getHeadshotMultiplier(gun, gunData)` | `public static double getHeadshotMultiplier(ItemStack, GunData)` | 爆头倍率 |
| `ZtRefitScreen.java:1480` | `getWightWithAttachment(gun, gunData)` | `public static double getWightWithAttachment(ItemStack, GunData)` | 移速惩罚 |

> TACZ 源：`TACZ/src/main/java/com/tacz/guns/util/AttachmentDataUtils.java:179,126,139,159,91`。
> 该类实际有 **8 个 public 静态方法**（`getAllAttachmentData`、`getMagExtendLevel`、`getAmmoCountWithAttachment`、`getWightWithAttachment`、`isExplodeEnabled`、`getArmorIgnoreWithAttachment`、`getHeadshotMultiplier`、`getDamageWithAttachment`），附属只用了其中 5 个（`AttachmentDataUtils.java:32,54,79,91,126,139,159,179`）。
> 内部还依赖 `AttachmentPropertyManager.eval(List<Modifier>, double)`（`AttachmentDataUtils.java:123,156,176,201`）与 `SyncConfig.*_MULTIPLIER`（`:153,173,198`）—— 这些是**传递依赖**，附属看不到但会一起坏。

#### 3.1.2 `AttachmentPropertyManager`（`com.tacz.guns.resource.modifier`）

- import：`ZtRefitScreen.java:26`
- TACZ 源：`TACZ/.../resource/modifier/AttachmentPropertyManager.java`

| 调用点 | 调用 | TACZ 完整签名 | 用途 |
| --- | --- | --- | --- |
| `ZtRefitScreen.java:515` | `AttachmentPropertyManager.getModifiers().forEach((id, modifier) -> ...)` | `public static Map<String, IAttachmentModifier<?, ?>> getModifiers()`（`AttachmentPropertyManager.java:49`） | 遍历全部属性修改器 |
| `ZtRefitScreen.java:596` | 同上 | 同上 | 采样 `positivelyString`/`negativeString` |

> 该类暴露的是**内部静态注册表**（`MODIFIERS`，`AttachmentPropertyManager.java:28,30`）。`eval(Modifier,double)` / `eval(List<Modifier>,double)` / `eval(List<Boolean>,boolean)`（`:77,81,102`）本身附属没直调，但被 `AttachmentDataUtils` 内部调用，属传递依赖。

#### 3.1.3 `AttachmentCacheProperty`（`com.tacz.guns.resource.modifier`）

- import：`ZtRefitScreen.java:25`
- TACZ 源：`TACZ/.../resource/modifier/AttachmentCacheProperty.java:20,26`

| 调用点 | 调用 | TACZ 完整签名 | 用途 |
| --- | --- | --- | --- |
| `ZtRefitScreen.java:508` | `new AttachmentCacheProperty()` | 隐式无参构造 | 造基准缓存 |
| `ZtRefitScreen.java:509` | `base.eval(gun, gunData)` | `public void eval(ItemStack gunItem, GunData gunData)`（`:26`） | 求值基准属性 |
| `ZtRefitScreen.java:512` | `new AttachmentCacheProperty()` | 同上 | 造候选缓存 |
| `ZtRefitScreen.java:513` | `modified.eval(withCandidate, gunData)` | 同上 | 求值候选属性 |
| `ZtRefitScreen.java:589` | `IGunOperator.fromLivingEntity(player).getCacheProperty()` | `AttachmentCacheProperty getCacheProperty()`（`api.entity.IGunOperator`；实现 `LivingEntityMixin.java:188`） | 取实时缓存 |
| `ZtRefitScreen.java:596` | `modifier.getPropertyDiagramsData(gun, gunData, cache)` 传参 | — | 用实时缓存取属性条数据 |

#### 3.1.4 `IAttachmentModifier` / `DiagramsData`（`com.tacz.guns.api.modifier`）

- import：`ZtRefitScreen.java:13`（API 包，但语义内部）
- TACZ 源：`TACZ/.../api/modifier/IAttachmentModifier.java:65,90-93`

| 调用点 | 调用 | TACZ 完整签名 | 用途 |
| --- | --- | --- | --- |
| `ZtRefitScreen.java:516,517` | `modifier.getPropertyDiagramsData(gun, gunData, base/withCandidate/modified)` | `List<DiagramsData> getPropertyDiagramsData(ItemStack, GunData, AttachmentCacheProperty)`（default，`@OnlyIn(CLIENT)`，`:65`） | 逐属性取图解数据 |
| `ZtRefitScreen.java:516,517,520,521` | `IAttachmentModifier.DiagramsData` | `record DiagramsData(double defaultPercent, double modifierPercent, Number modifier, String titleKey, String positivelyString, String negativeString, String defaultString, boolean positivelyBetter)`（`:90`） | 属性条数据体 |
| `ZtRefitScreen.java:522` | `now.modifier().doubleValue()` / `old.modifier()` | 记录访问器 `modifier()` | 差值比较 |
| `ZtRefitScreen.java:527,529,597,599` | `positivelyString()` / `negativeString()` | 记录访问器 | Pros/Cons 文本 |
| `ZtRefitScreen.java:530` | `now.titleKey()` | 记录访问器 | 属性名 |
| `ZtRefitScreen.java:531` | `now.positivelyBetter()` | 记录访问器 | 增减好坏判定 |

> 附属甚至**解析 TACZ 成品串**来抠增量（`ZtRefitScreen.java:537-561` 正则 `DELTA_TOKEN`），这是对 `positivelyString` 文本格式的隐式契约 —— 换 TACZ 改文案格式，解析静默退回旧写法（`:548` 注释）。

#### 3.1.5 内部数据 POJO：`GunData` / `AttachmentData` / `ExplosionData`

- import：`ZtRefitScreen.java:30`（GunData）、`:29`（AttachmentData）、`:11`（ExplosionData）

| 调用点 | 调用 | TACZ 完整签名 | 用途 |
| --- | --- | --- | --- |
| `ZtRefitScreen.java:1438` | `gunData.getAmmoId()` | `GunData.getAmmoId()` | 口径名 |
| `ZtRefitScreen.java:1461` | `gunData.getBulletData().getExplosionData()` | `GunData.getBulletData()` | 取爆炸数据 |
| `ZtRefitScreen.java:1463,1465` | `explosion.isExplode()` / `explosion.getDamage()` | `ExplosionData.isExplode()` / `getDamage()` | 爆炸附伤展示 |
| `ZtRefitScreen.java:464,465` | `index.getData().getModifier().forEach(...)` | `AttachmentData.getModifier()` | 读配件 modifier |
| `ZtRefitScreen.java:1421,595,503-504` | `index.getGunData()` | `CommonGunIndex.getGunData()`（`CommonGunIndex.java:122`） | 取枪械数据 |

#### 3.1.6 `SyncConfig`（`com.tacz.guns.config.sync`）

- import：`ZtRefitScreen.java:10`
- TACZ 源：`TACZ/.../config/sync/SyncConfig.java:19-22`

| 调用点 | 调用 | TACZ 声明 | 用途 |
| --- | --- | --- | --- |
| `ZtRefitScreen.java:1465` | `SyncConfig.DAMAGE_BASE_MULTIPLIER.get()` | `public static ForgeConfigSpec.DoubleValue`（`:19`） | 爆炸附伤基数 |
| `ZtRefitScreen.java:1479` | `SyncConfig.WEIGHT_SPEED_MULTIPLIER.get()` | `public static ForgeConfigSpec.DoubleValue`（`:22`） | 移速惩罚系数 |

### 3.2 索引 / 资源读取类

| 类 | import | 调用点与签名 | 用途 |
| --- | --- | --- | --- |
| `CommonGunIndex`（`resource.index`） | `ZtRefitScreen.java:28` | `:504,1421` `getGunData()`；`:1425` `getPojo().getTooltip()`；`:1455` `getType()` | 枪械 tooltip / 类型 |
| `CommonAttachmentIndex`（`resource.index`） | `ZtRefitScreen.java:27` | `:371-375` 遍历 `Map.Entry<ResourceLocation, CommonAttachmentIndex>`；`:376` `getType()`；`:464` `getData()` | 候选配件枚举 / 数据 |
| `ClientAttachmentIndex`（`client.resource.index`） | `ZtRefitScreen.java:19` | `:318` `::getLaserConfig`；`:1375` `getName()`；`:1607` `getTooltipKey()` | 配件名 / tooltip / 镭射配置 |
| `GunDisplayInstance`（`client.resource`） | `ZtRefitScreen.java:18` | `:303` `::getLaserConfig` | 整枪镭射配置 |
| `LaserConfig`（`client.resource.pojo.display`） | `ZtRefitScreen.java:20` | `:304,319` `::canEdit` | 是否允许改镭射色 |
| `GunData`（`resource.pojo.data.gun`） | `ZtRefitScreen.java:30` | 见 §3.1.5 | 枪械数据 |

> TACZ 源签名：`CommonGunIndex.java:122`、`CommonAttachmentIndex.java:43`、`ClientAttachmentIndex.java:300,305,343,378`、`GunDisplayInstance.java:759`、`LaserConfig.java:46`。

### 3.3 界面 / 渲染 / 音效 / 网络类

#### 3.3.1 `GunRefitScreen`（父类继承，`com.tacz.guns.client.gui`）

- import：`ZtRefitScreen.java:15`；`ZtRefitTakeover.java:4`
- TACZ 源：`TACZ/.../client/gui/GunRefitScreen.java:33` `public class GunRefitScreen extends Screen`

| 调用点 | 调用 | TACZ 签名/说明 | 用途 |
| --- | --- | --- | --- |
| `ZtRefitScreen.java:76` | `class ZtRefitScreen extends GunRefitScreen` | 继承内部 Screen | 父类继承 |
| `ZtRefitScreen.java:175` | `super()` | `public GunRefitScreen()`（`:46-49`，内部调 `RefitTransform.init()`） | 构造 |
| `ZtRefitScreen.java:181` | `public void init()` 覆写，**不调 super.init()** | `GunRefitScreen.init()`（`:86-87`） | 丢弃原生按钮 |
| `ZtRefitScreen.java:282-284` | `super.onClose()` | `public void onClose()`（`:273-274`，内部上传 `ClientMessageLaserColor`） | **依赖父类副作用上传镭射色** |
| `ZtRefitScreen.java:612` | `super.render(...)` | `render(GuiGraphics,int,int,float)`（`:112-113`） | 原生渲染 |
| `ZtRefitScreen.java:1049` | `super.mouseClicked(...)` | `Screen#mouseClicked` | 事件 |
| `ZtRefitScreen.java:1120,1146` | `super.mouseDragged(...)` | `Screen#mouseDragged` | 事件 |
| `ZtRefitScreen.java:1153` | `super.mouseReleased(...)` | `Screen#mouseReleased` | 事件 |
| `ZtRefitScreen.java:1200,1240` | `super.keyPressed(...)` | `Screen#keyPressed` | 事件 |
| `ZtRefitTakeover.java:32` | `screen instanceof GunRefitScreen` | 类型判定 | 拦截原生界面 |

> `ZtRefitScreen.java:71,182` 注释明确「刻意不调用 `super.init()`」；`:279` 注释明确 `super.onClose()` 负责上传镭射色「必须调用」。
> `GunRefitScreen` 覆写的父方法只有 4 个：`init`、`render`、`isPauseScreen`、`onClose`（`:86,112,126,273`）。附属覆写了 `init`/`onClose`/`render` 三个，其中 `init` 是**替换**而非扩展，最脆弱。

#### 3.3.2 `RefitTransform`（`com.tacz.guns.client.animation.screen`，静态状态机）

- import：`ZtRefitScreen.java:14`
- TACZ 源：`TACZ/.../client/animation/screen/RefitTransform.java`

| TACZ 完整签名 | 附属调用点 |
| --- | --- |
| `public static AttachmentType getCurrentTransformType()`（`:43`） | `ZtRefitScreen.java:207,300,361,688,747,878,899,920,959,1020,1188,1253,1272` |
| `public static float getOpeningProgress()`（`:33`） | `:1037` |
| `public static float getTransformProgress()`（`:47`） | `:1037` |
| `public static boolean changeRefitScreenView(AttachmentType)`（`:51`） | `:1187` |

> 附属 **18 处**读同一静态状态机，耦合面最广。

#### 3.3.3 `HSVSliderGroup`（`com.tacz.guns.client.gui.components.refit`）

- import：`ZtRefitScreen.java:17`
- TACZ 源：`TACZ/.../components/refit/HSVSliderGroup.java:24`

| 调用点 | 调用 | TACZ 完整签名 | 用途 |
| --- | --- | --- | --- |
| `ZtRefitScreen.java:325` | `new HSVSliderGroup(PAD, detailY()-42, 120, 14, inventory, inventory.selected, type)` | `public HSVSliderGroup(int x, int y, int width, int height, Inventory inventory, int gunItemIndex, @NotNull AttachmentType type)` | 镭射色滑块 |
| `ZtRefitScreen.java:326-327` | `group.getHueSlider()` / `getSaturationSlider()` | 返回 widget | 挂到界面 |

> 依赖该组件的**构造参数顺序 + getter**，且它内部脏写客户端本地 NBT 做实时预览（`ZtRefitScreen.java:289` 注释）。

#### 3.3.4 `GunPropertyDiagrams`（`com.tacz.guns.client.gui.components.refit`）

- import：`ZtRefitScreen.java:16`
- TACZ 源：`TACZ/.../components/refit/GunPropertyDiagrams.java:31`

| 调用点 | 调用 | TACZ 完整签名 | 用途 |
| --- | --- | --- | --- |
| `ZtRefitScreen.java:631` | `GunPropertyDiagrams.draw(graphics, this.font, 11, 96)` | `public static void draw(GuiGraphics graphics, Font font, int x, int y)` | 叠加原生属性条对照 |

#### 3.3.5 音效：`SoundPlayManager` + `SoundManager`

- import：`ZtRefitScreen.java:21,31`
- TACZ 源：`SoundPlayManager.java:106`、`SoundManager.java:92,96`

| 调用点 | 调用 | TACZ 完整签名 |
| --- | --- | --- |
| `ZtRefitScreen.java:1256` | `SoundPlayManager.playerRefitSound(candidate, player, SoundManager.INSTALL_SOUND)` | `public static void playerRefitSound(ItemStack attachmentItem, LocalPlayer player, String soundName)` |
| `ZtRefitScreen.java:1312` | `SoundPlayManager.playerRefitSound(installed, player, SoundManager.UNINSTALL_SOUND)` | 同上 |
| 常量 | `SoundManager.INSTALL_SOUND` / `UNINSTALL_SOUND` | `public static String = "install"` / `"uninstall"`（可变静态字段） |

#### 3.3.6 网络：`NetworkHandler` + 两条客户端消息

- import：`ZtRefitScreen.java:22,23,24`
- TACZ 源：`NetworkHandler.java:33`、`ClientMessageRefitGun.java:21`、`ClientMessageUnloadAttachment.java:19`

| 调用点 | 调用 | TACZ 完整签名 |
| --- | --- | --- |
| `ZtRefitScreen.java:1257-1258` | `NetworkHandler.CHANNEL.sendToServer(new ClientMessageRefitGun(inventorySlot, player.getInventory().selected, type))` | `public static final SimpleChannel CHANNEL`；`public ClientMessageRefitGun(int attachmentSlotIndex, int gunSlotIndex, AttachmentType attachmentType)` |
| `ZtRefitScreen.java:1316-1317` | `NetworkHandler.CHANNEL.sendToServer(new ClientMessageUnloadAttachment(player.getInventory().selected, type))` | `public ClientMessageUnloadAttachment(int gunSlotIndex, AttachmentType attachmentType)` |

> 附属还依赖两条消息**服务端的语义**（`ClientMessageRefitGun.java:64`、`ClientMessageUnloadAttachment.java:53` 会调 `AttachmentPropertyManager.postChangeEvent`），注释见 `ZtRefitScreen.java:1262,1305`。

---

## 4. 两处 mixin 目标明细

混合配置：`src/main/resources/z_tweaks.mixins.json:9-10` 注册两个 mixin 类。两处均 `require = 0`。

### 4.1 `FirstPersonRenderGunEventMixin` → `FirstPersonRenderGunEvent.applyFirstPersonPositioningTransform`

| 项 | 值 |
| --- | --- |
| 附属文件 | `src/main/java/com/ztweaks/mixin/FirstPersonRenderGunEventMixin.java:29-50` |
| TACZ 目标 | `TACZ/.../client/event/FirstPersonRenderGunEvent.java:140` |
| 目标签名 | `private static void applyFirstPersonPositioningTransform(PoseStack poseStack, BedrockGunModel model, ItemStack stack, float aimingProgress, float refitScreenOpeningProgress)` |
| 注入方式 | `@Inject(method = "applyFirstPersonPositioningTransform", at = @At("RETURN"), remap = false, require = 0)`（`:32`） |
| 处理器签名 | `private static void ztweaks$applyOrbitCamera(PoseStack, BedrockGunModel, ItemStack, float, float, CallbackInfo)`（`:33-35`） |
| 依赖的额外内部符号 | `BedrockGunModel.getRootNode()`（`BedrockGunModel.java:497`）、`BedrockPart.x` / `.y` 公有字段（`BedrockPart.java:23-24`） |
| 降级行为 | 注入失败 → 相机固定原生视角；界面其余功能不受影响；靠诊断 HUD「mixin hits」读数识别（`:25-27` 注释） |

> 注意：TACZ 里**同名方法有两处** —— `FirstPersonRenderGunEvent.java:140`（本 mixin 目标，`BedrockGunModel`）与 `AnimateGeoItemRenderer.java:301`（`BedrockAnimatedModel`，非目标）。因 `@Mixin` 锁定类，无歧义，但排障时易混。
> 目标方法是 **`private static`**：这是全项目最脆弱的一处（见 §6）。

### 4.2 `GunItemRendererWrapperMixin` → `GunItemRendererWrapper.renderFirstPerson`

| 项 | 值 |
| --- | --- |
| 附属文件 | `src/main/java/com/ztweaks/mixin/GunItemRendererWrapperMixin.java:31-38` |
| TACZ 目标 | `TACZ/.../client/renderer/item/GunItemRendererWrapper.java:163-164` |
| 目标签名 | `public void renderFirstPerson(LocalPlayer player, ItemStack stack, ItemDisplayContext ctx, PoseStack poseStack, MultiBufferSource bufferSource, int light, float partialTick)` |
| 注入方式 | `@ModifyVariable(method = "renderFirstPerson", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)`（`:34`），按处理器形参类型 `ItemStack` 匹配（该方法仅一个 `ItemStack` 形参，无歧义） |
| 处理器签名 | `private ItemStack ztweaks$virtualAssembly(ItemStack stack)`（`:35`） |
| 降级行为 | 注入失败 → 枪照常渲染成真枪，只是没有虚拟装配预览；界面其余功能不受影响；失效可用「虚拟装配：命中」读数为 0 识别（`:28-29` 注释） |

> 目标方法在父类 `AnimateGeoItemRenderer.java:214` 有**完全相同的签名**（`renderFirstPerson`）。mixin 锁 `GunItemRendererWrapper` 自身的方法，但若 TACZ 把该方法上收到父类 / 改名，`@ModifyVariable` 会静默失效。

---

## 5. 票面点名但代码中**不存在**的项

以下 4 个符号在**本分支 + 主工作树** `tacz-z-tweaks/src` 全量搜索均为 **0 命中**，属票面预设项（可能源自早期计划或其它 ticket）：

| 票面项 | 实际引用情况 |
| --- | --- |
| `CommonAssetsManager` | 无引用（仅 TACZ 本体内部 `CommonAssetsManager.java` 存在） |
| `CommonNetworkCache` | 无引用 |
| `ClientMessagePlayerZoom` | 无引用（仅 TACZ 本体 `network/message/ClientMessagePlayerZoom.java` 存在） |
| `ClientGunIndex` | 无引用（附属改用 API 的 `TimelessAPI.getCommonGunIndex`） |

> 另有 `ClientMessageLaserColor` 仅出现在**注释**（`ZtRefitScreen.java:290`），不是代码依赖 —— 附属靠继承 `GunRefitScreen.onClose()` 间接触发它。

---

## 6. 脆弱度分级

判据：**TACZ 升级一个小版本时最可能先坏的**。权重 = 可见性（private/内部 vs public）+ 签名复杂度（参数个数）+ 是否历史已变（1.1.8）+ 调用点数量。

| 依赖项 | 类/方法 | 脆弱度 | 判据 |
| --- | --- | --- | --- |
| mixin 1 目标 | `FirstPersonRenderGunEvent.applyFirstPersonPositioningTransform` | **高** | `private static`、5 参数、被 `@Inject` 精确匹配、`require = 0` 静默失效；1.1.8 已变动 |
| 父类继承 | `GunRefitScreen`（init/onClose/render 契约） | **高** | 继承内部 GUI 类并覆盖生命周期；`init` 不调 super 属「替换」；`onClose` 副作用上传镭射色 |
| `RefitTransform` | `getCurrentTransformType` 等 4 个静态方法 | **高** | 18 处调用、内部静态状态机、无 API 承诺 |
| `IAttachmentModifier`/`DiagramsData` | `getPropertyDiagramsData` + 8 分量 record 访问器 | **高** | record 分量名/顺序即契约，且附属解析 `positivelyString` 文本格式 |
| `AttachmentPropertyManager` | `getModifiers()` 返回内部可变注册表 | **高** | 返回 `Map<String, IAttachmentModifier<?,?>>`，暴露内部注册表 |
| mixin 2 目标 | `GunItemRendererWrapper.renderFirstPerson` | **中** | 7 参数、`@ModifyVariable` 按类型匹配、父类有同名方法 |
| `HSVSliderGroup` | 7 参数构造 + 2 getter | **中** | 参数多且顺序敏感 |
| `AttachmentCacheProperty` | `new` + `eval(ItemStack,GunData)` | **中** | public 但属内部类；`getCache(String)` 泛型 |
| `AttachmentDataUtils` | 8 个 static 方法（用 5） | **中** | 签名简单稳定，但内部依赖 `eval` 与 `SyncConfig`，会连带坏 |
| 网络 | `NetworkHandler.CHANNEL` + 2 消息构造 | **中** | 包体构造签名与 `SimpleChannel` 字段；服务端语义也变 |
| `CommonGunIndex`/`CommonAttachmentIndex` | getter + `getPojo()` | **中** | `getPojo()` 暴露序列化 POJO 类型 |
| `ClientAttachmentIndex`/`GunDisplayInstance`/`LaserConfig` | `getLaserConfig()` / `canEdit()` | **中** | 客户端索引内部结构 |
| `GunData`/`AttachmentData`/`ExplosionData` | POJO getter | **低-中** | 数据对象方法名较稳定，但字段重命名无编译期保护（反射/序列化） |
| `GunPropertyDiagrams` | `draw(GuiGraphics,Font,int,int)` | **低-中** | 静态工具，签名简单 |
| `BedrockGunModel.getRootNode()`/`BedrockPart.x,.y` | 渲染数据结构 | **低-中** | 公有字段/方法，结构稳定 |
| `SyncConfig` | 2 个 `DoubleValue` 常量 | **低** | 配置常量，改名才坏 |
| `SoundPlayManager`/`SoundManager` | `playerRefitSound` + 2 字符串常量 | **低** | 参数简单；常量为可变静态字段 |
| `IGunOperator` | `fromLivingEntity` / `getCacheProperty()` | **低** | 已是公开 API |

---

## 7. `compat/` 适配层收口清单

> 面向 ticket #13 的输入。分「必须收」与「收口意义不大」。

### 7.1 必须收口（收口后单点替换，TACZ 变了只改一个文件）

| # | 收口对象 | 现有散落位置 | 建议接口 |
| --- | --- | --- | --- |
| 1 | 两处 **mixin 目标**（`FirstPersonRenderGunEvent.applyFirstPersonPositioningTransform`、`GunItemRendererWrapper.renderFirstPerson`） | 两个 mixin 类 | 无法抽象掉；**隔离 + 版本断言 + 失效上报**（把 `require = 0` 的静默降级变成显式告警） |
| 2 | `AttachmentDataUtils` 5 个求值方法 | `ZtRefitScreen.java:1459,1463,1472,1476,1480` | `compat.GunStatReader`（胜在把传递依赖 `eval`/`SyncConfig` 一起隔离） |
| 3 | `AttachmentPropertyManager.getModifiers()` + `IAttachmentModifier.getPropertyDiagramsData` + `DiagramsData` 访问器 | `ZtRefitScreen.java:515-534,596-601` | `compat.DiagramsProvider`（含 `positivelyString` 文本解析） |
| 4 | `AttachmentCacheProperty`（构造 + `eval` + `getCacheProperty`） | `ZtRefitScreen.java:508-513,589` | `compat.CachePropertyFactory` |
| 5 | `RefitTransform` 4 个静态方法 | `ZtRefitScreen.java` 18 处 | `compat.RefitViewState` |
| 6 | `GunRefitScreen` 父类生命周期（构造/init/onClose/render/事件） | `ZtRefitScreen.java` 全类 | **最难**：`compat.AbstractRefitScreen` 或改为普通 `Screen` 自管生命周期 + 补齐镭射色上传 |
| 7 | `NetworkHandler.CHANNEL` + 2 条消息构造 | `ZtRefitScreen.java:1257-1258,1316-1317` | `compat.RefitNetwork` |
| 8 | `HSVSliderGroup` 构造 + getter | `ZtRefitScreen.java:325-327` | `compat.LaserSliderFactory` |
| 9 | `GunPropertyDiagrams.draw` | `ZtRefitScreen.java:631` | `compat.DiagramOverlay` |
| 10 | 客户端索引读取（`ClientAttachmentIndex`/`GunDisplayInstance`/`LaserConfig`/`CommonGunIndex`/`CommonAttachmentIndex` 的 POJO getter） | `ZtRefitScreen.java:302-320,371-379,463-465,1374-1376,1421-1425,1455,1607` | `compat.TaczIndexAccess` |
| 11 | `SyncConfig` 常量 | `ZtRefitScreen.java:1465,1479` | `compat.TaczConfig` |

### 7.2 收口意义不大

| 对象 | 理由 |
| --- | --- |
| `IGunOperator` | 已是 `com.tacz.guns.api.entity` 公开 API，无需包一层 |
| `BedrockGunModel.getRootNode()` / `BedrockPart.x,.y` | 只在 mixin 处理器内部用；包一层反而把 mixin 逻辑拆散 |
| `GunData`/`AttachmentData`/`ExplosionData` 的个别 getter | 数据对象，属 §7.1#10 的一部分即可；单独再包一层收益低 |
| `SoundPlayManager` / `SoundManager` | 参数极简，坏了是编译期错误而非运行期静默 |

### 7.3 收口无法解决的问题

- **静默降级**：两处 mixin `require = 0` 的失效是运行期的，`compat/` 不能自动发现；需配「启动自检 / 诊断读数」（归 ticket #11「TACZ 版本下限与兼容策略」）。
- **文本格式隐式契约**：`positivelyString` 增量解析（`ZtRefitScreen.java:537-561`）靠正则，签名不变也会因文案改动而退化 —— 收口只能集中这个风险，不能消除。

---

## 8. 局限与后续

- 本盘点基于 **1.1.8-hotfix** 分支当前代码；行号可能随改动漂移，符号名与签名稳定。
- 未做「与旧版本（1.1.4～1.1.7）对照」的差异考古 —— 若 ticket #11 需要「历史上已变过的签名」清单，需另开一轮。
- `mods.toml` 声明依赖范围与实测版本的差异属 ticket #11 范围，本文件不展开。
