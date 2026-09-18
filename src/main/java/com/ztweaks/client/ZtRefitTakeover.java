package com.ztweaks.client;

import com.mojang.logging.LogUtils;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.ztweaks.ZTweaksMod;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * 原型（throwaway）：接管原生改装界面。
 *
 * <p>用 {@code ScreenEvent.Opening} 拦截所有 {@code setScreen} 调用点
 * （按键、其它模组、服务端刷新一并接管），无需 mixin。</p>
 */
@Mod.EventBusSubscriber(modid = ZTweaksMod.MOD_ID, value = Dist.CLIENT)
public final class ZtRefitTakeover {

    private static final Logger LOGGER = LogUtils.getLogger();

    private ZtRefitTakeover() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen screen = event.getNewScreen();
        // 只接管"原生"及其它模组的改装界面；自己的子类不再二次接管，避免递归。
        if (screen instanceof GunRefitScreen && !(screen instanceof ZtRefitScreen)) {
            LOGGER.info("[Z-Tweaks] takeover: {} -> ZtRefitScreen", screen.getClass().getName());
            event.setNewScreen(new ZtRefitScreen());
        }
    }
}
