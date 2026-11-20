package com.ztweaks.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 客户端配置。
 *
 * <p>{@code refit} 段是功能项（默认开）；{@code look} 段是外观（主题与自定义强调色，见 ADR-0007）；
 * {@code debug} 段是原型期的调参与诊断探针，其中布尔项默认全部关闭，数值调参项默认取代码里原本
 * 硬编码的值 —— 需要时在 {@code config/z_tweaks-client.toml} 里手动调整。</p>
 */
public final class ZtConfig {

    public enum ProsConsMode {
        /** 自算 delta：对比"当前枪"与"当前枪+候选配件"两份缓存，逐属性算增减。 */
        DELTA,
        /** 直接复用 TACZ 为配件预拼好的文本（JsonProperty.getComponents）。 */
        TACZ_TEXT
    }

    /** 界面动效档位（见 ADR-0008 第 5 条）。 */
    public enum Motion {
        /** 过渡 + 粒子/流光。 */
        FULL,
        /** 去掉粒子/流光，保留短过渡。 */
        REDUCED,
        /** 全部即时。 */
        OFF
    }

    /** 轨道相机枢轴的取法。 */
    public enum PivotSource {
        /** 根骨骼所在的高度 —— 落在枪身长轴上，观感是"绕枪管自旋"。 */
        ROOT_PIVOT,
        /** 模型空间原点 —— 悬在枪身上方约 1 格，是枢轴修复前的行为，用于改前/改后对照。 */
        ORIGIN
    }

    public static final ModConfigSpec SPEC;

    // ------------------------------------------------------------------ refit
    public static final ModConfigSpec.EnumValue<ProsConsMode> PROS_CONS_MODE;
    /** 是否接管原生改装界面；关掉即回到 TACZ 原生界面（逃生开关）。 */
    public static final ModConfigSpec.BooleanValue TAKEOVER;
    /** 是否启用轨道相机（硬关：关掉后 V 键也开不回来）。 */
    public static final ModConfigSpec.BooleanValue ORBIT_CAMERA;
    /** 是否启用悬停虚拟装配（硬关）。 */
    public static final ModConfigSpec.BooleanValue VIRTUAL_ASSEMBLY;

    // ------------------------------------------------------------------ look
    /**
     * 界面主题 id（见 ADR-0008）。两个入口读写同一项：改装界面左上角的按钮、配置屏那一行。
     * 存字符串而不是枚举：资源包可以新增主题，枚举装不下。旧配置里的枚举名会在读取时映射到新 id。
     */
    public static final ModConfigSpec.ConfigValue<String> THEME;
    /**
     * 默认主题的自定义强调色（0xRRGGBB）。**只对默认主题生效** —— 其余主题的强调色是它调性的
     * 组成部分，随手改会立刻变味。压在强调色上的文字按亮度自动切黑 / 白，所以纯黑也看得清。
     */
    public static final ModConfigSpec.IntValue THEME_ACCENT;
    /** 动效档位：FULL / REDUCED / OFF。运行时自动降级只影响本次界面，不改这个值。 */
    public static final ModConfigSpec.EnumValue<Motion> MOTION;
    /** 玻璃主题的背景模糊；关掉后玻璃面板退回半透明底色。 */
    public static final ModConfigSpec.BooleanValue BLUR;

    // ------------------------------------------------------------------ debug
    /** 左上角诊断 HUD：mixin 命中数、相机读数、枢轴读数、取景进度、字体测试。 */
    public static final ModConfigSpec.BooleanValue DEBUG_HUD;
    /** 对照样本：在详情条里打印 TACZ 成品文本原文（含色码）。 */
    public static final ModConfigSpec.BooleanValue DEBUG_SAMPLES;
    /** G 键：叠加原生 {@code GunPropertyDiagrams} 属性条做对照。 */
    public static final ModConfigSpec.BooleanValue DEBUG_NATIVE_BARS;
    /** V 键：运行时开关轨道相机（关掉即纯原生取景，用于开关对照）。 */
    public static final ModConfigSpec.BooleanValue DEBUG_CAMERA_HOTKEY;
    /** 枢轴取法：{@code ROOT_PIVOT} 贴枪身长轴 / {@code ORIGIN} 修复前的模型原点。 */
    public static final ModConfigSpec.EnumValue<PivotSource> PIVOT_SOURCE;
    /** 枢轴微调：在 {@link #PIVOT_SOURCE} 之上再抬高/降低多少格。 */
    public static final ModConfigSpec.DoubleValue PIVOT_OFFSET_Y;
    /** 上下拖一整屏高对应的滚转角度（度）。 */
    public static final ModConfigSpec.DoubleValue ROLL_SPEED;
    /** 左右拖一整屏宽对应的环绕角度（度）。 */
    public static final ModConfigSpec.DoubleValue YAW_SPEED;
    /** 右键拖一整屏对应的平移距离（格）。 */
    public static final ModConfigSpec.DoubleValue PAN_SPEED;
    /** 滚轮每格对应的缩放增量。 */
    public static final ModConfigSpec.DoubleValue ZOOM_STEP;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
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

        builder.comment("界面外观（见 docs/adr/0008-theme-system-v2.md）").push("look");
        THEME = builder
                .comment("界面主题 id",
                        "内置：default（TACZ 青，可调强调色）/ md3 / aero / win_classic",
                        "资源包可在 assets/<命名空间>/themes/<id>.json 下覆盖或新增主题")
                .define("theme", "default");
        THEME_ACCENT = builder
                .comment("默认主题的自定义强调色（0xRRGGBB，默认取 TACZ HUD 的 0x55FFFF）",
                        "只对默认主题生效；压在强调色上的文字会按亮度自动切黑 / 白")
                .defineInRange("accent_rgb", 0x55FFFF, 0x000000, 0xFFFFFF);
        MOTION = builder
                .comment("界面动效档位",
                        "FULL = 过渡 + 粒子/流光（默认）",
                        "REDUCED = 去掉粒子/流光，保留短过渡",
                        "OFF = 全部即时")
                .defineEnum("motion", Motion.FULL);
        BLUR = builder
                .comment("玻璃主题的毛玻璃背景模糊（千禧航空）",
                        "true = 面板下方采样并模糊当前帧背景（每 8 帧更新一次）",
                        "false = 退回半透明底色，不采样帧缓冲")
                .define("blur", true);
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
