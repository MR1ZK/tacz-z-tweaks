# TACZ 附属模组开发计划 —— "Refit+"（暂定名）

> 目标：为 Forge 1.20.1 开发一个 TACZ（v1.1.8）附属模组，**接管 TACZ 自带的配件改装功能**，以 GUI 形式提供**更好的武器预览、更为简单的改装流程、更易阅读的配件简介和参数**，改装体验对标 ARC-9。
> 协议：**GPL-3.0**（与 TACZ 本体 LICENSE 一致，无传染性冲突）。

---

> ## ⚠️ 本文档已降级为历史资料
>
> 这是**开工前的规划稿**，不是当前方案。仓库最终叫 `tacz-z-tweaks`（不是本文里写的 `refit-plus`），模组 id 是 `z_tweaks`，实现走出了一条与本文不同的路：**纯客户端、零自定义数据、零网络通道**（[ADR-0001](adr/0001-client-only-no-custom-data.md)）。已被推翻的章节在下面逐处标注了。
>
> **功能清单的最新真相源是 [TACZ: Z-Tweaks 路线图](https://github.com/MR1ZK/tacz-z-tweaks/issues/1)** —— 一次 wayfinder 寻路留下的决策地图，每张票解决一个决策，已解决的记在地图的 Decisions so far 里。凡与本文冲突，以地图和各 ADR 为准。
>
> 原文件名 `TACZ-RefitPlus-开发计划.md`，原先躺在 `myprojects/` 工作区根、**不在任何仓库里**（这正是它开始腐烂的原因）。

---

## 1. 现状调研结论（决定架构的事实）

### 1.1 TACZ 侧（Java / Forge 1.20.1）

| 事实 | 出处 | 对附属的影响 |
|---|---|---|
| Mod ID `tacz`，版本 1.1.8-hotfix，Java 17，含 mixin + AT | `build.gradle` | 常规 Forge 附属依赖方式 |
| 配件类型为硬编码 7 值枚举 `SCOPE/MUZZLE/STOCK/GRIP/LASER/EXTENDED_MAG/NONE`，**每类型仅 1 槽** | `api/item/attachment/AttachmentType.java` | 不能通过注册表加类型；多槽/嵌套槽需 mixin，列为可选附录（附录 A） |
| 配件数据用 Gson 反序列化；modifier 键（如 `damage`、`ads`）在 `AttachmentPropertyManager.MODIFIERS` map 中，共 17 种内置属性 | `resource/manager/AttachmentDataManager.java` | **读取现有 17 种 modifier 即可自动生成参数展示**，主线无需注入新属性 |
| 属性求值：`value = addend * percent * multiplier` + Lua function | `resource/modifier/AttachmentPropertyManager.java` | 修改器数值 → Pros/Cons 文本的换算依据 |
| 属性重算触发 Forge 事件 `AttachmentPropertyEvent` | `api/event/` | 官方指定的附属 hook 点 |
| 改装界面 `GunRefitScreen` 是纯客户端 `Screen`（无 Container），Z 键经 `RefitKey` 打开；枪械特写视角由 `RefitTransform` 驱动（固定机位切换，**不支持自由旋转/缩放**） | `client/gui/`、`client/animation/screen/RefitTransform` | **UI 层可 100% 替换**；自由预览相机是主要增值点 |
| 安装/卸载走 `ClientMessageRefitGun` / `ClientMessageUnloadAttachment`，服务端校验配件锁与兼容性 | `network/` | 复用现有 packet 可保持服务端校验一致 |
| 兼容性 = 类型层（`allow_attachment_types`）+ 个体层标签树 `AllowAttachmentTagMatcher`（公开 API，注释明确支持附属） | `util/AllowAttachmentTagMatcher.java` | 客户端可预先判断并**标注不可安装原因** |
| 枪包数据（索引+数据）服务端 → 客户端整体同步，`DataType` 枚举不可扩展 | `resource/network/` | 附属自有数据需自建 SimpleChannel 同步 |
| 枪上配件存 NBT key `Attachment<TYPE>`（完整 ItemStack） | `api/item/nbt/GunItemDataAccessor.java` | 附属扩展数据须用独立 NBT 命名空间避免冲突 |
| 3D 渲染：`BedrockGunModel` 按类型槽位渲染配件，挂载靠模型节点约定；瞄具支持模板缓冲镜内画面 | `client/model/` | 预览渲染可复用；"hover 即挂载预览"具备可行性（纯客户端装配模型） |
| 公开 API 包 `com.tacz.guns.api`（TimelessAPI / IGun / IAttachment 等） | `api/` | 附属仅依赖 api 包 + 少量内部类（mixin 目标） |

### 1.2 ARC-9 侧（设计提炼，不搬代码）

1. **兼容判定 = 双向分类标签**：枪的槽声明接受的 Category 列表，配件声明所属 Category，取交集；UI 的"树状分类"由配件的 `Folder`（`a/b/c` 路径串）单独提供。
2. **属性表达**：`Add / Mult / Override(+优先级) / Hook` 四种操作；合并管线固定顺序：Override(比优先级) → 加法 → 乘法 → 钩子。
3. **易读性核心 = AutoStats 自动优缺点**：每个属性登记 `(单位换算函数, lowerIsBetter)`，扫描配件的修改器字段自动生成 **Pros / Cons 双栏**（如 `+15% 伤害`、`-10% 后坐力`），手写 `CustomPros/CustomCons` 仅作补充。
4. **UI 布局**：中央 3D 实时预览（**鼠标拖拽旋转 + 滚轮缩放**，第一人称即预览）+ 底部横条（槽位条 → 配件条 → 详情条三层展开）；顶部标签页：Customize（功能）/ Personalize（外观）/ Stats（整枪参数对比）/ Trivia（背景资料）。
5. **配件详情**：左侧名称+描述滚动区，右侧 Pros/Cons 双栏；配件按钮带角标（已安装/可安装/缺依赖置灰）。
6. **交互极简**：LMB 装、RMB 拆；点槽位即展开该槽配件；配件在列表中**悬停即预览装上枪的效果**，确认后才真正安装。
7. **预设系统**：仅存"槽位→配件 id+模式号"的最小树，支持导入导出码分享。
8. **排序**：`SortOrder` + 收藏加权 + 未拥有沉底。

---

## 2. 总体架构

```
tacz_refit_plus/                    （新仓库，GPL-3.0）
├── src/main/java/com/refitplus/
│   ├── RefitPlusMod.java           主类：注册 keybind、网络、事件
│   ├── api/                        附属自己的公开 API（供更下游附属/整合包用）
│   ├── data/
│   │   ├── ExtendedAttachmentMeta  附属扩展元数据（folder/pros/cons/trivia/单位覆盖）
│   │   ├── MetaLoader              服务端加载附属枪包目录中的 meta json
│   │   └── StatCatalog             属性目录（对应 ARC-9 AutoStatsMains：单位换算 + lowerIsBetter）
│   ├── compat/                     TACZ 兼容层：安装校验包装、NBT 独立命名空间
│   ├── network/                    自有 SimpleChannel：meta 同步、预设应用
│   └── client/
│       ├── gui/                    新改装 Screen（接管 GunRefitScreen）
│       │   ├── RefitScreen         主框架：3D 预览 + 底部三层横条 + 顶部标签页
│       │   ├── preview/            ★ 预览相机：自由旋转/缩放、槽位聚焦、悬停装配预览
│       │   ├── component/          槽位条 / 配件浏览器(文件夹+搜索) / 详情条 / ProsCons 面板
│       │   └── compare/            整枪参数对比面板（裸枪 vs 当前改装）
│       ├── render/                 预览渲染（复用 BedrockGunModel，含 hover 虚拟装配）
│       ├── autostats/              AutoStats 引擎：modifier → 优缺点文本
│       └── preset/                 预设保存/加载/导入导出码
│   └── mixin/                      最小 mixin 集（见 §5）
└── src/main/resources/
    ├── data/refit_plus/…           附属枪包结构（扩展 meta、兼容标签）
    └── assets/refit_plus/lang/     UI 翻译键
```

**分层原则**：
- **数据层**完全数据驱动（JSON），附属内容 = 附属自己的"扩展枪包"；
- **属性层只读**：读取 TACZ 现有 17 种 modifier 生成展示，不改动任何数值计算（新属性注入见附录 A，非主线）；
- **UI 层整体替换**（纯客户端），安装/卸载一律复用 TACZ 的 packet/服务端校验，**服务端逻辑零改动**。

---

## 3. 模块设计

### 3.1 扩展元数据（ExtendedAttachmentMeta）

> ⚠️ **整节不采纳（不只是"v1 不做"）。** 这里设想的"附属自有扩展元数据 + 服务端 `MetaLoader` + 网络同步"被 [ADR-0001](adr/0001-client-only-no-custom-data.md) 推翻（v1 不要自定义数据、不要网络通道、不要求服务端安装），而它描述的**字段本身后来也一并否掉了**：配件怎么分类、怎么介绍是**枪包作者的领域**，本模组不替作者定义内容，也不另立一套平行体系。所以 v1.1 也不做，这不是排期问题。
>
> 落地口径：分类只用 `AttachmentType`（六个槽位，TACZ 本来就这么分组），文案交作者（描述沿用 TACZ 原生 tooltip key）。决策与理由见 issue #3 的解决评论。
>
> **连带作废的下游提法**（本文其他地方还会出现，一律以此为准）：§3.3-4 的"文件夹浏览"、§3.3-5 的"记住上次浏览的文件夹"、§3.4 里的 `stat_overrides` 单位覆盖、§3.5 布局图里的文件夹按钮与"背景"标签页（Trivia）、§4 的 `ServerMessageSyncMeta`、以及架构图里的 `ExtendedAttachmentMeta` / `MetaLoader`。
>
> 另外，本节末尾那句"分类也可直接复用 TACZ 标签体系"**也已被证否**：`tacz_tags/attachments` 那 103 个 json 是兼容关系索引，命名无层级、不可枚举、覆盖不完整，当不了分类——见 `research/tacz-attachment-tags.md`。

为每个配件 id 提供附属侧的补充数据，与 TACZ 的 `AttachmentIndex` 运行时合并：

```jsonc
// data/refit_plus/meta/attachments/tacz_sight_552.json
{
  "attachment": "tacz:sight_552",
  "compact_name": "552",                  // 按钮短名
  "folder": "瞄具/红点/紧凑型",            // 浏览器文件夹层级（"/"分隔）
  "sort": 10,
  "description_extra": "refit_plus.att.sight_552.extra",   // 补充描述（长文）
  "trivia": "refit_plus.att.sight_552.trivia",             // 背景资料（Trivia 页）
  "custom_pros": ["refit_plus.pro.快速瞄准"],
  "custom_cons": ["refit_plus.con.镜框遮挡"],
  "stat_overrides": { "recoil": { "unit": "%", "lower_is_better": true } }
}
```

- 键为配件 `ResourceLocation`；未提供 meta 的配件自动降级为 TACZ 原生展示（无文件夹、无补充描述），保证兼容任意第三方枪包。
- 服务端由 `MetaLoader` 从附属枪包目录加载；登录/换包时经自有 channel `ServerMessageSyncMeta` 全量同步客户端（数据量小，直接 JSON 字符串 map）。

### 3.2 武器预览（GUI 核心能力一）

对 TACZ 原生"固定机位切换"的全面增强，对标 ARC-9 的预览体验：

1. **自由相机**：预览区内鼠标拖拽旋转（俯仰/偏航）、滚轮缩放；支持"回到默认视角"一键复位。相机状态按槽位记忆（切槽自动恢复该槽上次视角）。
2. **槽位聚焦与高亮**：点击底部槽位 → 相机平滑运镜到该配件挂载点（基于 `RefitTransform` 扩展插值），挂载点短暂高亮描边；空槽在 3D 预览中显示 `+` 标记（屏幕空间投影定位）。
3. **悬停装配预览（install-before-install）**：鼠标悬停配件列表项 → 预览中**即时虚拟装配**该配件模型（纯客户端内存 ItemStack，不落 NBT、不发 packet），移开即还原；这样玩家"先看到效果再决定安装"。对瞄具可额外提供镜内视角预览（复用 TACZ 模板缓冲渲染，可选）。
4. **参数即时 diff**：悬停预览的同时，详情条的 Pros/Cons 与整枪对比面板实时切换为"装上此配件后"的数值（同样纯前端离线计算）。
5. **动效**：安装/卸下瞬间配件模型缩放淡入淡出 + 对应安装音效（TACZ display json 已含 `sounds.install/uninstall`）。

技术要点：虚拟装配 = 克隆枪 ItemStack + `IGun.installAttachment`（内存副本）→ 交给渲染管线；不触发服务端任何逻辑。

### 3.3 简化改装流程（GUI 核心能力二）

以"从打开界面到装好一个配件 ≤ 2 次点击"为设计指标：

1. **入口不变**：接管 Z 键（mixin `RefitKey`），零学习成本；提供"使用原生界面"配置逃生开关。
2. **LMB 装 / RMB 拆**（ARC-9 语义）：配件条目左键安装并自动收起列表；右键条目或槽位直接卸下。当前 TACZ 需要"选中配件 → 点安装按钮"两步分离操作，全部合并。
3. **只显示能装的**：列表默认仅展示 `allowAttachment` 通过的配件；不兼容但玩家拥有的配件折叠进"不可安装"分组，角标注明原因（类型不允许 / 标签不匹配 / 配件锁锁定），杜绝玩家"为什么装不上"的困惑。
4. **文件夹浏览 + 搜索**：按 `folder` 元数据分文件夹（无 meta 的归"其他"），文件夹按钮带"已装/可用"计数徽标；顶栏搜索框支持按名称/短名模糊过滤（含本地化文本匹配）。
5. **收藏与记忆**：配件可收藏（置顶加权，ARC-9 排序方案：`SortOrder` + 收藏加权）；记住每把枪上次浏览的文件夹与滚动位置。
6. **快捷操作**：一键拆空（确认弹窗）、镭射调色板与瞄具变焦档位收纳进详情条（沿用 TACZ 现有交互与 packet）。

> ⚠️ **本节有三处被后续决策改掉，以票与地图为准：**
>
> - **第 3 条（不可安装分组）的"三个原因"不成立**：`allowAttachment` 只有标签匹配、不做类型检查（类型那层由"槽位本身合不合法"决定，配件锁是枪级标志），所以只有"标签不匹配"真会发生。做法定为**置底 + 分隔线 + 灰显 + 文字角标**、不做可折叠分组；角标两档说辞：「装不上这个配件」（有白名单但不含它）/「这里装不了配件」（枪压根没声明白名单）。见 [issue #6](https://github.com/MR1ZK/tacz-z-tweaks/issues/6) 与 `CONTEXT.md` 的「不可安装」。
> - **第 6 条的后半句（瞄具变焦档位）已否决**（不是"v1 不做"，是不做）：TACZ 原生本来就没有档位选择器，档位是"开镜时再按一次瞄准键循环"，C2S 只有无载荷的 `ClientMessagePlayerZoom`，想"选档"必须新增协议 —— 正面撞 ADR-0001。见 [issue #14](https://github.com/MR1ZK/tacz-z-tweaks/issues/14)。前半句（镭射调色板收纳进详情条）**已实现**。
> - **第 6 条里的"一键拆空"被推迟**（不是否决），见 [issue #15](https://github.com/MR1ZK/tacz-z-tweaks/issues/15)。第 4 条（文件夹浏览）的作废见 §3.1 的连带清单。

### 3.4 AutoStats 引擎（易读参数的核心）

只读消费 TACZ 内置 17 种 modifier（damage / recoil / ads / rpm / weight / effective_range / ammo_speed / silence 等）：

```
StatCatalog（注册表，数据驱动 + 代码默认值）
  stat id → { i18nKey, unit, convertFn, lowerIsBetter, TACZ modifier id 映射 }
       ↓ 输入：AttachmentData 中的 Modifier{addend, percent, multiplier}
ProsConsGenerator
  addend    → "+5"（带单位）
  multiplier→ "+15%" / "-10%"（(m-1)*100）
  percent   → 合并于 multiplier 展示
  lowerIsBetter × 正负 → 归入 Pros / Cons 双栏
  CustomPros/CustomCons 追加自由文本
```

整枪对比面板（"参数"标签页）：用 `AttachmentDataUtils` 的离线计算逐项求 `裸枪值 vs 当前改装值`，绿色/红色标注（正负+颜色文本，辅以可选条形图）；配合 §3.2 的悬停 diff，实现"看着参数装配件"。

### 3.5 新改装 UI（接管 GunRefitScreen）

**接管方式（mixin，仅 1 处）**：mixin `RefitKey` 的界面打开调用，改为打开 `RefitPlusScreen`。

**布局**（对标 ARC-9，适配 MC GUI）：

```
┌──────────────────────────────────────────────────────┐
│ [改装] [参数] [背景] [搜索____]            [预设 ▾]  │  顶部标签页
│                    （3D 枪械预览：                      │
│                     拖拽旋转/滚轮缩放/                  │
│                     悬停即虚拟装配/                     │
│                     槽位聚焦运镜）                      │
│                                                      │
├──────────────────────────────────────────────────────┤
│ [握把+] [瞄具•] [枪口+] [枪托•] [弹匣+] [镭射+]  ←槽位条│
│ ┌─ 瞄具/红点 ─┬─ 配件列表(横向,文件夹按钮+计数徽标) ──┐│
│ │             │  名称 | 短名图标 角标:已装/不兼容原因  ││
│ │  详情条:    │                                      ││
│ │  左:描述滚动 │  ┌─ Pros ──────┬─ Cons ─────────┐   ││
│ │    +Trivia  │  │ +15% 射程    │ +10% 开镜时间  │   ││
│ │  右:双栏    │  │ 消音         │ 占用导轨       │   ││
│ └─────────────┴──┴──────────────┴────────────────┘   ││
└──────────────────────────────────────────────────────┘
```

- 交互约定：左键配件 = 安装（虚拟装配已先行预览，所见即所得）；右键 = 卸下；`Esc`/再按 Z 关闭。
- 点击安装 → 发 TACZ 的 `ClientMessageRefitGun`（服务端照常校验配件锁/兼容性），收到 `ServerMessageRefreshRefitScreen` 后刷新。
- "背景"标签页 = Trivia（武器与配件的背景资料长文，来自 meta + 枪包原生 tooltip 汇总）。

### 3.6 预设系统

- 客户端按枪械 id 存最小结构：`{scope: {id, zoom}, muzzle: {...}, ...}`，文件 `config/refit_plus/presets/<gunId>/<name>.json`。
- 导入导出码：Base64(GZip(JSON))，聊天栏复制粘贴。
- 应用 = 逐槽发安装 packet；服务端照常校验，缺件自动跳过。

---

## 4. 网络设计（自有 SimpleChannel，版本独立）

> ⚠️ **本节已废（v1）。** 与 [ADR-0001](adr/0001-client-only-no-custom-data.md) 直接冲突：v1 不自建任何通道，安装/卸载/变焦/镭射色全部复用 TACZ 现有 packet，服务端零改动。
>
> 表里三个包**一个都不存在**。唯一还活着的是"预设"这个功能本身，但它的形态还在票 [预设系统的形态与应用语义](https://github.com/MR1ZK/tacz-z-tweaks/issues/10) 里定，未必需要网络。

| 包 | 方向 | 用途 |
|---|---|---|
| `ServerMessageSyncMeta` | S→C | 登录/换包时同步扩展元数据 + StatCatalog 覆盖项 |
| `ServerMessagePresetInfo` | S→C | （可选）服务端预设/整合包出厂预设下发 |
| `ClientMessagePresetApply` | C→S | 预设一键应用（服务端逐槽校验后执行） |

安装/卸载/变焦/镭射调色**不新增协议**，全部复用 TACZ channel。

---

## 5. Mixin 清单（最小化，逐条评估）

| 目标 | 作用 | 必要性 |
|---|---|---|
| `RefitKey` 打开界面调用 | UI 接管入口 | **唯一必须项** |
| `BedrockGunModel.render`（配件循环处） | 附录 A/远期：扩展槽位渲染 | 可选 |

原则：能走公开 API / 事件 / 标签的绝不 mixin；每个 mixin 独立 commit 并注释 TACZ 版本号。主线仅 1 处 mixin，将升级破坏面压到最小。

> ⚠️ **本表与实际做法不符。** UI 接管**没有用 mixin**：走的是继承 `GunRefitScreen` + `ScreenEvent.Opening` 拦截（[ADR-0002](adr/0002-takeover-via-gunrefitscreen-subclass.md)）。全项目现有两处 mixin，都用在别处，且都是 `require = 0`（注入失败静默降级）：`FirstPersonRenderGunEvent.applyFirstPersonPositioningTransform`（轨道相机，[ADR-0003](adr/0003-model-level-orbit-camera.md)）与 `GunItemRendererWrapper.renderFirstPerson`（悬停虚拟装配，[ADR-0004](adr/0004-virtual-assembly-by-clone-stack.md)）。
>
> 所以**"主线仅 1 处 mixin"这句话（本节与 §7、§8 都出现过）已作废，实际是 2 处**。另附一条现状：内部 API 的调用点分布与脆弱度已经有完整盘点，见 `research/tacz-internal-api-surface.md`。

---

## 6. 构建与依赖

> ⚠️ **本节三行现状都不对，逐条更正：**
>
> - `mods.toml` 实际声明的是 `tacz@[1.1.4,)`，不是 `[1.1.8,)`；而且**只对 1.1.8-hotfix 编译和试玩过**，更早版本没冒烟（下限的理由写在 `src/main/resources/META-INF/mods.toml` 的注释里）。**这里"写 1.1.8、实际声明 1.1.4"的不一致，正是本文档被当成权威、把版本号抄进 README 与 CODEBUDDY 之后造成的那处错误** —— 教训是别复述，指过去。
> - 仓库位置：就是本仓库 `tacz-z-tweaks`，没有另建 `refit-plus`（`build.gradle` 的根项目名倒还叫 `refit-plus`，是这处的遗留）。
> - CI：**还没建**。要不要建、版本下限收不收窄，归票 [TACZ 版本下限与兼容策略](https://github.com/MR1ZK/tacz-z-tweaks/issues/11)。

```gradle
// build.gradle（ForgeGradle 6 + mixin，与 TACZ 1.20.1-47.3.19 / Forge 47.3.x 一致）
dependencies {
    compileOnly fg.deobf("curse.maven:tacz-706784:1.1.8-hotfix文件ID")   // 或本地 jar：
    // compileOnly fg.deobf(files("libs/tacz-1.20.1-1.1.8.jar"))
}
```

- `mods.toml`：`dependencies: tacz@[1.1.8,)`（required-after）。
- 仓库位置：`myprojects/` 下新建 `refit-plus`（或你定的名字）。
- CI：GitHub Actions 构建 + 版本矩阵对 TACZ 小版本做 API 兼容冒烟。

---

## 7. 里程碑

> **实际进度（截至 2026-09-25）**：M0 / M1 / M2 完成；M3 部分完成（只列能装的 + 搜索 + 可用置顶 + 按参数排序筛选已做；不可安装分组与原因角标、收藏、滚动位置记忆、一键拆空未做）；M4 只落了 Pros/Cons 双栏（**"扩展元数据 + 同步"与 Trivia 页已整节不采纳**，见 §3.1；悬停实时 diff、整枪对比面板未做）；M5 未开始。
>
> 下表是规划期的估计，**已经不按它走了** —— 各功能的归属、取舍与顺序以[路线图](https://github.com/MR1ZK/tacz-z-tweaks/issues/1)为准。

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| **M0 骨架**（0.5 周） | 仓库/构建/CI、mod 骨架、TACZ 依赖打通、能读 `TimelessAPI` 索引 | 服务端启动打印配件索引数量 |
| **M1 UI 接管**（1-2 周） | mixin `RefitKey` 接管入口、新 Screen：静态 3D 预览 + 槽位条 + 兼容配件列表、安装/卸载走 TACZ packet（含镭射调色、变焦档位） | 全功能等价替换原生 `GunRefitScreen` |
| **M2 武器预览增强**（2 周） | 自由相机（拖拽/缩放）、槽位聚焦运镜与挂载点高亮、**悬停虚拟装配预览**、安装动效与音效 | 玩家悬停即可看到配件上枪效果，无需实际安装 |
| **M3 简化改装**（1-2 周） | LMB 装/RMB 拆、不可安装折叠分组+原因角标、文件夹浏览+搜索、收藏与位置记忆、一键拆空 | "打开界面 → 装好配件"≤ 2 次点击；不兼容原因全部可见 |
| **M4 易读参数**（2 周） | 扩展元数据 + 同步、**AutoStats Pros/Cons 双栏**、悬停实时 diff、整枪对比面板、Trivia 页 | 任意第三方枪包装上附属后，配件详情自动生成中英双语优缺点 |
| **M5 预设与打磨**（1-2 周） | 预设系统 + 导入导出码、配置项（原生界面逃生开关）、性能与低配优化 | 预设跨存档可用，导出码可分享 |
| **远期可选** | 附录 A 新属性 modifier、Bench 靶场模拟、嵌套槽/多槽（mixin 渲染层）、皮肤/外观分页 | — |

---

## 8. 风险与对策

| 风险 | 对策 |
|---|---|
| 悬停虚拟装配需复用 TACZ 渲染管线（`BedrockGunModel` 挂载逻辑在内部类中） | M2 先做"整枪旋转预览"保底，虚拟装配若渲染管线侵入过大则降级为"选中配件显示其独立模型特写"；渲染只读复用，不改 TACZ 类 |
| 自由相机与 `RefitTransform` 动画状态机冲突（TACZ 切槽位有固定运镜） | 预览相机作为 RefitTransform 的外层包装（组合而非替换），切槽运镜结束后交还控制权 |
| 第三方枪包无 meta | 全部降级策略：TACZ 原生字段兜底（name/tooltip/sort），Pros/Cons 由原生 modifier 数据自动生成（**不需要 meta 也能出完整优缺点**） |
| `AttachmentType` 枚举硬编码限制多槽 | 明确划入远期可选，用独立 NBT 命名空间 + 渲染 mixin 方案，不阻塞主线 |
| TACZ 小版本升级破坏 API/渲染内部调用 | 主线仅 1 处 mixin；CI 对 TACZ 新版本跑冒烟；api 包调用集中在 `compat/` 层便于集中修补 |

> 关于这张风险表：下半句（虚拟装配复用渲染管线、自由相机与 `RefitTransform` 组合而非替换）**实践下来是成立的**，前两行的对策也真的照做了。但**"主线仅 1 处 mixin"已作废（实际 2 处）**；`compat/` 适配层**至今没建**，内部调用仍然散在屏幕类里，归票 [内部 API 适配层该不该建](https://github.com/MR1ZK/tacz-z-tweaks/issues/13)。
| GPL-3.0 合规 | 附属与 TACZ 同为 GPL-3.0，衍生关系明确；仓库附 LICENSE + 头注；引用 TACZ 代码处注明来源 |

---

## 附录 A：可选扩展——新属性 modifier（非主线）

> ⚠️ **已明确划出路线之外。** 这条连同多槽/嵌套槽、靶场模拟、皮肤分页一起写在[路线图](https://github.com/MR1ZK/tacz-z-tweaks/issues/1)的 **Out of scope** 里：它要碰 TACZ 内部实现，升级破坏风险高于主线，只有重画目的地才会回来。

如未来需要"更多改装选项"：在 mod 构造期（早于 `GunPackLoader`）向 `AttachmentPropertyManager.getModifiers().put()` 注入自定义 `IAttachmentModifier`（如 reload_time / draw_time / ergonomics）。每个新属性须先确认在 TACZ 射击逻辑中的生效路径（优先利用 `GunReloadEvent` 等可取消事件），确实无 hook 的只做展示层聚合；同时实现 `getPropertyDiagramsData()` 保证新旧 UI 双兼容。此项涉及 TACZ 内部实现细节，升级破坏风险高于主线，故不纳入核心目标。

---

## 9. 立即可做的下一步

> ⚠️ **三条都做完了，做法与这里写的不完全一样**：仓库叫 `tacz-z-tweaks` 不是 `refit-plus`；M0 的依赖打通与索引冒烟已完成；第 3 条**没有用 mixin**，接管走的是继承 `GunRefitScreen` + 事件拦截（[ADR-0002](adr/0002-takeover-via-gunrefitscreen-subclass.md)）。本节保留只作考古。

1. 新建 `myprojects/refit-plus` 仓库（GPL-3.0，ForgeGradle 6 模板）。
2. M0：打通 `compileOnly` 依赖 + `TimelessAPI.getCommonAttachmentIndex` 冒烟测试。
3. M1 第一刀：mixin `RefitKey` → 打开空白自绘 Screen（含 3D 枪模渲染）。
