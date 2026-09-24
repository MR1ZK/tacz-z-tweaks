# 客户端可读的枪械参数全集

> issue #8（wayfinder:research）。本文是「整枪对比面板的落点与形态」（issue #7）的输入。
>
> **结论 gist**：客户端能从枪械 NBT + 客户端资源索引里读到的东西很多，但**真正值得塞进"概览态参数卡"的只有 6～7 项**：开火模式、弹匣容量、射速(RPM)、弹速、穿透、重量（换算成移动速度）、开镜时间。TACZ 原生自己的属性条（`GunPropertyDiagrams`）就是按这个口径排的，直接对齐它最省事。后坐力是样条曲线不是标量、切枪/收枪时间是「手感参数」且无配件派生值，**不建议放进对比卡**；内置配件属于「拆解向」信息，放详情不放概览。

## 0. 口径与证据基线

路径前缀（下文简写）：

- `[T]` = `TACZ/src/main/java/com/tacz/guns/`
- `[Z]` = `tacz-z-tweaks/src/main/java/com/ztweaks/`

三条关键前提：

1. **`IGun` 是纯 NBT 访问层**，注释明写"这里不包含枪械的逻辑，只包含枪械的各种 nbt 访问"（`[T]api/item/IGun.java:20`）。因此它的 getter 客户端随时可调，读的就是本地物品栈的 NBT。实现分散在 `GunItemDataAccessor`（default 方法，NBT）与 `AbstractGunItem`（索引查询）。
2. **`GunData` / `BulletData` 是客户端资源索引里的静态数据**，公开入口 `TimelessAPI.getCommonGunIndex(id).getGunData()`；纯展示场景也可走 `TimelessAPI.getClientGunIndex(id)`（`ClientGunIndex` 同样带 `getGunData()`，见 `[T]client/event/CameraSetupEvent.java:182-188`）。字段读取 O(1)，可每帧调。参数的**读取入口全部是公开 API**（`TimelessAPI`），落在 `resource.pojo.data.*` 内部包里的只是 POJO 类型，详见研究文档 `docs/research/tacz-internal-api-surface.md`（分支 `research/tacz-internal-api`）。
3. **`AttachmentDataUtils` 类注释明写"不应该频繁调用，应尽可能调用实体缓存"**（`[T]util/AttachmentDataUtils.java:28`）。它的每个方法都要遍历全部 6 个配件槽 + 走 `AttachmentPropertyManager.eval`，**必须缓存**。
4. **TACZ 自己认定"该展示"的属性集合**，就是 `gui.tacz.gun_refit.property_diagrams.*` 这批 lang key（`TACZ/src/main/resources/assets/tacz/lang/zh_cn.json:127-152`）+ `GunPropertyDiagrams` 里手画的三项（开火模式 / 弹匣容量 / 跑射延迟）。这是最权威的"展示价值"参照。

## 1. 汇总表

单位/格式取自 TACZ 自己的成品格式化串（修饰器的 `positivelyString`），与它原生 UI 完全一致。

### A. `IGun` 直接读取（推荐主来源）

