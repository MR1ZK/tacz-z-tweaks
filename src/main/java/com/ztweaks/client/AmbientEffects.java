package com.ztweaks.client;

import com.ztweaks.client.ZtUi.Rect;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/**
 * 主题氛围动效的 2D 粒子池（见 ADR-0008 第 5 条）。
 *
 * <p>两层：背景层铺满屏幕，画在 3D 画面之上、面板之下；前景层只铺在 3D 预览区，
 * 画在面板之上、菜单/弹条之下。粒子数量有硬上限（背景 64 + 前景 32），每帧只更新位置、
 * 不 new 对象；主题或窗口尺寸变了才重建池子。</p>
 */
public final class AmbientEffects {

    private static final int MAX_BACKGROUND = 64;
    private static final int MAX_FOREGROUND = 32;

    private final Particle[] background = new Particle[MAX_BACKGROUND];
    private final Particle[] foreground = new Particle[MAX_FOREGROUND];
    private final Random random = new Random();

    private int backgroundCount;
    private int foregroundCount;
    private String key = "";
    private long lastNanos;
    private ThemeEffect effect = ThemeEffect.NONE;
    private Rect foregroundArea = new Rect(0, 0, 0, 0);

    /** 每帧更新一次；{@code area} 是前景层允许出现的矩形（3D 预览区）。 */
    public void update(ThemeEffect effect, int width, int height, Rect area) {
        long now = System.nanoTime();
        float delta = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        this.effect = effect;
        this.foregroundArea = area;

        String nextKey = effect.type() + "|" + width + "x" + height + "|" + area;
        if (!nextKey.equals(key)) {
            key = nextKey;
            rebuild(effect, width, height, area);
        }
        if (effect.type() == ThemeEffect.Type.NONE || effect.alpha() <= 0f) {
            return;
        }
        advance(background, backgroundCount, delta, 0, 0, width, height);
        advance(foreground, foregroundCount, delta, area.x(), area.y(), area.w(), area.h());
    }

    public void renderBackground(GuiGraphics graphics) {
        renderLayer(graphics, background, backgroundCount);
    }

    public void renderForeground(GuiGraphics graphics) {
        Rect area = foregroundArea;
        if (area.w() <= 0 || area.h() <= 0) {
            return;
        }
        graphics.enableScissor(area.x(), area.y(), area.x() + area.w(), area.y() + area.h());
        renderLayer(graphics, foreground, foregroundCount);
        graphics.disableScissor();
    }

    private void renderLayer(GuiGraphics graphics, Particle[] particles, int count) {
        if (effect.type() == ThemeEffect.Type.NONE || effect.alpha() <= 0f) {
            return;
        }
        for (int i = 0; i < count; i++) {
            Particle particle = particles[i];
            int alpha = Math.max(0, Math.min(255, Math.round(255f * effect.alpha() * particle.alpha)));
            int color = ZtTheme.withAlpha(effect.color(), alpha);
            int x = Math.round(particle.x);
            int y = Math.round(particle.y);
            switch (effect.type()) {
                case FLOW -> graphics.fill(x, y, x + 1, y + Math.round(particle.size), color);
                case BOKEH -> ZtUi.roundedFill(graphics, x, y, 4, 4, color);
                default -> graphics.fill(x, y, x + 1, y + 1, color);
            }
        }
    }

    private void rebuild(ThemeEffect effect, int width, int height, Rect area) {
        if (effect.type() == ThemeEffect.Type.NONE) {
            backgroundCount = 0;
            foregroundCount = 0;
            return;
        }
        random.setSeed(effect.type().ordinal() * 7919L + width * 31L + height);
        backgroundCount = Math.round(MAX_BACKGROUND * effect.density());
        for (int i = 0; i < backgroundCount; i++) {
            background[i] = spawn(0, 0, width, height, effect.type());
        }
        foregroundCount = Math.round(MAX_FOREGROUND * effect.density() / 3f);
        for (int i = 0; i < foregroundCount; i++) {
            foreground[i] = spawn(area.x(), area.y(), area.w(), area.h(), effect.type());
        }
    }

    private Particle spawn(int left, int top, int width, int height, ThemeEffect.Type type) {
        Particle particle = new Particle();
        particle.x = left + random.nextFloat() * Math.max(1, width);
        particle.y = top + random.nextFloat() * Math.max(1, height);
        particle.alpha = 0.45f + random.nextFloat() * 0.55f;
        switch (type) {
            case FLOW -> {
                particle.vx = -3f + random.nextFloat() * 6f;
                particle.vy = -14f - random.nextFloat() * 16f;
                particle.size = 8f + random.nextFloat() * 14f;
            }
            case BOKEH -> {
                particle.vx = -3f + random.nextFloat() * 6f;
                particle.vy = -2f - random.nextFloat() * 5f;
                particle.size = 4f;
            }
            default -> {
                particle.vx = -5f + random.nextFloat() * 10f;
                particle.vy = -5f - random.nextFloat() * 9f;
                particle.size = 1f;
            }
        }
        return particle;
    }

    private void advance(Particle[] particles, int count, float delta,
                         int left, int top, int width, int height) {
        if (delta <= 0f || width <= 0 || height <= 0) {
            return;
        }
        float speed = effect.speed();
        for (int i = 0; i < count; i++) {
            Particle particle = particles[i];
            particle.x += particle.vx * speed * delta;
            particle.y += particle.vy * speed * delta;
            if (particle.y + particle.size < top) {
                particle.y = top + height + particle.size;
                particle.x = left + random.nextFloat() * width;
            }
            if (particle.x < left - 8f) {
                particle.x = left + width + 8f;
            } else if (particle.x > left + width + 8f) {
                particle.x = left - 8f;
            }
        }
    }

    private static final class Particle {
        private float x;
        private float y;
        private float vx;
        private float vy;
        private float size;
        private float alpha;
    }
}
