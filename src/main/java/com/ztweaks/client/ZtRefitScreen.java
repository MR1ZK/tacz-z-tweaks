package com.ztweaks.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.config.sync.SyncConfig;
import com.tacz.guns.resource.pojo.data.gun.ExplosionData;
import com.tacz.guns.util.AttachmentDataUtils;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.GunPropertyDiagrams;
import com.tacz.guns.client.gui.components.refit.HSVSliderGroup;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.client.sound.SoundPlayManager;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.tacz.guns.sound.SoundManager;
import com.ztweaks.config.ZtConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 原型（throwaway）：接管后的改装界面。
 *
 * <p>三个待验证的问题各自对应本文件的一块：
 * 布局（三层）、轨道相机（操作 + 诊断读数）、Pros/Cons 文本（复用 TACZ 成品文本）。</p>
 *
 * <p>刻意不调用 {@code super.init()}：原生按钮会叠加到自绘 UI 上（见 ADR-0002）。</p>
 *
 * <p>M2：候选列表的悬停行驱动 {@link VirtualAssembly} 做虚拟装配预览 —— 只换渲染件、
 * 不碰手持物品，原理与护栏见该类注释与 ADR-0004。</p>
 */
public class ZtRefitScreen extends GunRefitScreen {

    private static final int SLOT = 22;
    private static final int ROW_H = 19;
    private static final int DETAIL_H = 68;
    private static final int PAD = 6;
    /** 候选面板宽度与内部两栏高度：绘制与命中检测共用 {@link #listRect()}，不再各写一份。 */
    private static final int LIST_W = 190;
    private static final int LIST_HEADER = 15;
    /** 候选面板底部预留给搜索框 + 排序按钮这一行的高度（原"滚轮=翻页"提示行只有 11px，放不下输入框）。 */
    private static final int LIST_FOOTER = 17;
    private static final int SEARCH_H = 12;
    /** 搜索框右侧的排序按钮宽度，以及它与搜索框之间的缝。 */
    private static final int SORT_W = 58;
    private static final int SORT_GAP = 4;
    /** 双击阈值，与 MC 原生 AbstractContainerScreen 一致。 */
    private static final long DOUBLE_CLICK_MS = 250L;
    /**
     * 概览态信息卡：三列（名字+描述 / 参数 / 补充），各列独立滚动。
     *
     * <p>文字按 0.8 缩放绘制（MC 字体是位图，缩放后略糊，换来一屏多放两行）。
     * 行距 11 是缩放后的局部单位，实际约 8.8px；按钮顶边在 {@code detailY()+52}，
     * 所以每列最多 5 行。</p>
     */
    private static final int INFO_COLUMNS = 3;
    private static final int INFO_ROWS = 5;
    private static final int INFO_LINE_H = 11;
    private static final float INFO_SCALE = 0.8f;
    /** 第一列（名字 + 描述）要宽一些，折行最吃宽度。 */
    private static final float[] INFO_COLUMN_SHARE = {1.5f, 1.0f, 1.0f};
    /** 与 TACZ 的 ClientGunTooltip 同款格式器（带 % 后缀 = 自动乘 100，传小数比例进去）。 */
    private static final DecimalFormat DAMAGE_FORMAT = new DecimalFormat("#.##");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("#.##%");
    private static final DecimalFormat PERCENT_1_FORMAT = new DecimalFormat("#.#%");

    // ------------------------------------------------------------------ 调色板
    // 深色玻璃面板 + TACZ 青强调色：与 TACZ 军事风一致，同时把原来散落各处的魔法色值收拢到一处。
    // 半透明由 GUI 渲染管线正常混合（GUI pass 默认开 blend）。

    /** 面板主体：上浅下深的纵向渐变。 */
    private static final int PANEL_TOP = 0xD2181C21;
    private static final int PANEL_BOTTOM = 0xD20C0E11;
    /** 面板外描边（1px，半透明白发丝线）与强调描边。 */
    private static final int HAIRLINE = 0x38FFFFFF;
    private static final int BORDER_STRONG = 0xFF353B42;
    /**
     * TACZ 青（选中态、标题、焦点）。取的是 TACZ 自己 HUD 里用来标注"虚拟"备弹的 0x55FFFF ——
     * 本模组主打虚拟装配，"虚拟"用 TACZ 认的这个颜色最不违和，也比金色更贴军事科技风。
     */
    private static final int ACCENT = 0xFF55FFFF;
    private static final int ACCENT_SOFT = 0x4055FFFF;
    /** 弹条正文：青的提亮版，保证在深底上依然够亮（原色直接当文字略刺眼）。 */
    private static final int ACCENT_LIGHT = 0xFFBFF7FF;
    /** 文字三级灰阶：正文、次要、失效。 */
    private static final int TEXT = 0xFFE8E8E8;
    private static final int TEXT_DIM = 0xFFA8AEB5;
    private static final int TEXT_MUTED = 0xFF6E7479;
    /** Pros/Cons 沿用 TACZ 的红绿语义，只是降低一点饱和度避免刺眼。 */
    private static final int GOOD = 0xFF5FD96A;
    private static final int BAD = 0xFFFF6E6E;
    /** 悬停行叠加与不可用底色。 */
    private static final int HOVER_OVERLAY = 0x22FFFFFF;
    private static final int TRACK_BG = 0x33FFFFFF;

    // 本帧待渲染的 tooltip：后画的悬浮层必须在所有面板之后才不会被盖住，
    // 所以绘制阶段只登记，最后由 render() 末尾统一 flush。
    private Component pendingTooltip = null;
    private int tooltipX = 0;
    private int tooltipY = 0;

    private final List<ItemStack> candidates = new ArrayList<>();
    private final List<Integer> candidateInvSlots = new ArrayList<>();

    private final List<String> pros = new ArrayList<>();
    private final List<String> cons = new ArrayList<>();
    private final List<String> neutral = new ArrayList<>();
    private final List<String> samples = new ArrayList<>();

    private AttachmentType cachedType = null;
    private int selected = 0;
    private int scroll = 0;
    /** 本帧鼠标悬停的候选行（-1 = 没有）。每帧由 {@link #drawCandidateList} 重算。 */
    private int hoveredRow = -1;
    private boolean dragging = false;
    private int dragButton = -1;
    private boolean showNativeBars = false;
    private boolean samplesDirty = true;

    /** 候选面板底部的搜索框（概览态随候选框一起隐藏）。 */
    @Nullable
    private EditBox searchBox = null;
    /** 当前搜索词。切槽位时清空：换槽位就是换一批配件，旧关键词大概率不适用。 */
    private String searchQuery = "";

    /**
     * 候选列表排序偏好：按名称还是按模组、正序还是倒序。
     * 切槽位<b>不</b>清空 —— 排序是玩家的全局习惯，不是某个槽位的属性（与搜索词相反）。
     */
    private SortField sortField = SortField.NAME;
    private boolean sortAscending = true;

    /** 上一次点击候选条目的时间戳 / 下标 / 鼠标键，用于双击判定。 */
    private long lastRowClickTime = 0L;
    private int lastRowClickIndex = -1;
    private int lastRowClickButton = -1;

    /** 信息卡三列：名字+描述 / 参数 / 补充（第三列暂空，留给以后加数据）。 */
    private final List<Component> infoMain = new ArrayList<>();
    private final List<Component> infoStats = new ArrayList<>();
    private final List<Component> infoExtra = new ArrayList<>();
    /** 各列独立滚动的偏移量，以及上一帧算出的各列行数（用来夹取偏移）。 */
    private final int[] infoScroll = new int[INFO_COLUMNS];
    private int[] infoColumnLines = new int[INFO_COLUMNS];
    /** 指针当前悬停在哪一列（每帧由 {@link #drawGunInfo} 记下，供滚轮决定滚哪一列）。 */
    private int infoHoverColumn = -1;
    /** 配件描述列的滚动偏移，以及"当前是哪个配件"的键（换配件就回到顶部）。 */
    private int attachScroll = 0;
    @Nullable
    private String attachKey = null;
    /** 缓存键：AttachmentDataUtils 是离线全量重算，不能每帧调，按枪 id + NBT 缓存。 */
    @Nullable
    private ResourceLocation gunInfoId = null;
    @Nullable
    private CompoundTag gunInfoTag = null;

    private String popup = "";
    private long popupUntil = 0;

    public ZtRefitScreen() {
        super();
    }

    // ---------------------------------------------------------------- 生命周期

    @Override
    public void init() {
        // 原型：不调用 super.init()，全部自绘。
        this.clearWidgets();
        this.cachedType = null;
        this.selected = 0;
        this.scroll = 0;
        this.samplesDirty = true;
        this.hoveredRow = -1;
        this.lastRowClickTime = 0L;
        this.lastRowClickIndex = -1;
        this.lastRowClickButton = -1;
        Arrays.fill(this.infoScroll, 0);
        this.attachScroll = 0;
        this.attachKey = null;
        VirtualAssembly.clear();
        addLaserSliders();
        addSearchBox();
    }

