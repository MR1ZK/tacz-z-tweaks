# TACZ `tacz_tags/attachments` 现状盘点

- 调研对象：TACZ 本体（`c:/Users/YTH/Documents/myprojects/TACZ`），分支 `1.20.1`，`build.gradle:33` 版本 `1.1.8-hotfix`
- 关联 issue：[#2](https://github.com/MR1ZK/tacz-z-tweaks/issues/2)
- 结论速览：**这套 tag 是"枪↔配件兼容性白名单"机制，不是配件分类体系，不能当文件夹分组语义用**（详见 §7）
- 路径约定：本文中 `TACZ/xxx:12` 表示 TACZ 仓库根下的文件第 12 行

---

## 1. 文件结构与语义

- 目录（唯一一处，本体仓库只带一个枪包）：
  `TACZ/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/tacz_tags/attachments/`
- 该目录下共 **103 个 json**，分三层：

  | 位置 | 数量 | 语义 |
  | --- | --- | --- |
  | `attachments/*.json` | 51 | 真正的 tag（配件 id 的有名集合） |
  | `attachments/allow_attachments/*.json` | 51 | **不是 tag**，是"某把枪的兼容白名单"，按枪名一文件 |
  | `attachments/intrinsic/slug.json` | 1 | 内部特殊 tag（独头弹） |
  | 合计 | **103** | 与票面数字吻合 |

- 每个文件的格式是**纯 JSON 字符串数组**，元素为两种东西：`"<配件id>"` 或 `"#<tagid>"`（tag 引用）。没有对象、没有 metadata。

  完整样例，`TACZ/src/main/resources/assets/tacz/custom/tacz_default_gun/data/tacz/tacz_tags/attachments/scope.json`：

  ```json
  [
    "tacz:scope_elcan_4x",
    "tacz:scope_standard_8x",
    "tacz:scope_hamr",
    "tacz:sight_552",
    "tacz:sight_exp3",
    "tacz:sight_uh1",
    "tacz:sight_t2",
    "tacz:scope_lpvo_1_6",
    "tacz:scope_acog_ta31",
    "tacz:sight_coyote",
    "tacz:sight_acro_rifle",
    "tacz:sight_deltapoint_rifle",
    "tacz:sight_fastfire_rifle",
    "tacz:sight_pk06_rifle",
    "tacz:scope_vudu",
    "tacz:scope_mk5hd",
    "tacz:sight_srs_02",
    "tacz:scope_qmk152",
    "tacz:sight_okp7"
  ]
  ```

  另一侧的枪白名单样例，`.../tacz_tags/attachments/allow_attachments/m4a1.json`：

  ```json
  [
    "#tacz:scope_scope", "#tacz:scope_sight", "#tacz:muzzle", "#tacz:extended_mag",
    "#tacz:grip", "#tacz:bayonet_ar", "#tacz:stock", "#tacz:ammo_mod_no_he",
    "#tacz:ar_laser", "#tacz:pistol_laser"
  ]
  ```

- 解析链路（服务端）：`AttachmentsTagManager.java:39` 用 `FileToIdConverter.json("tacz_tags/attachments")` 作为目录 → `:44-46` 扫描 → `:86-88` 反序列化成 `List<String>` → `:73-78` **按 path 前缀分流**：路径以 `allow_attachments/` 开头且长度 > 18 的，键取”去掉前缀后的枪 id”存进 `allow_attachments`，其余存进 `tags`。
- `#` 引用是**递归展开**的：`AllowAttachmentTagMatcher.java:13` 定义 `TAG_PREFIX = "#"`，`:73-93` `treeSearch` 遇到 `#xxx` 就去 `getAttachmentTags(xxx)` 继续递归。**没有环检测、没有深度上限**。
- **一个配件可以同时属于多个 tag**（已验证）：
  - `tacz:scope_elcan_4x` 出现在 3 个 tag 文件里
  - `tacz:muzzle_silencer_ursus`、`tacz:grip_vertical_talon`、`tacz:muzzle_brake_cyclone_d2` 各出现在 2 个
  - 例：`grip_afg.json` 含 `tacz:grip_cobra`、`tacz:grip_se_5`，这两个同时也在 `grip.json` 里
- 反之，**大量配件不在任何 tag 里**（见 §7 覆盖度）。

## 2. tag id 命名规律（无层级，只有下划线分词）

- id 就是 `命名空间:相对路径去掉 .json`，即 `tacz:scope`、`tacz:allow_attachments/m4a1`、`tacz:intrinsic/slug`。**只有 `intrinsic/` 和 `allow_attachments/` 两个路径前缀存在**；除它们之外，全部 51 个 tag 都是**平铺**的，没有目录层级。
- 命名分四类模式：
  1. **功能大类**（名字与 `AttachmentType` 枚举一一对齐）：`scope`、`muzzle`、`stock`、`grip`、`extended_mag`、`ammo_mod`，以及激光类用 `ar_laser` / `pistol_laser` 表达。对应 `AttachmentType.java:5-40`（SCOPE/MUZZLE/STOCK/GRIP/LASER/EXTENDED_MAG/NONE）。
  2. **子类**：`<大类>_<细分>`
     - 枪口：`muzzle_brake`、`muzzle_compensator`、`muzzle_silencer`、`muzzle_shotgun`、`pistol_muzzle`、`pistol_brake`、`pistol_compensator`、`pistol_silencer`、`amr_muzzle_silencer`
     - 瞄具：`scope_scope`、`scope_sight`、`scope_sniper`、`scope_lowsight`、`pistol_sight`、`deagle_sight`
     - 握把：`grip_afg`、`grip_vertical`、`pistol_grip`
     - 弹匣：`light_extended_mag`、`shotgun_extended_mag`、`sniper_extended_mag`
     - 弹药：`ammo_mod_no_he`、`ammo_mod_slug`、`ammo_mod_shotgun_exclusive`
     - 刺刀：`bayonet_ak`、`bayonet_ar`、`bayonet_other`
  3. **枪械专属白名单**（命名空间相同，语义完全不同）：`aug_scope`、`kar98_scope`、`m16a1_scope`、`okp_scope`、`p90_scope`、`rhino357_sight`、`deagle_sight`、`deagle_exclusive`、`timeless50_exclusive`、`m4a1_stock`、`hk416d_stock`、`db_short_stock`、`sks_tactical_stock`、`spas_12_stock`、`mk23_laser`
  4. **两个路径前缀特例**：`allow_attachments/<gun>`（枪白名单，不参与 tag 匹配）、`intrinsic/slug`（唯一带 `/` 的 tag id，内部机制）
- 结论：**`_` 分割只是一部分约定，且第 1、2、3 类混在同一命名空间、无机械可解析的规则**（例如 `deagle_sight` 是枪名+部件，`scope_sight` 是类+子类，字符串层面无法区分）。
- 全部 51 个直挂 tag 名（按字母序）：`ammo_mod, ammo_mod_no_he, ammo_mod_shotgun_exclusive, ammo_mod_slug, amr_muzzle_silencer, ar_laser, aug_scope, bayonet_ak, bayonet_ar, bayonet_other, db_short_stock, deagle_exclusive, deagle_sight, extended_mag, grip, grip_afg, grip_vertical, hk416d_stock, kar98_scope, light_extended_mag, m16a1_scope, m4a1_stock, mk23_laser, muzzle, muzzle_brake, muzzle_compensator, muzzle_shotgun, muzzle_silencer, oem_stock, okp_scope, p90_scope, pistol_brake, pistol_compensator, pistol_grip, pistol_laser, pistol_muzzle, pistol_sight, pistol_silencer, rhino357_sight, scope, scope_lowsight, scope_scope, scope_sight, scope_sniper, scope_springfield, shotgun_extended_mag, sks_tactical_stock, sniper_extended_mag, spas_12_stock, stock, timeless50_exclusive`

## 3. TACZ 自己拿 tag 干什么

存在**三条相互独立**的"能不能装"判定，别混淆：

1. **细粒度白名单**（就是本目录的 `allow_attachments/`）
   - 数据入口：`AttachmentsTagManager.getAllowAttachmentTags(gunId)` `AttachmentsTagManager.java:104-106`
   - 匹配：`AllowAttachmentTagMatcher.match(gunId, attachmentId)` `AllowAttachmentTagMatcher.java:25-42`，内部 `treeSearch` 递归展开 `#` 引用
   - 语义：`allow_attachment_tags` 为空 → **一律返回 false**，源码注释写明"如果枪械对应的 allowAttachmentTags 为空，说明目前没有任何可以装的配件" `AllowAttachmentTagMatcher.java:34-37`
   - 对外 API：`AbstractGunItem.allowAttachment(gun, attachmentItem)` `AbstractGunItem.java:284-293`
   - 调用点（全仓库只有 3 处）：
     - 改装界面过滤玩家背包里的配件 `client/gui/GunRefitScreen.java:146-149`
     - 枪械工作台的合成槽校验 `client/gui/GunSmithTableScreen.java:206-213`
     - JEI 配件-枪查询 `compat/jei/entry/AttachmentQueryEntry.java:75`
2. **普通 tag 的匹配**（`getAttachmentTags`）
   - `AllowAttachmentTagMatcher.matchTag(tag, attachmentId)` `:54-71`
   - 目前 TACZ **内部只有一个用途**：独头弹。`item/ModernKineticGunItem.java:295` 定义 `SLUGS = tacz:intrinsic/slug`，`:301` 判断已装弹匣是否为独头弹从而把 `BULLET_AMOUNT` 改成 1；客户端 tooltip 同样判断以显示单发伤害 `client/tooltip/ClientGunTooltip.java:179-180`
   - Javadoc 明说这是留给外部的钩子：*"目前内部用于独头弹特殊标签的判断，也能方便到外部（附属，整合包等）制作它们的特殊标签"*，`@since 1.1.7` `AllowAttachmentTagMatcher.java:44-53`。→ **这是官方承认的公开用法**。
3. **粗粒度槽位白名单**（与 tag 无关，但决定 UI）
   - `GunData.allow_attachment_types` `resource/pojo/data/gun/GunData.java:105-106`（默认包 51 个枪 data 全部声明，例：`.../data/guns/m4a1_data.json:167-174` 列了 scope/stock/laser/grip/muzzle/extended_mag）
   - `AbstractGunItem.allowAttachmentType(gun, type)` `AbstractGunItem.java:295-311`
   - 用途：决定改装界面**是否出现/可点这个槽位** `client/gui/components/refit/GunAttachmentSlot.java:99-106`、`GunRefitScreen.java:215-221`

缓存：`AllowAttachmentTagMatcher` 自带两级 `ConcurrentHashMap` 缓存，资源重载后统一清空 `AllowAttachmentTagMatcher.java:16-23, 96-99`；重载钩子在 `CommonAssetsManager.java:103-107`。

## 4. 对玩家是否可见

**不可见，纯内部机制。**证据：

- 语言文件里没有任何 tag 相关键：对 `TACZ/src/main/resources/assets/tacz/lang/*.json` 搜 `tacz_tags` / `attachment_tag` 无命中。
- GUI 上所有显示都用 `AttachmentType`，不用 tag：槽位 tooltip key 由枚举名拼出 `String.format("tooltip.tacz.attachment.%s", type.name().toLowerCase(...))` `GunAttachmentSlot.java:33,51`。
- 改装界面按 `AttachmentType` 出槽位、并按当前选中的 type 过滤背包 `GunRefitScreen.java:146`（`attachment.getType(item) == RefitTransform.getCurrentTransformType()`）。
- `getAttachmentTags(...)` 的消费者全仓库仅 2 处（`ModernKineticGunItem.java:301`、`ClientGunTooltip.java:179`），都是独头弹判断。**没有任何地方按 tag 分组、排序或展示**。
- tag 也没有中文名/图标/排序字段——文件里只有 id 字符串。

## 5. 第三方枪包会不会写自己的 tag

**会，而且是惯例。** TACZ 本体仓库里只有 `tacz_default_gun` 一个枪包目录（`TACZ/src/main/resources/assets/tacz/custom/` 下仅此一项）。但真实实例目录里能看到 5 个第三方包（全部在 `tacz-z-tweaks/run/client/tacz/` 下），每个都建了 `tacz_tags`：

- `ARIPS_ver.1.3.0`：`data/apdf/tacz_tags/attachments/allow_attachments/*.json`（6 个）+ `data/tacz/tacz_tags/attachments/*.json`（44 个 tag、5 个 allow）
- `bluearchive1.5.0`：`data/bluearchive/tacz_tags/attachments/{scope,muzzle,grip,laser}.json` + `allow_attachments/` 28 个；同时**也**向 tacz 命名空间写 `data/tacz/tacz_tags/attachments/{scope,muzzle,grip,laser}.json`
- `Delta Force-Storm Assault-v2.5`：`data/tacz/.../{scope,scope_scope,scope_sight,grip,grip_afg,muzzle_silence}.json` + `data/wemql_r/tacz_tags/attachments/allow_attachments/*`（13 个）
- `MCS2_Gunpack_v1.0.4_AWP_tacz1.1.4_hotfix3`：只有 `data/mcs2/tacz_tags/attachments/allow_attachments/*`（22 个），自己没写普通 tag
- `[eof]helldivers_gun_pack`：`data/tacz/tacz_tags` 与 `data/zeta/tacz_tags` 目录存在，但目录下**没有发现 json 文件**

关键观察（对下游影响最大的两点）：

- **第三方包会往 `tacz` 命名空间注入内容**，复用默认包的 tag id。也就是说 `tacz:scope` 的最终成员 = 所有已安装枪包里同名文件内容的**并集**，随玩家装了什么包而变。
- **白名单是"跨命名空间拼装"的**。`bluearchive` 的 `allow_attachments/mika.json` 同时引用 `#tacz:scope #tacz:muzzle #tacz:grip #tacz:laser #tacz:extended_mag #tacz:ammo_mod_no_he` 和 `#bluearchive:scope #bluearchive:muzzle #bluearchive:grip #bluearchive:laser` —— 即"默认包公共配件 + 本包专属配件"两段拼起来。这就是 `#` 递归引用的设计目的。

## 6. 客户端能否拿到 tag

**能，但只能按 id 点查，不能枚举。**

- 统一入口 `CommonAssetsManager.get()` `resource/CommonAssetsManager.java:234-236`：单人/服务端返回 `CommonAssetsManager`，**多人客户端返回 `CommonNetworkCache.INSTANCE`**（服务端实例为 null 时）。接口声明在 `ICommonResourceProvider.java:45-47`：`getAttachmentTags(id)`、`getAllowAttachmentTags(id)`。
- 客户端实现（枚举单例，直接从客户端内存读）：`resource/network/CommonNetworkCache.java`
  - 字段 `attachmentTags` / `allowAttachmentTags` `:42-43`；getter `:116-123`
  - `resolveAttachmentTags(...)` `:189-199` 与 `AttachmentsTagManager.apply` `:73-78` 是**同一套分流逻辑**（path 前缀 `allow_attachments/` 且长度 > 18）
- 同步链路：
  1. 服务端资源重载 → `AttachmentsTagManager.getNetworkCache()` `:90-93` 把每个文件的**原始 JSON 文本**收进 `DataType.ATTACHMENT_TAGS` `:96-98`
  2. `CommonAssetsManager.getNetworkCache()` `:115-121` 汇总所有 listener
  3. `OnDatapackSync` 全量打包发送 `CommonAssetsManager.java:272-281` → `network/message/ServerMessageSyncGunPack.java`
  4. 客户端 `doSync` `ServerMessageSyncGunPack.java:52-59` → `CommonNetworkCache.INSTANCE.fromNetwork` → `:211 case ATTACHMENT_TAGS -> resolveAttachmentTags(data)`，随后 `ClientIndexManager.reload()`
  5. 登录时清空客户端缓存 `client/event/CommonNetworkCacheEvent.java:15-22`
- **踩坑点**：
  - `DataType.ALLOW_ATTACHMENT_TAGS` `resource/network/DataType.java:15` 这个枚举值**从未被处理**，`CommonNetworkCache.fromNetwork` 的 switch `:205-215` 里没有它。两种语义共用一个 `ATTACHMENT_TAGS` 包，靠 path 前缀区分。
  - `getAllowAttachmentTags()` 的 key 是**枪 id**（`tacz:m4a1`），不是 tag id；`getAttachmentTags()` 的 key 才是 tag id（`tacz:scope`）。
  - **没有任何"列出全部 tag"的 API**：`ICommonResourceProvider` 只有点查方法 `ICommonResourceProvider.java:45-47`，`CommonNetworkCache` 的 `attachmentTags` 字段虽是 public，但通过接口拿不到全量。想枚举只能（a）直接 cast 到 `CommonNetworkCache` 读 public 字段，或（b）自己遍历全部配件再反查，或（c）硬编码 id 列表。
  - `AllowAttachmentTagMatcher` 是 `public final` 且方法全 static，客户端可直接调（`ClientGunTooltip` 就是客户端调用方）。
- 同步时机：**datapack sync 时全量下发**，不是增量；内容取决于服务端资源包集合，换整合包/换服会变。

## 7. 结论：能不能当"配件分类 / 文件夹分组"用

**不能。** 它是"枪↔配件兼容关系"的布尔判定数据，不是分类学。逐条理由：

1. **语义是"关系"不是"类别"**。tag 文件的成员是"为了给某把枪拼白名单而被抽出来的公共片段"，同一个 tag 会被不同枪以不同角度引用（`#tacz:scope_scope` 和 `#tacz:scope_sight` 就是 `tacz:scope` 的二次切分）。它回答"能不能装"，不回答"这属于哪一类"。
2. **覆盖不完整，按它分类会丢配件**。默认包共 99 个配件 index，其中 `type=scope` 的有 32 个，但 `tacz:scope` 只有 19 条 —— 典型的"部分白名单"。多数配件不在任何 tag 中。
3. **不互斥**。一个配件可同时在 3 个 tag 里（`tacz:scope_elcan_4x`），无法当文件夹/唯一分组键。
4. **命名不系统**。功能 tag、子类 tag、枪名专属 tag 混在同一命名空间，字符串层面不可区分（`deagle_sight` vs `scope_sight`）；无层级（只有 `intrinsic/`、`allow_attachments/` 两个特例前缀）。
5. **无 UI 语义**。没有中文名、图标、排序，TACZ 自己从不展示（§4）。
6. **不稳定**。内容随服务端资源包 / 玩家整合包而变（第三方包会往 `tacz` 命名空间注入），不能当持久化分类键。而且客户端**没有枚举全部 tag 的 API**（§6），连"遍历现有分类"都做不到。
7. TACZ 真正现成的"分类"是 `AttachmentType` 六个槽位（SCOPE/MUZZLE/STOCK/GRIP/LASER/EXTENDED_MAG，`AttachmentType.java:5-40`），每个配件 index 的 `type` 字段声明自己属于哪个（例 `.../index/attachments/ammo_mod_fmj.json` 的 `"type": "extended_mag"`），且改装界面就是按它分组/过滤的。

**能支撑到什么粒度**（如果非要用）：

- 可以当**"兼容关系索引"**用：
  - 正向：给定枪 id → `getAllowAttachmentTags(gunId)` 得到它可装的 id/`#tag` 集合（递归展开后即"这把枪的配件池"）→ 可做"推荐/可用配件列表"、"这把枪不能装什么"。
  - 反向：给定配件 id → 遍历已知 tag（需自行解决枚举问题）→ 反查"哪些枪能装它"，等价于扫描所有 `allow_attachments/*`，或直接用 JEI 已有的 `AttachmentQueryEntry` 思路。
  - 判定：等价于 `IGun.allowAttachment(gun, attachment)`，**客户端可用且已缓存**，适合做安装前的即时校验/高亮。
- 做不到：稳定的配件分类树、文件夹分组、互斥单选集、带显示名的分组。
- 若产品确实需要"分类/文件夹"，需另建元数据（自己的分类字段或映射表），而不是复用这套 tag。数据来源问题对应 issue [#3](https://github.com/MR1ZK/tacz-z-tweaks/issues/3)「v1.1 的配件元数据从哪来」。

---

## 附：本文用到的关键坐标速查

| 关注点 | 坐标 |
| --- | --- |
| tag 目录扫描 | `TACZ/src/main/java/com/tacz/guns/resource/manager/AttachmentsTagManager.java:39,44-46,73-84` |
| 解析成 List\<String\> | 同上 `:86-88` |
| 服务端 tag 查询 | 同上 `:100-106` |
| 匹配 / 递归展开 `#` | `com/tacz/guns/util/AllowAttachmentTagMatcher.java:13,25-42,54-93` |
| 枪-配件 API | `com/tacz/guns/api/item/gun/AbstractGunItem.java:284-293,295-311` |
| 槽位是否可用（UI） | `com/tacz/guns/client/gui/components/refit/GunAttachmentSlot.java:99-106` |
| 改装界面过滤 | `com/tacz/guns/client/gui/GunRefitScreen.java:142-149,215-221` |
| 独头弹内部用途 | `com/tacz/guns/item/ModernKineticGunItem.java:295,301`；`com/tacz/guns/client/tooltip/ClientGunTooltip.java:179-180` |
| 客户端缓存与入口 | `com/tacz/guns/resource/network/CommonNetworkCache.java:42-43,116-123,189-199,211`；`com/tacz/guns/resource/CommonAssetsManager.java:234-236` |
| 同步 | `CommonAssetsManager.java:272-281`；`com/tacz/guns/network/message/ServerMessageSyncGunPack.java:52-59` |
| 数据类型枚举 | `com/tacz/guns/resource/network/DataType.java:7-18` |
