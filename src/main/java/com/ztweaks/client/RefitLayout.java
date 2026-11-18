package com.ztweaks.client;

import com.ztweaks.client.ZtUi.Rect;

import java.util.List;

/**
 * 一次布局计算的结果（见 ADR-0008 第 3 条）。
 *
 * <p>只依赖「主题 metrics + 窗口尺寸 + 槽位数量」，在 {@code init()}、窗口尺寸变化与主题切换时
 * 由 {@link ZtRefitScreen#rebuildLayout()} 重算一次。绘制、命中、滚轮都从这里取矩形；候选行与
 * 弹层这类随内容变化的矩形由纯函数从这里的锚点派生，不再各写一套。</p>
 *
 * <p>不可变：字段全是 final，数组 {@code detailColumns} 只读约定（不要原地改）。</p>
 */
public record RefitLayout(
        int width, int height,
        int pad, int slotGap,
        float infoScale,
        int rowH, int menuRowH, int menuSepH,
        int listHeader, int listFooter,
        int slotSize, int slotStep,
        Rect presetButton, Rect themeButton, Rect presetName, Rect presetSave,
        Rect slotBar, List<Rect> slots,
        Rect list, Rect search, Rect sortButton,
        Rect detail, Rect install, Rect unload,
        int detailLeftWidth, int detailColumnWidth, int[] detailColumns) {
}
