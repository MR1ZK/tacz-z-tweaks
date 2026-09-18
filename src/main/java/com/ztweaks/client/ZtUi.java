package com.ztweaks.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 自绘界面的调色板与绘制原语：从 {@link ZtRefitScreen} 里整块搬出来，画法一字未改。
 *
 * <p>拆出来的理由只有一条：屏幕类里"布局 + 交互 + 3D 相机"已经够满，调色和圆角这种
 * 与改装业务无关的画法混在里面，改一个按钮得在近两千行里来回找。</p>
 *
 * <p>全是无状态静态成员，调用方 {@code import static com.ztweaks.client.ZtUi.*;} 之后按原名
 * 调用，调用点与抽取前逐字一致 —— 只有 {@link #button} 多收一个 {@link Font} 参数，
 * 因为它原先读的是屏幕自己的 {@code font} 字段。</p>
 */
public final class ZtUi {

    private ZtUi() {
    }

    // ------------------------------------------------------------------ 调色板
    // 深色玻璃面板 + TACZ 青强调色：与 TACZ 军事风一致，同时把原来散落各处的魔法色值收拢到一处。
    // 半透明由 GUI 渲染管线正常混合（GUI pass 默认开 blend）。

    /** 面板主体：上浅下深的纵向渐变。 */
    public static final int PANEL_TOP = 0xD2181C21;
    public static final int PANEL_BOTTOM = 0xD20C0E11;
    /** 面板外描边（1px，半透明白发丝线）与强调描边。 */
    public static final int HAIRLINE = 0x38FFFFFF;
    public static final int BORDER_STRONG = 0xFF353B42;
    /**
     * TACZ 青（选中态、标题、焦点）。取的是 TACZ 自己 HUD 里用来标注"虚拟"备弹的 0x55FFFF ——
     * 本模组主打虚拟装配，"虚拟"用 TACZ 认的这个颜色最不违和，也比金色更贴军事科技风。
     */
    public static final int ACCENT = 0xFF55FFFF;
    public static final int ACCENT_SOFT = 0x4055FFFF;
    /** 弹条正文：青的提亮版，保证在深底上依然够亮（原色直接当文字略刺眼）。 */
    public static final int ACCENT_LIGHT = 0xFFBFF7FF;
    /** 文字三级灰阶：正文、次要、失效。 */
    public static final int TEXT = 0xFFE8E8E8;
    public static final int TEXT_DIM = 0xFFA8AEB5;
    public static final int TEXT_MUTED = 0xFF6E7479;
    /** Pros/Cons 沿用 TACZ 的红绿语义，只是降低一点饱和度避免刺眼。 */
    public static final int GOOD = 0xFF5FD96A;
    public static final int BAD = 0xFFFF6E6E;
    /** 悬停行叠加与不可用底色。 */
    public static final int HOVER_OVERLAY = 0x22FFFFFF;
    public static final int TRACK_BG = 0x33FFFFFF;

    /** 屏幕坐标下的轴对齐矩形。绘制与命中检测共用同一份几何，避免两边各写一套。 */
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** 圆角矩形填充：切掉四个角像素。1px 圆角就足够柔和，且不引入任何贴图依赖。 */
    public static void roundedFill(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        graphics.fill(x + 1, y, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** 圆角描边，与 {@link #roundedFill} 配套（同样缺角，否则边框会比填充多出一角）。 */
    public static void roundedBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 1, y, x + w - 1, y + 1, color);
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** 面板标准外观：上浅下深渐变 + 圆角 + 发丝描边。全场统一，各处不再自己调色。 */
    public static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fillGradient(x + 1, y, x + w - 1, y + h, PANEL_TOP, PANEL_BOTTOM);
        graphics.fill(x, y + 1, x + 1, y + h - 1, PANEL_TOP);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, PANEL_BOTTOM);
        roundedBorder(graphics, x, y, w, h, HAIRLINE);
    }

    /**
     * 按钮：常态/悬停/禁用三态。禁用态压暗并去饱和 —— 直接对应"这个动作现在做不了"，
     * 而不是让玩家点了没反应。{@code primary} 是绿=确认、红=取消这套语义。
     */
    public static void button(GuiGraphics graphics, Font font, Rect rect, String label, boolean hovered,
                              boolean primary, boolean enabled) {
        int top;
        int bottom;
        int borderColor;
        int textColor;
        if (!enabled) {
            top = 0x80202428;
            bottom = 0x80161A1D;
            borderColor = 0x40FFFFFF;
            textColor = TEXT_MUTED;
        } else if (hovered) {
            top = primary ? 0xFF2F5F3A : 0xFF6F2F2F;
            bottom = primary ? 0xFF1F3F27 : 0xFF4F1F1F;
            borderColor = primary ? 0xFF3F9F4F : 0xFFB04A4A;
            textColor = 0xFFFFFFFF;
        } else {
            top = primary ? 0xFF24452C : 0xFF4A2424;
            bottom = primary ? 0xFF182E1D : 0xFF311818;
            borderColor = 0x66FFFFFF;
            textColor = TEXT;
        }
        graphics.fillGradient(rect.x() + 1, rect.y(), rect.x() + rect.w() - 1, rect.y() + rect.h(), top, bottom);
        graphics.fill(rect.x(), rect.y() + 1, rect.x() + 1, rect.y() + rect.h() - 1, top);
        graphics.fill(rect.x() + rect.w() - 1, rect.y() + 1, rect.x() + rect.w(), rect.y() + rect.h() - 1, bottom);
        roundedBorder(graphics, rect.x(), rect.y(), rect.w(), rect.h(), borderColor);
        graphics.drawCenteredString(font, label, rect.x() + rect.w() / 2, rect.y() + 4, textColor);
    }
}