| 参数 | 出处 | 签名 | 单位/格式 | 已用? | 展示价值 | 成本 |
|---|---|---|---|---|---|---|
| 开火模式 | `[T]api/item/IGun.java:207` | `FireMode getFireMode(ItemStack gun)` | 枚举 `AUTO`/`SEMI`/`BURST`/`UNKNOWN`（`[T]api/item/gun/FireMode.java:5-26`） | 否 | **高**，原生属性条第一行 | NBT 读，每帧 OK |
| 弹匣容量（含配件） | `[T]util/AttachmentDataUtils.java:79` | `int getAmmoCountWithAttachment(ItemStack, GunData)` | 整数，「发」 | 否（概览卡未用；HUD/tooltip 用） | **高** | 遍历配件槽，**要缓存** |
| 当前弹量 | `[T]api/item/IGun.java:217` | `int getCurrentAmmoCount(ItemStack gun)` | 整数，「发」；闭膛待击要 +`hasBulletInBarrel` | 否 | 中（改装界面通常有 HUD） | NBT 读 |
| 射速 RPM | `[T]api/item/IGun.java:369` | `int getRPM(ItemStack gun)` | 整数，「rpm」；按开火模式调整 + 过热 lerp（实现 `[T]api/item/gun/AbstractGunItem.java:452-466`） | 否 | **高** | 索引查询 O(1) |
| 瞄准放大倍率 | `[T]api/item/IGun.java:79` | `float getAimingZoom(ItemStack gun)` | 倍数（如 4.0=4x），实现见 `[T]api/item/nbt/GunItemDataAccessor.java:328-342`，会读当前瞄具的 zoom 档位 | 否 | **高**（与"开镜时间"配对） | NBT + 客户端索引 |
| 内置配件 id | `[T]api/item/IGun.java:316` | `ResourceLocation getBuiltInAttachmentId(ItemStack gun, AttachmentType type)` | ResourceLocation；空为 `DefaultAssets.EMPTY_ATTACHMENT_ID` | 否 | 中（详情向，非概览） | 读 data map |
| 已装配件 id | `[T]api/item/IGun.java:324` | `ResourceLocation getAttachmentId(ItemStack gun, AttachmentType type)` | ResourceLocation | 间接（delta 系统内部） | 低（有专门配件面板） | NBT 读 |
| 虚拟备弹 | `[T]api/item/IGun.java:89` | `int getDummyAmmoAmount(ItemStack gun)` | 整数，仅在 `useDummyAmmo()` 为真时有效 | 否 | 低（机制态，非属性） | NBT 读 |
| 背包直读 | `[T]api/item/IGun.java:359` | `boolean useInventoryAmmo(ItemStack gun)` | 布尔 | 否 | 低（弹容显示成 "INV" 时需要） | NBT 读 |
| 过热值 | `[T]api/item/IGun.java:403` | `float getHeatAmount(ItemStack gun)` / `isOverheatLocked` | 0..1 与布尔 | 否 | 低（运行时态，非静态属性） | NBT 读 |

### B. `GunData` 字段（`[T]resource/pojo/data/gun/GunData.java`）

| 参数 | 行 | getter | 单位/格式 | 已用? | 展示价值 |
|---|---|---|---|---|---|
| 基础弹匣容量 | 137 | `getAmmoAmount()` | 整数 | 间接（被 `getAmmoCountWithAttachment` 用） | 中 |
| 扩容档位弹容 | 141 | `int[] getExtendedMagAmmoAmount()` | 整数数组 | 间接 | 低 |
| 射速（按模式） | 162 | `getRoundsPerMinute(FireMode)` | 整数 rpm | 否 | **高**（无过热时与 `IGun.getRPM` 同值） |
| 重量 | 219 | `getWeight()` | float，「kg」 | 间接（派生移动速度后展示） | **高** |
| 开镜时间 | 187 | `getAimTime()` | float，「s」（`%.2fs`） | 否 | **高** |
| 切枪时间（取出） | 179 | `getDrawTime()` | float 秒 | 否 | 中低（无配件派生值） |
| 收枪时间 | 183 | `getPutAwayTime()` | float 秒 | 否 | 低 |
| 跑射延迟 | 191 | `getSprintTime()` | float，「s」（`%.2fs`） | 否 | 中（原生属性条有） |
| 拉栓时间 | 195/199 | `getBoltActionTime()` / `getBoltFeedTime()` | float 秒 | 否 | 低（栓动枪专属） |
| 开火模式集合 | 211 | `List<FireMode> getFireModeSet()` | 枚举列表 | 间接 | 中（可显示"可切换的档位"） |
| 内置配件表 | 281 | `Map<AttachmentType, ResourceLocation> getBuiltInAttachments()` | 槽位→id | 否 | 中（详情向） |
| 允许配件类型 | 277 | `List<AttachmentType> getAllowAttachments()` | 枚举列表 | 否（有配件面板） | 低 |
| 移动速度系数 | 259 | `MoveSpeed getMoveSpeed()` → `getBase/Aim/ReloadMultiplier()` | float 系数（`[T]resource/pojo/data/gun/MoveSpeed.java:26-36`） | 否 | 中（和重量互补） |
| 扩散（按姿态） | 251 | `float getInaccuracy(InaccuracyType)` | float；`inaccuracy` 字段默认 **null**，会 NPE，需判空 | 间接（delta 用） | 中 |
| 后坐力 | 235 | `GunRecoil getRecoil()` | **样条关键帧数组**（pitch/yaw），非标量 | 间接（delta 用 multiplier） | 低（见 §2） |
| 换弹数据 | 207 | `GunReloadData getReloadData()` | 类型/是否无限/换弹时间 | 否 | 中低 |
| 栓动方式 | 153 | `Bolt getBolt()` | 枚举 `OPEN_BOLT`/`CLOSED_BOLT`/`MANUAL_ACTION` | 间接 | 低 |
| 近战数据 | 263 | `getMeleeData()` | 距离/冷却 | 否 | 低 |
| 爆炸数据 | — | 见 `BulletData.getExplosionData()` | — | **是**（附加伤害） | 高 |

