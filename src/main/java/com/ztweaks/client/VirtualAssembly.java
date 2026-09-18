package com.ztweaks.client;

import com.tacz.guns.api.item.IAttachment;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.attachment.AttachmentType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * 悬停虚拟装配状态（计划 §3.2-3，M2）。
 *
 * <p><b>为什么能这么干</b>：改装界面里的 3D 枪不是 {@code GunRefitScreen} 画的 —— 它是
 * "第一人称手部渲染 pass 被 {@code RefitTransform} 掰到取景视角"的结果。整条链路只有一个咽喉：
 * {@code GunItemRendererWrapper.renderFirstPerson(player, stack, ...)}，它把同一个 {@code stack}
 * 既交给取景变换，又交给 {@code BedrockGunModel.render}；而后者在方法开头就照
 * {@code iGun.getAttachment(gunItem, type)} 把配件灌进渲染缓存（{@code BedrockGunModel:246-263}）。
 * 于是一定：在入口把 {@code stack} 换成"已经装上悬停配件"的克隆件，模型、配件、枪口、取景
 * 变换就全部跟着变 —— <b>不改 TACZ 一行代码，也不侵入它的内部类</b>（见 ADR-0004）。</p>
 *
 * <p><b>为什么必须是克隆件</b>：写进手里那份真 ItemStack 服务端毫不知情（服务端只认自己那份
 * 背包），界面显示装了、服务端还是空槽，两边分叉 —— 那正是 M1 删掉的本地写。
 * 克隆件只活在渲染调用里，渲染结束即弃。</p>
 *
 * <p><b>生效护栏</b>（缺一不可，否则会把预览画到别的枪上）：
 * 界面是本模组界面、悬停对象仍在、手上还是预览针对的那把枪、该槽位确实允许装。
 * 于是换枪、切槽、关界面都会自动失效。</p>
 */
public final class VirtualAssembly {

    /** 预览针对的槽位类型。 */
    private static AttachmentType type = AttachmentType.NONE;
    /** 悬停中的候选配件 id（{@code null} = 没有预览）。 */
    @Nullable
    private static ResourceLocation candidateId = null;
    /** 悬停中的候选配件（副本，避免外部改动影响我们）。 */
    @Nullable
    private static ItemStack candidate = null;

    // 渲染管线每帧都会来问一次，克隆不能每帧重建：按"基准枪 NBT + 悬停对象"缓存。
    @Nullable
    private static ItemStack cachedClone = null;
    @Nullable
    private static ResourceLocation cachedGunId = null;
    @Nullable
    private static CompoundTag cachedBaseTag = null;

    // 诊断读数：命中（渲染入口确实生效过）与重建（克隆次数）。前者为 0 说明 mixin 没注入。
    private static int hits = 0;
    private static int builds = 0;

    private VirtualAssembly() {
    }

    /**
     * 由界面在每帧画完候选列表后调用：把"鼠标正悬停的那一行"设为预览对象。
     *
     * @param slotType      当前槽位类型；{@link AttachmentType#NONE} 表示取消预览
     * @param candidateStack 悬停的候选配件；{@code null}/空 表示取消预览
     */
    public static void setPreview(AttachmentType slotType, @Nullable ItemStack candidateStack) {
        ResourceLocation id = attachmentIdOf(candidateStack);
        if (slotType == type && Objects.equals(id, candidateId)) {
            return; // 悬停对象没变：保留克隆缓存
        }
        type = slotType;
        candidateId = id;
        candidate = (candidateStack == null || candidateStack.isEmpty()) ? null : candidateStack.copy();
        invalidate();
    }

    /** 取消预览，立刻还原为真枪。切槽、关屏、注入失败时调用。 */
    public static void clear() {
        if (type == AttachmentType.NONE && candidateId == null && cachedClone == null) {
            return;
        }
        type = AttachmentType.NONE;
        candidateId = null;
        candidate = null;
        invalidate();
    }

    /**
     * 供渲染入口调用（第二处 mixin）：返回该帧应当渲染的枪栈。
     * 虚拟装配不生效时原样返回 {@code incoming}，行为与原生完全一致。
     */
    public static ItemStack previewStack(ItemStack incoming) {
        Minecraft minecraft = Minecraft.getInstance();
        // 护栏一：只在自家界面里生效 —— 离开界面立刻回到真枪，绝不漏到世界里
        if (!(minecraft.screen instanceof ZtRefitScreen)) {
            return incoming;
        }
        if (candidate == null || type == AttachmentType.NONE) {
            return incoming;
        }
        hits++;
        IGun iGun = IGun.getIGunOrNull(incoming);
        LocalPlayer player = minecraft.player;
        if (iGun == null || player == null) {
            return incoming;
        }
        // 护栏二：只对"预览针对的那把枪"生效 —— 手上换成别的枪，预览自动作废
        ItemStack mainHand = player.getMainHandItem();
        IGun mainGun = IGun.getIGunOrNull(mainHand);
        if (mainGun == null) {
            return incoming;
        }
        ResourceLocation gunId = iGun.getGunId(incoming);
        if (!gunId.equals(mainGun.getGunId(mainHand))) {
            return incoming;
        }
        // 护栏三：服务端会拒的装配不预览 —— 预览只画"点下去真能装上"的样子
        if (!iGun.allowAttachmentType(incoming, type)) {
            return incoming;
        }

        CompoundTag baseTag = incoming.getTag();
        if (cachedClone != null && gunId.equals(cachedGunId) && Objects.equals(cachedBaseTag, baseTag)) {
            return cachedClone;
        }
        ItemStack clone = incoming.copy();
        iGun.installAttachment(clone, candidate);
        cachedClone = clone;
        cachedGunId = gunId;
        cachedBaseTag = baseTag == null ? null : baseTag.copy();
        builds++;
        return clone;
    }

    /** 渲染入口命中次数。为 0 说明第二处 mixin 没注入（虚拟装配静默失效）。 */
    public static int hits() {
        return hits;
    }

    /** 克隆件重建次数。持续增长说明缓存一直失效（会拖性能）。 */
    public static int builds() {
        return builds;
    }

    /** 当前预览对象显示名；无预览时为 {@code "-"}。 */
    public static String previewName() {
        return candidateId == null ? "-" : candidateId.getPath();
    }

    private static void invalidate() {
        cachedClone = null;
        cachedGunId = null;
        cachedBaseTag = null;
    }

    @Nullable
    private static ResourceLocation attachmentIdOf(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        IAttachment attachment = IAttachment.getIAttachmentOrNull(stack);
        return attachment == null ? null : attachment.getAttachmentId(stack);
    }
}
