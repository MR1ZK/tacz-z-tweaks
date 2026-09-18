package com.ztweaks.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 客户端配置。
 *
 * <p>{@code refit} 段是功能项；{@code debug} 段是原型期的诊断探针，
 * **默认全部关闭**——需要时在 {@code config/z_tweaks-client.toml} 里手动打开。</p>
 */
public final class ZtConfig {

    public enum ProsConsMode {
        /** 自算 delta：对比"当前枪"与"当前枪+候选配件"两份缓存，逐属性算增减。 */
        DELTA,
        /** 直接复用 TACZ 为配件预拼好的文本（JsonProperty.getComponents）。 */
        TACZ_TEXT
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<ProsConsMode> PROS_CONS_MODE;

    /** 左上角诊断 HUD：mixin 命中数、相机读数、取景进度、字体测试。 */
    public static final ForgeConfigSpec.BooleanValue DEBUG_HUD;
    /** 对照样本：在详情条里打印 TACZ 成品文本原文（含色码）。 */
    public static final ForgeConfigSpec.BooleanValue DEBUG_SAMPLES;
    /** G 键：叠加原生 {@code GunPropertyDiagrams} 属性条做对照。 */
    public static final ForgeConfigSpec.BooleanValue DEBUG_NATIVE_BARS;
    /** V 键：运行时开关轨道相机（关掉即纯原生取景，用于开关对照）。 */
    public static final ForgeConfigSpec.BooleanValue DEBUG_CAMERA_HOTKEY;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("改装界面").push("refit");
        PROS_CONS_MODE = builder
                .comment("候选配件 Pros/Cons 的生成方式",
                        "DELTA = 自算该配件装到当前枪上带来的增减（默认）",
                        "TACZ_TEXT = 直接复用 TACZ 预拼好的成品文本")
                .defineEnum("pros_cons_mode", ProsConsMode.DELTA);
        builder.pop();

        builder.comment("原型期的诊断探针：默认关闭，排查问题时再逐项打开").push("debug");
        DEBUG_HUD = builder
                .comment("左上角诊断 HUD（mixin 命中数 / 相机读数 / 取景进度 / 字体测试）")
                .define("hud", false);
        DEBUG_SAMPLES = builder
                .comment("详情条里的对照样本：TACZ 成品文本原文（含色码）")
                .define("samples", false);
        DEBUG_NATIVE_BARS = builder
                .comment("启用 G 键：叠加原生 GunPropertyDiagrams 属性条做对照")
                .define("native_bars_key", false);
        DEBUG_CAMERA_HOTKEY = builder
                .comment("启用 V 键：运行时开关轨道相机（关掉即纯原生取景，用于开关对照）")
                .define("camera_hotkey", false);
        builder.pop();

        SPEC = builder.build();
    }

    private ZtConfig() {
    }
}