### C. `BulletData` 字段（`[T]resource/pojo/data/gun/BulletData.java`）

| 参数 | 行 | getter | 单位/格式 | 已用? | 展示价值 |
|---|---|---|---|---|---|
| 伤害 | 54 | `getDamageAmount()` | float；裸值，**展示要走派生** | 间接 | 高 |
| 弹速 | 63 | `getSpeed()` | float，「m/s」（`%dm/s`），按开火模式 +`GunFireModeAdjustData.getSpeed()` | 否（裸值） | **高**；`AmmoSpeedModifier` 缓存值即实际生效弹速基数（`[T]item/ModernKineticGunScriptAPI.java:139-141`），显示值=玩法真值 |
| 穿透 | 79 | `getPierce()` | int，无单位（"n"） | 否（裸值） | **高** |
| 击退 | 71 | `getKnockback()` | float，无单位（`%.2f`） | 否（裸值） | 中 |
| 弹丸数 | 50 | `getBulletAmount()` | int | 间接（TACZ tooltip 按弹丸显示伤害） | 中 |
| 护甲穿透倍率 | 59 | `getExtraDamage().getArmorIgnore()` | float 0..1 → 展示成 % | 间接 | 高 |
| 爆头倍率 | 59 | `getExtraDamage().getHeadShotMultiplier()` | float → 展示成 % | 间接 | 高 |
| 优势射程 | 59 | `getExtraDamage().getDamageAdjust().get(0).getDistance()` | float，「m」（`%.1fm`） | 间接（delta 里 `effective_range`） | 中 |
| 生命期 | 46 | `getLifeSecond()` | float 秒 | 否 | 低（噪声） |
| 重力 | 67 | `getGravity()` | float | 否 | 低 |
| 摩擦 | 75 | `getFriction()` | float | 否 | 低（噪声） |
| 引燃 | 83 | `getIgnite()` | 布尔+时长 | 否 | 低（特例标记） |
| 爆炸 | 100 | `getExplosionData()` | 伤害/半径 | **是** | 高 |
| 曳光间隔 | 95 | `getTracerCountInterval()` | int | 否 | 低 |

### D. `AttachmentDataUtils` 派生整枪值（"装上配件后"，均需缓存）

| 参数 | 出处 | 签名 | 单位/格式 | 已用? |
|---|---|---|---|---|
| 伤害（含配件） | `[T]util/AttachmentDataUtils.java:179` | `double getDamageWithAttachment(ItemStack, GunData)` | float，`#.##` | **是** `[Z]client/ZtRefitScreen.java:1459` |
| 护甲穿透（含配件） | `:139` | `double getArmorIgnoreWithAttachment(...)` | 0..1 → `#.##%` | **是** `[Z]:1472` |
| 爆头倍率（含配件） | `:159` | `double getHeadshotMultiplier(...)` | → `#.##%` | **是** `[Z]:1475` |
| 重量（含配件） | `:91` | `double getWightWithAttachment(...)`（TACZ 自身拼写，勿改） | 「kg」；展示时乘 `WEIGHT_SPEED_MULTIPLIER` 成移动速度 | **是** `[Z]:1478` |
| 弹匣容量（含配件） | `:79` | `int getAmmoCountWithAttachment(...)` | 整数「发」 | 否（概览卡） |
| 扩容等级 | `:54` | `int getMagExtendLevel(...)` | 0..3 | 否 |
| 是否爆炸 | `:126` | `boolean isExplodeEnabled(...)` | 布尔 | **是** `[Z]:1463` |

