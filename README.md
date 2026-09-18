# TACZ: Z-Tweaks（TACZ：改装UI调整）

面向 **Minecraft Forge 1.20.1** 的 [TACZ](https://github.com/MCModderAnchor/TACZ)（Timeless & Classics Guns）附属模组。

## 定位

接管 TACZ 自带的改装界面（默认 Z 键），以 GUI 形式提供：

- **更好的武器预览** —— 可拖拽旋转、滚轮缩放的轨道相机；悬停配件即在 3D 预览里虚拟装配
- **更简单的改装** —— 列表只列当前槽位装得上的配件，点选即预览，`Enter` / `U` 或按钮完成装卸
- **更易读的简介和参数** —— 由配件属性修改器自动推导的 Pros / Cons 双栏（相对裸枪基准）

设计参考 Garry's Mod 的 ARC-9 配件系统。术语表见 `CONTEXT.md`，决策记录见 `docs/adr/`。

## 已实现

| 能力 | 说明 |
|---|---|
| 界面接管 | 用 `ScreenEvent.Opening` 拦截一切打开原生 `GunRefitScreen` 的调用点（按键、其它模组、服务端刷新一并接管），无需 mixin |
| 轨道相机 | 左键左右拖环绕、上下拖**绕枪械自身长轴滚转**（枢轴取根骨骼，贴在枪身上）、右键拖平移、滚轮缩放，可转满圈 |
| 悬停虚拟装配 | 鼠标划过候选列表即在预览里装上该配件（克隆枪栈驱动渲染，不落 NBT、不发 packet），先看效果再决定装不装 |
| Pros / Cons | 由属性修改器自动推导增减，绿/红双栏直读；`DELTA` 模式自算"装上后会变成什么样"，`TACZ_TEXT` 模式复用 TACZ 成品文本 |
| 候选列表 | 只列当前槽位装得上的配件；不在背包里的压暗 + 红点，点安装会提示"缺少配件" |
| 概览态 | 没选槽位时不画候选框，那块区域交还给 3D 预览的拖拽与缩放；详情条改显示枪械本身的描述（枪包 `tooltip`，缺失时退化为「类型 / 口径 / 射速 / 弹匣」摘要） |
| 搜索与双击 | 候选框底部搜索框按显示名过滤（切槽位自动清空）；双击候选行直接装上，省掉"点配件 → 点安装" |

全项目只有 2 处 mixin（轨道相机、虚拟装配），均 `require = 0`：注入失败时静默降级为原生行为，界面其余功能不受影响。诊断 HUD 会显示"轨道注入命中"计数，可直观确认。

## 操作

| 输入 | 作用 |
|---|---|
| 左键拖 | 左右=环绕，上下=绕枪管长轴滚转（一屏 360°） |
| 右键拖 | 平移（只挪位置，不改朝向） |
| 滚轮 | 缩放（指针在候选框内时翻列表） |
| `1`–`6` / 点槽位 | 选槽位（同一槽位再点一次退回概览态） |
| `↑` `↓` | 切换候选配件 |
| 双击候选行 | 直接装上该配件 |
| `Enter` / `U` | 安装 / 卸下 |
| 中键 / `R` | 相机复位（角度、缩放、平移一起清零） |
| `Esc` | 关闭 |

`V`（开关相机，用于和原生取景做对照）与 `G`（叠加原生属性条）默认关闭，需在配置文件里先启用 —— 见下。

## 配置

`config/z_tweaks-client.toml`：

| 段 | 项 | 默认 | 说明 |
|---|---|---|---|
| `refit` | `pros_cons_mode` | `DELTA` | Pros/Cons 生成方式：`DELTA` 自算增减 / `TACZ_TEXT` 复用 TACZ 成品文本 |
| `refit` | `takeover` | `true` | 接管原生改装界面；关掉即回到 TACZ 原生界面（逃生开关，会在日志里留痕） |
| `refit` | `orbit_camera` | `true` | 轨道相机总开关。硬关：关掉后 `V` 键也开不回来 |
| `refit` | `virtual_assembly` | `true` | 悬停虚拟装配总开关。硬关：关掉后预览始终是手上的真枪 |
| `debug` | `hud` | `false` | 左上角诊断 HUD（mixin 命中数、相机读数、枢轴读数、取景进度、字体测试） |
| `debug` | `samples` | `false` | 详情条里打印 TACZ 成品文本原文（含色码） |
| `debug` | `native_bars_key` | `false` | 启用 `G` 键叠加原生属性条 |
| `debug` | `camera_hotkey` | `false` | 启用 `V` 键开关轨道相机 |
| `debug` | `pivot_source` | `ROOT_PIVOT` | 枢轴取法：`ROOT_PIVOT` 贴枪身长轴 / `ORIGIN` 枢轴修复前的模型原点（用于改前改后对照） |
| `debug` | `pivot_offset_y` | `0.0` | 枢轴微调，在 `pivot_source` 之上再抬高/降低多少格（±1.0） |
| `debug` | `roll_speed` | `360.0` | 上下拖一整屏高对应的滚转角度（度） |
| `debug` | `yaw_speed` | `360.0` | 左右拖一整屏宽对应的环绕角度（度） |
| `debug` | `pan_speed` | `1.0` | 右键拖一整屏对应的平移距离（格） |
| `debug` | `zoom_step` | `0.15` | 滚轮每格对应的缩放增量 |

## 状态

原型（throwaway）阶段，M0–M2 已完成、M3 部分完成。界面标题与部分代码注释仍标着 prototype —— 视觉与手感还在按实际试玩反馈调，未定稿。

## 构建

1. 取得 TACZ 本体 jar（二选一）：
   - 在相邻仓库构建：`cd ../TACZ && ./gradlew build`，取 `build/libs/` 产物；
   - 或从 CurseForge / Modrinth 下载对应版本。
2. 将 jar 放入 `libs/`，命名为 `tacz-1.20.1-1.1.8-hotfix.jar`（与 `gradle.properties` 中的 `tacz_version` 一致）。
3. 构建本模组：

```bash
./gradlew build          # 产物在 build/libs/z_tweaks-1.20.1-0.1.0.jar
./gradlew runClient      # 启动开发客户端（需要 libs/ 中存在 TACZ jar）
```

> 注：改动依赖版本后同步修改 `gradle.properties` 与 `libs/` 中的文件名。

## 许可

**GPL-3.0**，与 TACZ 本体许可一致。引用 TACZ 源码之处均注明来源。
