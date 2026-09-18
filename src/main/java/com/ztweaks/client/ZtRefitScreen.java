package com.ztweaks.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.entity.IGunOperator;
import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import com.tacz.guns.api.item.builder.AttachmentItemBuilder;
import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.client.animation.screen.RefitTransform;
import com.tacz.guns.client.gui.GunRefitScreen;
import com.tacz.guns.client.gui.components.refit.GunPropertyDiagrams;
import com.tacz.guns.client.gui.components.refit.HSVSliderGroup;
import com.tacz.guns.client.resource.GunDisplayInstance;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.pojo.display.LaserConfig;
import com.tacz.guns.network.NetworkHandler;
import com.tacz.guns.network.message.ClientMessageRefitGun;
import com.tacz.guns.network.message.ClientMessageUnloadAttachment;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.index.CommonAttachmentIndex;
import com.tacz.guns.resource.index.CommonGunIndex;
import com.tacz.guns.resource.pojo.data.attachment.AttachmentData;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import com.ztweaks.config.ZtConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 原型（throwaway）：接管后的改装界面。
 *
 * <p>三个待验证的问题各自对应本文件的一块：
 * 布局（三层）、轨道相机（操作 + 诊断读数）、Pros/Cons 文本（复用 TACZ 成品文本）。</p>
 *
 * <p>刻意不调用 {@code super.init()}：原生按钮会叠加到自绘 UI 上（见 ADR-0002）。</p>
 */
public class ZtRefitScreen extends GunRefitScreen {

    private static final int SLOT = 22;
    private static final int ROW_H = 20;
    private static final int DETAIL_H = 68;
    private static final int PAD = 6;

    private final List<ItemStack> candidates = new ArrayList<>();
    private final List<Integer> candidateInvSlots = new ArrayList<>();

    private final List<String> pros = new ArrayList<>();
    private final List<String> cons = new ArrayList<>();
    private final List<String> neutral = new ArrayList<>();
    private final List<String> samples = new ArrayList<>();

