package com.ztweaks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.ztweaks.client.OrbitCamera;
import com.ztweaks.config.ZtConfig;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 原型（throwaway）：全项目唯一一处 mixin —— 模型级轨道相机。
 *
 * <p><b>目标 TACZ 版本：1.1.8-hotfix</b>（Forge 1.20.1-47.3.19）。升级 TACZ 时先核对
 * {@code FirstPersonRenderGunEvent.applyFirstPersonPositioningTransform} 的签名与
 * {@code BedrockGunModel} 参数是否还在；改动此处必须同步更新本行版本号。</p>
 *
 * <p>注入点：TACZ 应用改装取景变换之后（{@code @At("RETURN")}），
 * 因此世界不动，只有枪械模型绕自身枢轴旋转。</p>
 *
 * <p>{@code require = 0}：注入失败时静默降级为原生固定视角，
 * 界面其余功能不受影响。这正是本原型要验证的点之一（可用界面上的
 * "mixin hits" 计数直观确认）。</p>
 */
@Mixin(FirstPersonRenderGunEvent.class)
public class FirstPersonRenderGunEventMixin {

    @Inject(method = "applyFirstPersonPositioningTransform", at = @At("RETURN"), remap = false, require = 0)
    private static void ztweaks$applyOrbitCamera(PoseStack poseStack, BedrockGunModel model, ItemStack stack,
                                                 float aimingProgress, float refitScreenOpeningProgress,
                                                 CallbackInfo ci) {
        // 枢轴：ROOT_PIVOT = 根骨骼在模型空间的落点（BedrockPart 坐标单位是基岩像素，/16 换算成格），
        // 它的 x/y 就是枪身长轴所在的高度，滚转绕它转才是"绕枪管自旋"而不是绕外点翻筋斗。
        // ORIGIN = 模型空间原点，即枢轴修复前的行为，留给改前/改后对照。
        float pivotX = 0f;
        float pivotY = 0f;
        if (ZtConfig.PIVOT_SOURCE.get() == ZtConfig.PivotSource.ROOT_PIVOT) {
            BedrockPart root = model.getRootNode();
            if (root != null) {
                pivotX = root.x / 16f;
                pivotY = root.y / 16f;
            }
        }
        pivotY += ZtConfig.PIVOT_OFFSET_Y.get().floatValue();
        OrbitCamera.applyTo(poseStack, pivotX, pivotY);
    }
}
