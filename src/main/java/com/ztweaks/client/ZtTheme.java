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
        AERO("aero"),
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

    /** 面板上的暗色衬底（槽位底、暗角）：由面板底色压暗而来，跟着主题走。 */
    public int shadow() {
        return withAlpha(darken(panelBottom, 0.55f), 0x55);
    }

    /** 槽位条上"既不是当前、也没有悬停"的槽位底：比面板再沉一点。 */
    public int slotBg() {
        return withAlpha(0xFF000000, onLightPanel() ? 0x18 : 0x22);
    }

    /** 数量徽标、按钮常态底这类"比面板再沉一档"的底。 */
    public int badgeBg() {
        return withAlpha(0xFF000000, onLightPanel() ? 0x22 : 0x40);
    }

    /** 按钮激活（弹层开着、当前页）的底：深色面板提亮、浅色面板压暗，两边都看得见。 */
    public int badgeBgActive() {
        return onLightPanel() ? withAlpha(0xFF000000, 0x33) : withAlpha(0xFFFFFF, 0x40);
    }

    /** 图标上的灰罩：表示"这一行点不动"。 */
    public int iconDim() {
        return withAlpha(0xFF000000, 0x80);
    }

    /** 滑块之类"跟着强调色走"的标记。 */
    public int accentMark() {
        return withAlpha(accent, 0xAA);
    }

    /** 弹层滚动区上下端的小黑条。 */
    public int menuScrollHint() {
        return withAlpha(0xFF000000, 0x66);
    }

    /** 面板是不是浅色 —— 衬底、徽标这些"压一层"的色要按它选黑还是白。 */
    public boolean onLightPanel() {
        return luminance(panelTop) > 0.55f;
    }

    /** 行间分隔线 / 面板内分割线：比发丝线重一档，保证在纯色面板上也看得见。 */
    public int divider() {
        return withAlpha(luminance(panelTop) > 0.55f ? 0xFF000000 : 0xFFFFFFFF, 0x99);
    }

    /** 悬停时压在白底上的白：选中竖条、滚动条滑块这类"不跟主题走、只表示当前"的标记。 */
    public int whiteOverlay() {
        return onLightPanel() ? withAlpha(0xFF000000, 0x66) : 0x88FFFFFF;
    }

    /** 弹层滚动条，以及"当前项"的标记。 */
    public int mark() {
        return onLightPanel() ? withAlpha(0xFF000000, 0xAA) : 0xAAFFFFFF;
    }

    /**
     * 次要文字（{@code TEXT_DIM} / {@code TEXT_MUTED}）在浅色面板上要往**黑**里走，
     * 而不是往面板色里混 —— 往面板混会把字洗成"跟底差不多亮"，实机反馈就是"字看不清"
     * （Win 经典最明显）。深色面板上原样返回。
     */
    public int textDimInk() {
        return onLightPanel() ? darken(textDim, 0.35f) : textDim;
    }

    public int textMutedInk() {
        return onLightPanel() ? darken(textMuted, 0.45f) : textMuted;
    }

    /**
     * 不可安装的角标 / 提示文字：浅色面板上只**微微**加深。加深多了会把这档从"红"洗成"近黑"，
     * 反而丢掉"这里不能用"的辨识度 —— Win 经典实机反馈就是"颜色不够醒目"。
     */
    public int blockedInk() {
        return onLightPanel() ? darken(blocked, 0.15f) : blocked;
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
        return mix(accent, 0xFFFFFFFF, 0.45f);
    }

    /** 弹条（toast）的四件套：底、描边、正文，外加左侧那条强调色。 */
    public int toastBg() {
        return withAlpha(mix(panelBottom, 0xFFFFFFFF, 0.055f), 0xF0);
    }

    public int toastBorder() {
        return withAlpha(accent, 0xAA);
    }

    /** 弹条描边：浅色面板上要压一条深边，不然一个浅底贴一个浅底等于没有边框。 */
    public int toastEdge() {
        return onLightPanel() ? mix(panelTop, 0xFF000000, 0.35f) : mix(panelTop, 0xFFFFFFFF, 0.28f);
    }

    public int toastText() {
        return text;
    }

    /**
     * 失效槽位（枪不支持该槽位）的边框与文字：深色面板上比不可安装更沉（一眼是"这里没有"）；
     * 浅色面板上边框**直接用这档红本身** —— 红得明确才一眼看得出，压深等于又变回浅灰系。
     */
    public int brokenBorder() {
        int base = blockedInk();
        return onLightPanel() ? base : mix(base, panelBottom, 0.63f);
    }

    public int brokenText() {
        int base = blockedInk();
        return onLightPanel() ? darken(base, 0.25f) : mix(base, panelBottom, 0.38f);
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

    /** 千禧航空（Frutiger Aero）：天空蓝渐变 + 水绿强调 + 泛白高光，圆润。 */
    private static final ZtTheme AERO = new ZtTheme(Id.AERO,
            0xE62A7FB0, 0xE6164A6E, Surface.GLASS, 2, 0x99DFF6FF,
            0xFF7FE3B0, 0xFFF2FBFF, 0xFFCDE9F5, 0xFF9FC4D4,
            0xFF8BE07A, 0xFFFF9A8A, 0xFFC98A8A, 0x22EAF9FF, 0x33FFFFFF,
            0xD92A6E96, 0x99E8FAFF, 0x33FFFFFF);

    /**
     * Win 经典：银灰浮雕面板 + 标题栏藏青，直角、无渐变。
     *
     * <p>这一套的"不可用"用**Windows 错误图标的红**（{@code #E81123}）：银灰底上红褐太糊，
     * 实机反馈是"看不出这个槽位不能用"。其余主题仍用各自的红褐色。</p>
     */
    private static final ZtTheme WIN_CLASSIC = new ZtTheme(Id.WIN_CLASSIC,
            0xFFC0C0C0, 0xFFC0C0C0, Surface.SOLID, 0, 0xFF808080,
            0xFF000080, 0xFF000000, 0xFF303030, 0xFF5A5A5A,
            0xFF008000, 0xFF800000, 0xFFE81123, 0x22000000, 0x22000000,
            0xFFC0C0C0, 0xFF808080, 0x22000000);

    /** 按 id 取主题定义。 */
    public static ZtTheme of(Id id) {
        return switch (id) {
            case DEFAULT -> DEFAULT;
            case MD3 -> MD3;
            case AERO -> AERO;
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
