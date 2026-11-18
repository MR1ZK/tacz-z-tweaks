package com.ztweaks.client;

/**
 * 主题的毛玻璃模糊强度（见 ADR-0009）。
 *
 * <p>{@code strength} 只控制降采样倍率：越大越糊，范围 0.1–1.0。实际采样尺寸还会被
 * 帧缓冲尺寸与最小/最大像素数夹住，避免 4K 下回读过大。</p>
 */
public record ThemeBlur(boolean enabled, float strength) {

    public static final ThemeBlur DISABLED = new ThemeBlur(false, 0f);
}
