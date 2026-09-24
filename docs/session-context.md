# 会话上下文摘要

> 2026-09-24，截至「候选列表按配件参数排序与筛选」这轮更新（新增 `StatCatalog` 参数目录）。面向下一个接手的人（人或 agent）：读完这份文档应该能接着干活，不用翻对话记录。

## 项目一句话

**TACZ: Z-Tweaks** —— Forge 1.20.1 客户端模组，用自绘 GUI 接管 TACZ（Timeless & Classics Guns）的改装界面（Z 键），提供更好的武器预览、更简单的改装流程、更易读的参数。对标 Garry's Mod 的 ARC-9。GPL-3.0。

- 仓库：`github.com/MR1ZK/tacz-z-tweaks`，`main` 分支
- 已发布：**v0.1.1-hotfix1**（Latest，tag 即 v0.1.1-hotfix1）；上一版正式 release 是 v0.1.1
- 定位：原型（throwaway）阶段，M0–M2 完成、M3 部分完成，UI 仍按试玩反馈调整

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
- 候选列表：只列 `allowAttachment` 通过的。生存模式只列背包里有的；创造模式列全部，没带在身上的图标盖 50% 黑。双击即装。底部一行是搜索框 + 排序按钮（概览态都不创建）
- 候选排序：**已拥有（真能装上）的恒置顶**，其后按玩家所选排序。排序按钮现在展开一个**弹层**（不再循环切换），里面是 名称 / 模组 / 十个参数项（开镜时间、后坐力、射速、重量、有效射程、弹速、护甲穿透、穿透、击退、爆头倍率），外加方向与筛选两个开关；方向语义是**优劣**（"后坐力 优→劣"= 后坐力最小的在前），名称/模组这两项下就是 A-Z / Z-A。"只看改善的"只在选中参数时可用，按**边际**改善量过滤（比现在装着的更好才算）。排序偏好切槽位不清空，搜索词则清空
- 参数排序的取值走 `StatCatalog`：统一用 TACZ 的 `getPropertyDiagramsData` 拿"相对基值的增减量"（类型是 `Number`，不用强转），方向随 `positivelyBetter` 走；多条的（目前只有后坐力的 pitch/yaw）取绝对值最大的那个轴。改善量在列表重建时算一次，每个候选克隆枪栈 + 一次 `eval`，不是每帧
- 概览态（没选槽位）：不画候选框、不画搜索框/排序按钮、不画安装/卸下按钮；详情条整条是枪械参数卡（三列：名字+描述 / 参数 / 预留），0.8 缩放，各列独立滚
- 选中配件：详情条左列（0.8 缩放、可滚）+ Pros/Cons 双栏
- 槽位条（底部）：左键点 / 数字键 `1`–`6` = 选槽位（同一槽位再点一次退回概览态）；**右键点槽位 = 直接卸下**该槽位上的配件，与"卸下"按钮 / `U` 键共用 `unloadSlot(type)` 一份护栏（不支持该槽位、空槽、背包没空位各有各自的提示）。命中槽位后右键就早退，不会落进"空白处右键=平移"那一支
- 滚轮归属：候选框内（搜索框、排序按钮除外）翻列表 → 概览态在详情条滚卡片（按列）→ 选中配件时落左列滚描述 → 其余缩放相机

## 已知问题 / 待办

- **mixin 目标是 1.1.8-hotfix 的签名**。mods.toml 下限放到 `[1.1.4,)` 但没对 1.1.4 实际冒烟过；旧版本若改了目标方法，注入静默降级（相机/虚拟装配失效，其余正常）
- `computeProsCons()` 每帧无缓存地跑（`AttachmentCacheProperty.eval` ×2），是既有性能问题
- 配置屏输入框改数值不会把滑块拖回（输入中间态会乱跳，故意不同步）
- 闲置卡片第三列预留了但没放数据（候选：开火模式、弹匣容量、弹速/穿透/击退、内置配件——调研结论见对话记录，TACZ 的 `IGun`/`GunData` API 清单当时已摸清）
- 配置屏没有"恢复默认"按钮
- TACZ 经验等级百分比有个 int 整除 bug（非满级恒显 0.0%），我们的卡片已绕开；如果改回对齐原生行为要注意
- **参数排序与筛选只过了编译，没在游戏里实跑过**。排序值本身的算式有 TACZ 源码为据（见 `StatCatalog` 注释），但弹层的绘制与命中、以及"改排序当场重建列表"的观感都没验证
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