    private AttachmentType cachedType = null;
    private int selected = 0;
    private int scroll = 0;
    private boolean dragging = false;
    private int dragButton = -1;
    private boolean showNativeBars = false;
    private boolean samplesDirty = true;

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
        addLaserSliders();
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
     * 候选配件：原型图省事，直接枚举资源索引里**全部**该类型配件（创造模式思路），
     * 不再要求玩家真的把配件带在背包里；能装到手里的枪上才进列表。
     * 列表同时记录"背包里有没有"，有才能真的装。
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
        candidates.clear();
        candidateInvSlots.clear();
        List<Map.Entry<ResourceLocation, CommonAttachmentIndex>> all =
                new ArrayList<>(TimelessAPI.getAllCommonAttachmentIndex());
        all.sort(Comparator.comparing(entry -> entry.getKey().toString()));
        Inventory inventory = player.getInventory();
        for (Map.Entry<ResourceLocation, CommonAttachmentIndex> entry : all) {
            if (entry.getValue().getType() != type) {
                continue;
            }
            ItemStack stack = AttachmentItemBuilder.create().setId(entry.getKey()).build();
            if (!iGun.allowAttachment(gun, stack)) {
                continue;
            }
            candidates.add(stack);
            candidateInvSlots.add(findInventorySlot(inventory, entry.getKey()));
        }
        selected = 0;
        scroll = 0;
        samplesDirty = true;
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
     * <p>已知代价：文本里的**单位丢了**（单位只存在于 TACZ 预拼字符串中，见 ADR-0001），
     * 这里只有从成品文本里剥出来的单位残留可作近似。</p>
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
                String text = I18n.get(now.titleKey()) + " "
                        + (delta > 0 ? "+" : "")
                        + String.format("%.2f", delta) + unitOf(now.positivelyString());
                boolean better = (delta > 0) == now.positivelyBetter();
                (better ? pros : cons).add(text);
            }
        });
    }

    /** 从 TACZ 成品文本里剥出单位残留：去掉色码、数字、正负号、括号与空白后剩下的字母。 */
    private static String unitOf(String sample) {
        String stripped = sample.replaceAll("§.", "").replaceAll("[0-9.+\\-()\\s]", "");
        return stripped.length() <= 4 ? stripped : "";
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
        drawSlotBar(graphics, mouseX, mouseY);
        drawCandidateList(graphics, mouseX, mouseY);
        drawDetail(graphics, mouseX, mouseY);
        if (showNativeBars && ZtConfig.DEBUG_NATIVE_BARS.get()) {
            GunPropertyDiagrams.draw(graphics, this.font, 11, 96);
        }
        drawOverlay(graphics);
    }

    private int slotBarY() {
        return this.height - SLOT - 6;
    }

    private int detailY() {
        return slotBarY() - DETAIL_H - 4;
    }

    private void drawSlotBar(GuiGraphics graphics, int mouseX, int mouseY) {
        List<AttachmentType> types = slotTypes();
        int step = slotStep(types);
        int barWidth = types.size() * step;
        int x0 = (this.width - barWidth) / 2;
        int y = slotBarY();
        AttachmentType current = RefitTransform.getCurrentTransformType();
        IGun iGun = IGun.getIGunOrNull(gunStack());

        graphics.fill(x0 - 4, y - 12, x0 + barWidth + 4, y + SLOT + 4, 0xB0101010);
        for (int i = 0; i < types.size(); i++) {
            AttachmentType type = types.get(i);
            int x = x0 + i * step;
            boolean hovered = mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT;
            boolean isCurrent = type == current;
            boolean allowed = iGun != null && iGun.allowAttachmentType(gunStack(), type);

            int background = isCurrent ? 0xFF2F5F9F : (hovered ? 0xFF454545 : 0xFF1C1C1C);
            graphics.fill(x, y, x + SLOT, y + SLOT, background);
            border(graphics, x, y, SLOT, SLOT, allowed ? 0xFF7F7F7F : 0xFF402020);
            graphics.drawCenteredString(this.font, truncate(slotName(type), step - 2),
                    x + SLOT / 2, y - 8, allowed ? 0xFFCCCCCC : 0xFF885555);

            ItemStack installed = iGun == null ? ItemStack.EMPTY : iGun.getAttachment(gunStack(), type);
            if (!installed.isEmpty()) {
                graphics.renderItem(installed, x + 3, y + 3);
            } else {
                graphics.drawCenteredString(this.font, "+", x + SLOT / 2, y + 7, 0xFF808080);
            }
        }
    }

    private void drawCandidateList(GuiGraphics graphics, int mouseX, int mouseY) {
        int panelWidth = 156;
        int x = this.width - panelWidth - PAD;
        int y = 26;
        int height = Math.max(40, detailY() - 8 - y);

        graphics.fill(x, y, x + panelWidth, y + height, 0xB0101010);
        graphics.drawString(this.font, I18n.get("gui.z_tweaks.refit.candidates", candidates.size()),
                x + 4, y + 3, 0xFFFFD700, true);

        int listTop = y + 14;
        int rows = Math.max(1, (height - 26) / ROW_H);
        int maxScroll = Math.max(0, candidates.size() - rows);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        for (int i = 0; i < rows && scroll + i < candidates.size(); i++) {
            int index = scroll + i;
            int rowY = listTop + i * ROW_H;
            boolean isSelected = index == selected;
            boolean hovered = mouseX >= x + 2 && mouseX < x + panelWidth - 2 && mouseY >= rowY && mouseY < rowY + ROW_H - 1;
            if (isSelected || hovered) {
                graphics.fill(x + 2, rowY, x + panelWidth - 2, rowY + ROW_H - 1, isSelected ? 0xFF2F5F9F : 0xFF3A3A3A);
            }
            graphics.renderItem(candidates.get(index), x + 4, rowY + 1);
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(nameOf(candidates.get(index)), panelWidth - 34),
                    x + 24, rowY + 6, 0xFFFFFFFF, false);
        }
        graphics.drawString(this.font, I18n.get("gui.z_tweaks.refit.candidates.scroll"),
                x + 4, y + height - 10, 0xFF808080, false);
    }

    private void drawDetail(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = PAD;
        int width = this.width - PAD * 2;
        int y = detailY();
        int height = DETAIL_H;
        graphics.fill(x, y, x + width, y + height, 0xC0101010);
        border(graphics, x, y, width, height, 0xFF555555);

        int leftWidth = (int) (width * 0.40f);
        int line = y + 4;

        if (!candidates.isEmpty()) {
            ItemStack candidate = candidates.get(Math.min(selected, candidates.size() - 1));
            graphics.drawString(this.font, I18n.get("gui.z_tweaks.refit.candidate", nameOf(candidate)),
                    x + 4, line, 0xFFFFD700, false);
            line += 10;
            for (String desc : describe(candidate)) {
                graphics.drawString(this.font, truncate(desc, leftWidth - 8), x + 4, line, 0xFFAAAAAA, false);
                line += 10;
            }
            for (int i = 0; i < Math.min(2, neutral.size()); i++) {
                graphics.drawString(this.font,
                        truncate(I18n.get("gui.z_tweaks.refit.neutral", neutral.get(i)), leftWidth - 8),
                        x + 4, line, 0xFF777777, false);
                line += 10;
            }
            if (ZtConfig.DEBUG_SAMPLES.get()) {
                for (String sample : samples) {
                    graphics.drawString(this.font,
                            truncate(I18n.get("gui.z_tweaks.refit.native", sample), leftWidth - 8),
                            x + 4, line, 0xFF00AAAA, false);
                    line += 10;
                }
            }
        }

        int columnX = x + leftWidth + 6;
        int columnWidth = (width - leftWidth - 20) / 2;
        graphics.drawString(this.font, I18n.get("gui.z_tweaks.refit.pros", pros.size()),
                columnX, y + 4, 0xFF55FF55, false);
        graphics.drawString(this.font, I18n.get("gui.z_tweaks.refit.cons", cons.size()),
                columnX + columnWidth + 8, y + 4, 0xFFFF5555, false);
        for (int i = 0; i < pros.size() && i < 4; i++) {
            graphics.drawString(this.font, truncate(pros.get(i), columnWidth - 4), columnX + 2, y + 15 + i * 10, 0xFF55FF55, false);
        }
        for (int i = 0; i < cons.size() && i < 4; i++) {
            graphics.drawString(this.font, truncate(cons.get(i), columnWidth - 4),
                    columnX + columnWidth + 10, y + 15 + i * 10, 0xFFFF5555, false);
        }

        Rect installRect = installRect();
        Rect unloadRect = unloadRect();
        button(graphics, installRect, I18n.get("gui.z_tweaks.refit.install"),
                installRect.contains(mouseX, mouseY), true);
        button(graphics, unloadRect, I18n.get("gui.z_tweaks.refit.unload"),
                unloadRect.contains(mouseX, mouseY), false);
    }

    /**
     * 左上角浮层：诊断 HUD（默认关，见 {@link ZtConfig#DEBUG_HUD}）+ 操作反馈弹条。
     * 弹条是给玩家看的正常反馈，不受调试开关影响。
     */
    private void drawOverlay(GuiGraphics graphics) {
        int y = 6;
        if (ZtConfig.DEBUG_HUD.get()) {
            for (String text : debugLines()) {
                graphics.drawString(this.font, text, PAD, y, 0xFF00FFFF, true);
                y += 10;
            }
        }
        if (!popup.isEmpty() && System.currentTimeMillis() < popupUntil) {
            graphics.drawString(this.font, popup, PAD, y + 4, 0xFFFFAA00, true);
        }
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
                fmt(OrbitCamera.yaw()), fmt(OrbitCamera.pitch()), fmt(OrbitCamera.zoom()),
                fmt(OrbitCamera.offsetX()), fmt(OrbitCamera.offsetY())));
        lines.add(I18n.get(hits > 0
                ? "gui.z_tweaks.refit.hud.mixin.hit"
                : "gui.z_tweaks.refit.hud.mixin.miss", hits));
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
        // 槽位条
        List<AttachmentType> types = slotTypes();
        int step = slotStep(types);
        int x0 = (this.width - types.size() * step) / 2;
        int y = slotBarY();
        for (int i = 0; i < types.size(); i++) {
            int x = x0 + i * step;
            if (hit(mouseX, mouseY, x, y, SLOT, SLOT)) {
                selectSlot(types.get(i));
                return true;
            }
        }
        // 候选列表
        int panelWidth = 156;
        int listX = this.width - panelWidth - PAD;
        int listTop = 26 + 14;
        int rows = Math.max(1, (Math.max(40, detailY() - 8 - 26) - 26) / ROW_H);
        for (int i = 0; i < rows && scroll + i < candidates.size(); i++) {
            int rowY = listTop + i * ROW_H;
            if (hit(mouseX, mouseY, listX + 2, rowY, panelWidth - 4, ROW_H - 1)) {
                selected = scroll + i;
                samplesDirty = true;
                return true;
            }
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
                // 右键：只平移，不旋转。第一参数=左右（1 屏宽 = 1 格，鼠标往右武器往右），
                // 第二参数=上下（屏幕 Y 向下、视图空间 Y 向上，所以取负）
                OrbitCamera.pan((float) fx, (float) -fy);
            } else {
                // 左键：上下拖 = pitch，绕武器原点的 X 轴翻转；左右拖 = yaw，绕 Y 轴环绕
                OrbitCamera.rotateX((float) (fy * 180.0));
                OrbitCamera.rotateY((float) (fx * 360.0));
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
        int panelWidth = 156;
        int listX = this.width - panelWidth - PAD;
        if (mouseX >= listX) {
            scroll -= (int) Math.signum(delta);
            return true;
        }
        OrbitCamera.addZoom((float) delta * 0.15f);
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
            this.init();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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
        notify(I18n.get("gui.z_tweaks.refit.msg.not_owned", name));
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
        if (iGun == null || iGun.getAttachment(gun, type).isEmpty()) {
            notify(I18n.get("gui.z_tweaks.refit.msg.overview"));
            return;
        }
        // 与原生同款护栏：背包没空位就别发包。服务端那边是 inventory.add(配件) 返回 false
        // 就**静默什么都不做**（连刷新包都不发），不预检的话点击会像没反应一样。
        if (player.getInventory().getFreeSlot() == -1) {
            notify(I18n.get("gui.tacz.gun_refit.unload.no_space"));
            return;
        }
        // 只发包：界面交给服务端回来的 ServerMessageRefreshRefitScreen 刷新（它会对
        // 当前 GunRefitScreen 调 init()）。不再本地抢跑写 NBT —— 服务端一旦拒绝，
        // 本地会一直显示"已卸下"。
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

    /** 候选面板当前能显示几行（面板几何与 drawCandidateList 保持一致）。 */
    private int visibleRows() {
        int height = Math.max(40, detailY() - 8 - 26);
        return Math.max(1, (height - 26) / ROW_H);
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
        return new Rect(PAD + this.width - PAD * 2 - 132, detailY() + DETAIL_H - 15, 62, 13);
    }

    private Rect unloadRect() {
        return new Rect(PAD + this.width - PAD * 2 - 68, detailY() + DETAIL_H - 15, 62, 13);
    }

    private static boolean hit(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    private void button(GuiGraphics graphics, Rect rect, String label, boolean hovered, boolean primary) {
        int color = primary ? (hovered ? 0xFF3F7F3F : 0xFF2F5F2F) : (hovered ? 0xFF7F3F3F : 0xFF5F2F2F);
        graphics.fill(rect.x(), rect.y(), rect.x() + rect.w(), rect.y() + rect.h(), color);
        border(graphics, rect.x(), rect.y(), rect.w(), rect.h(), 0xFFAAAAAA);
        graphics.drawCenteredString(this.font, label, rect.x() + rect.w() / 2, rect.y() + 3, 0xFFFFFFFF);
    }

    private static void border(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        graphics.fill(x, y, x + w, y + 1, color);
        graphics.fill(x, y + h - 1, x + w, y + h, color);
        graphics.fill(x, y, x + 1, y + h, color);
        graphics.fill(x + w - 1, y, x + w, y + h, color);
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
