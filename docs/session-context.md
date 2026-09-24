# 会话上下文摘要

> 2026-09-25，截至「地图归档后全面开工」这轮更新：**不可安装的配件分组（#6）、参数卡补六项（#8）、详情条第三列变化项（#7）、悬停驱动整条详情条（#9）、预设系统（#10）** 全部落进代码。面向下一个接手的人（人或 agent）：读完这份文档应该能接着干活，不用翻对话记录。

## 项目一句话

**TACZ: Z-Tweaks** —— Forge 1.20.1 客户端模组，用自绘 GUI 接管 TACZ（Timeless & Classics Guns）的改装界面（Z 键），提供更好的武器预览、更简单的改装流程、更易读的参数。对标 Garry's Mod 的 ARC-9。GPL-3.0。

- 仓库：`github.com/MR1ZK/tacz-z-tweaks`，`main` 分支
- 已发布：**v0.1.1-hotfix1**（Latest，tag 即 v0.1.1-hotfix1）；上一版正式 release 是 v0.1.1
- 定位：原型（throwaway）阶段，M0–M2 完成、M3 部分完成，UI 仍按试玩反馈调整
- **动功能之前先读[路线图](https://github.com/MR1ZK/tacz-z-tweaks/issues/1)**：一次 wayfinder 寻路留下的决策地图（1 张地图 + 16 张票），每个待做的功能都有一张票承载它的决策、取舍与依赖。**地图已于 2026-09-25 归档**（目的地达成、frontier 空）：16 张子票全部有结论 —— 12 解决 / 3 推迟 / 1 否决；推迟的是 #11（TACZ 版本下限与兼容策略）、#13（内部 API 适配层 compat/）、#15（一键拆空），否决的是 #14（瞄具变焦档位做成界面可选），**已解决的决策不因归档而失效**。开工前的规划稿在 `docs/refit-plus-plan.md`（已标注哪几节作废），三份调研在 `docs/research/`

## 构建与运行

- 需要 `libs/tacz-1.20.1-1.1.8-hotfix.jar`（TACZ 本体，不入库）
- `./gradlew build` / `./gradlew runClient`
- 提交用 `git -c user.name=MR1ZK -c user.email=...noreply.github.com`（**仓库没配 git 身份**，别改全局 config）
- 发布流程：改代码 → build → commit → `git tag -f v0.x.y` 强移标签 → `gh release upload <tag> <jar> --clobber` + `gh release edit --notes`

## 架构要点

- **纯客户端**。接管靠 `ScreenEvent.Opening` 拦截（无 mixin），安装/卸载/变焦/镭射色全部复用 TACZ 的 packet
- **全项目只有 2 处 mixin**，都是 `require = 0`（注入失败静默降级）：
  - `FirstPersonRenderGunEventMixin` —— 轨道相机，注入 `applyFirstPersonPositioningTransform` 的 RETURN
  - `GunItemRendererWrapperMixin` —— 悬停虚拟装配，`@ModifyVariable` 换入口的 gun stack
- 核心文件：`ZtRefitScreen.java`（~1600 行，界面全部在这里）；`OrbitCamera` / `VirtualAssembly` 是状态单例
- 几何原则：**绘制与命中检测共用同一个 rect 来源**（`listRect` / `rowRect` / `slotRect` / `attachColumnRect`），改一处不会飘
- 文案原则：枪的参数全部复用 TACZ 自己的 lang key（`tooltip.tacz.gun.*`）和 `AttachmentDataUtils` 离线计算，中英与原生 tooltip 对齐
- 性能注意：`AttachmentDataUtils` 每次调用都全量重算，**必须缓存**（枪械参数卡按枪 id+NBT 缓存）

## 当前界面行为速览

- 轨道相机：左键拖=环绕+绕枪管长轴滚转（上下方向已按手感反馈**反转**；枢轴取根骨骼，`T(p)·R·T(−p)` 共轭搬到枪身长轴上），右键拖=平移，滚轮=缩放（向上=拉近），中键/R=复位
- 候选列表：**分两组**。能装的在上（生存模式只列背包里有的；创造模式列全部，没带在身上的图标盖 50% 黑）；**不可安装但手里有的**置底、灰显、上面压一条分隔线、右侧文字角标两档（「装不上这个配件」= 枪有白名单但不含它 / 「这里装不了配件」= 枪压根没声明白名单，后者还会在标题行说一次）。空列表的说法按成因分三句（搜索筛没 / 枪没声明白名单 / 其余），**同一成因同一个说法**。双击即装；不可安装的行可选中、可看名字与描述，但**不驱动 3D 预览与参数**，双击 / 安装按钮 / ENTER 一律拒绝并说明（护栏只有一条，见 `installSelected`）。底部一行是搜索框 + 排序按钮（概览态都不创建）
- 候选排序：**已拥有（真能装上）的恒置顶**，其后按玩家所选排序。排序按钮现在展开一个**弹层**（不再循环切换），里面是 名称 / 模组 / 十个参数项（开镜时间、后坐力、射速、重量、有效射程、弹速、护甲穿透、穿透、击退、爆头倍率），外加方向与筛选两个开关；方向语义是**优劣**（"后坐力 优→劣"= 后坐力最小的在前），名称/模组这两项下就是 A-Z / Z-A。"只看改善的"只在选中参数时可用，按**边际**改善量过滤（比现在装着的更好才算）。排序偏好切槽位不清空，搜索词则清空
- 参数排序的取值走 `StatCatalog`：统一用 TACZ 的 `getPropertyDiagramsData` 拿"相对基值的增减量"（类型是 `Number`，不用强转），方向随 `positivelyBetter` 走；多条的（目前只有后坐力的 pitch/yaw）取绝对值最大的那个轴。改善量在列表重建时算一次，每个候选克隆枪栈 + 一次 `eval`，不是每帧
- 概览态（没选槽位）：不画候选框、不画搜索框/排序按钮、不画安装/卸下按钮；详情条整条是枪械参数卡（三列：名字+描述 / **全量参数** / 变化项），0.8 缩放，各列独立滚。参数 14 项：口径、经验等级、枪种、伤害、护甲穿透、爆头伤害、移动速度 + **开火模式、弹匣容量、射速、弹速、穿透、开镜时间**（后六项是 #8 补的，文案复用 TACZ 的 `property_diagrams.*`）
- 选中槽位：详情条左列（名字 + 描述 + 「预览：<件名>（未安装）」）+ Pros/Cons 双栏 + **变化列（第三列）**。变化列只列"装上正在看的那件之后会变的项"，给绝对双值 `12.5 → 14.2` 并按好坏染色（`ParamRow.raw` + `higherIsBetter`），顺序固定、列内可滚
- **悬停驱动整条详情条**（#9）：悬停行优先、否则选中件（`previewCandidate()`），移出不保持；Pros/Cons 与变化列共用一个重算入口 `refreshPreview()`，键是 **枪 id + NBT + 预览件 id**，命中就什么都不做（顺带还掉了"Pros/Cons 每帧两次 eval"那笔债）
- 预设（#10）：详情条上一颗"预设"按钮 → 弹层（左键应用 / 右键删除 / 保存当前 / 从剪贴板导入 / 导出码）。应用到当前枪前先**干跑**：算出装上几件、卸下几件、缺几件、背包空位够不够，摆出来让玩家确认；**预设没提到的槽位按"就是要空"处理**（没提到就卸）。存储见 `PresetStore`（`<游戏目录>/ztweaks/…`，多人按主机名隔离），码 = 前缀 + Base64(GZip(json))
- 槽位条（底部）：左键点 / 数字键 `1`–`6` = 选槽位（同一槽位再点一次退回概览态）；**右键点槽位 = 直接卸下**该槽位上的配件，与"卸下"按钮 / `U` 键共用 `unloadSlot(type)` 一份护栏（不支持该槽位、空槽、背包没空位各有各自的提示）。命中槽位后右键就早退，不会落进"空白处右键=平移"那一支
- 滚轮归属：候选框内（搜索框、排序按钮除外）翻列表 → 概览态在详情条滚卡片（按列）→ 选中配件时落左列滚描述 → 其余缩放相机

## 已知问题 / 待办

- **地图归档 ≠ 功能做完**：仓库没有打开的 issue，**#6/#7/#8/#9/#10 已落进代码**；还留在纸上的只剩**收藏（#5）与会话内位置记忆**——`CONTEXT.md` 有「收藏」词条、ADR-0005 给它留了 `favorites.json`，但一颗星都没画（两个入口、排序里插在"已拥有"之后、只看收藏开关、按 枪 id + 槽位类型 记的滚动与选中项）。
- **#7 的宽度表是概览态卡的三列几何（`usable/3.5`），槽位模式下的变化列是另一套**：详情条右侧现在三等分（Pros / Cons / 变化列），480×270 下每段约 93px。变化列那条"最多 5 行、要滚"的约束因此更紧，`drawParamChanges` 里做了裁剪与滚动。
- **mixin 目标是 1.1.8-hotfix 的签名**。mods.toml 下限放到 `[1.1.4,)` 但没对 1.1.4 实际冒烟过；旧版本若改了目标方法，注入静默降级（相机/虚拟装配失效，其余正常）
- ~~`computeProsCons()` 每帧无缓存地跑（`AttachmentCacheProperty.eval` ×2）~~ —— 已随 #9 的缓存入口还掉：现在只有"枪 + NBT + 预览件"三者之一变化时才重算
- 配置屏输入框改数值不会把滑块拖回（输入中间态会乱跳，故意不同步）
- ~~闲置卡片第三列预留了但没放数据~~ —— 第二列现在是全量参数（含 `docs/research/client-gun-params.md` 定的那六项，顺序照它）、第三列是变化项（#7/#8 已落地）。后坐力仍**不该进**（样条曲线，非标量）
- 配置屏没有"恢复默认"按钮
- TACZ 经验等级百分比有个 int 整除 bug（非满级恒显 0.0%），我们的卡片已绕开；如果改回对齐原生行为要注意
- **参数排序与筛选已实跑验证**（弹层绘制与命中、方向语义、改排序当场重建）。
- 记一个约束：弹层内容有 `170px`，而它可用的垂直空间只有 `GUI 高 − 150` —— 所以 **GUI 高度不足 320 时它会滚动**（不只是裁掉）。将来加排序项或调行高，它会继续变长，别假设一屏放得下
- 用户侧验证方式：`runClient` 截图确认手感（本会话所有 3D 手感改动均未实跑验证）

## 本会话踩过的坑（别再踩）

- **PowerShell 不支持 heredoc**，`git commit -F - <<'EOF'` 会炸；多行消息拆成多个 `-m` 或写临时文件
- **PowerShell 会把多行字符串参数拆开**，含换行的 `-m "..."` 会变成多个 pathspec
- **`OptionsList.addSmall` 只收 `OptionInstance<?>`**，塞任意 widget 编译报错；要自己造滚动列表
- **TACZ 的枪械 tooltip 不走 `appendHoverText`**，参数全在 `getTooltipImage()` 的自定义组件里，`getTooltipLines` 拿不到
- **模型空间的轴**：注入点处 X≈视线方向、Y≈屏幕竖直、Z≈屏幕水平；枪的长轴是模型 Z，枢轴共轭是 `T(p)·R·T(−p)`，p 取 `BedrockGunModel.getRootNode()`（像素 /16）
- **Forge `mods.toml` 的 `versionRange = ""` 会崩**，空 range 不行
- 推送偶尔 `Failed to connect to github.com:443`，重试即可
- `gh release upload --clobber` 可覆盖同名资产；复用版本号时 `git tag -f` + `git push -f origin <tag>`

## 工作惯例

- 设计决策用 grilling 流程（用户挂 `grill-me` skill）：一轮问全 frontier，给推荐答案，用户回选项后实现
- 每个功能一个 commit，中文 conventional 风格（`feat(ui):` / `fix:` / `chore:`），推送由用户明确指示
- README、中英 lang 与代码改动同步