    /**
     * 候选面板底部的搜索框。
     *
     * <p>{@code init()} 会在切槽位与服务端刷新时重建，所以这里用 {@link #searchQuery}
     * 把词接回来——只在 {@link #selectSlot} 里清空，避免装完一件就把搜索词丢了。</p>
     */
    private void addSearchBox() {
        // 概览态压根不建这个控件。候选框都隐藏了，搜索自然无从谈起；留着的话它会被
        // Screen 当成普通 widget 画在 3D 画面上（我们的面板这时候不画），还能点、
        // 还能聚焦 —— 一旦聚焦，keyPressed 就会把按键让给它，槽位快捷键全失灵。
        if (RefitTransform.getCurrentTransformType() == AttachmentType.NONE) {
            this.searchBox = null;
            return;
        }
        Rect rect = searchRect();
        EditBox box = new EditBox(this.font, rect.x(), rect.y(), rect.w(), rect.h(),
                Component.translatable("gui.z_tweaks.refit.search.hint"));
        box.setMaxLength(32);
        box.setValue(searchQuery);
        box.setResponder(value -> {
            searchQuery = value;
            // 候选列表按 cachedType 做缓存，置空才能触发下一帧重建
            cachedType = null;
            selected = 0;
            scroll = 0;
            samplesDirty = true;
        });
        this.searchBox = box;
        addRenderableWidget(box);
    }

    /** 搜索框矩形：贴候选面板底部内侧，右边腾出 {@link #SORT_W} 给排序按钮，几何与 {@link #listRect()} 同源。 */
    private Rect searchRect() {
        Rect list = listRect();
        int width = list.w() - 8 - SORT_W - SORT_GAP;
        return new Rect(list.x() + 4, footerY(list), width, SEARCH_H);
    }

    /** 排序按钮矩形：与搜索框同一行，紧贴其右侧。 */
    private Rect sortRect() {
        Rect search = searchRect();
        return new Rect(search.x() + search.w() + SORT_GAP, search.y(), SORT_W, SEARCH_H);
    }

    /** 候选面板底部这一行（搜索框 / 排序按钮）的顶边。 */
    private static int footerY(Rect list) {
        return list.y() + list.h() - SEARCH_H - 2;
    }

    /**
     * 循环切换排序：名称 A-Z → 名称 Z-A → 模组 A-Z → 模组 Z-A → 名称 A-Z。
     *
     * <p>用一个按钮装下四种状态，比"字段 + 方向两个按钮"省一半宽度（候选面板只有
     * {@link #LIST_W} 宽，还要和搜索框挤同一行）。当前状态直接写在按钮上，鼠标悬停有提示。</p>
     *
     * <p>改完把 {@code cachedType} 置空触发下一帧重建 —— 候选列表按槽位类型缓存，
     * 不置空的话排序不会立即生效。</p>
     */
    private void cycleSort() {
        if (sortField == SortField.NAME) {
            if (sortAscending) {
                sortAscending = false;
            } else {
                sortField = SortField.MOD;
                sortAscending = true;
            }
        } else {
            if (sortAscending) {
                sortAscending = false;
            } else {
                sortField = SortField.NAME;
                sortAscending = true;
            }
        }
        cachedType = null;
        selected = 0;
        scroll = 0;
        samplesDirty = true;
    }

    /** 排序按钮上的文字：字段 + 方向，取 lang（如"名称 A-Z" / "Mod Z-A"）。 */
    private String sortLabel() {
        return I18n.get("gui.z_tweaks.refit.sort." + sortField.name().toLowerCase(Locale.ROOT)
                + (sortAscending ? ".asc" : ".desc"));
    }

    /**
     * 关屏时先撤掉虚拟装配：预览状态不能越过界面生命周期活到世界里去
     * （渲染入口那边的护栏已经能兜住，这里是第二道）。
     * {@code super.onClose()} 负责上传镭射色，必须调用。
     */
    @Override
    public void onClose() {
        VirtualAssembly.clear();
        super.onClose();
    }

