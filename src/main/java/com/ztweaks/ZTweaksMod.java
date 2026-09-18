package com.ztweaks;

import com.ztweaks.client.ZtConfigScreen;
import com.ztweaks.config.ZtConfig;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * TACZ: Z-Tweaks 主类。
 *
 * <p>注册客户端配置与它的配置屏：后者让「选项 → Mod → 本模组 → Config」能打开
 * {@link ZtConfigScreen}。注册必须在 mod loading 阶段（构造函数里），
 * 且 {@code ConfigScreenHandler} 是客户端类，专用服务端上要跳过。</p>
 */
@Mod(ZTweaksMod.MOD_ID)
public class ZTweaksMod {

    public static final String MOD_ID = "z_tweaks";

    public ZTweaksMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ZtConfig.SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory(
                            (minecraft, parent) -> new ZtConfigScreen(parent)));
        }
    }
}
