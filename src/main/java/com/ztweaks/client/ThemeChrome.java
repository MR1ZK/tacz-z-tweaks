package com.ztweaks.client;

import net.minecraft.resources.ResourceLocation;

/**
 * 主题的装饰层（见 ADR-0008 第 4 条）：外框、标题条、角饰、底纹、选中态、按钮面。
 *
 * <p>每个部件都是**可选**的；JSON 里缺哪一段就用这里的默认值。所有样式都是程序化绘制 ——
 * 贴图 atlas 是下一层的覆盖能力，缺图时仍然回落到这一套，所以主题不会因为美术没跟上而开天窗。</p>
 */
public record ThemeChrome(Frame frame, TitleBar titleBar, Corners corners,
                          Pattern pattern, Selection selection, Button button) {

    /** 整个装饰层的出厂值：与改造前完全一致（外框发丝线、标题强调色、无角饰/底纹、选中竖条、默认按钮）。 */
    public static final ThemeChrome DEFAULT = new ThemeChrome(
            new Frame(FrameStyle.DEFAULT, FrameStyle.DEFAULT, null, null),
            new TitleBar(TitleStyle.ACCENT, null),
            new Corners(CornerStyle.NONE),
            new Pattern(PatternStyle.NONE, 0.08f),
            new Selection(SelectionStyle.BAR),
            new Button(ButtonStyle.DEFAULT, null, null, null));

    /** 面板 / 弹层外框：程序化样式打底，贴图九宫格可选覆盖。 */
    public record Frame(FrameStyle panel, FrameStyle popup, NineSlice panelSlice, NineSlice popupSlice) {
    }

    /**
     * 九宫格贴图：{@code texture} 是 atlas 路径，{@code u/v/width/height} 是源矩形，
     * 四边 insets 是九宫格边距。贴图缺失时由注册表把整个对象置 null，回落到 {@link FrameStyle}。
     */
    public record NineSlice(ResourceLocation texture, int u, int v, int width, int height,
                            int left, int top, int right, int bottom) {
    }

    /** 标题条：可选九宫格贴图铺在标题行，缺失时按 {@code style} 程序化绘制。 */
    public record TitleBar(TitleStyle style, NineSlice slice) {
    }

    public record Corners(CornerStyle style) {
    }

    public record Pattern(PatternStyle style, float alpha) {
    }

    public record Selection(SelectionStyle style) {
    }

    /** 按钮面：三态各自可选九宫格贴图；缺失时按 {@code style} 程序化绘制。 */
    public record Button(ButtonStyle style, NineSlice normal, NineSlice hover, NineSlice disabled) {
    }

    public enum FrameStyle {
        /** 发丝线 + 内侧高光/暗边（改造前的外观）。 */
        DEFAULT,
        /** 更重的立体边框：外框压暗 + 内侧一圈高光，Win 经典那种浮雕。 */
        BEVEL,
        /** 双线：外发丝线 + 内圈强调色细线。 */
        DOUBLE,
        /** 不画边框（只留底色与内侧高光）。 */
        NONE
    }

    public enum TitleStyle {
        /** 只有标题行下沿的分节线（改造前）。 */
        ACCENT,
        /** 整条标题底色带一点强调色。 */
        BANNER,
        /** 不额外画标题底。 */
        NONE
    }

    public enum CornerStyle {
        NONE,
        /** 四角各画一个 L 形强调色角标。 */
        BRACKETS
    }

    public enum PatternStyle {
        NONE,
        /** 横向扫描线。 */
        SCANLINE,
        /** 细网格。 */
        GRID
    }

    public enum SelectionStyle {
        /** 整行渐变 + 左侧竖条（改造前）。 */
        BAR,
        /** 整行更浓的强调色填充。 */
        FILL,
        /** 填充 + 一圈强调色描边。 */
        GLOW
    }

    public enum ButtonStyle {
        /** 渐变 + 上沿受光面（改造前）。 */
        DEFAULT,
        /** 更强的立体边：上沿高光更亮、下沿压暗。 */
        BEVEL,
        /** 平面：单色底 + 发丝线，无渐变。 */
        FLAT
    }
}
