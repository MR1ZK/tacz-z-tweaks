# 主题系统 v2：数据化、受控布局、可关闭的动效

上一版（[ADR-0007](0007-ui-theme-system.md)）把主题定成"颜色 + 质感"，并明确**不动布局、不做动效、主题只写在代码里**。实机与后续试玩暴露了它的上限：四套主题共享同一套几何与同一套静态绘制，换主题更像换色；新增一套主题要改 Java；界面没有过渡，弹层与状态变化都是硬切。

这次重写要解决的是"主题表现力"，不是"再把颜色收一遍"（颜色已经收干净了）。下面的决定取代 ADR-0007 的第 1、2、5 条，其余条款继续有效。

## 决定

1. **主题是数据，不是代码**：内置主题住 `assets/z_tweaks/themes/<id>.json`，随 jar 发布；资源包可以在自己的命名空间下覆盖或新增。配置存字符串 id，启动/开屏时对照注册表校验，认不出就回落默认并记一次日志。**不开放** `<游戏目录>/ztweaks/themes/` 这种写盘目录：那会引入版本、校验、坏文件兜底一整套协议，当前没有第二个作者。
2. **主题文件四段 + 一层继承**：`palette` / `metrics` / `chrome` / `effects`；`extends` 只允许指向另一套内置主题，**只解析一层**，禁止环。身份字段 `schema` / `id` / `name_key` / `short_name_key` / `order` / `accent_editable`。显示名一律走 lang key（默认 `gui.z_tweaks.theme.name.<id>`，缺失回落到 id），不在 JSON 内联翻译。
3. **布局参数受控**：只开放五个 —— `density`（0.9–1.15）、`detail_left_ratio`（0.36–0.44）、`info_font_scale`（0.75–0.90）、`panel_padding`（4–8）、`slot_gap`（2–6）。面板宽度、弹层宽度、控件摆放一律固定。**几何只有一个来源**：`layout(theme, w, h)` 纯函数产出一份不可变 `Layout`，绘制、命中、滚轮都读它；行与弹层这类随内容变化的矩形由纯函数从 `Layout` 派生，不再各写一套。
4. **装饰层是有界部件**：代码提供 `frame`（panel / popup 两套九宫格）、`title_bar`、`corners`、`pattern`、`selection`、`button`（三态）；每部件是可选对象，写 UV、九宫格边距、透明度，缺失即回落程序化绘制。贴图每套主题一张 atlas（`textures/gui/theme/<id>.png`）。**不做**声明式绘制脚本、不做自定义字体、不做 shader、不做音效。

   v1 先落程序化样式，JSON 形状如下（所有字段可省略，缺省取 `ThemeChrome.DEFAULT`）：

   ```json
   "chrome": {
     "frame":     { "panel": "default|bevel|double|none", "popup": "default|bevel|double|none" },
     "title_bar": { "style": "accent|banner|none" },
     "corners":   { "style": "none|brackets" },
     "pattern":   { "style": "none|scanline|grid", "alpha": 0.08 },
     "selection": { "style": "bar|fill|glow" },
     "button":    { "style": "default|bevel|flat" }
   }
   ```

   贴图的 `texture/u/v/left/right/top/bottom` 是同一字段的对象写法，优先于程序化样式，缺图时自动回落：

   ```json
   "frame": {
     "panel": {
       "style": "default",
       "texture": "z_tweaks:textures/gui/theme/default.png",
       "u": 0, "v": 0, "width": 64, "height": 64,
       "left": 8, "top": 8, "right": 8, "bottom": 8
     },
     "popup": "bevel"
   }
   ```

   `title_bar` 与 `button` 也是同一套对象写法：`title_bar` 直接带 texture 字段；`button` 的 `normal` / `hover` / `disabled` 各是一个九宫格对象。

   ```json
   "title_bar": {
     "style": "banner",
     "texture": "z_tweaks:textures/gui/theme/aero.png",
     "u": 0, "v": 128, "width": 128, "height": 16,
     "left": 4, "top": 2, "right": 4, "bottom": 2
   },
   "button": {
     "style": "bevel",
     "normal":   { "texture": "...", "u": 0,  "v": 64, "width": 64, "height": 32, "left": 6, "top": 5, "right": 6, "bottom": 5 },
     "hover":    { "texture": "...", "u": 64, "v": 64, "width": 64, "height": 32, "left": 6, "top": 5, "right": 6, "bottom": 5 },
     "disabled": { "texture": "...", "u": 0,  "v": 96, "width": 64, "height": 32, "left": 6, "top": 5, "right": 6, "bottom": 5 }
   }
   ```

   解析时用客户端资源管理器确认贴图存在；不存在就记一条日志、把该九宫格置空，回落到 `style`。

   **atlas 画布必须是 256×256**：1.20.1 的 `blitNineSliced` 内部调用的是固定 256×256 的 `blit`，非 256 画布会把 `u/v/width/height` 按 256 归一化，中心区域会采到边框、整块面板被平铺成网格（实机踩过）。源区域坐标保持不变。
