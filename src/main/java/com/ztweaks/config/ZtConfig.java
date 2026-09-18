package com.ztweaks.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 客户端配置。
 *
 * <p>{@code refit} 段是功能项（默认开）；{@code debug} 段是原型期的调参与诊断探针，
 * 其中布尔项默认全部关闭，数值调参项默认取代码里原本硬编码的值 —— 需要时在
 * {@code config/z_tweaks-client.toml} 里手动调整。</p>
 */
public final class ZtConfig {

    public enum ProsConsMode {
        /** 自算 delta：对比"当前枪"与"当前枪+候选配件"两份缓存，逐属性算增减。 */
        DELTA,
        /** 直接复用 TACZ 为配件预拼好的文本（JsonProperty.getComponents）。 */
        TACZ_TEXT
    }

    /** 轨道相机枢轴的取法。 */
    public enum PivotSource {
        /** 根骨骼所在的高度 —— 落在枪身长轴上，观感是"绕枪管自旋"。 */
        ROOT_PIVOT,
        /** 模型空间原点 —— 悬在枪身上方约 1 格，是枢轴修复前的行为，用于改前/改后对照。 */
        ORIGIN
    }

    public static final ForgeConfigSpec SPEC;

    // ------------------------------------------------------------------ refit
    public static final ForgeConfigSpec.EnumValue<ProsConsMode> PROS_CONS_MODE;
    /** 是否接管原生改装界面；关掉即回到 TACZ 原生界面（逃生开关）。 */
    public static final ForgeConfigSpec.BooleanValue TAKEOVER;
    /** 是否启用轨道相机（硬关：关掉后 V 键也开不回来）。 */
    public static final ForgeConfigSpec.BooleanValue ORBIT_CAMERA;
    /** 是否启用悬停虚拟装配（硬关）。 */
    public static final ForgeConfigSpec.BooleanValue VIRTUAL_ASSEMBLY;

    // ------------------------------------------------------------------ debug
    /** 左上角诊断 HUD：mixin 命中数、相机读数、枢轴读数、取景进度、字体测试。 */
    public static final ForgeConfigSpec.BooleanValue DEBUG_HUD;
    /** 对照样本：在详情条里打印 TACZ 成品文本原文（含色码）。 */
    public static final ForgeConfigSpec.BooleanValue DEBUG_SAMPLES;
    /** G 键：叠加原生 {@code GunPropertyDiagrams} 属性条做对照。 */
    public static final ForgeConfigSpec.BooleanValue DEBUG_NATIVE_BARS;
    /** V 键：运行时开关轨道相机（关掉即纯原生取景，用于开关对照）。 */
    public static final ForgeConfigSpec.BooleanValue DEBUG_CAMERA_HOTKEY;
    /** 枢轴取法：{@code ROOT_PIVOT} 贴枪身长轴 / {@code ORIGIN} 修复前的模型原点。 */
    public static final ForgeConfigSpec.EnumValue<PivotSource> PIVOT_SOURCE;
    /** 枢轴微调：在 {@link #PIVOT_SOURCE} 之上再抬高/降低多少格。 */
    public static final ForgeConfigSpec.DoubleValue PIVOT_OFFSET_Y;
    /** 上下拖一整屏高对应的滚转角度（度）。 */
    public static final ForgeConfigSpec.DoubleValue ROLL_SPEED;
    /** 左右拖一整屏宽对应的环绕角度（度）。 */
    public static final ForgeConfigSpec.DoubleValue YAW_SPEED;
    /** 右键拖一整屏对应的平移距离（格）。 */
    public static final ForgeConfigSpec.DoubleValue PAN_SPEED;
    /** 滚轮每格对应的缩放增量。 */
    public static final ForgeConfigSpec.DoubleValue ZOOM_STEP;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("改装界面").push("refit");
        PROS_CONS_MODE = builder
                .comment("候选配件 Pros/Cons 的生成方式",
                        "DELTA = 自算该配件装到当前枪上带来的增减（默认）",
                        "TACZ_TEXT = 直接复用 TACZ 预拼好的成品文本")
                .defineEnum("pros_cons_mode", ProsConsMode.DELTA);
        TAKEOVER = builder
                .comment("接管 TACZ 原生改装界面",
                        "false = 打开改装界面时仍是原生 GunRefitScreen（逃生开关）")
                .define("takeover", true);
        ORBIT_CAMERA = builder
                .comment("启用轨道相机（拖拽旋转 / 滚轮缩放）",
                        "false = 完全不注入变换，V 键也无法临时开启")
                .define("orbit_camera", true);
        VIRTUAL_ASSEMBLY = builder
                .comment("启用悬停虚拟装配（鼠标划过候选配件即在预览里试装）",
                        "false = 预览始终是手上的真枪")
                .define("virtual_assembly", true);
        builder.pop();

        builder.comment("原型期的调参与诊断探针：布尔项默认关闭，数值项默认取代码里的原值").push("debug");
        DEBUG_HUD = builder
                .comment("左上角诊断 HUD（mixin 命中数 / 相机读数 / 枢轴读数 / 取景进度 / 字体测试）")
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
        PIVOT_SOURCE = builder
                .comment("轨道相机的枢轴取法",
                        "ROOT_PIVOT = 根骨骼所在高度，落在枪身长轴上（默认，绕枪管自旋）",
                        "ORIGIN = 模型空间原点，悬在枪身上方约 1 格（修复前的行为，用于对照）")
                .defineEnum("pivot_source", PivotSource.ROOT_PIVOT);
        PIVOT_OFFSET_Y = builder
                .comment("枢轴微调：在 pivot_source 之上再抬高/降低多少格（正=抬高）")
                .defineInRange("pivot_offset_y", 0.0, -1.0, 1.0);
        ROLL_SPEED = builder
                .comment("上下拖一整屏高对应的滚转角度（度）")
                .defineInRange("roll_speed", 360.0, 1.0, 1440.0);
        YAW_SPEED = builder
                .comment("左右拖一整屏宽对应的环绕角度（度）")
                .defineInRange("yaw_speed", 360.0, 1.0, 1440.0);
        PAN_SPEED = builder
                .comment("右键拖一整屏对应的平移距离（格）")
                .defineInRange("pan_speed", 1.0, 0.1, 8.0);
        ZOOM_STEP = builder
                .comment("滚轮每格对应的缩放增量")
                .defineInRange("zoom_step", 0.15, 0.01, 1.0);
        builder.pop();

        SPEC = builder.build();
    }

    private ZtConfig() {
    }
}
