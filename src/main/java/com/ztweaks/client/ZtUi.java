package com.ztweaks.client;

import com.ztweaks.config.ZtConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 自绘界面的调色板与绘制原语。
 *
 * <p><b>调色板是"活动值"</b>（见 ADR-0007 第 3 条）：这里的字段不是编译期常量，而是"当前主题"的
 * 一份展开，由 {@link #refresh()} 一个地方写入 —— 全项目只有它能改这些字段，其它地方一律只读。
 * 调用方 {@code import static com.ztweaks.client.ZtUi.*;} 之后按原名使用，主题化之前的调用点
 * 一行都不用改。</p>
 *
 * <p>几何参数（{@link #CORNER}、{@link #SURFACE}）也跟着主题走，但**布局**（行高、面板宽、字号、
 * 内边距）不跟 —— 那些数字同时参与绘制、裁剪与命中检测，主题化等于每个主题一套点击判定。</p>
 *
 * <p>面板与圆角都是纯像素画法，不引入任何贴图依赖；半透明由 GUI 渲染管线正常混合（GUI pass 默认
 * 开 blend）。1.20.1 的 GUI 没有背景模糊，所以"玻璃"质感只能靠半透明 + 高光描边 + 渐变仿真。</p>
 */
public final class ZtUi {

    private ZtUi() {
    }

    // ------------------------------------------------------------------ 活动调色板
    // 字段名与主题化之前一致，值是当前主题的一份展开；只在 apply(...) 里写入。

    /** 面板主体（上浅下深）。 */
    public static int PANEL_TOP = 0xD2181C21;
    public static int PANEL_BOTTOM = 0xD20C0E11;
    /** 面板外描边（发丝线）与强调描边。 */
    public static int HAIRLINE = 0x38FFFFFF;
    public static int BORDER_STRONG = 0xFF353B42;
    /**
     * 强调色（选中态、标题、焦点）。默认主题的出厂值取 TACZ HUD 里标注"虚拟"备弹的 0x55FFFF ——
     * 本模组主打虚拟装配，"虚拟"用 TACZ 认的那个颜色最不违和。
     */
    public static int ACCENT = 0xFF55FFFF;
    public static int ACCENT_SOFT = 0x4055FFFF;
    /** 弹条正文那一档：强调色的提亮版（强调了当正文太刺眼）。 */
    public static int ACCENT_LIGHT = 0xFFBFF7FF;
    /** 压在强调色上的文字：按强调色亮度自动切黑 / 白。 */
    public static int ON_ACCENT = 0xFF11141A;
    /** 文字三级灰阶：正文、次要、失效。 */
    public static int TEXT = 0xFFE8E8E8;
    public static int TEXT_DIM = 0xFFA8AEB5;
    public static int TEXT_MUTED = 0xFF6E7479;
    /** Pros/Cons 的红绿语义（不随主题的调性走，只随主题的深浅微调）。 */
    public static int GOOD = 0xFF5FD96A;
    public static int BAD = 0xFFFF6E6E;
    /**
     * 不可安装（{@code Compat} 非 OK）的角标与提示文字：与"不支持的槽位"同一族的红褐，
     * 一眼是"这里不行"，而不是"次要信息"。
     */
    public static int BLOCKED = 0xFF8A5A5A;
    /** 悬停行叠加与不可用底色、滚动条槽。 */
    public static int HOVER_OVERLAY = 0x22FFFFFF;
    public static int TRACK_BG = 0x33FFFFFF;
    /** 暗色衬底（槽位底、暗角）与行间分隔线。 */
    public static int SHADOW = 0x88000000;
    public static int DIVIDER = 0x99FFFFFF;
    /** 弹层三件套：底、描边、悬停行。 */
    public static int MENU_BG = 0xF0101010;
    public static int MENU_BORDER = 0x88FFFFFF;
    public static int MENU_HOVER = 0x20FFFFFF;
    /** "当前项"标记（勾选、滚动条滑块）与悬停白条。 */
    public static int MENU_MARK = 0xAAFFFFFF;
    public static int WHITE_OVERLAY = 0x88FFFFFF;
    /** 3D 预览区的上下暗角。 */
    public static int VIGNETTE = 0x99000000;
    /** 诊断 HUD 的底衬与文字。 */
    public static int HUD_BG = 0x90000000;
    public static int HUD_TEXT = 0xFF7FE7FF;
    /** 弹条（toast）三件套。 */
    public static int TOAST_BG = 0xF0121720;
    public static int TOAST_BORDER = 0xAA55FFFF;
    public static int TOAST_TEXT = 0xFFE8E8E8;
    /** 失效槽位（枪不支持）的边框与文字。 */
    public static int BROKEN_BORDER = 0xFF3A2424;
    public static int BROKEN_TEXT = 0xFF5A3A3A;
    /** 槽位底、徽标底与按钮底、按钮激活底（浅色面板会换成压暗的那一档）。 */
    public static int SLOT_BG = 0x22000000;
    public static int BADGE_BG = 0x40000000;
    public static int BADGE_BG_ACTIVE = 0x40FFFFFF;
    /** 图标上的灰罩（行点不动）、强调色滑块、弹层滚动提示条、弹条描边。 */
    public static int ICON_DIM = 0x80000000;
    public static int ACCENT_MARK = 0xAA55FFFF;
    public static int MENU_SCROLL_HINT = 0x66000000;
    public static int TOAST_EDGE = 0xFF4A5158;

    /** 圆角半径（0 = 直角）与面板质感。 */
    public static int CORNER = 1;
    public static ZtTheme.Surface SURFACE = ZtTheme.Surface.GRADIENT;

    private static ZtTheme current = ZtTheme.of(ZtTheme.Id.DEFAULT);

    /** 当前主题（按钮显示、弹层打勾用）。 */
    public static ZtTheme theme() {
        return current;
    }

    /** 圆角半径的别名，读起来更顺（{@code ZtUi.corner()}）。 */
    public static int corner() {
        return CORNER;
    }

    /**
     * 从配置读当前主题与自定义强调色，重算整份调色板。**唯一的写入点**：模组初始化、进改装界面、
     * 配置屏改动后各调一次。
     *
     * <p>自定义强调色只对默认主题生效（其余主题的强调色是它调性的组成部分，见 ADR-0007 第 5 条）；
     * 存储结构是"每主题一份"的通用形态，将来放开只需改这一行判断。</p>
     */
    public static void refresh() {
        ZtTheme.Id id = ZtConfig.THEME.get();
        ZtTheme theme = ZtTheme.of(id);
        int override = id == ZtTheme.Id.DEFAULT ? ZtConfig.THEME_ACCENT.get() : -1;
        apply(theme, override);
    }

    /** 把一套主题展开成活动调色板。{@code accentRgb} 为 -1 表示用主题的出厂强调色。 */
    public static void apply(ZtTheme theme, int accentRgb) {
        current = theme;
        int accent = accentRgb < 0 ? theme.accent() : 0xFF000000 | (accentRgb & 0xFFFFFF);
        PANEL_TOP = theme.panelTop();
        PANEL_BOTTOM = theme.panelBottom();
        HAIRLINE = theme.border();
        BORDER_STRONG = theme.surface() == ZtTheme.Surface.SOLID
                ? ZtTheme.darken(theme.border(), 0.35f) : theme.border();
        ACCENT = accent;
        ACCENT_SOFT = ZtTheme.withAlpha(accent, 0x40);
        ACCENT_LIGHT = ZtTheme.mix(accent, 0xFFFFFFFF, 0.72f);
        ON_ACCENT = theme.onAccent();
        TEXT = theme.text();
        // 次要 / 失效两档按面板深浅换算法：浅色面板上往黑里走，否则字会被洗成"跟底差不多亮"
        TEXT_DIM = theme.textDimInk();
        TEXT_MUTED = theme.textMutedInk();
        GOOD = theme.good();
        BAD = theme.bad();
        BLOCKED = theme.blockedInk();
        HOVER_OVERLAY = theme.hoverOverlay();
        TRACK_BG = theme.controlBg();
        SHADOW = theme.shadow();
        DIVIDER = theme.divider();
        MENU_BG = theme.menuBg();
        MENU_BORDER = theme.menuBorder();
        MENU_HOVER = theme.menuHover();
        MENU_MARK = theme.mark();
        WHITE_OVERLAY = theme.whiteOverlay();
        VIGNETTE = theme.vignette();
        HUD_BG = theme.hudBg();
        HUD_TEXT = theme.hudText();
        TOAST_BG = theme.toastBg();
        TOAST_BORDER = theme.toastBorder();
        TOAST_TEXT = theme.toastText();
        BROKEN_BORDER = theme.brokenBorder();
        BROKEN_TEXT = theme.brokenText();
        SLOT_BG = theme.slotBg();
        BADGE_BG = theme.badgeBg();
        BADGE_BG_ACTIVE = theme.badgeBgActive();
        ICON_DIM = theme.iconDim();
        ACCENT_MARK = theme.accentMark();
        MENU_SCROLL_HINT = theme.menuScrollHint();
        TOAST_EDGE = theme.toastEdge();
        CORNER = theme.corner();
        SURFACE = theme.surface();
    }

    /** 屏幕坐标下的轴对齐矩形。绘制与命中检测共用同一份几何，避免两边各写一套。 */
    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /**
     * 圆角矩形填充：切掉四个角。半径来自当前主题（Win 经典是 0，也就是直角）。
     * 1–2px 的切角就足够柔和，且不引入任何贴图依赖。
     */
    public static void roundedFill(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        int r = Math.max(0, Math.min(CORNER, Math.min(w, h) / 2));
        if (r == 0) {
            graphics.fill(x, y, x + w, y + h, color);
            return;
        }
        graphics.fill(x + r, y, x + w - r, y + h, color);
        graphics.fill(x, y + r, x + r, y + h - r, color);
        graphics.fill(x + w - r, y + r, x + w, y + h - r, color);
    }

    /** 圆角描边，与 {@link #roundedFill} 配套（同样缺角，否则边框会比填充多出一角）。 */
    public static void roundedBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        int r = Math.max(0, Math.min(CORNER, Math.min(w, h) / 2));
        if (r > 1) {
            // 半径大于 1 时，角上多出来的那一小段用逐行收窄来处理，保证边框跟着圆角走。
            for (int i = 0; i < r; i++) {
                int inset = r - i;
                graphics.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
                graphics.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
            }
            graphics.fill(x, y + r, x + 1, y + h - r, color);
            graphics.fill(x + w - 1, y + r, x + w, y + h - r, color);
            return;
        }
        graphics.fill(x + 1, y, x + w - 1, y + 1, color);
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /**
     * 面板标准外观：底色（渐变 / 纯色 / 半透明）+ 圆角 + 描边。全场统一，各处不再自己调色。
     */
    public static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        if (SURFACE == ZtTheme.Surface.SOLID || PANEL_TOP == PANEL_BOTTOM) {
            roundedFill(graphics, x, y, w, h, PANEL_TOP);
        } else {
            graphics.fillGradient(x + 1, y, x + w - 1, y + h, PANEL_TOP, PANEL_BOTTOM);
            graphics.fill(x, y + 1, x + 1, y + h - 1, PANEL_TOP);
            graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, PANEL_BOTTOM);
        }
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
            top = ZtTheme.withAlpha(PANEL_TOP, 0x80);
            bottom = ZtTheme.withAlpha(PANEL_BOTTOM, 0x80);
            borderColor = ZtTheme.withAlpha(HAIRLINE, 0x40);
            textColor = TEXT_MUTED;
        } else {
            // 绿=确认 / 红=取消是语义，但**深浅**必须跟着主题走：这十来个色值原先写死在这里，
            // 切成浅色主题后按钮还是深底（ADR-0007 第 3 条，review 也点了这一条）。
            int semantic = primary ? GOOD : BAD;
            top = current.buttonTop(semantic, hovered);
            bottom = current.buttonBottom(semantic, hovered);
            borderColor = current.buttonBorder(semantic, hovered);
            textColor = hovered ? current.buttonText() : TEXT;
        }
        graphics.fillGradient(rect.x() + 1, rect.y(), rect.x() + rect.w() - 1, rect.y() + rect.h(), top, bottom);
        graphics.fill(rect.x(), rect.y() + 1, rect.x() + 1, rect.y() + rect.h() - 1, top);
        graphics.fill(rect.x() + rect.w() - 1, rect.y() + 1, rect.x() + rect.w(), rect.y() + rect.h() - 1, bottom);
        roundedBorder(graphics, rect.x(), rect.y(), rect.w(), rect.h(), borderColor);
        graphics.drawCenteredString(font, label, rect.x() + rect.w() / 2, rect.y() + 4, textColor);
    }
}
