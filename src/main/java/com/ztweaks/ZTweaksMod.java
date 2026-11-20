package com.ztweaks;

import com.ztweaks.client.ZtConfigScreen;
import com.ztweaks.config.ZtConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * TACZ: Z-Tweaks 主类。
 *
 * <p>注册客户端配置与它的配置屏：后者让「选项 → Mod → 本模组 → Config」能打开
 * {@link ZtConfigScreen}。NeoForge 1.21 用 {@link ModContainer#registerConfig} 与
 * {@link IConfigScreenFactory}，不再有 Forge 的 ConfigScreenHandler。</p>
 */
@Mod(ZTweaksMod.MOD_ID)
public class ZTweaksMod {

    public static final String MOD_ID = "z_tweaks";

    public ZTweaksMod(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ZtConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            container.registerExtensionPoint(IConfigScreenFactory.class,
                    (modContainer, parent) -> new ZtConfigScreen(parent));
        }
    }
}