### E. 修饰器派生值（`AttachmentCacheProperty` + `IAttachmentModifier`）

`AttachmentPropertyManager.getModifiers()` 是 `LinkedHashMap`，注册顺序即 TACZ 原生属性条顺序（`[T]resource/modifier/AttachmentPropertyManager.java:28,31-46`）：

| 顺序 | 参数 | 修饰器 | 成品格式（单位） |
|---|---|---|---|
| 1 | 开镜时间 | `AdsModifier` | `%.2fs`（`:72`），越小越好 |
| 2 | 弹速 | `AmmoSpeedModifier` | `%dm/s`（`:76`） |
| 3 | 穿甲倍率 | `ArmorIgnoreModifier` | `%.1f%%`（`:93`） |
| 4 | 伤害 | `DamageModifier` | `%.2f`（`:118`） |
| 5 | 优势射程 | `EffectiveRangeModifier` | `%.1fm`（`:85`） |
| 6 | 爆炸 | `ExplosionModifier` | 布尔 |
| 7 | 爆头倍率 | `HeadShotModifier` | `x%.1f`（`:93`） |
| 8 | 引燃 | `IgniteModifier` | 布尔 |
| 9 | 瞄准精度 | `AimInaccuracyModifier` | 返回空列表（`:55`），实际由 `InaccuracyModifier` 出 4 条：瞄准 `%.1f%%`（`:182`）、腰际/潜行/趴伏（`:132-134`） |
| 10 | 击退 | `KnockbackModifier` | `%.2f`（`:85`） |
| 11 | 穿透力 | `PierceModifier` | `%d`（`:60`） |
| 12 | 后坐力 | `RecoilModifier` | 垂直/水平两条（`:97,107`） |
| 13 | 射速 | `RpmModifier` | `%drpm`（`:68`） |
| 14 | 消音 | `SilenceModifier` | 布尔 |
| 15 | 重量 | `WeightModifier` | `%.2fkg`（`:71`） |
| 16 | 额外移速 | `ExtraMovementModifier` | 系数 |

数据来源：`AttachmentCacheProperty.eval()`（`[T]resource/modifier/AttachmentCacheProperty.java:26-54`）。这是「装上配件后整枪派生值」的**统一入口**，比逐项调 `AttachmentDataUtils` 更划算——一次 eval 拿到全部 16 项。

## 2. 逐条可读性确认（issue 点名的 10 个候选）

| 候选 | 读得到吗 | 单位 | 每帧能算吗 | 备注 |
|---|---|---|---|---|
| 开火模式 `getFireMode` | 是，NBT（`GunItemDataAccessor.java:183-189`，缺省返回 `UNKNOWN`） | 枚举 | 是 | 客户端读的是**物品实例当前模式**，正是玩家想看的 |
| 弹匣容量 | 是，`getAmmoCountWithAttachment` | 发 | 能但**别** | 遍历配件槽；闭膛待击要额外 +1（`GunPropertyDiagrams.java:92-96`） |
| 弹速 | 是，`BulletData.getSpeed`（裸值）/ `AmmoSpeedModifier` 缓存（含配件） | m/s | 裸值可；含配件需缓存 | 受开火模式 +配件双重影响 |
| 穿透 | 是，`BulletData.getPierce` / `PierceModifier` | 无单位 | 裸值可；含配件需缓存 | `PierceModifier.eval` 会 round 成 int（`:46`） |
| 击退 | 是，`BulletData.getKnockback` / `KnockbackModifier` | 无单位 | 同上 | 数值普遍很小（0..1） |
| 内置配件 | 是，`GunData.getBuiltInAttachments()` / `IGun.getBuiltInAttachmentId` | 槽位→id | 是 | 要再查名字得走 `TimelessAPI.getClientAttachmentIndex` |
| 重量 | 是，`GunData.getWeight()` / `getWightWithAttachment` | kg | 裸值可；含配件需缓存 | 概览卡实际展示的是**移动速度**，不是 kg |
| 后坐力 | **拿到的是样条**，不是标量（`GunRecoil.java:43-74`，`PolynomialSplineFunction`） | 无 | 否 | 只能展示"修饰器倍率"或"pitch/yaw 曲线图"，不适合一行文字 |
| 开镜时间 | 是，`GunData.getAimTime()` / `AdsModifier` | s | 是 | `AdsModifier` 的 `positivelyBetter=false`（越小越好） |
| 切枪时间 | 是，`GunData.getDrawTime()`（还有 `getPutAwayTime`） | s | 是 | **没有**对应 modifier，配件改不了，属纯静态手感值 |

