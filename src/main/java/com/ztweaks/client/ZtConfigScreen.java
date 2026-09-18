package com.ztweaks.client;

import com.ztweaks.config.ZtConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.widget.ForgeSlider;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.List;

/**
 * 开发者选项屏：从「选项 → Mod → 本模组 → Config」进入。
 *
 * <p>Forge 1.20.1 只提供入口（{@code ConfigScreenHandler.ConfigScreenFactory}），
 * 不提供任何配置控件 —— TACZ 是靠 Cloth Config 的，我们不想引依赖就自己搭：
 * 布尔/枚举走 {@link CycleButton}，数值走 {@code ForgeSlider} + {@link EditBox}
 * 并排（滑块粗调、输入框填精确值）。</p>
 *
 * <p>为什么没用 {@code OptionsList}：它在 1.20.1 的 {@code addSmall} 只收
 * {@code OptionInstance<?>}，塞不进任意 widget。这里改成自己按行摆放 + 滚动，
 * 行只是"一组 widget"，滚动靠每帧重设 y 并用 {@code visible} 裁掉界外的行。</p>
 *
 * <p>改动即时生效：全项目读取配置都是运行时 {@code .get()}，没有一处把值缓存进
 * {@code static final}。写盘统一放在 {@link #onClose()}。</p>
 */
public final class ZtConfigScreen extends Screen {

    private static final int ROW_H = 24;
    private static final int TOP = 32;
    private static final int BOTTOM_MARGIN = 34;

    private final Screen parent;
    /** 每一行是一组 widget（多数只有 1 个，数值行是滑块 + 输入框）。 */
    private final List<List<AbstractWidget>> rows = new ArrayList<>();
    private int scroll = 0;

    public ZtConfigScreen(Screen parent) {
        super(Component.translatable("gui.z_tweaks.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.rows.clear();
        addHeader("group.refit");
        addEnum("pros_cons_mode", ZtConfig.PROS_CONS_MODE, ZtConfig.ProsConsMode.values());
        addBool("takeover", ZtConfig.TAKEOVER);
        addBool("orbit_camera", ZtConfig.ORBIT_CAMERA);
        addBool("virtual_assembly", ZtConfig.VIRTUAL_ASSEMBLY);

        addHeader("group.debug");
        addBool("hud", ZtConfig.DEBUG_HUD);
        addBool("samples", ZtConfig.DEBUG_SAMPLES);
        addBool("native_bars_key", ZtConfig.DEBUG_NATIVE_BARS);
        addBool("camera_hotkey", ZtConfig.DEBUG_CAMERA_HOTKEY);
        addEnum("pivot_source", ZtConfig.PIVOT_SOURCE, ZtConfig.PivotSource.values());
        addNumber("pivot_offset_y", ZtConfig.PIVOT_OFFSET_Y, -1.0, 1.0, 0.01, 2);
        addNumber("roll_speed", ZtConfig.ROLL_SPEED, 1.0, 1440.0, 5.0, 1);
        addNumber("yaw_speed", ZtConfig.YAW_SPEED, 1.0, 1440.0, 5.0, 1);
        addNumber("pan_speed", ZtConfig.PAN_SPEED, 0.1, 8.0, 0.1, 2);
        addNumber("zoom_step", ZtConfig.ZOOM_STEP, 0.01, 1.0, 0.01, 2);

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(this.width / 2 - 50, this.height - 28, 100, 20).build());
    }

    // ------------------------------------------------------------------ 行

    /** 分组标题：用一个不可点的按钮占位。 */
    private void addHeader(String key) {
        Button header = Button.builder(Component.translatable("gui.z_tweaks.config." + key), b -> {
        }).bounds(0, 0, 310, 20).build();
        header.active = false;
        addRow(header);
    }

    private <T extends Enum<T>> void addEnum(String id, ForgeConfigSpec.EnumValue<T> spec, T[] values) {
        addRow(CycleButton.<T>builder(v -> Component.literal(v.name()))
                .withValues(values)
                .withInitialValue(spec.get())
                .create(0, 0, 310, 20, label(id), (btn, v) -> spec.set(v)));
    }

    private void addBool(String id, ForgeConfigSpec.BooleanValue spec) {
        addRow(CycleButton.onOffBuilder(spec.get())
                .create(0, 0, 310, 20, label(id), (btn, v) -> spec.set(v)));
    }

    /**
     * 数值行：滑块粗调 + 输入框精调。
     *
     * <p>两个方向都写配置值，但输入框不把滑块拖回去 —— 输入过程中会经过空串、
     * 单独的负号、半个小数这些中间态，动滑块会让数字乱跳。</p>
     */
    private void addNumber(String id, ForgeConfigSpec.DoubleValue spec, double min, double max,
                           double step, int precision) {
        EditBox box = new EditBox(this.font, 0, 0, 70, 20, Component.empty());
        box.setMaxLength(16);
        box.setValue(fmt(spec.get()));
        box.setResponder(text -> {
            try {
                spec.set(Mth.clamp(Double.parseDouble(text.trim()), min, max));
            } catch (NumberFormatException ignored) {
                // 还在输入，等玩家输完再算
            }
        });
        ForgeSlider slider = new ForgeSlider(0, 0, 234, 20, label(id), Component.empty(),
                min, max, spec.get(), step, precision, true) {
            @Override
            protected void applyValue() {
                double current = getValue();
                spec.set(current);
                box.setValue(fmt(current));
            }
        };
        addRow(slider, box);
    }

    private void addRow(AbstractWidget... widgets) {
        List<AbstractWidget> group = new ArrayList<>();
        for (AbstractWidget widget : widgets) {
            this.addRenderableWidget(widget);
            group.add(widget);
        }
        this.rows.add(group);
    }

    // ------------------------------------------------------------------ 渲染

    /** 按当前滚动量重排行位置，界外的行直接隐藏（顺带就不会被点到）。 */
    private void layoutRows() {
        int bottom = this.height - BOTTOM_MARGIN;
        int y = TOP - this.scroll;
        int x = this.width / 2 - 155;
        for (List<AbstractWidget> group : this.rows) {
            int gx = x;
            for (AbstractWidget widget : group) {
                widget.setX(gx);
                widget.setY(y);
                widget.visible = y >= TOP - ROW_H && y + widget.getHeight() <= bottom;
                gx += widget.getWidth() + 6;
            }
            y += ROW_H;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        layoutRows();
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 12, 0xFFFFFF);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int bottom = this.height - BOTTOM_MARGIN;
        int max = Math.max(0, this.rows.size() * ROW_H - (bottom - TOP));
        this.scroll = Mth.clamp((int) (this.scroll - delta * ROW_H), 0, max);
        return true;
    }

    @Override
    public void onClose() {
        ZtConfig.SPEC.save();
        this.minecraft.setScreen(this.parent);
    }

    private static Component label(String id) {
        return Component.translatable("gui.z_tweaks.config." + id);
    }

    private static String fmt(double value) {
        return value == (long) value ? Long.toString((long) value) : String.format("%.3f", value);
    }
}
