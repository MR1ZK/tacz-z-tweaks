package com.ztweaks.client;

/**
 * 主题的氛围动效（见 ADR-0008 第 5 条）。
 *
 * <p>只做 2D 屏幕层的粒子/流光：背景层在 3D 画面与面板之间，前景层只裁在 3D 预览区。
 * 类型是有界的几种，参数只允许密度 / 速度 / 透明度 / 颜色 —— 这不是一套粒子编辑器。</p>
 */
public record ThemeEffect(Type type, int color, float density, float speed, float alpha) {

    public static final ThemeEffect NONE = new ThemeEffect(Type.NONE, 0xFFFFFFFF, 0f, 1f, 0f);

    public enum Type {
        NONE,
        /** 竖向流光：细长条向上漂。 */
        FLOW,
        /** 微粒：1px 小点缓慢上浮。 */
        SPARK,
        /** 光斑：4px 的柔光点，最慢。 */
        BOKEH
    }
}
