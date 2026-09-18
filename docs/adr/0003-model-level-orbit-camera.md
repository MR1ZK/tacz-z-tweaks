# v1 的预览增强采用"模型级轨道相机"（唯一一处 mixin）

"更好的武器预览"要求在改装界面对枪械做拖拽旋转与滚轮缩放。TACZ 没有提供可包裹其预览渲染的钩子，可选介入点只有两类：**相机级**（`ViewportEvent.ComputeCameraAngles` 叠加偏航/俯仰）会让背景世界一同旋转，需要额外绘制不透明背景遮盖；**模型级**（mixin 注入 `FirstPersonRenderGunEvent.applyFirstPersonPositioningTransform`，在 TACZ 应用改装取景变换之后追加轨道矩阵）只旋转枪械模型，世界保持静止，观感才是"绕枪看"。选择模型级，代价是全项目唯一一处 mixin。

## Consequences

- 该 mixin 设为非致命（`require = 0`）：注入失败时预览退化为 TACZ 原生固定视角，界面其余功能不受影响——这是"不锁定 TACZ 版本"策略下的必要保险。
- 缩放若走模型级矩阵，与瞄具倍率等功能不冲突；不再需要 `ViewportEvent.ComputeFov`。
- 若未来该注入点消失（TACZ 重构渲染链），降级路径是回到相机级方案，无需改动界面层。
