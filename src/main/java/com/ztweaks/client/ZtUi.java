package com.ztweaks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ztweaks.config.ZtConfig;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

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
    /** 面板 / 弹出层的内侧高光与暗边、栏间软分隔线（都由主题派生，见 {@link ZtTheme}）。 */
    public static int PANEL_HIGHLIGHT = 0x14FFFFFF;
    public static int PANEL_SHADE = 0x30000000;
    public static int MENU_HIGHLIGHT = 0x18FFFFFF;
    public static int MENU_SHADE = 0x30000000;
    public static int DIVIDER_SOFT = 0x33FFFFFF;

    /** 圆角半径（0 = 直角）与面板质感。 */
    public static int CORNER = 1;
    public static ZtTheme.Surface SURFACE = ZtTheme.Surface.GRADIENT;

    private static ZtTheme current = ThemeRegistry.fallbackTheme();
    private static ThemeMetrics metrics = ThemeMetrics.DEFAULT;
    private static ThemeChrome activeChrome = ThemeChrome.DEFAULT;
    private static ThemeEffect activeEffect = ThemeEffect.NONE;
    private static ThemeBlur activeBlur = ThemeBlur.DISABLED;

    /** 当前帧的模糊纹理（由 ScreenBlur 每 N 帧更新；未就绪时 panel 走原来的半透明底）。 */
    private static ResourceLocation blurTexture;
    private static int blurTexW;
    private static int blurTexH;
    private static int blurScreenW;
    private static int blurScreenH;
    private static boolean blurReady;

    /** 当前主题（按钮显示、弹层打勾用）。 */
    public static ZtTheme theme() {
        return current;
    }

    /** 当前主题的受控布局参数（布局快照的唯一来源）。 */
    public static ThemeMetrics metrics() {
        return metrics;
    }

    /** 当前主题的装饰层（外框 / 标题 / 角饰 / 底纹 / 选中态 / 按钮面）。 */
    public static ThemeChrome chrome() {
        return activeChrome;
    }

    /** 当前主题的氛围动效定义（是否播放由动效档位与自动降级决定）。 */
    public static ThemeEffect effect() {
        return activeEffect;
    }

    /** 当前主题的毛玻璃定义；是否真正启用还要看配置与帧率降级。 */
    public static ThemeBlur blur() {
        return activeBlur;
    }

    /** ScreenBlur 每 N 帧写入一张新的模糊纹理；面板绘制时按屏幕坐标取样。 */
    public static void setBlur(ResourceLocation texture, int texW, int texH, int screenW, int screenH) {
        blurTexture = texture;
        blurTexW = texW;
        blurTexH = texH;
        blurScreenW = screenW;
        blurScreenH = screenH;
        blurReady = texture != null && texW > 0 && texH > 0 && screenW > 0 && screenH > 0;
    }

    public static void clearBlur() {
        blurReady = false;
    }

    /** 切主题过渡到中点时换主题的模糊声明（调色板仍在插值，不能走 refresh）。 */
    public static void setBlurSpec(ThemeBlur value) {
        activeBlur = value;
    }

    // 过渡期的受控写入点：切主题时按 0.5 的切换点换 chrome / effect / metrics，
    // 调色板由 ZtTheme.lerp 每帧 apply。平常只有 refresh() 写这些字段。

    public static void setChrome(ThemeChrome value) {
        activeChrome = value;
    }

    public static void setEffect(ThemeEffect value) {
        activeEffect = value;
    }

    public static void setMetrics(ThemeMetrics value) {
        metrics = value;
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
     * v1 配置里只有**一个**全局的 {@code accent_rgb}，并不是"每主题一份" —— 将来真要那样，
     * 在这里按主题 id 取色即可（那时的注释与 ADR 一起改）。</p>
     */
    public static void refresh() {
        ThemeSpec spec = ThemeRegistry.resolve(ZtConfig.THEME.get());
        ZtTheme theme = spec.theme();
        metrics = spec.metrics();
        activeChrome = spec.chrome();
        activeEffect = spec.effect();
        activeBlur = spec.blur();
        // 强调色只对声明了 accent_editable 的主题生效（目前只有默认主题，见 ADR-0008）
        int override = theme.accentEditable() ? ZtConfig.THEME_ACCENT.get() : -1;
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
        PANEL_HIGHLIGHT = theme.panelHighlight();
        PANEL_SHADE = theme.panelShade();
        MENU_HIGHLIGHT = theme.menuHighlight();
        MENU_SHADE = theme.menuShade();
        DIVIDER_SOFT = theme.dividerSoft();
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

    /**
     * 圆角矩形 + 纵向渐变填充：与 {@link #roundedFill} 同样缺角，左中右三块各自铺同一段渐变，
     * 拼起来看不出接缝。选中行、当前槽位这类"要一点厚度"的小矩形用它。
     */
    public static void roundedFillGradient(GuiGraphics graphics, int x, int y, int w, int h, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(CORNER, Math.min(w, h) / 2));
        if (r == 0) {
            graphics.fillGradient(x, y, x + w, y + h, top, bottom);
            return;
        }
        graphics.fillGradient(x + r, y, x + w - r, y + h, top, bottom);
        graphics.fillGradient(x, y + r, x + r, y + h - r, top, bottom);
        graphics.fillGradient(x + w - r, y + r, x + w, y + h - r, top, bottom);
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
     * 面板标准外观：底色（渐变 / 纯色 / 半透明）+ 圆角 + 内侧高光与暗边 + 描边。全场统一，
     * 各处不再自己调色。
     *
     * <p>内侧那一圈是这一轮加的"厚度"：默认 / MD3 只在上下各压 1px（很淡的环境光），
     * Win 经典四面都压（上下亮、下边暗，银灰浮雕），千禧航空再加一条渐隐高光带当玻璃反光。
     * 全部是画在底色之上的像素，不碰布局、不碰命中。</p>
     */
    public static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        boolean glassBlur = SURFACE == ZtTheme.Surface.GLASS && blurReady;
        if (SURFACE == ZtTheme.Surface.GLASS) {
            shadow(graphics, x, y, w, h);
        }
        if (glassBlur) {
            blurBackdrop(graphics, x, y, w, h);
        }
        if (SURFACE == ZtTheme.Surface.SOLID || PANEL_TOP == PANEL_BOTTOM) {
            roundedFill(graphics, x, y, w, h, PANEL_TOP);
        } else if (glassBlur) {
            // 有模糊底时压薄主题色，让背景透出来；0x8C 是"能看见糊、但字仍清楚"的折中
            roundedFillGradient(graphics, x, y, w, h,
                    ZtTheme.withAlpha(PANEL_TOP, 0x8C), ZtTheme.withAlpha(PANEL_BOTTOM, 0x8C));
        } else {
            // 渐变面板也按主题圆角切角：九宫格外框的圆角要能对得上，不然角上会露出方形底色
            roundedFillGradient(graphics, x, y, w, h, PANEL_TOP, PANEL_BOTTOM);
        }
        int r = Math.max(0, Math.min(CORNER, Math.min(w, h) / 2));
        if (SURFACE == ZtTheme.Surface.GLASS) {
            // 顶部一条渐隐高光带：模糊层之上再加一层反光，玻璃才有厚度
            // 高度封顶 28px —— 面板一高就整片泛白，白字会先糊掉。
            int band = Math.min(28, Math.max(4, h / 3));
            graphics.fillGradient(x + 1, y + 1, x + w - 1, y + band,
                    ZtTheme.withAlpha(0xFFFFFF, 0x26), ZtTheme.withAlpha(0xFFFFFF, 0x00));
        }
        int inset = 1 + r;
        graphics.fill(x + inset, y + 1, x + w - inset, y + 2, PANEL_HIGHLIGHT);
        graphics.fill(x + inset, y + h - 2, x + w - inset, y + h - 1, PANEL_SHADE);
        if (SURFACE == ZtTheme.Surface.SOLID) {
            graphics.fill(x + 1, y + inset, x + 2, y + h - inset, PANEL_HIGHLIGHT);
            graphics.fill(x + w - 2, y + inset, x + w - 1, y + h - inset, PANEL_SHADE);
        }
        pattern(graphics, x, y, w, h);
        frame(graphics, x, y, w, h, activeChrome.frame().panel(), activeChrome.frame().panelSlice(),
                HAIRLINE, PANEL_HIGHLIGHT, PANEL_SHADE);
        corners(graphics, x, y, w, h);
    }

    /**
     * 把屏幕空间的小尺寸模糊纹理按面板矩形取样，铺成玻璃底。
     *
     * <p>模糊纹理由 {@code ScreenBlur} 每 N 帧从主帧缓冲降采样一次；这里只做“从大图里抠一块”
     * 的 UV 映射，不重复模糊。{@code blit} 的 u/v 是纹理像素，uWidth/vHeight 才是取样尺寸。</p>
     */
    private static void blurBackdrop(GuiGraphics graphics, int x, int y, int w, int h) {
        float u = x * (float) blurTexW / blurScreenW;
        float v = y * (float) blurTexH / blurScreenH;
        float uw = w * (float) blurTexW / blurScreenW;
        float vh = h * (float) blurTexH / blurScreenH;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        graphics.blit(blurTexture, x, y, w, h, u, v,
                Math.max(1, Math.round(uw)), Math.max(1, Math.round(vh)), blurTexW, blurTexH);
    }

    /**
     * 面板底纹：横向扫描线 / 细网格，颜色取自重主题的次要文字色，透明度由主题声明。
     * 画在内容之下、边框之上，用 scissor 裁在面板内。
     */
    private static void pattern(GuiGraphics graphics, int x, int y, int w, int h) {
        ThemeChrome.Pattern pattern = activeChrome.pattern();
        if (pattern.style() == ThemeChrome.PatternStyle.NONE || pattern.alpha() <= 0f) {
            return;
        }
        int alpha = Math.max(0, Math.min(255, Math.round(pattern.alpha() * 255f)));
        int line = ZtTheme.withAlpha(TEXT_MUTED, alpha);
        graphics.enableScissor(x + 1, y + 1, x + w - 1, y + h - 1);
        if (pattern.style() == ThemeChrome.PatternStyle.SCANLINE) {
            for (int yy = y + 2; yy < y + h - 2; yy += 4) {
                graphics.fill(x + 1, yy, x + w - 1, yy + 1, line);
            }
        } else {
            for (int yy = y + 2; yy < y + h - 2; yy += 8) {
                graphics.fill(x + 1, yy, x + w - 1, yy + 1, line);
            }
            for (int xx = x + 4; xx < x + w - 2; xx += 8) {
                graphics.fill(xx, y + 1, xx + 1, y + h - 1, line);
            }
        }
        graphics.disableScissor();
    }

    /** 外框：有贴图九宫格就用贴图，否则按样式程序化绘制。 */
    private static void frame(GuiGraphics graphics, int x, int y, int w, int h,
                              ThemeChrome.FrameStyle style, ThemeChrome.NineSlice slice,
                              int border, int highlight, int shade) {
        if (slice != null) {
            graphics.blitNineSliced(slice.texture(), x, y, w, h,
                    slice.left(), slice.top(), slice.right(), slice.bottom(),
                    slice.width(), slice.height(), slice.u(), slice.v());
            return;
        }
        switch (style) {
            case NONE -> {
            }
            case BEVEL -> {
                roundedBorder(graphics, x, y, w, h, border);
                int inset = 2;
                graphics.fill(x + inset, y + inset, x + w - inset, y + inset + 1, highlight);
                graphics.fill(x + inset, y + h - inset - 1, x + w - inset, y + h - inset, shade);
                graphics.fill(x + inset, y + inset, x + inset + 1, y + h - inset, highlight);
                graphics.fill(x + w - inset - 1, y + inset, x + w - inset, y + h - inset, shade);
            }
            case DOUBLE -> {
                roundedBorder(graphics, x, y, w, h, border);
                roundedBorder(graphics, x + 2, y + 2, w - 4, h - 4, ZtTheme.withAlpha(ACCENT, 0x55));
            }
            default -> roundedBorder(graphics, x, y, w, h, border);
        }
    }

    /** 四角 L 形角标：只画短线，不描整圈，避免把小面板挤满。 */
    private static void corners(GuiGraphics graphics, int x, int y, int w, int h) {
        if (activeChrome.corners().style() != ThemeChrome.CornerStyle.BRACKETS) {
            return;
        }
        int len = 6;
        int color = ZtTheme.withAlpha(ACCENT, 0xCC);
        graphics.fill(x, y, x + len, y + 1, color);
        graphics.fill(x, y, x + 1, y + len, color);
        graphics.fill(x + w - len, y, x + w, y + 1, color);
        graphics.fill(x + w - 1, y, x + w, y + len, color);
        graphics.fill(x, y + h - 1, x + len, y + h, color);
        graphics.fill(x, y + h - len, x + 1, y + h, color);
        graphics.fill(x + w - len, y + h - 1, x + w, y + h, color);
        graphics.fill(x + w - 1, y + h - len, x + w, y + h, color);
    }

    /**
     * 浮层投影：两层错位的半透明黑，让弹层 / 弹条从 3D 画面上浮起来。
     * 只画在元素背后，不占布局；画完再由调用方铺自己的底。
     */
    public static void shadow(GuiGraphics graphics, int x, int y, int w, int h) {
        roundedFill(graphics, x + 2, y + 3, w, h, 0x33000000);
        roundedFill(graphics, x + 1, y + 1, w, h, 0x1C000000);
    }

    /**
     * 弹层标准外框：投影 + {@code MENU_BG} 底 + 内侧高光/暗边 + 描边。
     *
     * <p>只负责"外框"这一层 —— 行序列、内容高、命中与逐行绘制仍留在各自的弹层里
     * （三套弹层骨架合一已确认不做，这里不碰那部分）。</p>
     */
    public static void popup(GuiGraphics graphics, int x, int y, int w, int h) {
        shadow(graphics, x, y, w, h);
        roundedFill(graphics, x, y, w, h, MENU_BG);
        if (SURFACE == ZtTheme.Surface.GLASS) {
            int band = Math.min(20, Math.max(4, h / 3));
            graphics.fillGradient(x + 1, y + 1, x + w - 1, y + band,
                    ZtTheme.withAlpha(0xFFFFFF, 0x1A), ZtTheme.withAlpha(0xFFFFFF, 0x00));
        }
        int r = Math.max(0, Math.min(CORNER, Math.min(w, h) / 2));
        int inset = 1 + r;
        graphics.fill(x + inset, y + 1, x + w - inset, y + 2, MENU_HIGHLIGHT);
        graphics.fill(x + inset, y + h - 2, x + w - inset, y + h - 1, MENU_SHADE);
        if (SURFACE == ZtTheme.Surface.SOLID) {
            graphics.fill(x + 1, y + inset, x + 2, y + h - inset, MENU_HIGHLIGHT);
            graphics.fill(x + w - 2, y + inset, x + w - 1, y + h - inset, MENU_SHADE);
        }
        frame(graphics, x, y, w, h, activeChrome.frame().popup(), activeChrome.frame().popupSlice(),
                MENU_BORDER, MENU_HIGHLIGHT, MENU_SHADE);
    }

    /** 在内侧上沿压一条 1px 高光：让"预设 / 主题 / 排序"这类平面小按钮也有一点点凸起感。 */
    public static void innerBevel(GuiGraphics graphics, Rect rect, boolean raised) {
        int r = Math.max(0, Math.min(CORNER, Math.min(rect.w(), rect.h()) / 2));
        graphics.fill(rect.x() + 1 + r, rect.y() + 1, rect.x() + rect.w() - 1 - r, rect.y() + 2,
                ZtTheme.withAlpha(0xFFFFFF, raised ? 0x3A : 0x1C));
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
        if (buttonSlice(graphics, rect, hovered, enabled)) {
            graphics.drawCenteredString(font, label, rect.x() + rect.w() / 2, rect.y() + 4, textColor);
            return;
        }

        ThemeChrome.ButtonStyle style = activeChrome.button().style();
        if (style == ThemeChrome.ButtonStyle.FLAT) {
            int fill = enabled && hovered ? ZtTheme.mix(top, 0xFFFFFFFF, 0.10f) : top;
            roundedFill(graphics, rect.x(), rect.y(), rect.w(), rect.h(), fill);
        } else {
            roundedFillGradient(graphics, rect.x(), rect.y(), rect.w(), rect.h(), top, bottom);
            if (enabled) {
                // 上沿一层高光 = 按键的"受光面"；悬停时更亮，配合底色变化，三态更容易分辨。
                // BEVEL 再把高光提一档，和 FLAT 拉开差别。
                int alpha = style == ThemeChrome.ButtonStyle.BEVEL
                        ? (hovered ? 0x5A : 0x38)
                        : (hovered ? 0x3C : 0x20);
                int r = Math.max(0, Math.min(CORNER, Math.min(rect.w(), rect.h()) / 2));
                graphics.fill(rect.x() + 1 + r, rect.y() + 1, rect.x() + rect.w() - 1 - r,
                        rect.y() + 2, ZtTheme.withAlpha(0xFFFFFF, alpha));
            }
        }
        roundedBorder(graphics, rect.x(), rect.y(), rect.w(), rect.h(), borderColor);
        graphics.drawCenteredString(font, label, rect.x() + rect.w() / 2, rect.y() + 4, textColor);
    }

    /** 按钮面贴图：三态有图就画并返回 true；没有图交给调用方的程序化路径。 */
    private static boolean buttonSlice(GuiGraphics graphics, Rect rect, boolean hovered, boolean enabled) {
        ThemeChrome.Button button = activeChrome.button();
        ThemeChrome.NineSlice face = !enabled ? button.disabled()
                : (hovered ? button.hover() : button.normal());
        if (face == null) {
            return false;
        }
        graphics.blitNineSliced(face.texture(), rect.x(), rect.y(), rect.w(), rect.h(),
                face.left(), face.top(), face.right(), face.bottom(),
                face.width(), face.height(), face.u(), face.v());
        return true;
    }

    /**
     * 中性按钮面（预设 / 主题）：有贴图就套主题的按钮面，没有就沿用原来的徽标底。
     * 只画面、不画字 —— 这两个按钮的内容是自定义的（色块 + 缩写 / 居中文本）。
     */
    public static void neutralButtonFace(GuiGraphics graphics, Rect rect, boolean hovered, boolean enabled) {
        if (buttonSlice(graphics, rect, hovered, enabled)) {
            return;
        }
        roundedFill(graphics, rect.x(), rect.y(), rect.w(), rect.h(),
                enabled && hovered ? BADGE_BG_ACTIVE : BADGE_BG);
        innerBevel(graphics, rect, enabled && hovered);
        roundedBorder(graphics, rect.x(), rect.y(), rect.w(), rect.h(),
                enabled && hovered ? WHITE_OVERLAY : HAIRLINE);
    }
}