## 3. 现有实现已经用掉的项

### 3.1 概览态参数卡 `buildGunInfo`（`[Z]client/ZtRefitScreen.java:1393-1483`）

三列 `infoMain / infoStats / infoExtra`，当前实际填入：

| 行 | 显示项 | 取数 |
|---|---|---|
| 1423 | 枪名 | `gun.getHoverName()` |
| 1425-1435 | 描述（≤3 行） | `index.getPojo().getTooltip()` |
| 1438-1439 | 弹药口径名 | `gunData.getAmmoId()` + `AmmoItemBuilder` |
| 1441-1453 | 经验等级 `level (xx.x%)` | `IGun.getLevel/getMaxLevel/getExpToNextLevel/getExpCurrentLevel` |
| 1454-1456 | 枪种 | `index.getType()` |
| 1458-1468 | 伤害（+爆炸） | `getDamageWithAttachment` / `getExplosionData` / `isExplodeEnabled` |
| 1472-1474 | 护甲穿透 | `getArmorIgnoreWithAttachment`（clamp 0..1） |
| 1475-1477 | 爆头伤害 | `getHeadshotMultiplier` |
| 1478-1481 | 移动速度 | `-WEIGHT_SPEED_MULTIPLIER * getWightWithAttachment` |

**`infoExtra` 列完全没填**，源码注释（`:1482`）明写"第三列：留给以后加的补充数据（开火模式、弹匣容量、内置配件……）"——**这就是本票要喂的落点**。注意方法名注释里写作 `getGunParamCard`，实际方法是 `buildGunInfo`（全仓库搜 `getGunParamCard` 为 0 命中）。

### 3.2 Delta 侧 `computeProsConsDelta`（`[Z]client/ZtRefitScreen.java:492-535`）

对"枪 + 候选配件"分别 `AttachmentCacheProperty.eval`，遍历 `AttachmentPropertyManager.getModifiers()` 的 `getPropertyDiagramsData` 取差值。**它已经间接消费了 E 表全部 16 项**，只是输出是 Pros/Cons 增量文本，不是绝对值卡片。

### 3.3 未使用的

`getFireMode` / `getCurrentAmmoCount` / `getRPM` / `getAmmoCountWithAttachment` 在 `[Z]` 全仓库 **0 命中**（`src/main/java`）。即"开火模式、弹匣容量、射速"完全没被现有实现碰过。

## 4. 推荐清单（概览态参数卡新增展示）

### 4.1 建议新增（按显示顺序，共 6 项）

顺序**照抄 TACZ 原生属性条**（`GunPropertyDiagrams.java:67-124` + `MODIFIERS` 注册顺序），玩家在两个界面看到的口径一致：

1. **开火模式** — `IGun.getFireMode`，枚举文案 `gui.tacz.gun_refit.property_diagrams.{auto|semi|burst|unknown}`。原生第一行，最该补。
2. **弹匣容量** — `AttachmentDataUtils.getAmmoCountWithAttachment`，`%d`。与现有"弹药口径名"同列相邻，语义连贯。
3. **射速** — `IGun.getRPM`，`%drpm`。
4. **弹速** — `BulletData.getSpeed`（裸值即可），`%dm/s`。
5. **穿透** — `BulletData.getPierce`，`%d`。
6. **开镜时间** — `GunData.getAimTime`，`%.2fs`。
7. **移动速度**（若算"新增"）— 现有卡片已用重量派生，保留即可。

