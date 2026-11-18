package com.ztweaks.client;

import net.minecraft.network.chat.Component;

/**
 * 一套主题的调色板与质感取值（见 ADR-0008）。
 *
 * <p><b>数据来自资源</b>：内置主题是 {@code assets/z_tweaks/themes/<id>.json}，由
 * {@link ThemeRegistry} 解析；资源包可以覆盖或新增。这个 record 只承载"已经解析好的一组值"，
 * 派生量（强调色的柔和版 / 提亮版、压在其上的文字色、悬停叠加…）仍由 {@link #accentSoft()}
 * 这类方法算出来，主题表因此能保持十几行的长度。</p>
 *
 * <p><b>布局受控</b>：行高、面板宽、字号、内边距不在这里 —— 它们只允许通过
 * {@link ThemeMetrics} 的五个参数调整，且由唯一的布局函数消费。颜色/质感随便换，
 * 命中判定的几何只有一套。</p>
 *
 * <p><b>"神似"而非考据</b>：关键色取近似值，一眼能认出风格即可，不抄字体与图标；名字走
 * lang key（{@code gui.z_tweaks.theme.name.<id>}），不内联在 JSON 里。</p>
 */
public record ZtTheme(String id,
                      // ---- 身份与本地化
                      String nameKey, String shortNameKey, int order, boolean accentEditable,
                      // ---- 面板：底色 / 质感 / 圆角 / 描边
                      int panelTop, int panelBottom, Surface surface, int corner, int border,
                      // ---- 强调色（出厂值）与三级文字灰阶
                      int accent, int text, int textDim, int textMuted,
                      // ---- 语义色与控件底
                      int good, int bad, int blocked, int hoverOverlay, int controlBg,
                      // ---- 弹层 / 菜单
                      int menuBg, int menuBorder, int menuHover) {

    /**
     * 面板质感。
     *
     * <p>只有三档，因为 1.20.1 的 GUI 画不出背景模糊：所谓"玻璃"只能靠半透明 + 高光描边 + 渐变
     * 仿真（见 ADR-0008 第 4 条）。</p>
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

    /** 栏与栏之间的软分隔线：只用来"分栏"，比 {@link #divider()} 淡得多，不抢内容。 */
    public int dividerSoft() {
        return withAlpha(onLightPanel() ? 0xFF000000 : 0xFFFFFFFF, 0x33);
    }

    /**
     * 面板内侧上沿的高光。
     *
     * <p>深色主题是很淡的一层白（模拟环境光），浅色主题（Win 经典）给真正的立体高光 ——
     * 那一套的"银灰浮雕"全靠上亮下暗这一圈，只换底色是出不来的。</p>
     */
    public int panelHighlight() {
        return onLightPanel() ? mix(panelTop, 0xFFFFFFFF, 0.60f) : withAlpha(0xFFFFFF, 0x14);
    }

    /** 面板内侧下沿的暗边，与 {@link #panelHighlight()} 配对，给平面板一点厚度。 */
    public int panelShade() {
        return onLightPanel() ? mix(panelBottom, 0xFF000000, 0.35f) : withAlpha(0x000000, 0x30);
    }

    /** 弹出层的上沿高光：底是 {@code menuBg}，不一定等于面板色，所以单独算一份。 */
    public int menuHighlight() {
        return onLightPanel() ? mix(menuBg, 0xFFFFFFFF, 0.55f) : withAlpha(0xFFFFFF, 0x18);
    }

    /** 弹出层的下沿暗边。 */
    public int menuShade() {
        return onLightPanel() ? mix(menuBg, 0xFF000000, 0.30f) : withAlpha(0x000000, 0x30);
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

    /**
     * 上下暗角（3D 预览区压暗用）：由面板底压暗而来 —— 四套主题原先共用同一个常量，
     * 与 ADR-0007 第 7 条"暗角也要进主题"不符。它压的是 3D 画面不是 UI，所以永远偏暗。
     */
    public int vignette() {
        return withAlpha(darken(panelBottom, 0.72f), 0x99);
    }

    /** 诊断 HUD 的底衬：同上，压暗面板底。 */
    public int hudBg() {
        return withAlpha(darken(panelBottom, 0.55f), 0x90);
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

    // ------------------------------------------------------------------ 按钮三态
    // 语义按钮（绿=确认 / 红=取消）的三态底色由"面板底 + 语义色"混出来，跟着主题走。
    // 原先这十几个色值写死在绘制里，浅色主题下按钮仍是深色 —— ADR-0007 第 3 条要求全部收拢。

    public int buttonTop(int semantic, boolean hovered) {
        return mix(panelTop, semantic, hovered ? 0.42f : 0.30f);
    }

    public int buttonBottom(int semantic, boolean hovered) {
        return mix(panelBottom, semantic, hovered ? 0.42f : 0.30f);
    }

    public int buttonBorder(int semantic, boolean hovered) {
        return hovered ? mix(semantic, 0xFFFFFFFF, 0.25f) : withAlpha(0xFFFFFF, 0x66);
    }

    /** 按钮上的文字：浅色面板上用深墨，深色面板上用白。 */
    public int buttonText() {
        return onLightPanel() ? 0xFF10131A : 0xFFFFFFFF;
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

    /**
     * 两套主题之间的调色板插值（切主题的 180ms 过渡用）。
     *
     * <p>身份字段取目标主题；质感与圆角在 t=0.5 处切换（与 chrome 的切换点一致）；
     * 所有颜色按 ARGB 四通道插值，半透明面板不会在过渡中变成不透明。</p>
     */
    public static ZtTheme lerp(ZtTheme from, ZtTheme to, float t) {
        float clamped = Math.max(0f, Math.min(1f, t));
        boolean second = clamped >= 0.5f;
        return new ZtTheme(to.id, to.nameKey, to.shortNameKey, to.order, to.accentEditable,
                lerpArgb(from.panelTop, to.panelTop, clamped),
                lerpArgb(from.panelBottom, to.panelBottom, clamped),
                second ? to.surface : from.surface,
                second ? to.corner : from.corner,
                lerpArgb(from.border, to.border, clamped),
                lerpArgb(from.accent, to.accent, clamped),
                lerpArgb(from.text, to.text, clamped),
                lerpArgb(from.textDim, to.textDim, clamped),
                lerpArgb(from.textMuted, to.textMuted, clamped),
                lerpArgb(from.good, to.good, clamped),
                lerpArgb(from.bad, to.bad, clamped),
                lerpArgb(from.blocked, to.blocked, clamped),
                lerpArgb(from.hoverOverlay, to.hoverOverlay, clamped),
                lerpArgb(from.controlBg, to.controlBg, clamped),
                lerpArgb(from.menuBg, to.menuBg, clamped),
                lerpArgb(from.menuBorder, to.menuBorder, clamped),
                lerpArgb(from.menuHover, to.menuHover, clamped));
    }

    static int lerpArgb(int from, int to, float t) {
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * t);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** 主题名（lang key 中英各一份）。 */
    public Component displayName() {
        return Component.translatable(nameKey);
    }

    /** 弹层里按钮上的缩写：默认主题给"默认"，其余给一行短名。 */
    public Component shortName() {
        return Component.translatable(shortNameKey);
    }
}