    /**
     * 接回原生在**概览态**（整枪镭射色）与**镭射槽**提供的颜色调色。
     * 直接复用 TACZ 的 {@link HSVSliderGroup}：它写的是客户端本地 NBT（脏写做实时预览），
     * 真正的上传由 {@code GunRefitScreen.onClose()} 里的 ClientMessageLaserColor 负责，
     * 这一段我们继承了，所以不需要自己发包。
     */
    private void addLaserSliders() {
        LocalPlayer player = getMinecraft().player;
        if (player == null) {
            return;
        }
        Inventory inventory = player.getInventory();
        ItemStack gun = player.getMainHandItem();
        AttachmentType current = RefitTransform.getCurrentTransformType();
        if (current == AttachmentType.NONE) {
            TimelessAPI.getGunDisplay(gun)
                    .map(GunDisplayInstance::getLaserConfig)
                    .filter(LaserConfig::canEdit)
                    .ifPresent(config -> addLaserSliders(inventory, AttachmentType.NONE));
            return;
        }
        if (current != AttachmentType.LASER) {
            return;
        }
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return;
        }
        ItemStack laser = iGun.getAttachment(gun, current);
        if (laser.getItem() instanceof IAttachment attachment) {
            TimelessAPI.getClientAttachmentIndex(attachment.getAttachmentId(laser))
                    .map(ClientAttachmentIndex::getLaserConfig)
                    .filter(LaserConfig::canEdit)
                    .ifPresent(config -> addLaserSliders(inventory, current));
        }
    }

    private void addLaserSliders(Inventory inventory, AttachmentType type) {
        HSVSliderGroup group = new HSVSliderGroup(PAD, detailY() - 42, 120, 14, inventory, inventory.selected, type);
        addRenderableWidget(group.getHueSlider());
        addRenderableWidget(group.getSaturationSlider());
    }

    // ---------------------------------------------------------------- 数据

    private ItemStack gunStack() {
        LocalPlayer player = getMinecraft().player;
        return player == null ? ItemStack.EMPTY : player.getMainHandItem();
    }

    private List<AttachmentType> slotTypes() {
        List<AttachmentType> list = new ArrayList<>();
        for (AttachmentType type : AttachmentType.values()) {
            if (type != AttachmentType.NONE) {
                list.add(type);
            }
        }
        return list;
    }

    /**
     * 候选配件：只保留能装到手里的枪上的（{@code iGun.allowAttachment}）。
     *
     * <p>是否要求"带在身上"按游戏模式分：生存模式只列背包里有的 —— 装配件要把背包
     * 槽位发给服务端，没带在身上的点了也装不上；创造模式列全部，没带在身上的在列表里
     * 把图标标灰（能预览、装不上）。TACZ 原生两种模式都只扫背包，创造分支是我们加的。</p>
     */
    private void rebuildCandidates() {
        LocalPlayer player = getMinecraft().player;
        if (player == null) {
            return;
        }
        ItemStack gun = gunStack();
        IGun iGun = IGun.getIGunOrNull(gun);
        AttachmentType type = RefitTransform.getCurrentTransformType();
        if (iGun == null || type == AttachmentType.NONE) {
            candidates.clear();
            candidateInvSlots.clear();
            cachedType = type;
            return;
        }
        if (type == cachedType) {
            return;
        }
        cachedType = type;
        List<Map.Entry<ResourceLocation, CommonAttachmentIndex>> all =
                new ArrayList<>(TimelessAPI.getAllCommonAttachmentIndex());
        Inventory inventory = player.getInventory();
        List<Candidate> found = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : all) {
            if (entry.getValue().getType() != type) {
                continue;
            }
            ItemStack stack = AttachmentItemBuilder.create().setId(entry.getKey()).build();
            if (!iGun.allowAttachment(gun, stack)) {
                continue;
            }
            if (!searchQuery.isBlank() && !matchesQuery(nameOf(stack))) {
                continue;
            }
            int invSlot = findInventorySlot(inventory, entry.getKey());
            // 生存模式只列真正带在身上的：装配件要把背包槽位发给服务端，没带在身上的
            // 点了也装不上。创造模式列全部，没带在身上的在列表里标灰（能预览、装不上）。
            if (invSlot < 0 && !player.isCreative()) {
                continue;
            }
            found.add(new Candidate(stack, invSlot, nameOf(stack), entry.getKey().getNamespace()));
        }
        found.sort(candidateComparator());
        candidates.clear();
        candidateInvSlots.clear();
        for (Candidate candidate : found) {
            candidates.add(candidate.stack());
            candidateInvSlots.add(candidate.invSlot());
        }
        selected = 0;
        scroll = 0;
        samplesDirty = true;
    }

    /**
     * 候选排序：**已拥有的（可用）配件永远置顶**，其次才按玩家选的排序方式。
     *
     * <p>置顶是独立的一层、优先级高于排序方式：创造模式下列表里混着大量没带在身上的配件
     * （图标盖灰、点了装不上），把真正能装的排到最前面，少滚一半列表。生存模式下列表本来
     * 就只剩背包里的，这一层退化成空操作。</p>
     */
    private Comparator<Candidate> candidateComparator() {
        Comparator<Candidate> byField = switch (sortField) {
            case NAME -> Comparator.comparing((Candidate c) -> c.name().toLowerCase(Locale.ROOT))
                    .thenComparing(c -> c.modId().toLowerCase(Locale.ROOT));
            case MOD -> Comparator.comparing((Candidate c) -> c.modId().toLowerCase(Locale.ROOT))
                    .thenComparing(c -> c.name().toLowerCase(Locale.ROOT));
        };
        if (!sortAscending) {
            byField = byField.reversed();
        }
        return Comparator.comparingInt((Candidate c) -> c.invSlot() >= 0 ? 0 : 1).thenComparing(byField);
    }

    /** 搜索匹配：本地化后的显示名，大小写不敏感的子串。玩家搜的是眼睛看到的名字。 */
    private boolean matchesQuery(String name) {
        return name.toLowerCase(Locale.ROOT).contains(searchQuery.toLowerCase(Locale.ROOT));
    }

    private static int findInventorySlot(Inventory inventory, ResourceLocation id) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            IAttachment attachment = IAttachment.getIAttachmentOrNull(inventory.getItem(i));
            if (attachment != null && id.equals(attachment.getAttachmentId(inventory.getItem(i)))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Pros/Cons 第一版：直接复用 TACZ 为配件预先拼好的成品文本
     * （{@code AttachmentData.getModifier()} → {@code JsonProperty.getComponents()}），
     * 按文本自带的颜色码分栏。
     */
    private void computeProsCons() {
        pros.clear();
        cons.clear();
        neutral.clear();
        if (candidates.isEmpty()) {
            return;
        }
        if (ZtConfig.PROS_CONS_MODE.get() == ZtConfig.ProsConsMode.TACZ_TEXT) {
            computeProsConsTaczText();
        } else {
            computeProsConsDelta();
        }
    }

    private void computeProsConsTaczText() {
        ItemStack candidate = candidates.get(Math.min(selected, candidates.size() - 1));
        IAttachment iAttachment = IAttachment.getIAttachmentOrNull(candidate);
        if (iAttachment == null) {
            return;
        }
        ResourceLocation id = iAttachment.getAttachmentId(candidate);
        TimelessAPI.getClientAttachmentIndex(id).ifPresent(index -> {
            AttachmentData data = index.getData();
            data.getModifier().forEach((modifierId, property) -> {
                for (Component component : property.getComponents()) {
                    String text = "[" + modifierId + "] " + component.getString();
                    TextColor color = component.getStyle().getColor();
                    if (color != null && color.getValue() == 0x55FF55) {
                        pros.add(text);
                    } else if (color != null && color.getValue() == 0xFF5555) {
                        cons.add(text);
                    } else {
                        neutral.add(text);
                    }
                }
            });
        });
    }

    /**
     * 默认模式：自算 delta。
     *
     * <p>把候选配件装到枪的**副本**上，分别算两份 {@link AttachmentCacheProperty}，
     * 逐属性比较 {@code modifier()} 的差值；差值符号配合 {@code positivelyBetter}
     * 决定进 Pros 还是 Cons。</p>
     *
     * <p>增量文本<b>优先直接取 TACZ 自己算好的那段</b>（见 {@link #deltaTextOf}）：它已经按
     * 各属性自己的规则换算过并带好单位，跟原生属性条完全对齐。解析不到时才退回自算 +
     * {@link #unitOf} 的近似写法。</p>
     */
    private void computeProsConsDelta() {
        LocalPlayer player = getMinecraft().player;
        if (player == null) {
            return;
        }
        ItemStack gun = gunStack();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return;
        }
        ItemStack candidate = candidates.get(Math.min(selected, candidates.size() - 1));
        GunData gunData = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                .map(CommonGunIndex::getGunData).orElse(null);
        if (gunData == null) {
            return;
        }
        AttachmentCacheProperty base = new AttachmentCacheProperty();
        base.eval(gun, gunData);
        ItemStack withCandidate = gun.copy();
        iGun.installAttachment(withCandidate, candidate);
        AttachmentCacheProperty modified = new AttachmentCacheProperty();
        modified.eval(withCandidate, gunData);

        AttachmentPropertyManager.getModifiers().forEach((id, modifier) -> {
            List<IAttachmentModifier.DiagramsData> before = modifier.getPropertyDiagramsData(gun, gunData, base);
            List<IAttachmentModifier.DiagramsData> after =
                    modifier.getPropertyDiagramsData(withCandidate, gunData, modified);
            for (int i = 0; i < before.size() && i < after.size(); i++) {
                IAttachmentModifier.DiagramsData old = before.get(i);
                IAttachmentModifier.DiagramsData now = after.get(i);
                double delta = now.modifier().doubleValue() - old.modifier().doubleValue();
                if (Math.abs(delta) < 1.0E-6) {
                    continue;
                }
                // 增量的符号决定取 positively 还是 negative 那一份成品文本
                String deltaText = deltaTextOf(delta > 0 ? now.positivelyString() : now.negativeString());
                String amount = deltaText != null ? deltaText
                        : (delta > 0 ? "+" : "") + String.format("%.2f", delta) + unitOf(now.positivelyString());
                String text = I18n.get(now.titleKey()) + " " + amount;
                boolean better = (delta > 0) == now.positivelyBetter();
                (better ? pros : cons).add(text);
            }
        });
    }

    /** TACZ 成品串里"括号内的增量"，如 {@code 85.0% §a(+15.0%)} 中的 {@code +15.0%}。 */
    private static final Pattern DELTA_TOKEN = Pattern.compile("\\(([+-]?[\\d.]+)([^)]*)\\)");

    /**
     * 从 TACZ 成品文本里抠出它自己算好的增量（含符号与单位）。
     *
     * <p>为什么不再自己算：TACZ 的 {@code positivelyString} 形如 {@code 85.0% §a(+15.0%)}，
     * 括号里的 {@code +15.0%} 就是原生属性条显示的增量 —— 数字已按该属性自己的规则换算过
     * （百分号类属性乘了 100），单位也是原生的。早先拿 {@code modifier()} 相减再自己剥单位，
     * 结果数字差 100 倍，且百分号类属性会剥出两个 {@code %}，显示成 {@code +0.15%%}。</p>
     *
     * @return {@code "+15.0%"} 这样的增量文本；解析不出来返回 {@code null}，由调用方退回旧写法
     */
    @Nullable
    private static String deltaTextOf(@Nullable String taczString) {
        if (taczString == null) {
            return null;
        }
        Matcher matcher = DELTA_TOKEN.matcher(taczString.replaceAll("§.", ""));
        if (!matcher.find()) {
            return null;
        }
        String token = matcher.group(1) + matcher.group(2);
        return token.isBlank() ? null : token;
    }

    /** 从 TACZ 成品文本里剥出单位残留：去掉色码、数字、正负号、括号与空白后剩下的字母。 */
    private static String unitOf(String sample) {
        String stripped = sample.replaceAll("§.", "").replaceAll("[0-9.+\\-()\\s]", "");
        if (stripped.isEmpty() || stripped.length() > 4) {
            return "";
        }
        // 百分号类属性的成品串里有两个 %（值一个、增量一个），剥出来会重复
        return stripped.chars().allMatch(c -> c == '%') ? "%" : stripped;
    }

    /**
     * 对照用：从实时缓存取 {@code DiagramsData}，把 {@code positivelyString} /
     * {@code negativeString} 的**原文**（含 {@code §a} / {@code §c} 色码）拿出来看渲染效果。
     */
    private void computeSamples() {
        samples.clear();
        samplesDirty = false;
        LocalPlayer player = getMinecraft().player;
        if (player == null) {
            return;
        }
        ItemStack gun = gunStack();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            return;
        }
        AttachmentCacheProperty cache = IGunOperator.fromLivingEntity(player).getCacheProperty();
        if (cache == null) {
            samples.add(I18n.get("gui.z_tweaks.refit.cache_null"));
            return;
        }
        TimelessAPI.getCommonGunIndex(iGun.getGunId(gun)).ifPresent(gunIndex -> {
            GunData gunData = gunIndex.getGunData();
            AttachmentPropertyManager.getModifiers().forEach((key, modifier) ->
                    modifier.getPropertyDiagramsData(gun, gunData, cache).forEach(d -> {
                        if (samples.size() < 3) {
                            samples.add(key + ": +'" + d.positivelyString() + "' -'" + d.negativeString() + "'");
                        }
                    }));
        });
        if (samples.isEmpty()) {
            samples.add(I18n.get("gui.z_tweaks.refit.no_diagrams"));
        }
    }

    // ---------------------------------------------------------------- 渲染

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        rebuildCandidates();
        computeProsCons();
        if (samplesDirty && ZtConfig.DEBUG_SAMPLES.get()) {
            computeSamples();
        }
        pendingTooltip = null;
        // 枪本体是 3D 世界里的渲染，压一层极淡的上下暗角把界面压出层次，同时不遮挡枪
        drawVignette(graphics);
        drawSlotBar(graphics, mouseX, mouseY);
        // 概览态不画候选框：空面板 + "0" 徽标是纯噪音。隐藏后那块区域交还给 3D 预览的拖拽/滚轮。
        if (candidateListVisible()) {
            drawCandidateList(graphics, mouseX, mouseY, partialTick);
        } else {
            hoveredRow = -1;
        }
        syncPreview();
        drawDetail(graphics, mouseX, mouseY);
        if (showNativeBars && ZtConfig.DEBUG_NATIVE_BARS.get()) {
            GunPropertyDiagrams.draw(graphics, this.font, 11, 96);
        }
        drawOverlay(graphics);
        flushTooltip(graphics);
    }

    /**
     * 顶部/底部极淡的纵向暗角。中段完全透明，保证转枪时枪不被糊住 ——
     * 只给"上缘调色、下缘操作区"提供一点对比度。
     */
    private void drawVignette(GuiGraphics graphics) {
        graphics.fillGradient(0, 0, this.width, 52, 0x99000000, 0x00000000);
        graphics.fillGradient(0, detailY() - 6, this.width, this.height, 0x00000000, 0xAA000000);
    }

    /** 登记一个本帧末尾才渲染的 tooltip（绘制顺序：必须先画完所有面板）。 */
    private void tooltip(Component text, int x, int y) {
        pendingTooltip = text;
        tooltipX = x;
        tooltipY = y;
    }

    private void flushTooltip(GuiGraphics graphics) {
        if (pendingTooltip != null) {
            graphics.renderTooltip(this.font, pendingTooltip, tooltipX, tooltipY);
            pendingTooltip = null;
        }
    }

    private int slotBarY() {
        return this.height - SLOT - 6;
    }

    private int detailY() {
        return slotBarY() - DETAIL_H - 4;
    }

    /** 槽位条的水平起点与列间距：绘制与命中检测共用，杜绝两处各写一份几何。 */
    private int[] slotBarGeometry(List<AttachmentType> types) {
        int step = slotStep(types);
        return new int[]{(this.width - types.size() * step) / 2, step};
    }

    /** 第 index 个槽位的方块矩形（列内居中，列宽大于方块时两侧留白对称）。 */
    private Rect slotRect(List<AttachmentType> types, int index) {
        int[] geo = slotBarGeometry(types);
        int cell = geo[0] + index * geo[1];
        return new Rect(cell + (geo[1] - SLOT) / 2, slotBarY(), SLOT, SLOT);
    }

    private void drawSlotBar(GuiGraphics graphics, int mouseX, int mouseY) {
        List<AttachmentType> types = slotTypes();
        int[] geo = slotBarGeometry(types);
        int step = geo[1];
        int x0 = geo[0];
        int barWidth = types.size() * step;
        int y = slotBarY();
        AttachmentType current = RefitTransform.getCurrentTransformType();
        ItemStack gun = gunStack();
        IGun iGun = IGun.getIGunOrNull(gun);

        // 整条槽位条坐在一块圆角渐变面板上：3D 画面上直接摆一排方块会"飘"，加个托底就有层次
        panel(graphics, x0 - 6, y - 15, barWidth + 12, SLOT + 22);

        for (int i = 0; i < types.size(); i++) {
            AttachmentType type = types.get(i);
            Rect rect = slotRect(types, i);
            boolean hovered = rect.contains(mouseX, mouseY) && !dragging;
            boolean isCurrent = type == current;
            boolean allowed = iGun != null && iGun.allowAttachmentType(gun, type);

            if (allowed) {
                int fill = isCurrent ? ACCENT_SOFT : (hovered ? HOVER_OVERLAY : 0x22000000);
                roundedFill(graphics, rect.x(), rect.y(), rect.w(), rect.h(), fill);
                roundedBorder(graphics, rect.x(), rect.y(), rect.w(), rect.h(),
                        isCurrent ? ACCENT : (hovered ? 0x88FFFFFF : BORDER_STRONG));
            } else {
                // 不支持的槽位：压暗 + 红褐描边，一眼看出点不动（点了也会弹提示）
                roundedFill(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 0x55000000);
                roundedBorder(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 0xFF3A2424);
            }

            ItemStack installed = iGun == null ? ItemStack.EMPTY : iGun.getAttachment(gun, type);
            if (!installed.isEmpty()) {
                graphics.renderItem(installed, rect.x() + 3, rect.y() + 3);
            } else {
                graphics.drawCenteredString(this.font, "+", rect.x() + rect.w() / 2, rect.y() + 7,
                        allowed ? TEXT_MUTED : 0xFF5A3A3A);
            }

            String label = truncate(slotName(type), step - 2);
            int labelColor = !allowed ? 0xFF8A5A5A : (isCurrent ? ACCENT : (hovered ? TEXT : TEXT_DIM));
            graphics.drawCenteredString(this.font, label, rect.x() + rect.w() / 2, y - 10, labelColor);
            // 当前槽位用名字下的强调线标记，比整块高亮更克制、也更不抢枪的视觉
            if (isCurrent) {
                int w = this.font.width(label);
                int cx = rect.x() + rect.w() / 2;
                graphics.fill(cx - w / 2, y - 2, cx + w / 2, y - 1, ACCENT);
            }

            if (hovered) {
                tooltip(Component.literal(allowed ? slotName(type)
                        : I18n.get("gui.z_tweaks.refit.msg.slot_not_allowed", slotName(type))),
                        (int) mouseX, (int) mouseY);
            }
        }
    }

    /**
     * 候选框是否可见：概览态（{@link AttachmentType#NONE}）没有"当前槽位"，候选列表必然为空，
     * 此时整块不画。绘制、点击命中、滚轮判定共用这一个判定，隐藏后该区域归 3D 预览。
     */
    private boolean candidateListVisible() {
        return RefitTransform.getCurrentTransformType() != AttachmentType.NONE;
    }

    /** 候选面板矩形：绘制、点击命中、滚轮判定三者共用同一来源。 */
    private Rect listRect() {
        int height = Math.max(64, detailY() - 8 - 26);
        return new Rect(this.width - LIST_W - PAD, 26, LIST_W, height);
    }

    /** 候选面板第 i 行的矩形（i 从 0 开始，不含滚动偏移）。两行之间留 2px 缝，观感更透气。 */
    private static Rect rowRect(Rect list, int i) {
        return new Rect(list.x() + 3, list.y() + LIST_HEADER + i * ROW_H, list.w() - 6, ROW_H - 2);
    }

    private void drawCandidateList(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Rect list = listRect();
        int x = list.x();
        int y = list.y();
        int height = list.h();
        int right = x + list.w();

        panel(graphics, x, y, list.w(), height);

        // 标题 + 数量徽标（数量是玩家最关心的信息，单独给它一个胶囊）
        graphics.drawString(this.font, I18n.get("gui.z_tweaks.refit.candidates.title"), x + 6, y + 4, ACCENT, true);
        String count = String.valueOf(candidates.size());
        int badgeW = this.font.width(count) + 9;
        int badgeX = right - 7 - badgeW;
        roundedFill(graphics, badgeX, y + 2, badgeW, 10, 0x40000000);
        roundedBorder(graphics, badgeX, y + 2, badgeW, 10, HAIRLINE);
        graphics.drawCenteredString(this.font, count, badgeX + badgeW / 2, y + 3, TEXT_DIM);
        graphics.fill(x + 4, y + LIST_HEADER - 3, right - 4, y + LIST_HEADER - 2, HAIRLINE);

        int rows = visibleRows();
        int maxScroll = Math.max(0, candidates.size() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        hoveredRow = -1;
        int listTop = y + LIST_HEADER;

        if (candidates.isEmpty()) {
            // 空列表有两种成因，不能混用一句：槽位本来就没配件 vs 被搜索词筛没了
            String key = searchQuery.isBlank()
                    ? "gui.z_tweaks.refit.candidates.empty"
                    : "gui.z_tweaks.refit.candidates.no_match";
            graphics.drawString(this.font, I18n.get(key), x + 6, listTop + 4, TEXT_MUTED, false);
        }

        // 裁剪：滚动/悬停的行不会越出面板边线
        graphics.enableScissor(x + 1, listTop, right - 1, listTop + rows * ROW_H);
        for (int i = 0; i < rows && scroll + i < candidates.size(); i++) {
            int index = scroll + i;
            Rect row = rowRect(list, i);
            boolean isSelected = index == selected;
            boolean hovered = !dragging && row.contains(mouseX, mouseY);
            if (hovered) {
                hoveredRow = index;
            }
            boolean owned = candidateInvSlots.get(index) >= 0;

            if (isSelected) {
                roundedFill(graphics, row.x(), row.y(), row.w(), row.h(), ACCENT_SOFT);
            } else if (hovered) {
                roundedFill(graphics, row.x(), row.y(), row.w(), row.h(), HOVER_OVERLAY);
            }
            // 左侧强调竖条：选中用强调色、悬停白 —— 比整行铺色精细，也不干扰阅读
            if (isSelected || hovered) {
                graphics.fill(row.x(), row.y() + 1, row.x() + 2, row.y() + row.h() - 1,
                        isSelected ? ACCENT : 0x88FFFFFF);
            }
            graphics.renderItem(candidates.get(index), row.x() + 5, row.y() + 1);
            if (!owned) {
                // 图标盖一层半透明黑：虚拟装配能预览它，但点下去服务端装不上（见
                // installSelected），不标出来会让"能预览"被误读成"能装"。
                // 只在创造模式看得到 —— 生存模式压根不列没带在身上的。
                graphics.fill(row.x() + 5, row.y() + 1, row.x() + 21, row.y() + 17, 0x80000000);
            }
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(nameOf(candidates.get(index)), row.w() - 34),
                    row.x() + 24, row.y() + 5, owned ? TEXT : TEXT_MUTED, false);
            if (hovered) {
                tooltip(Component.literal(nameOf(candidates.get(index))), (int) mouseX, (int) mouseY);
            }
        }
        graphics.disableScissor();

        if (maxScroll > 0) {
            int trackX = right - 3;
            int trackTop = listTop + 1;
            int trackH = rows * ROW_H - 2;
            graphics.fill(trackX, trackTop, trackX + 2, trackTop + trackH, TRACK_BG);
            int thumbH = Math.max(8, trackH * rows / candidates.size());
            int thumbY = trackTop + Math.round((trackH - thumbH) * (scroll / (float) maxScroll));
            graphics.fill(trackX, thumbY, trackX + 2, thumbY + thumbH, 0xAA55FFFF);
        }

        // 搜索框：面板是在 super.render 之后画的，不在这里补画一次会被面板盖住
        if (searchBox != null) {
            searchBox.render(graphics, mouseX, mouseY, partialTick);
        }
        drawSortButton(graphics, mouseX, mouseY);
    }

    /**
     * 底部这一行右侧的排序按钮：显示当前排序方式（如"名称 A-Z"），点击循环切换。
     *
     * <p>自绘而非用 {@link #button}：那个 helper 是安装/卸下的绿红语义（primary/secondary），
     * 排序是中性控件，套上去会让人误以为它是个"确认/取消"。观感对齐同一行的搜索框。</p>
     */
    private void drawSortButton(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect rect = sortRect();
        boolean hovered = !dragging && rect.contains(mouseX, mouseY);
        roundedFill(graphics, rect.x(), rect.y(), rect.w(), rect.h(), hovered ? 0x40FFFFFF : 0x40000000);
        roundedBorder(graphics, rect.x(), rect.y(), rect.w(), rect.h(), hovered ? 0x88FFFFFF : HAIRLINE);
        graphics.drawCenteredString(this.font, truncate(sortLabel(), rect.w() - 6),
                rect.x() + rect.w() / 2, rect.y() + 2, hovered ? TEXT : TEXT_DIM);
        if (hovered) {
            tooltip(Component.literal(I18n.get("gui.z_tweaks.refit.sort.tooltip")),
                    (int) mouseX, (int) mouseY);
        }
    }

    /**
     * 把"鼠标正悬停的那一行"交给 {@link VirtualAssembly}：下一帧渲染管线就用克隆件作画
     * （手部渲染在 GUI 之前，故有 1 帧延迟，肉眼无感）。
     *
     * <p>刻意只驱动 3D 预览、<b>不动 {@code selected}</b>：否则鼠标从列表划向安装按钮的途中
     * 会把待安装目标一并改掉，容易装错件。详情条仍跟随 {@code selected}，
     * "悬停实时 diff" 归 §3.2-4（M4）。</p>
     */
    private void syncPreview() {
        AttachmentType type = RefitTransform.getCurrentTransformType();
        if (hoveredRow < 0 || hoveredRow >= candidates.size() || type == AttachmentType.NONE) {
            VirtualAssembly.clear();
            return;
        }
        VirtualAssembly.setPreview(type, candidates.get(hoveredRow));
    }

    private void drawDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = PAD;
        int width = this.width - PAD * 2;
        int y = detailY();
        int height = DETAIL_H;
        panel(graphics, x, y, width, height);
        // 左侧一道强调色竖条，把"这里是当前查看的配件"点明
        graphics.fill(x, y + 1, x + 2, y + height - 1, ACCENT);

        int leftWidth = (int) (width * 0.40f);

        if (!candidates.isEmpty()) {
            drawAttachmentInfo(graphics, x, y, leftWidth);
        } else if (RefitTransform.getCurrentTransformType() == AttachmentType.NONE) {
            // 概览态没有候选配件可讲，整条详情条拿来放枪械参数
            drawGunInfo(graphics, x, y, width, mouseX, mouseY);
        }

        // Pros/Cons 两栏：不写"优点 N / 缺点 N"标题，靠栏色（绿/红）与条目前缀区分；
        // 省下的标题行高度直接换成多显示一条条目。
        int columnX = x + leftWidth + 6;
        int columnWidth = (width - leftWidth - 20) / 2;
        int rightColumnX = columnX + columnWidth + 8;
        int entryTop = y + 5;
        // 条目数按按钮位置反推，避免最后一行压到按钮上（按钮挪了这里自动跟着变）
        int entryLimit = Math.max(1, (installRect().y() - 2 - entryTop) / 10);
        drawPropertyColumn(graphics, columnX, columnWidth, entryTop, entryLimit, pros, GOOD);
        drawPropertyColumn(graphics, rightColumnX, columnWidth, entryTop, entryLimit, cons, BAD);

        Rect installRect = installRect();
        Rect unloadRect = unloadRect();
        boolean installEnabled = selectedOwned();
        // 概览态不画这两个按钮：没有选中槽位，它们永远处于禁用态，
        // 只会占着参数卡右下的空间。安装/卸载的提示也只跟按钮走。
        if (RefitTransform.getCurrentTransformType() != AttachmentType.NONE) {
            boolean unloadEnabled = canUnload();
            button(graphics, installRect, I18n.get("gui.z_tweaks.refit.install"),
                    installRect.contains(mouseX, mouseY) && !dragging, true, installEnabled);
            button(graphics, unloadRect, I18n.get("gui.z_tweaks.refit.unload"),
                    unloadRect.contains(mouseX, mouseY) && !dragging, false, unloadEnabled);
            // 不可用时把原因说清楚：悬停给提示，而不是点了没反应
            if (!dragging && installRect.contains(mouseX, mouseY) && !installEnabled) {
                tooltip(Component.literal(I18n.get("gui.z_tweaks.refit.msg.not_owned")),
                        (int) mouseX, (int) mouseY);
            }
            if (!dragging && unloadRect.contains(mouseX, mouseY) && !unloadEnabled) {
                // 禁用原因分两种："选中的槽位是空的"与槽位不支持，提示文案不能混用
                tooltip(Component.literal(I18n.get("gui.z_tweaks.refit.msg.nothing_to_unload")),
                        (int) mouseX, (int) mouseY);
            }
        }
    }

    /** 一栏属性：只有条目 + 色标，无标题、无分隔线。{@code limit} 由调用方按可用高度算出。 */
    private void drawPropertyColumn(GuiGraphics graphics, int x, int width, int entryTop,
                                    int limit, List<String> entries, int color) {
        for (int i = 0; i < entries.size() && i < limit; i++) {
            int rowY = entryTop + i * 10;
            graphics.fill(x + 1, rowY + 3, x + 3, rowY + 5, color);
            graphics.drawString(this.font, truncate(entries.get(i), width - 9), x + 7, rowY, color, false);
        }
    }

    /** 选中候选是否在背包里。不在就发不出包（服务端只认自己那份背包），按钮据此禁用。 */
    private boolean selectedOwned() {
        if (candidates.isEmpty()) {
            return false;
        }
        int index = Math.min(selected, candidates.size() - 1);
        return index < candidateInvSlots.size() && candidateInvSlots.get(index) >= 0;
    }

    /** 当前槽位是否有可卸下的配件。概览态或空槽一律禁用卸载按钮。 */
    private boolean canUnload() {
        AttachmentType type = RefitTransform.getCurrentTransformType();
        if (type == AttachmentType.NONE) {
            return false;
        }
        ItemStack gun = gunStack();
        IGun iGun = IGun.getIGunOrNull(gun);
        return iGun != null && !iGun.getAttachment(gun, type).isEmpty();
    }

    /**
     * 左上角浮层：诊断 HUD（默认关，见 {@link ZtConfig#DEBUG_HUD}）+ 操作反馈弹条。
     * 弹条是给玩家看的正常反馈，不受调试开关影响。
     */
    private void drawOverlay(GuiGraphics graphics) {
        if (ZtConfig.DEBUG_HUD.get()) {
            List<String> lines = debugLines();
            int widest = 0;
            for (String text : lines) {
                widest = Math.max(widest, this.font.width(text));
            }
            // 诊断文字直接盖在世界上会糊成一片，垫一层半透明底衬
            roundedFill(graphics, PAD - 3, 4, widest + 8, lines.size() * 10 + 6, 0x90000000);
            roundedBorder(graphics, PAD - 3, 4, widest + 8, lines.size() * 10 + 6, HAIRLINE);
            int y = 8;
            for (String text : lines) {
                graphics.drawString(this.font, text, PAD, y, 0xFF7FE7FF, true);
                y += 10;
            }
        }
        drawToast(graphics);
    }

    /**
     * 操作反馈弹条：贴在详情条正上方居中，最后 0.6 秒淡出。
     * 比"左上角一行会突然消失的字"更像正经反馈，也不挡枪。
     */
    private void drawToast(GuiGraphics graphics) {
        long remaining = popupUntil - System.currentTimeMillis();
        if (popup.isEmpty() || remaining <= 0) {
            return;
        }
        int alpha = (int) (Math.min(1.0f, remaining / 600.0f) * 255.0f);
        if (alpha <= 4) {
            return;
        }
        int w = this.font.width(popup) + 18;
        int h = 14;
        int x = (this.width - w) / 2;
        int y = detailY() - h - 8;
        roundedFill(graphics, x, y, w, h, (alpha << 24) | 0x14171B);
        roundedBorder(graphics, x, y, w, h, (alpha << 24) | 0x4A5158);
        graphics.fill(x + 1, y + 1, x + 3, y + h - 1, (alpha << 24) | 0x55FFFF);
        graphics.drawCenteredString(this.font, popup, x + w / 2 + 1, y + 3, (alpha << 24) | 0xBFF7FF);
    }

    /** 诊断 HUD 的每一行：mixin 是否注入、相机读数、取景进度、字体与按键速查。 */
    private List<String> debugLines() {
        int hits = OrbitCamera.applyCount();
        List<String> lines = new ArrayList<>();
        lines.add(I18n.get("gui.z_tweaks.refit.title"));
        lines.add(I18n.get("gui.z_tweaks.refit.hud.slot",
                slotName(RefitTransform.getCurrentTransformType()), candidates.size(), selected)
                + "  gui=" + this.width + "x" + this.height);
        lines.add(I18n.get("gui.z_tweaks.refit.hud.camera",
                I18n.get(OrbitCamera.enabled()
                        ? "gui.z_tweaks.refit.hud.camera.on"
                        : "gui.z_tweaks.refit.hud.camera.off"),
                fmt(OrbitCamera.yaw()), fmt(OrbitCamera.roll()), fmt(OrbitCamera.zoom()),
                fmt(OrbitCamera.offsetX()), fmt(OrbitCamera.offsetY())));
        // 枢轴读数：pivot_source / pivot_offset_y 改完看不到实际值等于盲调
        lines.add(I18n.get("gui.z_tweaks.refit.hud.pivot", ZtConfig.PIVOT_SOURCE.get(),
                fmt(OrbitCamera.lastPivotX()), fmt(OrbitCamera.lastPivotY())));
        lines.add(I18n.get(hits > 0
                ? "gui.z_tweaks.refit.hud.mixin.hit"
                : "gui.z_tweaks.refit.hud.mixin.miss", hits));
        lines.add(I18n.get("gui.z_tweaks.refit.hud.virtual",
                VirtualAssembly.hits(), VirtualAssembly.builds(), VirtualAssembly.previewName()));
        lines.add(I18n.get("gui.z_tweaks.refit.hud.refit",
                fmt(RefitTransform.getOpeningProgress()), fmt(RefitTransform.getTransformProgress())));
        lines.add(I18n.get("gui.z_tweaks.refit.hud.mode", ZtConfig.PROS_CONS_MODE.get()));
        lines.add(I18n.get("gui.z_tweaks.refit.hud.font"));
        lines.add(I18n.get("gui.z_tweaks.refit.hud.help"));
        return lines;
    }

    // ---------------------------------------------------------------- 交互

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先让 TACZ 的镭射调色滑条有机会吃掉这次点击
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 中键：复位相机，与 R 键走同一条路径（MC 的 button 从 0 起算，故中键是 2）
        if (button == 2) {
            OrbitCamera.reset();
            return true;
        }
        // 槽位条（几何与绘制同源：slotRect）
        List<AttachmentType> types = slotTypes();
        for (int i = 0; i < types.size(); i++) {
            if (slotRect(types, i).contains(mouseX, mouseY)) {
                selectSlot(types.get(i));
                return true;
            }
        }
        // 候选列表（几何与绘制同源：rowRect）。概览态框不画，点击自然也不该命中
        if (candidateListVisible()) {
            Rect list = listRect();
            int rows = visibleRows();
            for (int i = 0; i < rows && scroll + i < candidates.size(); i++) {
                if (rowRect(list, i).contains(mouseX, mouseY)) {
                    int index = scroll + i;
                    // 双击直接装上：省掉"点配件 → 点安装"那一步。判定照抄 MC 原生
                    // AbstractContainerScreen（本界面继承的是 Screen，拿不到那份实现）。
                    // 记的是候选下标而不是屏幕行号：两击之间滚一下滚轮，行号就漂移了。
                    long now = Util.getMillis();
                    boolean doubleClick = button == 0 && index == lastRowClickIndex
                            && lastRowClickButton == 0 && now - lastRowClickTime < DOUBLE_CLICK_MS;
                    lastRowClickTime = now;
                    lastRowClickIndex = index;
                    lastRowClickButton = button;
                    selected = index;
                    samplesDirty = true;
                    if (doubleClick) {
                        // 装完立刻作废，否则第三击又会被当成一次双击
                        lastRowClickIndex = -1;
                        installSelected();
                    }
                    return true;
                }
            }
        }
        // 排序按钮：与搜索框同一行，只在候选框可见时存在（几何与绘制同源：sortRect）
        if (candidateListVisible() && sortRect().contains(mouseX, mouseY)) {
            cycleSort();
            return true;
        }
        // 详情条按钮
        if (installRect().contains(mouseX, mouseY)) {
            installSelected();
            return true;
        }
        if (unloadRect().contains(mouseX, mouseY)) {
            unloadCurrent();
            return true;
        }
        // 其余空白：左键=绕武器原点旋转，右键=纯平移
        dragging = true;
        dragButton = button;
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 拖滑条时不能同时转相机
        if (super.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (dragging) {
            // 按"屏幕宽/高占比"换算，手感与 GUI scale 无关
            double fx = dragX / (double) this.width;
            double fy = dragY / (double) this.height;
            if (dragButton == 1) {
                // 右键：只平移，不旋转。第一参数=左右（1 屏宽 = pan_speed 格，鼠标往右武器往右），
                // 第二参数=上下（屏幕 Y 向下、视图空间 Y 向上，所以取负）
                float pan = ZtConfig.PAN_SPEED.get().floatValue();
                OrbitCamera.pan((float) fx * pan, (float) -fy * pan);
            } else {
                // 左键：上下拖 = roll，绕枪械自身长轴（模型 Z 轴）滚转；
                // 左右拖 = yaw，绕竖直轴环绕。两个自由度已足以到达任意姿态，故不再设"点头"。
                // 上下取负：按手感反馈反转 —— 鼠标下拖时枪往"看得见的那一面"转，往上拖反之。
                OrbitCamera.rotateRoll((float) (-fy * ZtConfig.ROLL_SPEED.get()));
                OrbitCamera.rotateY((float) (fx * ZtConfig.YAW_SPEED.get()));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        dragButton = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        // 指针在候选面板内（但不在底部搜索框 / 排序按钮上）才翻列表，其余位置一律给相机缩放
        if (candidateListVisible() && listRect().contains(mouseX, mouseY)
                && !searchRect().contains(mouseX, mouseY) && !sortRect().contains(mouseX, mouseY)) {
            scroll -= (int) Math.signum(delta);
            return true;
        }
        // 选中配件时：指针落在详情条左列就滚配件描述
        if (candidateListVisible() && !candidates.isEmpty()
                && attachColumnRect().contains(mouseX, mouseY)) {
            attachScroll -= (int) Math.signum(delta);
            return true;
        }
        // 概览态：指针落在详情条上就滚信息卡，滚的是指针所在的那一列
        if (!candidateListVisible() && detailRect().contains(mouseX, mouseY) && infoHoverColumn >= 0) {
            infoScroll[infoHoverColumn] -= (int) Math.signum(delta);
            return true;
        }
        // 取负：滚轮向上推 = 拉近（枪变大）
        OrbitCamera.addZoom((float) -delta * ZtConfig.ZOOM_STEP.get().floatValue());
        return true;
    }

    /** 选中槽位（同一槽位再选一次退回概览态）。鼠标点槽位与数字键 1–6 都走这里。 */
    private void selectSlot(AttachmentType type) {
        IGun iGun = IGun.getIGunOrNull(gunStack());
        if (iGun != null && !iGun.allowAttachmentType(gunStack(), type)) {
            notify(I18n.get("gui.z_tweaks.refit.msg.slot_not_allowed", slotName(type)));
            return;
        }
        if (RefitTransform.changeRefitScreenView(
                type == RefitTransform.getCurrentTransformType() ? AttachmentType.NONE : type)) {
            // 换槽位就是换一批配件，旧搜索词留着只会让人以为"列表怎么是空的"
            this.searchQuery = "";
            this.init();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 搜索框聚焦时把按键交回给 super：本方法在 super 之前就吞掉了 1–6 / 上下键 /
        // Enter / R / U，不早退的话输入框里连退格和方向键都用不了。
        if (searchBox != null && searchBox.isFocused()) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // 1–6 选槽位，上下键切换候选配件
        if (keyCode >= GLFW.GLFW_KEY_1 && keyCode <= GLFW.GLFW_KEY_6) {
            List<AttachmentType> types = slotTypes();
            int index = keyCode - GLFW.GLFW_KEY_1;
            if (index < types.size()) {
                selectSlot(types.get(index));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN || keyCode == GLFW.GLFW_KEY_UP) {
            if (!candidates.isEmpty()) {
                int step = keyCode == GLFW.GLFW_KEY_DOWN ? 1 : -1;
                selected = Math.floorMod(selected + step, candidates.size());
                samplesDirty = true;
                ensureSelectionVisible();
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_R) {
            OrbitCamera.reset();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            installSelected();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_U) {
            unloadCurrent();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G && ZtConfig.DEBUG_NATIVE_BARS.get()) {
            showNativeBars = !showNativeBars;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V && ZtConfig.DEBUG_CAMERA_HOTKEY.get()) {
            OrbitCamera.toggle();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void installSelected() {
        LocalPlayer player = getMinecraft().player;
        if (player == null || candidates.isEmpty()) {
            notify(I18n.get("gui.z_tweaks.refit.msg.nothing"));
            return;
        }
        int index = Math.min(selected, candidates.size() - 1);
        ItemStack candidate = candidates.get(index);
        String name = nameOf(candidate);
        int inventorySlot = candidateInvSlots.get(index);
        if (inventorySlot >= 0) {
            AttachmentType type = RefitTransform.getCurrentTransformType();
            // 与原生一致：声音在发包前就放（原生 GunRefitScreen 同款），不等服务端确认。
            // 音效取自被装配件自己的 pack 定义，我们只负责触发。
            SoundPlayManager.playerRefitSound(candidate, player, SoundManager.INSTALL_SOUND);
            NetworkHandler.CHANNEL.sendToServer(
                    new ClientMessageRefitGun(inventorySlot, player.getInventory().selected, type));
            notify(I18n.get("gui.z_tweaks.refit.msg.installed", name));
            return;
        }
        // 配件不在背包里，就没有能发给服务端的槽位坐标：ClientMessageRefitGun.handle 是拿
        // inventory.getItem(attachmentSlotIndex) 去取配件的，服务端只认自己那份背包。
        // 早先的写法是直接改客户端这份 NBT 假装装上，结果服务端毫不知情 —— 界面显示装了、
        // 服务端还是空槽，两边分叉。真正的"悬停虚拟装配"要克隆枪栈再驱动渲染管线（计划 §3.2），
        // 那属 M2；这里如实报错。
        notify(I18n.get("gui.z_tweaks.refit.msg.not_owned"));
    }

    private void unloadCurrent() {
        LocalPlayer player = getMinecraft().player;
        if (player == null) {
            return;
        }
        AttachmentType type = RefitTransform.getCurrentTransformType();
        if (type == AttachmentType.NONE) {
            notify(I18n.get("gui.z_tweaks.refit.msg.overview"));
            return;
        }
        ItemStack gun = gunStack();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            notify(I18n.get("gui.z_tweaks.refit.msg.overview"));
            return;
        }
        ItemStack installed = iGun.getAttachment(gun, type);
        if (installed.isEmpty()) {
            notify(I18n.get("gui.z_tweaks.refit.msg.overview"));
            return;
        }
        // 与原生同款护栏：背包没空位就别发包。服务端那边是 inventory.add(配件) 返回 false
        // 就**静默什么都不做**（连刷新包都不发），不预检的话点击会像没反应一样。
        if (player.getInventory().getFreeSlot() == -1) {
            notify(I18n.get("gui.tacz.gun_refit.unload.no_space"));
            return;
        }
        // 与原生一致：卸载音效取自被卸下那个配件自己
        SoundPlayManager.playerRefitSound(installed, player, SoundManager.UNINSTALL_SOUND);
        // 只发包：界面交给服务端回来的 ServerMessageRefreshRefitScreen 刷新（它会对
        // 当前 GunRefitScreen 调 init()）。不再本地抢跑写 NBT —— 服务端一旦拒绝，
        // 本地会一直显示"已卸下"。虚拟装配只是渲染件，与这里无关。
        NetworkHandler.CHANNEL.sendToServer(
                new ClientMessageUnloadAttachment(player.getInventory().selected, type));
        notify(I18n.get("gui.z_tweaks.refit.msg.unloaded", slotName(type)));
    }

    // ---------------------------------------------------------------- 小工具

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    /** 排序字段：按显示名，还是按模组（配件 id 的命名空间，即定义它的枪包/mod）。 */
    private enum SortField {NAME, MOD}

    /**
     * 候选列表里一行所需的全部信息，在 {@link #rebuildCandidates()} 里一次性算好：
     * 名字（本地化显示名，排序与绘制共用，避免每行重复查索引）与模组命名空间。
     */
    private record Candidate(ItemStack stack, int invSlot, String name, String modId) {
    }

    /** 候选面板当前能显示几行。几何与 {@link #listRect()} 同源，不会与绘制漂移。 */
    private int visibleRows() {
        return Math.max(1, (listRect().h() - LIST_HEADER - LIST_FOOTER) / ROW_H);
    }

    private void ensureSelectionVisible() {
        int rows = visibleRows();
        if (selected < scroll) {
            scroll = selected;
        } else if (selected >= scroll + rows) {
            scroll = selected - rows + 1;
        }
    }

    private Rect installRect() {
        return new Rect(PAD + this.width - PAD * 2 - 136, detailY() + DETAIL_H - 16, 64, 14);
    }

    private Rect unloadRect() {
        return new Rect(PAD + this.width - PAD * 2 - 68, detailY() + DETAIL_H - 16, 64, 14);
    }

    // ---------------------------------------------------------------- 绘制原语

    /** 圆角矩形填充：切掉四个角像素。1px 圆角就足够柔和，且不引入任何贴图依赖。 */
    private static void roundedFill(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) {
            return;
        }
        graphics.fill(x + 1, y, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** 圆角描边，与 {@link #roundedFill} 配套（同样缺角，否则边框会比填充多出一角）。 */
    private static void roundedBorder(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x + 1, y, x + w - 1, y + 1, color);
        graphics.fill(x + 1, y + h - 1, x + w - 1, y + h, color);
        graphics.fill(x, y + 1, x + 1, y + h - 1, color);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    /** 面板标准外观：上浅下深渐变 + 圆角 + 发丝描边。全场统一，各处不再自己调色。 */
    private static void panel(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fillGradient(x + 1, y, x + w - 1, y + h, PANEL_TOP, PANEL_BOTTOM);
        graphics.fill(x, y + 1, x + 1, y + h - 1, PANEL_TOP);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, PANEL_BOTTOM);
        roundedBorder(graphics, x, y, w, h, HAIRLINE);
    }

    /**
     * 按钮：常态/悬停/禁用三态。禁用态压暗并去饱和 —— 直接对应"这个动作现在做不了"，
     * 而不是让玩家点了没反应。
     */
    private void button(GuiGraphics graphics, Rect rect, String label, boolean hovered,
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
        graphics.drawCenteredString(this.font, label, rect.x() + rect.w() / 2, rect.y() + 4, textColor);
    }

    private String nameOf(ItemStack stack) {
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment != null) {
            ResourceLocation id = attachment.getAttachmentId(stack);
            return TimelessAPI.getClientAttachmentIndex(id)
                    .map(index -> Component.translatable(index.getName()).getString())
                    .orElse(id.getPath());
        }
        return stack.getHoverName().getString();
    }

    /**
     * 概览态信息卡：把 TACZ 枪械 tooltip 的每一行在详情条里自己画一遍，不整块渲染。
     *
     * <p>文案一律复用 TACZ 自己的 lang key（{@code tooltip.tacz.gun.*}），数值走
     * {@link AttachmentDataUtils} 的离线计算 —— 与 TACZ 的 {@code ClientGunTooltip}
     * 同一套来源，所以中英翻译、百分比口径都对得上。刻意不复制它的两个显示风格分支
     * （按弹丸显示伤害、按百分比显示弹容）：那两个是 tooltip 的表现层选择，
     * 我们这里是固定布局，只显示总伤害与 "当前/最大"。</p>
     *
     * <p>缓存是必须的：{@link AttachmentDataUtils} 每个方法都要遍历全部配件槽位重算，
     * 类注释本身就写了"不应该频繁调用"。这里按枪 id + NBT 缓存，换枪或改装后自动重算。</p>
     */
    private void buildGunInfo() {
        ItemStack gun = gunStack();
        IGun iGun = IGun.getIGunOrNull(gun);
        if (iGun == null) {
            infoMain.clear();
            infoStats.clear();
            infoExtra.clear();
            gunInfoId = null;
            gunInfoTag = null;
            return;
        }
        ResourceLocation id = iGun.getGunId(gun);
        CompoundTag tag = gun.getTag();
        if (id.equals(gunInfoId) && Objects.equals(gunInfoTag, tag)) {
            return;
        }
        gunInfoId = id;
        gunInfoTag = tag == null ? null : tag.copy();
        infoMain.clear();
        infoStats.clear();
        infoExtra.clear();
        // 换了枪就回到各列顶部：上一把枪的滚动位置没有意义
        Arrays.fill(infoScroll, 0);

        CommonGunIndex index = TimelessAPI.getCommonGunIndex(id).orElse(null);
        if (index == null) {
            return;
        }
        GunData gunData = index.getGunData();
        // 第一列：名字 + 描述（配色统一：数值白，描述灰）
        infoMain.add(Component.literal(gun.getHoverName().getString()).withStyle(ChatFormatting.WHITE));

        String tooltip = index.getPojo().getTooltip();
        if (tooltip != null) {
            int descLines = 0;
            for (String part : I18n.get(tooltip).split("\n")) {
                if (part.isBlank() || descLines >= 3) {
                    continue;
                }
                infoMain.add(Component.literal(part).withStyle(ChatFormatting.GRAY));
                descLines++;
            }
        }

        // 第二列：参数。口径只留文字 —— 弹药图标和弹容在改装界面里由 HUD 管
        infoStats.add(Component.literal(AmmoItemBuilder.create().setId(gunData.getAmmoId()).build()
                .getHoverName().getString()).withStyle(ChatFormatting.GRAY));

        int level = iGun.getLevel(gun);
        Component levelValue;
        if (level >= iGun.getMaxLevel()) {
            levelValue = Component.literal(String.format("%d (MAX)", level)).withStyle(ChatFormatting.WHITE);
        } else {
            int toNext = iGun.getExpToNextLevel(gun);
            int expCurrent = iGun.getExpCurrentLevel(gun);
            // TACZ 原式是 int 整除后再乘 100f，非满级时恒为 0.0%；这里改成浮点除法
            float percent = (toNext + expCurrent) == 0 ? 0f : expCurrent * 100f / (toNext + expCurrent);
            levelValue = Component.literal(String.format("%d (%.1f%%)", level, percent))
                    .withStyle(ChatFormatting.WHITE);
        }
        infoStats.add(labeled("tooltip.tacz.gun.level", levelValue));
        infoStats.add(labeled("tooltip.tacz.gun.type",
                Component.translatable("tacz.type." + index.getType() + ".name")
                        .withStyle(ChatFormatting.WHITE)));

        MutableComponent damageValue = Component
                .literal(DAMAGE_FORMAT.format(AttachmentDataUtils.getDamageWithAttachment(gun, gunData)))
                .withStyle(ChatFormatting.WHITE);
        ExplosionData explosion = gunData.getBulletData().getExplosionData();
        if (explosion != null
                && (AttachmentDataUtils.isExplodeEnabled(gun, gunData) || explosion.isExplode())) {
            damageValue.append(" + " + DAMAGE_FORMAT.format(
                            explosion.getDamage() * SyncConfig.DAMAGE_BASE_MULTIPLIER.get()))
                    .append(Component.translatable("tooltip.tacz.gun.explosion"));
        }
        infoStats.add(labeled("tooltip.tacz.gun.damage", damageValue));

        // 这两行的文案把数值包在 key 里（"25% 原版护甲穿透"），拆不出标签/数值两段，
        // 整行统一用灰；只有移动速度是负面数值，整行用红
        double armor = Mth.clamp(AttachmentDataUtils.getArmorIgnoreWithAttachment(gun, gunData), 0.0, 1.0);
        infoStats.add(Component.translatable("tooltip.tacz.gun.armor_ignore", PERCENT_FORMAT.format(armor))
                .withStyle(ChatFormatting.GRAY));
        infoStats.add(Component.translatable("tooltip.tacz.gun.head_shot_multiplier",
                        PERCENT_FORMAT.format(AttachmentDataUtils.getHeadshotMultiplier(gun, gunData)))
                .withStyle(ChatFormatting.GRAY));
        infoStats.add(Component.translatable("tooltip.tacz.gun.movement_speed", PERCENT_1_FORMAT.format(
                        -SyncConfig.WEIGHT_SPEED_MULTIPLIER.get()
                                * AttachmentDataUtils.getWightWithAttachment(gun, gunData)))
                .withStyle(ChatFormatting.RED));
        // 第三列：留给以后加的补充数据（开火模式、弹匣容量、内置配件……），当前没有就不画
    }

    /** 标签灰 + 数值白。TACZ 的标签 key 自带冒号，值直接接在后面。 */
    private static MutableComponent labeled(String labelKey, Component value) {
        return Component.translatable(labelKey).withStyle(ChatFormatting.GRAY).append(value);
    }

    /**
     * 三列铺进详情条，各列独立滚动，文字按 {@link #INFO_SCALE} 缩放。
     *
     * <p>缩放靠 {@code pose.scale}，所以坐标全在"局部空间"里：列宽要先除以缩放系数
     * 换算成局部宽度再交给 {@code font.split}，否则折行位置会算错。</p>
     */
    private void drawGunInfo(GuiGraphics graphics, int x, int y, int width, int mouseX, int mouseY) {
        buildGunInfo();
        float shareTotal = 0f;
        for (float share : INFO_COLUMN_SHARE) {
            shareTotal += share;
        }
        int usable = width - 12;
        float screenUnit = usable / shareTotal;
        float localUnit = screenUnit / INFO_SCALE;

        // 指针在哪一列：滚轮据此决定滚哪一列（屏幕坐标，不受缩放影响）
        infoHoverColumn = -1;
        float screenLeft = x + 6;
        for (int c = 0; c < INFO_COLUMNS; c++) {
            if (mouseX >= screenLeft && mouseX < screenLeft + INFO_COLUMN_SHARE[c] * screenUnit) {
                infoHoverColumn = c;
                break;
            }
            screenLeft += INFO_COLUMN_SHARE[c] * screenUnit;
        }

        List<List<Component>> columns = List.of(infoMain, infoStats, infoExtra);
        infoColumnLines = new int[INFO_COLUMNS];
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 6, y + 4, 0);
        pose.scale(INFO_SCALE, INFO_SCALE, 1f);
        float localLeft = 0f;
        for (int c = 0; c < INFO_COLUMNS; c++) {
            float localWidth = INFO_COLUMN_SHARE[c] * localUnit;
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component line : columns.get(c)) {
                lines.addAll(this.font.split(line, (int) (localWidth - 6)));
            }
            infoColumnLines[c] = lines.size();
            infoScroll[c] = Mth.clamp(infoScroll[c], 0, Math.max(0, lines.size() - INFO_ROWS));
            for (int i = infoScroll[c]; i < Math.min(lines.size(), infoScroll[c] + INFO_ROWS); i++) {
                graphics.drawString(this.font, lines.get(i), (int) localLeft,
                        (i - infoScroll[c]) * INFO_LINE_H, 0xFFFFFF, false);
            }
            localLeft += localWidth;
        }
        pose.popPose();
    }

    /** 详情条矩形：概览态下滚轮落在这里才滚信息卡。 */
    private Rect detailRect() {
        return new Rect(PAD, detailY(), this.width - PAD * 2, DETAIL_H);
    }

    /** 详情条左列（配件名字 + 描述）矩形：选中配件时滚轮落在这里才滚描述。 */
    private Rect attachColumnRect() {
        int width = this.width - PAD * 2;
        return new Rect(PAD, detailY(), (int) (width * 0.40f), DETAIL_H);
    }

    /**
     * 配件卡的左列（名字 + 描述）：排版与概览态信息卡对齐 —— {@link #INFO_SCALE} 缩放、
     * 按列宽换行、滚轮可翻。
     *
     * <p>名字仍用强调色而不是白色：它标的是"当前查看的配件"，跟候选列表的选中态呼应。
     * 换配件时滚动回到顶部（用名字 + 下标当键，换槽位由 {@link #init()} 归零）。</p>
     */
    private void drawAttachmentInfo(GuiGraphics graphics, int x, int y, int columnWidth) {
        ItemStack candidate = candidates.get(Math.min(selected, candidates.size() - 1));
        String key = nameOf(candidate) + "@" + selected;
        if (!key.equals(attachKey)) {
            attachKey = key;
            attachScroll = 0;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(nameOf(candidate)).withStyle(ChatFormatting.AQUA));
        for (String desc : describe(candidate)) {
            lines.add(Component.literal(desc).withStyle(ChatFormatting.GRAY));
        }
        for (int i = 0; i < Math.min(2, neutral.size()); i++) {
            lines.add(Component.literal(I18n.get("gui.z_tweaks.refit.neutral", neutral.get(i)))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        if (ZtConfig.DEBUG_SAMPLES.get()) {
            for (String sample : samples) {
                lines.add(Component.literal(I18n.get("gui.z_tweaks.refit.native", sample))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        graphics.renderItem(candidate, x + 6, y + 4);
        int rows = Math.max(1, (int) ((DETAIL_H - 12) / (INFO_LINE_H * INFO_SCALE)));
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(this.font.split(line, (int) ((columnWidth - 32) / INFO_SCALE)));
        }
        attachScroll = Mth.clamp(attachScroll, 0, Math.max(0, wrapped.size() - rows));
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x + 26, y + 4, 0);
        pose.scale(INFO_SCALE, INFO_SCALE, 1f);
        for (int i = attachScroll; i < Math.min(wrapped.size(), attachScroll + rows); i++) {
            graphics.drawString(this.font, wrapped.get(i), 0, (i - attachScroll) * INFO_LINE_H, 0xFFFFFF, false);
        }
        pose.popPose();
    }

    private List<String> describe(ItemStack stack) {
        List<String> lines = new ArrayList<>();
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        if (attachment == null) {
            return lines;
        }
        ResourceLocation id = attachment.getAttachmentId(stack);
        TimelessAPI.getClientAttachmentIndex(id).ifPresent(index -> {
            String key = index.getTooltipKey();
            if (key != null) {
                for (String part : I18n.get(key).split("\n")) {
                    if (!part.isBlank()) {
                        lines.add(part);
                    }
                }
            }
        });
        // 没有描述 key 就什么都不显示（不占位、不造假文案）
        return lines;
    }

    private String truncate(String text, int width) {
        if (this.font.width(text) <= width) {
            return text;
        }
        return this.font.plainSubstrByWidth(text, Math.max(0, width - 6)) + "...";
    }

    /**
     * 槽位的本地化名称：优先用附属自带的短名（zh_cn/en_us 里都有），
     * 缺失时（比如其它语言）退回 TACZ 官方的槽位译名，最后才是枚举名。
     */
    private static String slotName(AttachmentType type) {
        String suffix = type.name().toLowerCase(Locale.ROOT);
        String own = "gui.z_tweaks.refit.slot." + suffix;
        if (I18n.exists(own)) {
            return I18n.get(own);
        }
        String tacz = "tooltip.tacz.attachment." + suffix;
        if (I18n.exists(tacz)) {
            return I18n.get(tacz);
        }
        return suffix;
    }

    /** 槽位间距：至少能放下最宽的本地化槽位名，同时不让整条超出屏幕。 */
    private int slotStep(List<AttachmentType> types) {
        int widest = 0;
        for (AttachmentType type : types) {
            widest = Math.max(widest, this.font.width(slotName(type)));
        }
        int wanted = Math.max(SLOT + 2, widest + 6);
        int affordable = Math.max(SLOT + 2, (this.width - PAD * 2 - 8) / Math.max(1, types.size()));
        return Math.min(wanted, affordable);
    }

    private static String fmt(float value) {
        return String.format("%.2f", value);
    }

    private void notify(String message) {
        popup = message;
        popupUntil = System.currentTimeMillis() + 2500L;
    }
}
