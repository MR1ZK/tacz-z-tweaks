package com.ztweaks.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
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
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ztweaks.client.ZtUi.*;

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
    /**
     * 排序键的编码：0/1 是名称 / 模组，其余 = {@link StatCatalog#SORTABLE} 的下标 + {@link #SORT_STAT_BASE}。
     * 用一个 int 装下"字段排序"与"参数排序"两种东西，比较器里就只分两支。
     */
    private static final int SORT_NAME = 0;
    private static final int SORT_MOD = 1;
    private static final int SORT_STAT_BASE = 2;
    /** 排序 / 筛选弹出层：宽度、行高，以及分隔条另算的高度。 */
    private static final int MENU_W = 132;
    private static final int MENU_ROW_H = 11;
    private static final int MENU_PAD = 4;
    private static final int MENU_SEP_H = 4;
    /** 弹出层里的两个"非排序项"行，与"这一下点空了"的返回值。 */
    private static final int MENU_DIR = -1;
    private static final int MENU_FILTER = -2;
    private static final int MENU_SEP = -3;
    private static final int MENU_NONE = -4;
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

    // 本帧待渲染的 tooltip：后画的悬浮层必须在所有面板之后才不会被盖住，
    // 所以绘制阶段只登记，最后由 render() 末尾统一 flush。
    private Component pendingTooltip = null;
    private int tooltipX = 0;
    private int tooltipY = 0;

    private final List<Candidate> candidates = new ArrayList<>();

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
     * 候选列表排序偏好：{@link #SORT_NAME} / {@link #SORT_MOD} / 某个参数。
     * 切槽位<b>不</b>清空 —— 排序是玩家的全局习惯，不是某个槽位的属性（与搜索词相反）。
     */
    private int sortKey = SORT_NAME;
    /** 方向：true = 优→劣。名称/模组这两项没有"优劣"可言，就是 A-Z / Z-A。 */
    private boolean sortBestFirst = true;
    /** 筛选：只看对该参数有改善的配件。只对参数排序有意义，名称/模组下那一行置灰。 */
    private boolean onlyImproving = false;
    /** 排序 / 筛选弹出层是否展开。它是底部那一行唯一的排序入口。 */
    private boolean sortMenuOpen = false;
    /** 弹出层内容的滚动偏移（像素）。只有内容高过视口时才有意义，见 {@link #sortMenuRect()}。 */
    private int sortMenuScroll = 0;

    /** 上一次点击候选条目的时间戳 / 下标 / 鼠标键，用于双击判定。 */
    private long lastRowClickTime = 0L;
    private int lastRowClickIndex = -1;
    private int lastRowClickButton = -1;

    /** 信息卡三列：名字+描述 / 参数（当前值） / 参数（变化项）。 */
    private final List<Component> infoMain = new ArrayList<>();
    private final List<Component> infoStats = new ArrayList<>();
    private final List<Component> infoExtra = new ArrayList<>();
    /**
     * 第二列那张表的原始行（{@link ParamRow}）：第三列按同一顺序挑出"值不一样的行"，
     * 两列因此不会各算一套，也不会顺序对不上（issue #7/#8）。
     */
    private List<ParamRow> infoRows = new ArrayList<>();
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
        this.invalidateCandidates();
        // 弹出层跟着关：init() 会被切槽位与服务端刷新（装/卸完成后）触发，
        // 此时底下的列表已经换了一批，留着旧菜单容易点到不存在的内容。
        this.sortMenuOpen = false;
        this.sortMenuScroll = 0;
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
            invalidateCandidates();
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
     * 弹出层自上而下的行：名称 / 模组 / 十个参数项 / 方向 / 筛选。
     *
     * <p>静态建一次 —— 它每帧都要被绘制与命中检测各走一遍，没必要每帧重建。</p>
     */
    private static final List<Integer> SORT_MENU_ROWS = buildSortMenuRows();

    private static List<Integer> buildSortMenuRows() {
        List<Integer> rows = new ArrayList<>();
        rows.add(SORT_NAME);
        rows.add(SORT_MOD);
        rows.add(MENU_SEP);
        for (int i = 0; i < StatCatalog.SORTABLE.size(); i++) {
            rows.add(SORT_STAT_BASE + i);
        }
        rows.add(MENU_SEP);
        rows.add(MENU_DIR);
        rows.add(MENU_FILTER);
        return List.copyOf(rows);
    }

    /** 当前排序键对应的参数项；名称 / 模组时返回 null。 */
    @Nullable
    private StatCatalog.StatDef selectedStat() {
        return statOf(sortKey);
    }

    @Nullable
    private static StatCatalog.StatDef statOf(int key) {
        int index = key - SORT_STAT_BASE;
        return index >= 0 && index < StatCatalog.SORTABLE.size() ? StatCatalog.SORTABLE.get(index) : null;
    }

    /**
     * 改排序键。
     *
     * <p>改完必须把 {@code cachedType} 置空触发下一帧重建 —— 候选列表按槽位类型缓存，
     * 不置空的话排序不会立即生效。</p>
     */
    private void selectSortKey(int key) {
        sortKey = key;
        invalidateCandidates();
    }

    private void toggleSortDirection() {
        sortBestFirst = !sortBestFirst;
        invalidateCandidates();
    }

    private void toggleImprovingFilter() {
        onlyImproving = !onlyImproving;
        invalidateCandidates();
    }

    /** 排序按钮上的文字：名称/模组沿用 A-Z / Z-A，参数项写成"后坐力 优→劣"。 */
    private String sortLabel() {
        if (sortKey == SORT_NAME || sortKey == SORT_MOD) {
            String field = sortKey == SORT_NAME ? "name" : "mod";
            return I18n.get("gui.z_tweaks.refit.sort." + field + (sortBestFirst ? ".asc" : ".desc"));
        }
        StatCatalog.StatDef stat = selectedStat();
        String name = stat == null ? "" : I18n.get(stat.langKey());
        return I18n.get("gui.z_tweaks.refit.sort.stat", name,
                I18n.get("gui.z_tweaks.refit.sort." + (sortBestFirst ? "best" : "worst")));
    }

    /** 弹出层一行的文字。方向行与筛选行把当前状态写在冒号后面，不另占控件。 */
    private String sortMenuLabel(int key) {
        if (key == SORT_NAME) {
            return I18n.get("gui.z_tweaks.refit.sort.key.name");
        }
        if (key == SORT_MOD) {
            return I18n.get("gui.z_tweaks.refit.sort.key.mod");
        }
        if (key == MENU_DIR) {
            return I18n.get("gui.z_tweaks.refit.sort.dir",
                    I18n.get("gui.z_tweaks.refit.sort." + (sortBestFirst ? "best" : "worst")));
        }
        if (key == MENU_FILTER) {
            return I18n.get("gui.z_tweaks.refit.sort.filter",
                    I18n.get("gui.z_tweaks.refit.sort.filter." + (onlyImproving ? "on" : "off")));
        }
        StatCatalog.StatDef stat = statOf(key);
        return stat == null ? "" : I18n.get(stat.langKey());
    }

    /** 该行是不是"当前选中的排序键"。方向行 / 筛选行不是选项，永远不高亮。 */
    private boolean isSortKeyActive(int key) {
        return key >= SORT_NAME && key == sortKey;
    }

    /**
     * 筛选只在参数排序下有意义：名称 / 模组没有"改善"可言。
     * 这一行此时置灰并忽略点击 —— 与其藏起来让弹出层高度跳变，不如留着位置。
     */
    private boolean filterAvailable() {
        return selectedStat() != null;
    }

    /** 弹出层内容的总高度（十四行 + 两个分隔条 + 上下内边距），不受视口限制。 */
    private static int sortMenuContentHeight() {
        int height = MENU_PAD * 2;
        for (int key : SORT_MENU_ROWS) {
            height += key == MENU_SEP ? MENU_SEP_H : MENU_ROW_H;
        }
        return height;
    }

    /**
     * 弹出层矩形：右对齐贴在排序按钮上方、向上展开，并**夹进候选面板与窗口之内**。
     *
     * <p>两个约束都不是可选的：面板高度是 {@code GUI 高 − 134}，而弹层内容有
     * {@code 170px}，所以在 GUI 高度不足 320（例如 1080p 下 GUI scale 4 = 480×270）时，
     * 按内容高度直接向上展开会把顶部十几像素顶到屏幕外，最上面两行（名称 / 模组）直接看不见。
     * 这里改成"最多长到可用高度"，剩下的靠 {@link #sortMenuScroll} 滚——见 {@link #drawSortMenu}。</p>
     *
     * <p>往候选面板内侧长，是因为底部那一行已经塞满（搜索框 120 + 间隙 4 + 排序按钮 58 = 182，
     * 而面板内宽只有 182），既没地方放第二个按钮，也不该为它加宽面板。</p>
     */
    private Rect sortMenuRect() {
        Rect list = listRect();
        int top = Math.max(2, list.y() + LIST_HEADER);
        int bottom = footerY(list) - 2;
        int height = Math.max(MENU_ROW_H * 2 + MENU_PAD * 2,
                Math.min(sortMenuContentHeight(), bottom - top));
        Rect sort = sortRect();
        int x = Math.min(sort.x() + sort.w() - MENU_W, this.width - MENU_W - 2);
        return new Rect(Math.max(2, x), Math.max(2, bottom - height), MENU_W, height);
    }

    /** 内容高过视口时最多能滚多少像素。 */
    private int sortMenuMaxScroll() {
        return Math.max(0, sortMenuContentHeight() - sortMenuRect().h());
    }

    /** 弹出层里鼠标落在哪一行（已算上滚动偏移）；落在内边距或分隔条上返回 {@link #MENU_NONE}。 */
    private int sortMenuKeyAt(double mouseX, double mouseY) {
        Rect menu = sortMenuRect();
        if (!menu.contains(mouseX, mouseY)) {
            return MENU_NONE;
        }
        int contentY = (int) (mouseY - menu.y()) + sortMenuScroll;
        int y = MENU_PAD;
        for (int key : SORT_MENU_ROWS) {
            int height = key == MENU_SEP ? MENU_SEP_H : MENU_ROW_H;
            if (contentY >= y && contentY < y + height) {
                return key == MENU_SEP ? MENU_NONE : key;
            }
            y += height;
        }
        return MENU_NONE;
    }

    /** 点在弹出层的某一行上：排序项 = 选它，方向行 / 筛选行 = 翻转。 */
    private void clickSortMenu(int key) {
        if (key == MENU_DIR) {
            toggleSortDirection();
        } else if (key == MENU_FILTER) {
            if (filterAvailable()) {
                toggleImprovingFilter();
            }
        } else {
            selectSortKey(key);
        }
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
     * 候选配件：分两组装进同一个列表，靠 {@link Candidate#compat()} 与排序器分组（见 ADR 与
     * issue #6 的解决评论）。
     *
     * <p>第一组是**能装**的（{@code iGun.allowAttachment} 通过）：生存模式只列背包里有的 ——
     * 装配件要把背包槽位发给服务端，没带在身上的点了也装不上；创造模式列全部，没带在身上的
     * 在列表里把图标标灰（能预览、装不上）。TACZ 原生两种模式都只扫背包，创造分支是我们加的。</p>
     *
     * <p>第二组是**不可安装**、但玩家确实拥有的：置底、灰显、带角标。只在"玩家握着它"时出现
     * —— 创造模式下列出全部不可安装的配件是纯噪音（数量还是"能装"那批的好几倍），而且没有
     * "我手里这件为什么装不上"这个困惑需要解释。这一组不参与"只看改善的"筛选：它们不是候选，
     * 是解释。</p>
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
            cachedType = type;
            return;
        }
        if (type == cachedType) {
            return;
        }
        cachedType = type;
        // 枪压根没声明白名单时，整批配件都是这一档（角标「这里装不了配件」）。
        boolean noWhitelist = whitelistEmpty(iGun.getGunId(gun));
        // 参数排序 / 筛选才需要逐候选跑一次属性求值。名称/模组排序下这一整段都不执行，
        // 一分钱不花。
        StatCatalog.StatDef stat = selectedStat();
        GunData gunData = null;
        AttachmentCacheProperty base = null;
        if (stat != null) {
            gunData = TimelessAPI.getCommonGunIndex(iGun.getGunId(gun))
                    .map(CommonGunIndex::getGunData).orElse(null);
            base = baseCache(gun, gunData);
            if (base == null) {
                // 枪械数据缺失或畸形：这一轮退化成"没有参数排序"，界面照常。
                stat = null;
            }
        }
        List<Map.Entry<ResourceLocation, CommonAttachmentIndex>> all =
                new ArrayList<>(TimelessAPI.getAllCommonAttachmentIndex());
        Inventory inventory = player.getInventory();
        List<Candidate> found = new ArrayList<>();
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : all) {
            if (entry.getValue().getType() != type) {
                continue;
            }
            ItemStack stack = AttachmentItemBuilder.create().setId(entry.getKey()).build();
            Compat compat = iGun.allowAttachment(gun, stack)
                    ? Compat.OK
                    : (noWhitelist ? Compat.NO_WHITELIST : Compat.NOT_LISTED);
            if (!searchQuery.isBlank() && !matchesQuery(nameOf(stack))) {
                continue;
            }
            int invSlot = findInventorySlot(inventory, entry.getKey());
            if (compat == Compat.OK) {
                // 生存模式只列真正带在身上的：装配件要把背包槽位发给服务端，没带在身上的
                // 点了也装不上。创造模式列全部，没带在身上的在列表里标灰（能预览、装不上）。
                if (invSlot < 0 && !player.isCreative()) {
                    continue;
                }
            } else if (invSlot < 0) {
                // 不可安装那组只在"玩家真有"时出现，创造模式的"全都看得见"不适用于它。
                continue;
            }
            // 改善量只对能装的算：不可安装的组合不存在，"装上之后"是一组没有意义的数。
            double improvement = compat != Compat.OK || stat == null
                    ? 0 : measureImprovement(stat, gun, iGun, gunData, base, stack);
            // 筛选：只看比"现在装着的"更好的。刻意不区分是否拥有 —— 拥有性是生存/创造规则的
            // 职责（上面那一行已经在管），在这里再滤一次只会让创造模式下的列表行为变得难以解释。
            if (compat == Compat.OK && stat != null && onlyImproving && improvement <= 0) {
                continue;
            }
            found.add(new Candidate(stack, invSlot, compat, nameOf(stack),
                    entry.getKey().getNamespace(), improvement));
        }
        found.sort(candidateComparator());
        candidates.clear();
        candidates.addAll(found);
        selected = 0;
        scroll = 0;
        samplesDirty = true;
    }

    /**
     * 枪的 {@code allow_attachment_tags} 是不是空的。TACZ 自己的注释写着"为空说明目前没有任何
     * 可以装的配件"（{@code AllowAttachmentTagMatcher.match0}），所以这就是「这里装不了配件」
     * 那一档的判据 —— 与它给出的兼容判定同源，两句说辞不会打架。
     *
     * <p>代价：又多了一个 TACZ 内部类的接触点。这条记在 issue #13 的盘点上，属于"收不掉、
     * 只能集中 + 告警"的那一类。</p>
     */
    private static boolean whitelistEmpty(@Nullable ResourceLocation gunId) {
        if (gunId == null) {
            // 枪 id 都拿不到（理论上不该发生）：退回"白名单非空"，说得保守一点。
            return false;
        }
        Set<String> tags = CommonAssetsManager.get().getAllowAttachmentTags(gunId);
        return tags == null || tags.isEmpty();
    }

    /**
     * 候选排序：**已拥有的（可用）配件永远置顶**，其次才按玩家选的排序方式。
     *
     * <p>置顶是独立的一层、优先级高于排序方式：创造模式下列表里混着大量没带在身上的配件
     * （图标盖灰、点了装不上），把真正能装的排到最前面，少滚一半列表。生存模式下列表本来
     * 就只剩背包里的，这一层退化成空操作。</p>
     */
    private Comparator<Candidate> candidateComparator() {
        Comparator<Candidate> byField;
        if (sortKey == SORT_NAME) {
            byField = Comparator.comparing((Candidate c) -> c.name().toLowerCase(Locale.ROOT))
                    .thenComparing(c -> c.modId().toLowerCase(Locale.ROOT));
        } else if (sortKey == SORT_MOD) {
            byField = Comparator.comparing((Candidate c) -> c.modId().toLowerCase(Locale.ROOT))
                    .thenComparing(c -> c.name().toLowerCase(Locale.ROOT));
        } else {
            // 参数排序：improvement 已经是"越大越好"（见 StatCatalog），所以"优→劣"要的是**降序**——
            // 与名称/模组的 A-Z（升序）恰好相反，方向开关的语义在两种排序下不一样，见下面。
            // 同分按名字兜底：否则连续两帧重建（切槽位、改搜索）可能给出不同顺序，看着像抖动。
            byField = Comparator.comparingDouble((Candidate c) -> c.improvement())
                    .thenComparing(c -> c.name().toLowerCase(Locale.ROOT));
        }
        // 方向开关的两种含义：名称/模组下 sortBestFirst = A-Z（升序），
        // 参数项下 sortBestFirst = 优→劣（降序）。不区分的话，按后坐力排序会变成"最差的在最前"。
        boolean ascending = sortKey == SORT_NAME || sortKey == SORT_MOD ? sortBestFirst : !sortBestFirst;
        if (!ascending) {
            byField = byField.reversed();
        }
        Comparator<Candidate> usable = Comparator.comparingInt((Candidate c) -> c.invSlot() >= 0 ? 0 : 1)
                .thenComparing(byField);
        // 不可安装的那一组固定按名字排：它们不参与"按参数""方向"这些候选语义 —— 那是"选哪个装"
        // 的问题，而它们装不上。放在列表里只为解释"手里这件为什么装不上"（见 issue #6）。
        Comparator<Candidate> blocked = Comparator.comparing(c -> c.name().toLowerCase(Locale.ROOT));
        return (a, b) -> {
            boolean okA = a.compat() == Compat.OK;
            boolean okB = b.compat() == Compat.OK;
            if (okA != okB) {
                return okA ? -1 : 1;
            }
            return okA ? usable.compare(a, b) : blocked.compare(a, b);
        };
    }

    /**
     * 当前枪械状态的属性缓存，作为"改善量"的对比基准。
     *
     * <p>整轮列表重建只跑这一次 —— 每个候选各跑一次就已经够了（它们共享同一把枪、
     * 只是换掉同一个槽位）。</p>
     *
     * <p>包在 try 里：{@link AttachmentCacheProperty#eval} 会把全部 16 个 modifier 都算一遍，
     * 枪包里任何一项数据畸形（例如后坐力曲线缺帧）都会在这里抛 NPE。那是数据问题，
     * 不该把界面带崩 —— 返回 null，调用方退化成"这一轮没有参数排序"。</p>
     */
    @Nullable
    private static AttachmentCacheProperty baseCache(ItemStack gun, @Nullable GunData gunData) {
        if (gunData == null) {
            return null;
        }
        try {
            AttachmentCacheProperty cache = new AttachmentCacheProperty();
            cache.eval(gun, gunData);
            return cache;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 量"这个候选件装上之后"对该参数的**边际**改善量（正数 = 比现在装着的强）。
     *
     * <p>克隆枪栈 + 在内存里装件 + 求值，不落 NBT、不发 packet（与虚拟装配同一条路子）。
     * 代价说明：{@code eval} 会遍历枪上全部配件槽把 16 个 modifier 重算一遍，TACZ 类注释
     * 写明"不应该频繁调用"——所以它只在列表重建时跑（切槽位 / 改搜索 / 改排序），不是每帧。</p>
     *
     * <p>为什么不直接用 {@link StatCatalog#read} 的绝对值当改善量：那量的是"相对裸枪"，
     * 会把"比裸枪好、但不如当前已装件"的候选也算成改善。要的是"比现在更好"，
     * 所以拿装前装后两份 cache 相减。</p>
     */
    private static double measureImprovement(StatCatalog.StatDef stat, ItemStack gun, IGun iGun,
                                             GunData gunData, AttachmentCacheProperty base,
                                             ItemStack candidate) {
        try {
            ItemStack modified = gun.copy();
            iGun.installAttachment(modified, candidate);
            AttachmentCacheProperty after = new AttachmentCacheProperty();
            after.eval(modified, gunData);
            return StatCatalog.marginal(stat, modified, gunData, base, after);
        } catch (Exception e) {
            return 0;
        }
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
        if (selectedCompat() != Compat.OK) {
            // 不可安装的件不给 Pros/Cons 与参数：那是"装上之后"的账，而这个组合不存在。
            return;
        }
        if (ZtConfig.PROS_CONS_MODE.get() == ZtConfig.ProsConsMode.TACZ_TEXT) {
            computeProsConsTaczText();
        } else {
            computeProsConsDelta();
        }
    }

    private void computeProsConsTaczText() {
        ItemStack candidate = candidates.get(Math.min(selected, candidates.size() - 1)).stack();
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
        ItemStack candidate = candidates.get(Math.min(selected, candidates.size() - 1)).stack();
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
                // 槽位条现在有两种按法：装着配件的槽位把"右键=卸下"直接写进提示，省得玩家去猜
                String hint = !allowed
                        ? I18n.get("gui.z_tweaks.refit.msg.slot_not_allowed", slotName(type))
                        : (installed.isEmpty() ? slotName(type)
                        : slotName(type) + "\n" + I18n.get("gui.z_tweaks.refit.tip.right_click_unload"));
                tooltip(Component.literal(hint), (int) mouseX, (int) mouseY);
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
        // 「这里装不了配件」是整个槽位的事：逐行贴同一句话是成片的重复，标题行说一次。
        // 太挤就不画（英文标题更长），行内角标还在，信息不丢。
        if (hasNoWhitelistRows()) {
            String note = I18n.get("gui.z_tweaks.refit.blocked.slot");
            int noteX = badgeX - 5 - this.font.width(note);
            int titleRight = x + 6 + this.font.width(I18n.get("gui.z_tweaks.refit.candidates.title"));
            if (noteX > titleRight + 4) {
                graphics.drawString(this.font, note, noteX, y + 4, BLOCKED, false);
            }
        }
        graphics.fill(x + 4, y + LIST_HEADER - 3, right - 4, y + LIST_HEADER - 2, HAIRLINE);

        int rows = visibleRows();
        int maxScroll = Math.max(0, candidates.size() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        hoveredRow = -1;
        int listTop = y + LIST_HEADER;

        if (candidates.isEmpty()) {
            // 空列表有三种成因，不能混用一句：被搜索词筛没了 / 枪包压根没声明白名单 /
            // 白名单里有、但你现在手上没有能装的。第二句与行内角标用同一串字 ——
            // 同一个成因，同一个说法。
            String key;
            if (!searchQuery.isBlank()) {
                key = "gui.z_tweaks.refit.candidates.no_match";
            } else if (whitelistEmpty(heldGunId())) {
                key = "gui.z_tweaks.refit.blocked.slot";
            } else {
                key = "gui.z_tweaks.refit.candidates.empty";
            }
            graphics.drawString(this.font, I18n.get(key), x + 6, listTop + 4, TEXT_MUTED, false);
        }

        // 裁剪：滚动/悬停的行不会越出面板边线
        graphics.enableScissor(x + 1, listTop, right - 1, listTop + rows * ROW_H);
        int firstBlocked = firstBlockedIndex();
        for (int i = 0; i < rows && scroll + i < candidates.size(); i++) {
            int index = scroll + i;
            Rect row = rowRect(list, i);
            boolean isSelected = index == selected;
            boolean hovered = !dragging && row.contains(mouseX, mouseY);
            if (hovered) {
                hoveredRow = index;
            }
            Candidate entry = candidates.get(index);
            boolean owned = entry.invSlot() >= 0;
            String badge = blockedKey(entry.compat());

            if (index == firstBlocked) {
                // 不可安装那批的上面压一条线把它隔开：不折叠、不分组，一条线就够了。
                // 画在行间那 2px 的缝里，不额外吃一行高度。
                graphics.fill(row.x(), row.y() - 2, row.x() + row.w(), row.y() - 1, 0x66FFFFFF);
            }
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
            graphics.renderItem(entry.stack(), row.x() + 5, row.y() + 1);
            if (!owned || badge != null) {
                // 图标盖一层半透明黑：这两种行都点不动 —— 没带在身上的（服务端取不到件）
                // 与不可安装的（服务端两份包都会拒）。不标出来会让"能预览"被误读成"能装"。
                // 前者只在创造模式看得到，后者只在玩家真握着它时出现。
                graphics.fill(row.x() + 5, row.y() + 1, row.x() + 21, row.y() + 17, 0x80000000);
            }
            // 名字在 rebuildCandidates 里已经算好存在 Candidate 上，这里直接用，不再逐帧查索引
            String name = entry.name();
            int badgeWidth = badge == null ? 0 : this.font.width(badge) + 5;
            graphics.drawString(this.font, this.font.plainSubstrByWidth(name, row.w() - 34 - badgeWidth),
                    row.x() + 24, row.y() + 5, owned && badge == null ? TEXT : TEXT_MUTED, false);
            if (badge != null) {
                // 角标必须是文字：列表里已经有另一种灰（没带在身上的），光靠颜色分不开这两种"点不动"。
                // 名字按剩余宽度截断即可 —— 悬停有完整名字的 tooltip，不为角标加宽面板。
                graphics.drawString(this.font, badge, row.x() + row.w() - 3 - this.font.width(badge),
                        row.y() + 5, BLOCKED, false);
            }
            if (hovered) {
                tooltip(Component.literal(name), (int) mouseX, (int) mouseY);
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
        if (sortMenuOpen && candidateListVisible()) {
            drawSortMenu(graphics, mouseX, mouseY);
        }
    }

    /**
     * 底部这一行右侧的排序按钮：显示当前排序方式（如"后坐力 优→劣"），点击展开排序 / 筛选层。
     *
     * <p>自绘而非用 {@link ZtUi#button}：那个 helper 是安装/卸下的绿红语义（primary/secondary），
     * 排序是中性控件，套上去会让人误以为它是个"确认/取消"。观感对齐同一行的搜索框。</p>
     *
     * <p>弹出层展开期间按钮保持"按下"的样子，让玩家看得出这层是从哪冒出来的。</p>
     */
    private void drawSortButton(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect rect = sortRect();
        boolean hovered = !dragging && rect.contains(mouseX, mouseY);
        boolean active = hovered || sortMenuOpen;
        roundedFill(graphics, rect.x(), rect.y(), rect.w(), rect.h(), active ? 0x40FFFFFF : 0x40000000);
        roundedBorder(graphics, rect.x(), rect.y(), rect.w(), rect.h(), active ? 0x88FFFFFF : HAIRLINE);
        graphics.drawCenteredString(this.font, truncate(sortLabel(), rect.w() - 6),
                rect.x() + rect.w() / 2, rect.y() + 2, active ? TEXT : TEXT_DIM);
        if (hovered) {
            tooltip(Component.literal(I18n.get("gui.z_tweaks.refit.sort.tooltip")),
                    (int) mouseX, (int) mouseY);
        }
    }

    /**
     * 排序 / 筛选弹出层。
     *
     * <p>画在候选列表之上、最后一层，所以关闭前它会盖住列表下缘 —— 这也是它展开时
     * 点击必须优先被它吃掉的原因（见 {@code mouseClicked}）。</p>
     */
    private void drawSortMenu(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect menu = sortMenuRect();
        sortMenuScroll = Mth.clamp(sortMenuScroll, 0, sortMenuMaxScroll());
        roundedFill(graphics, menu.x(), menu.y(), menu.w(), menu.h(), 0xF0101010);
        roundedBorder(graphics, menu.x(), menu.y(), menu.w(), menu.h(), 0x88FFFFFF);
        // 内容可能高过视口（见 sortMenuRect），超出部分一律裁掉，而不是画到面板外面去
        graphics.enableScissor(menu.x() + 1, menu.y() + 1, menu.x() + menu.w() - 1, menu.y() + menu.h() - 1);
        boolean inside = menu.contains(mouseX, mouseY);
        int y = menu.y() + MENU_PAD - sortMenuScroll;
        for (int key : SORT_MENU_ROWS) {
            int height = key == MENU_SEP ? MENU_SEP_H : MENU_ROW_H;
            if (key == MENU_SEP) {
                graphics.fill(menu.x() + 4, y + 1, menu.x() + menu.w() - 4, y + 2, HAIRLINE);
            } else {
                boolean hovered = inside && mouseY >= y && mouseY < y + height;
                // 不可用的行（没选参数时的筛选开关）画得更暗，但保留位置，避免展开高度跳变
                boolean usable = key != MENU_FILTER || filterAvailable();
                boolean selected = isSortKeyActive(key);
                int color = selected ? ACCENT : (hovered ? TEXT : TEXT_DIM);
                if (hovered && usable) {
                    roundedFill(graphics, menu.x() + 2, y, menu.w() - 4, height, 0x20FFFFFF);
                }
                graphics.drawString(this.font, truncate(sortMenuLabel(key), menu.w() - 22),
                        menu.x() + 8, y + 2, usable ? color : TEXT_MUTED, false);
                if (selected) {
                    graphics.drawString(this.font, "•", menu.x() + menu.w() - 10, y + 2, ACCENT, false);
                }
            }
            y += height;
        }
        graphics.disableScissor();
        // 上/下沿还有没露出来的行时压一条暗条当提示。刻意不用箭头字形——不押注默认字体里有没有那个码位。
        if (sortMenuScroll > 0) {
            graphics.fill(menu.x() + 1, menu.y() + 1, menu.x() + menu.w() - 1, menu.y() + 3, 0x66000000);
        }
        if (sortMenuScroll < sortMenuMaxScroll()) {
            graphics.fill(menu.x() + 1, menu.y() + menu.h() - 3, menu.x() + menu.w() - 1, menu.y() + menu.h() - 1, 0x66000000);
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
        // 不可安装的件不虚拟装配：3D 讲的是"装上之后那把枪"，而那个组合不可能存在
        // （与 #9 拒绝"一把不存在的枪配一组真实的数"同一条理由）。
        if (hoveredRow < 0 || hoveredRow >= candidates.size() || type == AttachmentType.NONE
                || candidates.get(hoveredRow).compat() != Compat.OK) {
            VirtualAssembly.clear();
            return;
        }
        VirtualAssembly.setPreview(type, candidates.get(hoveredRow).stack());
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
        // 能装 = 拥有 + 兼容。只看"拥有"会让不可安装的行亮着按钮，点下去服务端两份包都静默拒绝，
        // 而客户端已经放过安装音效、弹过「已安装」—— 界面说装上了，枪上其实什么都没有。
        boolean installEnabled = selectedOwned() && selectedCompat() == Compat.OK;
        // 概览态不画这两个按钮：没有选中槽位，它们永远处于禁用态，
        // 只会占着参数卡右下的空间。安装/卸载的提示也只跟按钮走。
        if (RefitTransform.getCurrentTransformType() != AttachmentType.NONE) {
            boolean unloadEnabled = canUnload();
            button(graphics, this.font, installRect, I18n.get("gui.z_tweaks.refit.install"),
                    installRect.contains(mouseX, mouseY) && !dragging, true, installEnabled);
            button(graphics, this.font, unloadRect, I18n.get("gui.z_tweaks.refit.unload"),
                    unloadRect.contains(mouseX, mouseY) && !dragging, false, unloadEnabled);
            // 不可用时把原因说清楚：悬停给提示，而不是点了没反应
            if (!dragging && installRect.contains(mouseX, mouseY) && !installEnabled) {
                // 禁用原因分两种：不可安装说"为什么装不上"，没带在身上说"缺件"
                String blocked = blockedKey(selectedCompat());
                tooltip(Component.literal(
                                I18n.get(blocked != null ? blocked : "gui.z_tweaks.refit.msg.not_owned")),
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

    /** 当前选中的候选；列表为空时给 null。选中下标越界时钳到最后一项（与各处绘制一致）。 */
    @Nullable
    private Candidate selectedCandidate() {
        return candidates.isEmpty() ? null : candidates.get(Math.min(selected, candidates.size() - 1));
    }

    /** 当前选中候选的兼容档；没有选中项时当作"能装"，让不做额外拦截的调用点保持原行为。 */
    private Compat selectedCompat() {
        Candidate c = selectedCandidate();
        return c == null ? Compat.OK : c.compat();
    }

    /** 不可安装的角标 / 提示文案 key；能装时为 null。三处（行内角标、标题行、空列表）共用同一串字。 */
    @Nullable
    private static String blockedKey(Compat compat) {
        return switch (compat) {
            case OK -> null;
            case NOT_LISTED -> "gui.z_tweaks.refit.blocked.this";
            case NO_WHITELIST -> "gui.z_tweaks.refit.blocked.slot";
        };
    }

    /** 选中候选是否在背包里。不在就发不出包（服务端只认自己那份背包），按钮据此禁用。 */
    private boolean selectedOwned() {
        Candidate c = selectedCandidate();
        return c != null && c.invSlot() >= 0;
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
        // 槽位条（几何与绘制同源：slotRect）：左键=选中该槽位，右键=直接卸下该槽位上的配件。
        // 右键在这里就早退，才不会落到底部"空白处=平移"那一支去拖相机。
        List<AttachmentType> types = slotTypes();
        for (int i = 0; i < types.size(); i++) {
            if (slotRect(types, i).contains(mouseX, mouseY)) {
                if (button == 1) {
                    unloadSlot(types.get(i));
                } else {
                    selectSlot(types.get(i));
                }
                return true;
            }
        }
        // 排序 / 筛选弹出层：它盖在候选列表之上，所以判定必须排在列表行之前 —— 否则点弹出层里
        // 的一行会顺带把底下那行的配件选中、甚至触发双击装上。
        if (sortMenuOpen) {
            if (candidateListVisible() && sortMenuRect().contains(mouseX, mouseY)) {
                // 落在分隔条或内边距上也算"点在弹层里"：吃掉这一下，但既不做事也不收起 ——
                // 否则想点某一行、手抖偏到分隔条上就把整个弹层关了。
                int key = sortMenuKeyAt(mouseX, mouseY);
                if (key != MENU_NONE) {
                    clickSortMenu(key);
                }
                return true;
            }
            // 点别处 = 收起。这一下照样吃掉：不穿透给下面的列表，避免"想关菜单却装了个配件"。
            sortMenuOpen = false;
            return true;
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
        // 排序按钮：与搜索框同一行，只在候选框可见时存在（几何与绘制同源：sortRect）。
        // 点它只负责开合弹出层 —— 排序键、方向、筛选全在那层里选，按钮不再循环切换。
        if (candidateListVisible() && sortRect().contains(mouseX, mouseY)) {
            sortMenuOpen = !sortMenuOpen;
            sortMenuScroll = 0;   // 每次展开都从顶上开始，不记住上次滚到哪
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
                //
                // 手感语义（下拖 / 上拖互为反向，凭这个分支里的正负号切换）：
                // 鼠标下拖 = 枪"看得见的那一面"向观察者翻过来，上拖翻回去。
                // 取负是 d10311a 按手感反馈从 +fy 定的，也是这里唯一的方向开关，觉得反了改这一个符号即可。
                // 核查记录：构造（build/classes）与发布 jar（含游戏实例里那份）的 m_7979_ 字节码
                // 都在 dneg 之后紧跟 dmul + rotateRoll，即本行确实编译进去了。
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
        // 弹出层展开时，指针落在它上面就滚它自己（内容可能高过视口），不穿透给底下的列表
        if (sortMenuOpen && sortMenuRect().contains(mouseX, mouseY)) {
            sortMenuScroll = Mth.clamp(sortMenuScroll - (int) Math.signum(delta) * MENU_ROW_H,
                    0, sortMenuMaxScroll());
            return true;
        }
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
        Candidate entry = candidates.get(Math.min(selected, candidates.size() - 1));
        String blocked = blockedKey(entry.compat());
        if (blocked != null) {
            // 护栏只有这一条，双击 / 安装按钮 / ENTER 都走它：不可安装的件在这里就停住，
            // 不放音效、不发包。服务端那两个包都会静默拒绝（allowAttachment 不过就 return），
            // 客户端要是照旧弹「已安装」，界面就会说装上了而枪上什么都没有。
            notify(I18n.get(blocked));
            return;
        }
        ItemStack candidate = entry.stack();
        int inventorySlot = entry.invSlot();
        if (inventorySlot >= 0) {
            AttachmentType type = RefitTransform.getCurrentTransformType();
            // 与原生一致：声音在发包前就放（原生 GunRefitScreen 同款），不等服务端确认。
            // 音效取自被装配件自己的 pack 定义，我们只负责触发。
            SoundPlayManager.playerRefitSound(candidate, player, SoundManager.INSTALL_SOUND);
            NetworkHandler.CHANNEL.sendToServer(
                    new ClientMessageRefitGun(inventorySlot, player.getInventory().selected, type));
            notify(I18n.get("gui.z_tweaks.refit.msg.installed", entry.name()));
            return;
        }
        // 配件不在背包里，就没有能发给服务端的槽位坐标：ClientMessageRefitGun.handle 是拿
        // inventory.getItem(attachmentSlotIndex) 去取配件的，服务端只认自己那份背包。
        // 早先的写法是直接改客户端这份 NBT 假装装上，结果服务端毫不知情 —— 界面显示装了、
        // 服务端还是空槽，两边分叉。真正的"悬停虚拟装配"要克隆枪栈再驱动渲染管线（计划 §3.2），
        // 那属 M2；这里如实报错。
        notify(I18n.get("gui.z_tweaks.refit.msg.not_owned"));
    }

    /** 卸下当前选中槽位的配件（详情条的"卸下"按钮与 U 键）。 */
    private void unloadCurrent() {
        unloadSlot(RefitTransform.getCurrentTransformType());
    }

    /**
     * 卸下指定槽位上的配件。按钮 / U 键 / 右键点底栏槽位三条路径共用同一份护栏，
     * 免得到处各写一套判断后，"点了没反应"的原因在不同入口下说法不一。
     */
    private void unloadSlot(AttachmentType type) {
        LocalPlayer player = getMinecraft().player;
        if (player == null) {
            return;
        }
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
        // 右键能点到不支持的槽位（左键那条路已经在 selectSlot 里拦下了），这里补一道
        if (!iGun.allowAttachmentType(gun, type)) {
            notify(I18n.get("gui.z_tweaks.refit.msg.slot_not_allowed", slotName(type)));
            return;
        }
        ItemStack installed = iGun.getAttachment(gun, type);
        if (installed.isEmpty()) {
            // 空槽位与"概览态"要分开报：右键点空槽还说"概览态"会让人以为点错了地方
            notify(I18n.get("gui.z_tweaks.refit.msg.nothing_to_unload"));
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

    /**
     * 候选列表里一行所需的全部信息（列表本身就是一份 {@code List<Candidate>}，不再拆成平行列表），
     * 在 {@link #rebuildCandidates()} 里一次性算好：名字（本地化显示名，排序与绘制共用，
     * 避免每行重复查索引）、模组命名空间、它在背包里的槽位（-1 = 背包里没有），
     * 以及它对该参数的边际改善量（只在参数排序 / 筛选时算，见 {@link StatCatalog}）。
     */
    private record Candidate(ItemStack stack, int invSlot, Compat compat, String name, String modId,
                             double improvement) {
    }

    /**
     * 能不能装（术语见 `CONTEXT.md`「不可安装」）。两档对应屏幕上两句不同的话：
     * {@link #NOT_LISTED} 是"这一个装不上"，{@link #NO_WHITELIST} 是"这里什么也装不上"。
     *
     * <p>计划里原先列的三个原因（类型不允许 / 标签不匹配 / 配件锁）只有一个真会发生：
     * {@code allowAttachment} 里没有类型检查（`AbstractGunItem.java:284` 就是一行标签匹配），
     * 类型那层由"槽位本身合不合法"决定（不合法的槽位压暗、点了弹「不支持 X 槽位」，那些配件
     * 压根进不了这个列表），配件锁是枪级标志（锁了就开不了改装界面）。</p>
     */
    private enum Compat {
        /** 能装。 */
        OK,
        /** 枪有白名单，但不含这一个 —— 角标「装不上这个配件」。 */
        NOT_LISTED,
        /** 枪压根没声明白名单 —— 整个槽位都是这一档，角标「这里装不了配件」。 */
        NO_WHITELIST
    }

    /**
     * 让下一帧重建候选列表并回到列表顶部。
     *
     * <p>"列表的输入变了"有三个入口（初始化/切槽位、搜索词、排序方式），原来各抄一份
     * {@code cachedType = null; selected = 0; scroll = 0; samplesDirty = true;} ——
     * 漏抄一处就是"改了不生效"，收敛成一个方法。</p>
     */
    private void invalidateCandidates() {
        cachedType = null;
        selected = 0;
        scroll = 0;
        samplesDirty = true;
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

    /** 第一个不可安装的行下标；没有就返回 -1（分隔线画在它的上方）。 */
    private int firstBlockedIndex() {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).compat() != Compat.OK) {
                return i;
            }
        }
        return -1;
    }

    /** 列表里有没有"枪压根没声明白名单"那一档的行 —— 有的话标题行也要说一次那句话。 */
    private boolean hasNoWhitelistRows() {
        for (Candidate c : candidates) {
            if (c.compat() == Compat.NO_WHITELIST) {
                return true;
            }
        }
        return false;
    }

    /** 手上这把枪的 id；拿不到给 null（{@link #whitelistEmpty} 把 null 当"未知"处理）。 */
    @Nullable
    private ResourceLocation heldGunId() {
        ItemStack gun = gunStack();
        IGun iGun = IGun.getIGunOrNull(gun);
        return iGun == null ? null : iGun.getGunId(gun);
    }

    private Rect installRect() {
        return new Rect(PAD + this.width - PAD * 2 - 136, detailY() + DETAIL_H - 16, 64, 14);
    }

    private Rect unloadRect() {
        return new Rect(PAD + this.width - PAD * 2 - 68, detailY() + DETAIL_H - 16, 64, 14);
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
            infoRows.clear();
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

        // 第二列：参数卡。所有可比行都出自同一张 {@link #paramRows} 表 —— 顺序固定，
        // 第三列（变化项）按同一顺序挑"不一样的行"，两列口径同源，不会出现"上面写 12.5、
        // 下面按另一个口径比"（issue #7/#8）。
        infoRows = paramRows(gun, iGun, gunData, index, baseCache(gun, gunData));
        for (ParamRow row : infoRows) {
            infoStats.add(row.line());
        }
        // 第三列：变化项。由候选件的预览驱动（issue #7 的形态、#9 的悬停驱动），
        // 没预览时是空的 —— 空列比"显示一堆没变的数"干净。
    }

    /**
     * 参数卡里可比的一行。
     *
     * <p>{@code key} 是 TACZ 的 lang key：{@code wholeLine} 为真时它是整行模板（数值包在 key 里，
     * 如 {@code "%s 原版护甲穿透"}，拆不出标签与数值两段），为假时它是标签；{@code null} =
     * 这一行只有值没有标签（弹药口径）。</p>
     *
     * <p>两种形态都要能在变化列里渲染成「旧 → 新」，所以整行模板那一类必须留着模板本身 ——
     * 只存"渲染好的字符串"就没法把新值塞回去了。</p>
     */
    private record ParamRow(@Nullable String key, boolean wholeLine, String value, ChatFormatting valueFormat) {

        /** 参数卡（第二列）里的样子：标签灰 + 数值白。 */
        Component line() {
            Component v = Component.literal(value).withStyle(valueFormat);
            if (key == null) {
                return v;
            }
            return wholeLine ? Component.translatable(key, v) : label().append(v);
        }

        /** 变化列（第三列）里的样子：标签不变，数值换成「旧 → 新」。 */
        Component changeLine(String before) {
            Component pair = Component.literal(before).withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.literal(" → ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(value).withStyle(valueFormat));
            if (key == null) {
                return pair;
            }
            return wholeLine ? Component.translatable(key, pair) : label().append(pair);
        }

        /**
         * TACZ 的标签 key 有的自带冒号（{@code 伤害: }）、有的不带（{@code 射速}）。
         * 参数卡统一是"标签：值"的写法，所以不带的补一个中英通用的 {@code ": "} ——
         * TACZ 的文案本身一个字不改。
         */
        private MutableComponent label() {
            String text = I18n.get(key);
            boolean hasSeparator = text.endsWith(":") || text.endsWith("：");
            return Component.literal(hasSeparator ? text : text + ": ").withStyle(ChatFormatting.GRAY);
        }
    }

    /**
     * 参数卡的全部可比行，**顺序固定**（issue #7：宁可稳定也不要抖）。
     *
     * <p>现有项之后接 issue #8 定的六项，顺序照 TACZ 原生属性条：开火模式 → 弹匣容量 →
     * 射速 → 弹速 → 穿透 → 开镜时间。文案复用 TACZ 的 lang key，数值格式对齐各修饰器的
     * 成品串（{@code %drpm} / {@code %dm/s} / {@code %d} / {@code %.2fs}）。</p>
     *
     * <p>取数分两类：能含配件派生的走 {@link AttachmentDataUtils}（它自己遍历全部配件槽），
     * 纯枪械属性的走 {@link IGun} / {@link GunData}；能用属性缓存的优先用缓存
     * —— {@code eval} 一次拿到的就是"当前生效值"，含配件。</p>
     */
    private static List<ParamRow> paramRows(ItemStack gun, IGun iGun, GunData gunData, CommonGunIndex index,
                                            @Nullable AttachmentCacheProperty cache) {
        List<ParamRow> rows = new ArrayList<>();
        rows.add(new ParamRow(null, false, AmmoItemBuilder.create().setId(gunData.getAmmoId()).build()
                .getHoverName().getString(), ChatFormatting.GRAY));

        int level = iGun.getLevel(gun);
        String levelText;
        if (level >= iGun.getMaxLevel()) {
            levelText = String.format("%d (MAX)", level);
        } else {
            int toNext = iGun.getExpToNextLevel(gun);
            int expCurrent = iGun.getExpCurrentLevel(gun);
            // TACZ 原式是 int 整除后再乘 100f，非满级时恒为 0.0%；这里改成浮点除法
            float percent = (toNext + expCurrent) == 0 ? 0f : expCurrent * 100f / (toNext + expCurrent);
            levelText = String.format("%d (%.1f%%)", level, percent);
        }
        rows.add(new ParamRow("tooltip.tacz.gun.level", false, levelText, ChatFormatting.WHITE));
        rows.add(new ParamRow("tooltip.tacz.gun.type", false,
                I18n.get("tacz.type." + index.getType() + ".name"), ChatFormatting.WHITE));

        String damage = DAMAGE_FORMAT.format(AttachmentDataUtils.getDamageWithAttachment(gun, gunData));
        ExplosionData explosion = gunData.getBulletData().getExplosionData();
        if (explosion != null
                && (AttachmentDataUtils.isExplodeEnabled(gun, gunData) || explosion.isExplode())) {
            damage += " + " + DAMAGE_FORMAT.format(explosion.getDamage() * SyncConfig.DAMAGE_BASE_MULTIPLIER.get())
                    + I18n.get("tooltip.tacz.gun.explosion");
        }
        rows.add(new ParamRow("tooltip.tacz.gun.damage", false, damage, ChatFormatting.WHITE));

        // 这三行的文案把数值包在 key 里（"25% 原版护甲穿透"），整行只有一种色；
        // 移动速度是负面数值，整行用红
        double armor = Mth.clamp(AttachmentDataUtils.getArmorIgnoreWithAttachment(gun, gunData), 0.0, 1.0);
        rows.add(new ParamRow("tooltip.tacz.gun.armor_ignore", true, PERCENT_FORMAT.format(armor),
                ChatFormatting.GRAY));
        rows.add(new ParamRow("tooltip.tacz.gun.head_shot_multiplier", true,
                PERCENT_FORMAT.format(AttachmentDataUtils.getHeadshotMultiplier(gun, gunData)),
                ChatFormatting.GRAY));
        rows.add(new ParamRow("tooltip.tacz.gun.movement_speed", true, PERCENT_1_FORMAT.format(
                -SyncConfig.WEIGHT_SPEED_MULTIPLIER.get()
                        * AttachmentDataUtils.getWightWithAttachment(gun, gunData)), ChatFormatting.RED));

        // ---- 以下六项是 issue #8 补的 ----
        rows.add(new ParamRow("gui.tacz.gun_refit.property_diagrams.fire_mode", false,
                I18n.get("gui.tacz.gun_refit.property_diagrams."
                        + iGun.getFireMode(gun).name().toLowerCase(Locale.ROOT)), ChatFormatting.WHITE));
        rows.add(new ParamRow("gui.tacz.gun_refit.property_diagrams.ammo_capacity", false,
                String.valueOf(ammoCapacity(gun, gunData)), ChatFormatting.WHITE));
        rows.add(new ParamRow("gui.tacz.gun_refit.property_diagrams.rpm", false,
                Math.round(numberOr(cache, "rpm", iGun.getRPM(gun))) + "rpm", ChatFormatting.WHITE));
        rows.add(new ParamRow("gui.tacz.gun_refit.property_diagrams.ammo_speed", false,
                Math.round(numberOr(cache, "ammo_speed", gunData.getBulletData().getSpeed())) + "m/s",
                ChatFormatting.WHITE));
        rows.add(new ParamRow("gui.tacz.gun_refit.property_diagrams.pierce", false,
                String.valueOf(Math.round(numberOr(cache, "pierce", gunData.getBulletData().getPierce()))),
                ChatFormatting.WHITE));
        rows.add(new ParamRow("gui.tacz.gun_refit.property_diagrams.ads", false,
                String.format("%.2fs", numberOr(cache, "ads", gunData.getAimTime())), ChatFormatting.WHITE));
        return rows;
    }

    /**
     * 弹匣容量（含配件），口径照抄 TACZ 原生属性条：**非开膛枪膛里那一发也算上**
     * （{@code GunPropertyDiagrams} 的 {@code barrelBulletAmount}）。
     */
    private static int ammoCapacity(ItemStack gun, GunData gunData) {
        int count = AttachmentDataUtils.getAmmoCountWithAttachment(gun, gunData);
        boolean inBarrel = gunData.getBolt() != Bolt.OPEN_BOLT && gun != null
                && IGun.getIGunOrNull(gun) != null && IGun.getIGunOrNull(gun).hasBulletInBarrel(gun);
        return inBarrel ? count + 1 : count;
    }

    /**
     * 从属性缓存里取一个参数值，取不到就退回枪械静态值。
     *
     * <p>{@code getCache} 的泛型是指向推断的（写错类型能编译、运行期才炸），所以这里不硬转：
     * 只认 {@link Number}，其它（没有这个 modifier、或者值是别的类型）一律退回 fallback。</p>
     */
    private static double numberOr(@Nullable AttachmentCacheProperty cache, String modifierId, double fallback) {
        if (cache == null) {
            return fallback;
        }
        try {
            return cache.getCache(modifierId) instanceof Number number ? number.doubleValue() : fallback;
        } catch (Exception e) {
            // getCache 对未注册的 id 会 NPE（它直接 get(...).getValue()），按"取不到"处理
            return fallback;
        }
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
        ItemStack candidate = candidates.get(Math.min(selected, candidates.size() - 1)).stack();
        String key = nameOf(candidate) + "@" + selected;
        if (!key.equals(attachKey)) {
            attachKey = key;
            attachScroll = 0;
        }
        String blocked = blockedKey(candidates.get(Math.min(selected, candidates.size() - 1)).compat());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(nameOf(candidate)).withStyle(ChatFormatting.AQUA));
        if (blocked != null) {
            // 不可安装的件在这一列只讲"这是什么"与"为什么装不上"（与行内角标同一串字）；
            // Pros/Cons 与参数不给 —— 见 computeProsCons。
            lines.add(Component.literal(I18n.get(blocked)).withStyle(ChatFormatting.RED));
        }
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
