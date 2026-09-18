# 接管方式：继承 `GunRefitScreen` 的子类 + `ScreenEvent.Opening` 拦截

TACZ 的改装 3D 预览不在 `Screen` 里绘制，而是"第一人称手部渲染 pass 被改装取景掰过去"的结果，其开关是 `RefitTransform` 的私有静态进度值，**只有当 `Minecraft.getInstance().screen instanceof GunRefitScreen` 时才增长**（`RefitTransform.tickInterpolation`），且没有任何 setter。因此一个从零自写的 `Screen` 会失去整个 3D 预览、动画状态机与取景，而 `extends GunRefitScreen`（覆写 `init()` 且不调用 `super.init()`，UI 全部自绘）可以零成本继承这一切。接管入口选择 Forge 公开事件 `ScreenEvent.Opening`：把 `newScreen instanceof GunRefitScreen` 且非本模组子类的请求替换掉，即可覆盖所有 `setScreen` 调用点，**无需任何 mixin**。

## Considered Options

- **从零自写 `Screen` + 自写 3D 渲染**：可完全掌控，但需自行组矩阵（可抄 `GunSmithTableScreen.renderLeftModel`）、丢失动画状态机（模型停在静态 pose），且要重新实现取景。否决。
- **mixin `RefitKey` 替换打开调用**：只覆盖 TACZ 自己的按键路径，其它模组直接 `setScreen` 的情况漏掉。否决。

## Consequences

- 子类**不得**调用 `super.init()`，否则 TACZ 原生按钮会叠加到自绘 UI 上。
- 若 TACZ 把 `GunRefitScreen` 改成 `final` 或给构造函数加参数，本方案失效——这是继承带来的耦合，需在升级 TACZ 时优先检查。
- `ServerMessageRefreshRefitScreen` 以 `instanceof GunRefitScreen` 判定并调用 `screen.init()`，子类自动兼容。
- 不需要注册新按键：Z 键仍由 TACZ 的 `RefitKey` 打开，配件锁"锁定则不开界面"的行为被自动继承。
