# 配置

`config/z_tweaks-client.toml`，也可以从游戏内的「选项 → Mod → TACZ: Z-Tweaks → Config」打开。界面里改完即时生效（所有读取点都是运行时 `.get()`），关闭界面时写盘。

## `refit` 段

| 项 | 默认 | 说明 |
|---|---|---|
| `pros_cons_mode` | `DELTA` | Pros/Cons 生成方式：`DELTA` 自算增减 / `TACZ_TEXT` 复用 TACZ 成品文本 |
| `takeover` | `true` | 接管原生改装界面。关掉就回到 TACZ 原生界面，日志里会留一条说明是被配置关的 |
| `orbit_camera` | `true` | 轨道相机总开关，硬关：关掉后 `V` 键也开不回来 |
| `virtual_assembly` | `true` | 悬停虚拟装配总开关，硬关：关掉后预览始终是手上的真枪 |

## `look` 段

| 项 | 默认 | 说明 |
|---|---|---|
| `theme` | `default` | 主题 id。内置 `default` / `md3` / `aero` / `win_classic`；资源包可在 `assets/<命名空间>/themes/<id>.json` 下覆盖或新增，认不出的 id 回落 `default` |
| `accent_rgb` | `0x55FFFF` | 默认主题的强调色（`0xRRGGBB`）。只对 `accent_editable` 为 true 的主题生效 |
| `motion` | `FULL` | 动效档位：`FULL` 过渡 + 粒子/流光 / `REDUCED` 去粒子、保留短过渡 / `OFF` 全部即时。帧率低于 45 持续 3 秒会在本次界面自动关粒子（只降不升） |
| `blur` | `true` | 玻璃主题（千禧航空）的面板背景模糊：每 8 帧从主帧缓冲降采样一次。关掉后退回半透明底色；帧率降级时也会自动关 |

主题文件的结构与限制见 [ADR-0008](adr/0008-theme-system-v2.md)：四段 `palette` / `metrics` / `chrome` / `effects`，一层 `extends`，布局参数只开放五个。

## `debug` 段

调界面时用的，默认全关，正常游玩不用动。

| 项 | 默认 | 说明 |
|---|---|---|
| `hud` | `false` | 左上角诊断 HUD（mixin 命中数、相机读数、枢轴读数、取景进度、字体测试） |
| `samples` | `false` | 详情条里打印 TACZ 成品文本原文（含色码） |
| `native_bars_key` | `false` | 启用 `G` 键叠加原生属性条 |
| `camera_hotkey` | `false` | 启用 `V` 键开关轨道相机 |
| `pivot_source` | `ROOT_PIVOT` | 枢轴取法：`ROOT_PIVOT` 贴枪身长轴 / `ORIGIN` 是枢轴修复前的模型原点，用来做改前改后对照 |
| `pivot_offset_y` | `0.0` | 枢轴微调，在 `pivot_source` 之上再抬高或降低多少格（±1.0） |
| `roll_speed` | `360.0` | 上下拖一整屏高对应的滚转角度（度） |
| `yaw_speed` | `360.0` | 左右拖一整屏宽对应的环绕角度（度） |
| `pan_speed` | `1.0` | 右键拖一整屏对应的平移距离（格） |
| `zoom_step` | `0.15` | 滚轮每格对应的缩放增量 |

`V` 和 `G` 得先在 `camera_hotkey` / `native_bars_key` 里打开才响应。后四项（`roll_speed` / `yaw_speed` / `pan_speed` / `zoom_step`）决定拖一屏转多少，是唯一需要按手感反复调的。
