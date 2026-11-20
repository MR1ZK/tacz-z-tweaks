package com.ztweaks.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.nio.ByteBuffer;

/**
 * 屏幕空间毛玻璃：GPU 降采样 + 小图回读 + 动态纹理（见 ADR-0009）。
 *
 * <p>不写 shader：每 {@link #INTERVAL} 帧把主帧缓冲 blit 到一张很小的 FBO（GL_LINEAR 降采样），
 * 再只回读这张小图（约 100–300px 宽），转成 {@link DynamicTexture} 交给面板按屏幕坐标取样。
 * 面板绘制时只做一次纹理采样，不做逐像素模糊。</p>
 *
 * <p>模糊的是**当前帧、面板之前**的世界/枪画面；面板在之后绘制，所以玻璃底下是模糊的实时背景，
 * 面板外仍是清晰的 3D。回读与上传都在渲染线程，且被帧率降级闸门挡住。</p>
 */
public final class ScreenBlur {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation TEXTURE_ID =
            ResourceLocation.fromNamespaceAndPath("z_tweaks", "dynamic/glass_blur");
    /** 回读间隔：8 帧约 7–8 次/秒；对静态背景足够，也避免每帧 glReadPixels 卡住管线。 */
    private static final int INTERVAL = 8;

    private int framebuffer;
    private int framebufferTexture;
    private int frameWidth;
    private int frameHeight;
    private int blurWidth;
    private int blurHeight;
    private int screenWidth;
    private int screenHeight;
    private int frameCounter;

    private DynamicTexture texture;
    private ByteBuffer buffer;
    private boolean ready;
    private boolean disabled;

    /**
     * 每帧调用一次；{@code enabled} 为 false 时不采样（并清掉上一帧的模糊纹理引用）。
     *
     * @param strength 0.1–1.0，越大降采样越狠、越糊
     */
    public void update(Minecraft minecraft, int guiWidth, int guiHeight, float strength, boolean enabled) {
        if (!enabled || disabled) {
            ZtUi.clearBlur();
            return;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main == null || main.width <= 0 || main.height <= 0) {
            ZtUi.clearBlur();
            return;
        }
        screenWidth = Math.max(1, guiWidth);
        screenHeight = Math.max(1, guiHeight);
        if (framebuffer == -1 || main.width != frameWidth || main.height != frameHeight) {
            rebuild(minecraft, main, guiWidth, guiHeight, strength);
        }
        if (texture == null) {
            ZtUi.clearBlur();
            return;
        }
        // 重建后的第一帧就采样；之后每 INTERVAL 帧更新一次
        if (ready && frameCounter % INTERVAL != 0) {
            frameCounter++;
            ZtUi.setBlur(TEXTURE_ID, blurWidth, blurHeight, screenWidth, screenHeight);
            return;
        }
        capture(main);
        ready = true;
        frameCounter++;
        ZtUi.setBlur(TEXTURE_ID, blurWidth, blurHeight, screenWidth, screenHeight);
    }

    private void rebuild(Minecraft minecraft, RenderTarget main, int guiWidth, int guiHeight, float strength) {
        close();
        frameWidth = main.width;
        frameHeight = main.height;
        screenWidth = Math.max(1, guiWidth);
        screenHeight = Math.max(1, guiHeight);

        int factor = Math.max(4, Math.min(12, Math.round(4f + strength * 6f)));
        blurWidth = Math.max(96, Math.min(320, frameWidth / factor));
        blurHeight = Math.max(1, Math.round(blurWidth * (frameHeight / (float) frameWidth)));

        framebufferTexture = TextureUtil.generateTextureId();
        TextureUtil.prepareImage(framebufferTexture, blurWidth, blurHeight);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(framebufferTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        com.mojang.blaze3d.platform.GlStateManager._bindTexture(0);

        framebuffer = com.mojang.blaze3d.platform.GlStateManager.glGenFramebuffers();
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        com.mojang.blaze3d.platform.GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,
                GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, framebufferTexture, 0);
        int status = com.mojang.blaze3d.platform.GlStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, main.frameBufferId);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            LOGGER.warn("Glass blur framebuffer is incomplete (status 0x{}), disabling blur",
                    Integer.toHexString(status));
            disabled = true;
            close();
            return;
        }

        TextureManager textureManager = minecraft.getTextureManager();
        textureManager.release(TEXTURE_ID);
        texture = new DynamicTexture(blurWidth, blurHeight, false);
        textureManager.register(TEXTURE_ID, texture);

        buffer = ByteBuffer.allocateDirect(blurWidth * blurHeight * 4);
        frameCounter = 0;
        ready = false;
        ZtUi.clearBlur();
    }

    private void capture(RenderTarget main) {
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);

        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,
                main.frameBufferId);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
        com.mojang.blaze3d.platform.GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height,
                0, 0, blurWidth, blurHeight, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_LINEAR);

        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer);
        GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
        buffer.clear();
        GL11.glReadPixels(0, 0, blurWidth, blurHeight, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 4);

        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        com.mojang.blaze3d.platform.GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);

        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }
        for (int y = 0; y < blurHeight; y++) {
            int sourceY = blurHeight - 1 - y;
            for (int x = 0; x < blurWidth; x++) {
                int index = (sourceY * blurWidth + x) * 4;
                int r = buffer.get(index) & 0xFF;
                int g = buffer.get(index + 1) & 0xFF;
                int b = buffer.get(index + 2) & 0xFF;
                int a = buffer.get(index + 3) & 0xFF;
                // NativeImage.setPixelRGBA 要 ABGR，而 glReadPixels 给的是 RGBA
                pixels.setPixelRGBA(x, y, a << 24 | b << 16 | g << 8 | r);
            }
        }
        texture.upload();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** 屏幕关闭 / 重建前释放纹理与 FBO；不动全局 RenderSystem 状态。 */
    public void close() {
        ZtUi.clearBlur();
        if (texture != null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE_ID);
            texture = null;
        }
        if (framebuffer != -1) {
            com.mojang.blaze3d.platform.GlStateManager._glDeleteFramebuffers(framebuffer);
            framebuffer = -1;
        }
        if (framebufferTexture != -1) {
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(0);
            GL11.glDeleteTextures(framebufferTexture);
            framebufferTexture = -1;
        }
        buffer = null;
        frameWidth = 0;
        frameHeight = 0;
        ready = false;
        frameCounter = 0;
    }
}
