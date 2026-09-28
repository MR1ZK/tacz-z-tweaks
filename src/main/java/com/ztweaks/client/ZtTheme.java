package com.ztweaks.client;

import net.minecraft.network.chat.Component;

/**
 * 一套界面外观的取值：颜色 + 圆角 + 面板质感（见 ADR-0007）。
 *
 * <p><b>主题表写在代码里</b>，不引入外部主题文件 —— 玩家要的是"点一下换个样子"，不是一套主题协议。
 * 每个主题只填"原始色"，派生量（强调色的柔和版 / 提亮版、压在其上的文字色、悬停叠加…）由
 * {@link #accentSoft()} 这类方法算出来，主题表因此能保持十几行的长度。</p>
 *
 * <p><b>不改布局</b>：行高、面板宽、字号、内边距全局一套 —— 那些数字同时参与绘制、裁剪与命中检测，
 * 主题化等于每个主题一套点击判定。</p>
 *
 * <p><b>"神似"而非考据</b>：关键色取近似值，一眼能认出风格即可，不抄字体与图标；名字用风格描述
 * 而不是厂商名（lang key 见 {@code gui.z_tweaks.theme.name.*}）。</p>
 */
public record ZtTheme(Id id,
                      // ---- 面板：底色 / 质感 / 圆角 / 描边
                      int panelTop, int panelBottom, Surface surface, int corner, int border,
                      // ---- 强调色（出厂值）与三级文字灰阶
                      int accent, int text, int textDim, int textMuted,
                      // ---- 语义色与控件底
                      int good, int bad, int blocked, int hoverOverlay, int controlBg,
                      // ---- 弹层 / 菜单
                      int menuBg, int menuBorder, int menuHover) {

    /** 主题标识。取值同时是配置项的值（{@code defineEnum}）与 lang key 的后半段。 */
    public enum Id {
        DEFAULT("default"),
        MD3("md3"),
        FLUENT("fluent"),
        LIQUID_GLASS("liquid_glass"),
        AERO("aero"),
        MAC("mac"),
        WIN_CLASSIC("win_classic");

        private final String key;

        Id(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }

        /** 配置里存的枚举名反查主题；认不出来一律回默认主题。 */
        public static Id fromName(String name) {
            for (Id value : values()) {
                if (value.name().equals(name)) {
                    return value;
                }
            }
            return DEFAULT;
        }
    }

    /**
     * 面板质感。
     *
     * <p>只有三档，因为 1.20.1 的 GUI 画不出背景模糊：所谓"玻璃"只能靠半透明 + 高光描边 + 渐变
     * 仿真（见 ADR-0007 第 1 条）。</p>
     */
    public enum Surface {
        /** 上浅下深的纵向渐变（默认主题的现有外观）。 */
        GRADIENT,
        /** 纯色平面 + 描边（Win 经典那种）。 */
        SOLID,
        /** 半透明 + 高光描边（亚克力 / 玻璃 / aero）。 */
        GLASS
    }

    // ------------------------------------------------------------------ 派生量
    // 主题表里不填这些：改法只可能"整套一起变"（比如强调色变淡），不该由每个主题各写一份。

    /** 强调色的柔和版（选中行底色）。 */
    public int accentSoft() {
        return withAlpha(accent, 0x40);
    }

    /** 强调色的提亮版：强调色直接当正文太刺眼，文字用这一档。 */
    public int accentLight() {
        return mix(accent, 0xFFFFFFFF, 0.72f);
    }

    /** 压在强调色上的文字：按亮度自动切黑 / 白，所以玩家把强调色设成 #000000 也还看得清。 */
    public int onAccent() {
        return luminance(accent) > 0.55f ? 0xFF11141A : 0xFFFFFFFF;
    }

    /** 面板上的暗色衬底（槽位底、滚动条槽、暗角）：由面板底色压暗而来，跟着主题走。 */
    public int shadow() {
        return withAlpha(darken(panelBottom, 0.55f), 0x88);
    }

    /** 行间分隔线 / 面板内分割线：比发丝线重一档，保证在纯色面板上也看得见。 */
    public int divider() {
        return withAlpha(luminance(panelTop) > 0.55f ? 0xFF000000 : 0xFFFFFFFF, 0x99);
    }

    /** 悬停时压在白底上的白：选中竖条、滚动条滑块这类"不跟主题走、只表示当前"的标记。 */
    public int whiteOverlay() {
        return 0x88FFFFFF;
    }

    /** 弹层滚动条，以及"当前项"的勾选标记。 */
    public int mark() {
        return 0xAAFFFFFF;
    }

    /** 上下暗角（3D 预览区压暗用）。 */
    public int vignette() {
        return 0x99000000;
    }

    /** 诊断 HUD 的底衬与文字。 */
    public int hudBg() {
        return 0x90000000;
    }

    public int hudText() {
        return accentLight();
    }

    /** 弹条（toast）的三件套。 */
    public int toastBg() {
        return withAlpha(mix(panelBottom, 0xFF000000, 0.35f), 0xF0);
    }

    public int toastBorder() {
        return withAlpha(accent, 0xAA);
    }

    public int toastText() {
        return text;
    }

    /** 失效槽位（枪不支持该槽位）的边框与文字：比不可安装更沉，一眼是"这里没有"。 */
    public int brokenBorder() {
        return mix(blocked, panelBottom, 0.45f);
    }

    public int brokenText() {
        return mix(blocked, panelBottom, 0.25f);
    }

    // ------------------------------------------------------------------ 颜色小工具

    static int withAlpha(int rgb, int alpha) {
        return (alpha << 24) | (rgb & 0xFFFFFF);
    }

    static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int r = Math.round(ar + (br - ar) * t);
        int g = Math.round(ag + (bg - ag) * t);
        int bl = Math.round(ab + (bb - ab) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    static int darken(int rgb, float factor) {
        return mix(rgb, 0xFF000000, factor);
    }

    /** 感知亮度（0–1），用来决定"压在它上面的字该黑还是白"。 */
    static float luminance(int rgb) {
        float r = (rgb >> 16 & 0xFF) / 255f;
        float g = (rgb >> 8 & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        return 0.2126f * r + 0.7152f * g + 0.0722f * b;
    }

    // ------------------------------------------------------------------ 主题表
    // 顺序即弹层里的顺序：从现代到复古。

    /** 默认（TACZ 青）：就是抽取主题之前的现有外观，出厂强调色取 TACZ HUD 的 0x55FFFF。 */
    private static final ZtTheme DEFAULT = new ZtTheme(Id.DEFAULT,
            0xD2181C21, 0xD20C0E11, Surface.GRADIENT, 1, 0x38FFFFFF,
            0xFF55FFFF, 0xFFE8E8E8, 0xFFA8AEB5, 0xFF6E7479,
            0xFF5FD96A, 0xFFFF6E6E, 0xFF8A5A5A, 0x22FFFFFF, 0x33FFFFFF,
            0xF0101010, 0x88FFFFFF, 0x20FFFFFF);

    /** MD3 质感：中性深底 + 淡紫强调，圆角略大、动效感靠色阶层次做。 */
    private static final ZtTheme MD3 = new ZtTheme(Id.MD3,
            0xFF1F1D24, 0xFF141318, Surface.GRADIENT, 2, 0x30FFFFFF,
            0xFFD0BCFF, 0xFFE6E1E5, 0xFFCAC4D0, 0xFF938F99,
            0xFF7CD98A, 0xFFFF8A8A, 0xFFAF8A8A, 0x1FFFFFFF, 0x2FFFFFFF,
            0xFF211F26, 0x55FFFFFF, 0x1FFFFFFF);

    /** Fluent 亚克力：半透明深灰 + Windows 蓝，细描边走高光。 */
    private static final ZtTheme FLUENT = new ZtTheme(Id.FLUENT,
            0xCC232323, 0xCC181818, Surface.GLASS, 1, 0x33FFFFFF,
            0xFF60CDFF, 0xFFFFFFFF, 0xFFC8C8C8, 0xFF8C8C8C,
            0xFF6CCB5F, 0xFFFF99A4, 0xFFB08A8A, 0x18FFFFFF, 0x28FFFFFF,
            0xCC1F1F1F, 0x55FFFFFF, 0x1CFFFFFF);

    /** 液态玻璃：浅色半透明面板 + iOS 蓝，文字翻成深色（面板整体提亮）。 */
    private static final ZtTheme LIQUID_GLASS = new ZtTheme(Id.LIQUID_GLASS,
            0x88FFFFFF, 0x55E8ECF2, Surface.GLASS, 2, 0xAAFFFFFF,
            0xFF0A84FF, 0xFF14161A, 0xFF3A3F46, 0xFF6B7178,
            0xFF1E9E4A, 0xFFC63B3B, 0xFF9A5A5A, 0x1F000000, 0x22000000,
            0x99FFFFFF, 0xAAFFFFFF, 0x22000000);

    /** 千禧航空（Frutiger Aero）：天空蓝渐变 + 水绿强调 + 泛白高光，圆润。 */
    private static final ZtTheme AERO = new ZtTheme(Id.AERO,
            0xE62A7FB0, 0xE6164A6E, Surface.GLASS, 2, 0x99DFF6FF,
            0xFF7FE3B0, 0xFFF2FBFF, 0xFFCDE9F5, 0xFF9FC4D4,
            0xFF8BE07A, 0xFFFF9A8A, 0xFFC98A8A, 0x22EAF9FF, 0x33FFFFFF,
            0xD92A6E96, 0x99E8FAFF, 0x33FFFFFF);

    /** mac 风格：深色毛玻璃 + 系统蓝，灰阶偏冷。 */
    private static final ZtTheme MAC = new ZtTheme(Id.MAC,
            0xCC2C2C2E, 0xCC1E1E20, Surface.GLASS, 2, 0x2EFFFFFF,
            0xFF0A84FF, 0xFFF5F5F7, 0xFFAEAEB2, 0xFF7C7C80,
            0xFF30D158, 0xFFFF453A, 0xFFB07A7A, 0x1AFFFFFF, 0x26FFFFFF,
            0xCC262628, 0x44FFFFFF, 0x1AFFFFFF);

    /** Win 经典：银灰浮雕面板 + 标题栏藏青，直角、无渐变。 */
    private static final ZtTheme WIN_CLASSIC = new ZtTheme(Id.WIN_CLASSIC,
            0xFFC0C0C0, 0xFFC0C0C0, Surface.SOLID, 0, 0xFF808080,
            0xFF000080, 0xFF000000, 0xFF303030, 0xFF5A5A5A,
            0xFF008000, 0xFF800000, 0xFF7A4A4A, 0x22000000, 0x22000000,
            0xFFC0C0C0, 0xFF808080, 0x22000000);

    /** 按 id 取主题定义。 */
    public static ZtTheme of(Id id) {
        return switch (id) {
            case DEFAULT -> DEFAULT;
            case MD3 -> MD3;
            case FLUENT -> FLUENT;
            case LIQUID_GLASS -> LIQUID_GLASS;
            case AERO -> AERO;
            case MAC -> MAC;
            case WIN_CLASSIC -> WIN_CLASSIC;
        };
    }

    /** 主题名（lang key 中英各一份）。 */
    public Component displayName() {
        return Component.translatable("gui.z_tweaks.theme.name." + id.key());
    }

    /** 弹层里按钮上的缩写：默认主题给"默认"，其余给一行短名。 */
    public Component shortName() {
        return Component.translatable("gui.z_tweaks.theme.short." + id.key());
    }
}
