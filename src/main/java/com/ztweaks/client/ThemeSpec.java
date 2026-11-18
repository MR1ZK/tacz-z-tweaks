package com.ztweaks.client;

/**
 * 一套主题的完整数据：调色板（{@link ZtTheme}）+ 受控布局参数 + 装饰层（{@link ThemeChrome}）
 * + 氛围动效（{@link ThemeEffect}）。
 */
public record ThemeSpec(ZtTheme theme, ThemeMetrics metrics, ThemeChrome chrome, ThemeEffect effect) {
}
