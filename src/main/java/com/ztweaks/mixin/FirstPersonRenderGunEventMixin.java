package com.ztweaks.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.event.FirstPersonRenderGunEvent;
import com.tacz.guns.client.model.BedrockGunModel;
import com.ztweaks.client.OrbitCamera;
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
        OrbitCamera.applyTo(poseStack);
    }
}