5. **动效分三档**：`full` / `reduced` / `off`（客户端配置，默认 `full`）。`reduced` 去粒子与流光、保留 120–200ms 过渡；`off` 全即时。粒子/流光只画 2D 屏幕层：背景层在 3D 画面与面板之间，前景层只裁在 3D 预览区。全局上限 96 个、单帧 ≤0.3ms CPU，帧率低于 45 FPS 持续 3 秒自动降一档（只降不升）。
6. **视觉可以动画，命中永远用最终几何**：切主题时调色板逐色插值 180ms，chrome/effect 在 0.5 处切换，同时叠一层正弦压暗（峰值 22%）把硬切藏住；`metrics` 立刻换成目标，命中从第一帧起就是最终几何。**不做 chrome 双层交叉淡入**：chrome 是立即模式里逐帧重绘的像素，没有可独立合成的图层，双绘一遍等于把整屏画两遍，收益不抵成本；压暗遮罩是同一目的下更便宜的做法。切换过程中再切：从当前插值状态重定向，不排队。
7. **兼容是硬约束**：四套主题的 id（`default` / `md3` / `aero` / `win_classic`）、`accent_rgb`、既有配置键、`ScreenEvent.Opening` 接管、`extends GunRefitScreen`、TACZ `[1.1.4,)` 全部保留；480×270 是验收尺寸。

## Considered Options

- **保留"主题只写在代码里"**：加第五套要改 Java、重新编译，资源包作者无法参与；与"主题系统"这个目标相抵。
- **声明式绘制脚本（JSON 里写 draw op）**：自由度高，但等于自造一门渲染 DSL，要解释器、校验、性能上限与 480×270 的安全边界，收益不抵成本。
- **每套主题一个 Java 渲染类**：表现力最强，但杀死资源包与数据化，回到上一版。
- **完全自由布局**：每个主题一套命中判定，480×270 无法验收；且控件位置一变，玩家的肌肉记忆全部失效。
- **3D 世界空间粒子**：要接 MC 的世界粒子引擎或 TACZ 的枪械渲染管线，受世界光照、裁剪、相机变换影响；2D 双 z 层 + 视差已能覆盖观感。

## Consequences

- **绘制层可以慢慢来**：数据层先接上、渲染先不变（四套主题由 JSON 驱动但观感零差异），然后布局快照、再 UI kit、再动效、最后旗舰主题。每一步都能单独编译、单独回滚。
- **配置类型变了**：`theme` 从枚举名变成字符串 id。旧值（`DEFAULT` / `MD3` / `AERO` / `WIN_CLASSIC`）在读取时映射到新 id，玩家不需要手改 TOML。
- **资源包作者要带 lang**：主题名不内联在 JSON 里，新增主题必须同时提供 `gui.z_tweaks.theme.name.<id>`，缺失时界面显示 id。
- **贴图是唯一外部依赖**：没有 atlas 时六个部件全部回落程序化绘制，所以架构阶段可以先跑通、后补美术。
- **ADR-0007 仍有效的部分**：主题选择存客户端配置、主题不改布局以外的任何交互语义、无背景模糊、入口交互与四套主题的"神似而非考据"。
