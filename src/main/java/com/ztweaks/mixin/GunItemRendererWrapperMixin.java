package com.ztweaks.mixin;

import com.tacz.guns.client.renderer.item.GunItemRendererWrapper;
import com.ztweaks.client.VirtualAssembly;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 悬停虚拟装配（M2）—— 全项目第二处 mixin（第一处见 {@link FirstPersonRenderGunEventMixin}，
 * 决策与理由见 ADR-0004）。
 *
 * <p><b>目标 TACZ 版本：1.1.8-hotfix</b>（Forge 1.20.1-47.3.19）。升级 TACZ 时先核对
 * {@code GunItemRendererWrapper.renderFirstPerson} 的签名是否仍是
 * {@code (LocalPlayer, ItemStack, ItemDisplayContext, PoseStack, MultiBufferSource, int, float)}；
 * 改动此处必须同步更新本行版本号。</p>
 *
 * <p>注入点：方法入口 {@code @At("HEAD")}，只替换 {@code stack} 这一个参数。
 * {@code @ModifyVariable(..., argsOnly = true)} 按处理器参数类型 {@link ItemStack} 匹配，
 * 该方法只有一个 ItemStack 形参，无歧义。</p>
 *
 * <p>为什么改这里就够了：同一个 {@code stack} 既被交给
 * {@code FirstPersonRenderGunEvent.applyFirstPersonGunTransform}（取景变换），
 * 又被交给 {@code BedrockGunModel.render}（真正画配件，它照 {@code iGun.getAttachment(gunItem, type)}
 * 灌渲染缓存），所以替换入口参数即整条链路生效。</p>
 *
 * <p>{@code require = 0}：注入失败时静默降级 —— 枪照常渲染成真枪，只是没有虚拟装配预览，
 * 界面其余功能不受影响。失效可用界面上的 "虚拟装配：命中" 读数为 0 直观确认。</p>
 */
@Mixin(GunItemRendererWrapper.class)
public class GunItemRendererWrapperMixin {

    @ModifyVariable(method = "renderFirstPerson", at = @At("HEAD"), argsOnly = true, remap = false, require = 0)
    private ItemStack ztweaks$virtualAssembly(ItemStack stack) {
        return VirtualAssembly.previewStack(stack);
    }
}
