package com.ztweaks;

import net.minecraftforge.fml.common.Mod;

/**
 * TACZ: Z-Tweaks 主类。
 *
 * <p>当前为 M0 骨架：仅建立 mod 入口。后续将在此注册配置（M1）、
 * 客户端界面接管 mixin 的初始化与日志降级处理。</p>
 */
@Mod(ZTweaksMod.MOD_ID)
public class ZTweaksMod {

    public static final String MOD_ID = "z_tweaks";

    public ZTweaksMod() {
        // M0：无注册内容
    }
}
