package com.ztweaks.client;

/**
 * 受控布局参数（见 ADR-0008 第 3 条）。
 *
 * <p>主题只能动这五个数：它们覆盖"密度 / 版式 / 字号"的观感差异，且都能被布局函数安全吸收。
 * 面板宽度、弹层宽度、控件摆放一律固定 —— 那些一改，截字、锚点与命中全部要重算。</p>
 *
 * <p>数值在解析时按区间夹取，主题文件写错不会把界面撑破。</p>
 */
public record ThemeMetrics(float density, float detailLeftRatio, float infoFontScale,
                           int panelPadding, int slotGap) {

    /** 与改造前一致的默认值：480×270 下的现有排版就是按这一组调的。 */
    public static final ThemeMetrics DEFAULT = new ThemeMetrics(1.0f, 0.40f, 0.80f, 6, 6);
}