> 若严格只加 5 项，砍 **开镜时间**（对不装镜的枪无意义）；若 7 项，第 7 项留给 **优势射程**（`%.1fm`，`EffectiveRangeModifier`）。

### 4.2 明确不该进概览卡

| 项 | 理由 |
|---|---|
| 后坐力（pitch/yaw） | 是样条曲线数组，没有单值；塞一行文字只会误导。要展示就放到 A/B 对比图里画曲线。 |
| 切枪/收枪时间 | 纯静态、配件改不了，且玩家体感差别小；属噪声。 |
| 虚拟备弹 / 过热值 / 背包直读 | 运行时机制态，不是"枪的参数"，概览卡显示会随战况跳变。 |
| 内置配件 | 属"这把枪结构如何"的拆解信息，放详情/悬停，不进概览。 |
| 扩散四件套（瞄准/腰际/潜行/趴伏） | 4 行占位过多，且与"优势射程"信息重叠；概览卡放不下。 |
| 生命期 / 重力 / 摩擦 / 曳光间隔 / 消音 / 引燃 | 纯实现细节或特例布尔，玩家看不到、也无从比较。 |
| 优势射程（若版面紧） | TACZ 原生有，但它和 damage 曲线强耦合，单值意义弱。 |

### 4.3 缓存要求（给下游实现者的硬约束）

`AttachmentDataUtils.*` 与 `AttachmentCacheProperty.eval` 都是"遍历全部配件槽"的重活（类注释 `[T]util/AttachmentDataUtils.java:26-30`）。**整枪对比面板一次要比 N 把枪**，必须：

- 以「枪 id + NBT」为 key 缓存整张参数卡（现有 `buildGunInfo` 的 `gunInfoId`/`gunInfoTag` 已是此模式，`[Z]:1404-1410`）；
- 需要含配件派生值的项，优先一次 `new AttachmentCacheProperty().eval(gun, gunData)` 拿全 16 项，而不是逐项调 `AttachmentDataUtils`；
- 纯 `GunData`/`BulletData` 字段（弹速、穿透、开镜时间、开火模式）不必进缓存，每帧直读。

**不要误用实体实时缓存**：`IGunOperator.getCacheProperty()`（`[T]api/entity/IGunOperator.java:174`）返回的 `AttachmentCacheProperty` 是 TACZ 为**本地玩家当前手持枪**维护的实时缓存，原生改装界面就是这么用的（`[T]client/gui/components/refit/GunPropertyDiagrams.java:43`），并在换枪时由 `ChangeGunPropertyEvent` 重算（`[T]event/ChangeGunPropertyEvent.java:20`）。它对"列表里比 N 把候选枪"**没有用**（绑定的是手里那把），所以对比面板仍应各自 `new AttachmentCacheProperty().eval(...)`。两者数值同源（同一套 modifier 求值），差异只在来源与刷新时机。

## 5. 给「整枪对比面板」（issue #7）的一句话建议

**概览参数卡的三列已经有 `infoExtra` 空着**，按 `开火模式 / 弹匣容量 / 射速 / 弹速 / 穿透 / 开镜时间` 的顺序填满即可，文案全部复用 `gui.tacz.gun_refit.property_diagrams.*` 与 `tooltip.tacz.gun.*`，数值格式复用各修饰器的成品串，保证与 TACZ 原生界面零口径差。

## 6. 交叉引用

- `docs/research/tacz-internal-api-surface.md`（分支 `research/tacz-internal-api`，issue #12）：TACZ 内部 API 与 mixin 目标盘点，含 `TimelessAPI` 入口、`AttachmentDataUtils` 8 个静态方法的统一签名、公开 API 与内部包的边界。本文所有"读得到"的判断都以那份盘点为前提。
- 本文文件若与上述文档冲突，以源码为准；两文都标了 `文件:行`。
