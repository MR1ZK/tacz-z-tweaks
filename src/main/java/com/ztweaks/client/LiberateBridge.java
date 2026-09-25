package com.ztweaks.client;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * 与 <b>TACZ Addon</b>（`taczaddon`）的「随意配件」（它叫 Liberate）对接的软依赖桥。
 *
 * <p><b>为什么需要它。</b>Liberate 不走 TACZ 的装件包：它把配件塞进一个**虚拟背包**
 * （`LiberateAttachmentService.createLiberatedInventory` → `init.VirtualInventory`），
 * 然后由它自己的 `LiberateAttachmentInstallPacket(枪槽位, 配件id)` 装上 —— 只带 id、
 * 不带背包槽位，服务端自己解析。我们如果照旧发 TACZ 的 `ClientMessageRefitGun(背包槽位, …)`，
 * 服务端是拿**它自己那份真背包**按槽位取件的（`ClientMessageRefitGun.java:45-65`），
 * 虚拟槽位在那里不存在 → **静默 return**；而客户端已经放了音效、弹了「已安装」。
 * 客户报的"看得见、装不上"就是这条路径（issue #18）。</p>
 *
 * <p><b>为什么是反射而不是编译期依赖。</b>`taczaddon` 不是本模组的依赖项，客户可能装任何版本；
 * 把它塞进 `libs/` 当 `compileOnly` 会让构建依赖别人的 jar，版本一变就编译不过。这里只碰
 * **2 个符号 + 1 个字段**，任何一步失败就标成"接不上"，由界面提示玩家（那种情况下失败是静默的，
 * 不说清楚会被当成我们的 bug）。</p>
 *
 * <p><b>这不是 #13 说的那个 compat 适配层。</b>那个是"给 TACZ 内部调用做单点收口"的通用层，
 * 已推迟；这里是针对一个具体第三方模组的、可有可无的桥 —— 断了只影响 Liberate 这一条路。</p>
 */
public final class LiberateBridge {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ADDON_MOD_ID = "taczaddon";
    private static final String LIBERATE_CLASS = "com.mafuyu404.taczaddon.common.LiberateAttachment";
    private static final String PACKET_CLASS = "com.mafuyu404.taczaddon.network.LiberateAttachmentInstallPacket";
    private static final String NETWORK_CLASS = "com.mafuyu404.taczaddon.init.NetworkHandler";

    private static boolean probed = false;
    private static boolean ready = false;
    @Nullable
    private static Method isLiberated;
    @Nullable
    private static Constructor<?> packetCtor;
    @Nullable
    private static Method sendToServer;
    @Nullable
    private static Object channel;

    private LiberateBridge() {
    }

    /**
     * 探测一次（进程内只跑一次）：
     * {@code LiberateAttachment.isLiberated(Player)}、{@code LiberateAttachmentInstallPacket(int, ResourceLocation)}、
     * {@code init.NetworkHandler.CHANNEL} 的 {@code sendToServer(Object)}。
     * 任何一步拿不到就标"接不上"，调用方照旧走 TACZ 的包。
     */
    private static void probe() {
        if (probed) {
            return;
        }
        probed = true;
        if (!ModList.get().isLoaded(ADDON_MOD_ID)) {
            return;
        }
        try {
            isLiberated = Class.forName(LIBERATE_CLASS).getMethod("isLiberated", Player.class);
            packetCtor = Class.forName(PACKET_CLASS).getConstructor(int.class, ResourceLocation.class);
            Object found = Class.forName(NETWORK_CLASS).getField("CHANNEL").get(null);
            Method sender = null;
            for (Method method : found.getClass().getMethods()) {
                if (method.getName().equals("sendToServer") && method.getParameterCount() == 1) {
                    sender = method;
                    break;
                }
            }
            if (isLiberated == null || packetCtor == null || sender == null) {
                return;
            }
            sender.setAccessible(true);
            channel = found;
            sendToServer = sender;
            ready = true;
            LOGGER.info("[ztweaks] TACZ Addon 的随意配件（Liberate）桥已就绪");
        } catch (Throwable e) {
            // 版本对不上、类被改名 —— 都属于"接不上"，不是错误，静默降级
            LOGGER.info("[ztweaks] TACZ Addon 在，但随意配件桥接不上（{}）", e.toString());
        }
    }

    /** 桥是否可用（addon 在装 且 三个句柄都拿到了）。 */
    public static boolean ready() {
        probe();
        return ready;
    }

    /**
     * addon 是否装了（不管桥通不通）。界面用它来提示"你可能开着随意配件，但本模组接不上它" ——
     * 桥不通时我们**没法**知道那条规则有没有生效（判定方法本身就在桥的另一头），所以提示只能这么说。
     */
    public static boolean addonPresent() {
        probe();
        return ModList.get().isLoaded(ADDON_MOD_ID);
    }

    /** 这名玩家的「随意配件」是否正生效。桥不可用或查询抛异常一律给 false（照旧走原路）。 */
    public static boolean isActive(@Nullable Player player) {
        probe();
        if (!ready || player == null) {
            return false;
        }
        try {
            Object result = isLiberated.invoke(null, player);
            return result instanceof Boolean value && value;
        } catch (Throwable e) {
            return false;
        }
    }

    /**
     * 用 addon 自己的包装件。**只在 {@link #isActive(Player)} 为真时调**。
     *
     * @return true = 包已发出；false = 桥在这条路上失败了，调用方该退回原路（此时装件多半装不上）
     */
    public static boolean install(int gunSlotIndex, ResourceLocation attachmentId) {
        probe();
        if (!ready || packetCtor == null || sendToServer == null || channel == null) {
            return false;
        }
        try {
            Object packet = packetCtor.newInstance(gunSlotIndex, attachmentId);
            sendToServer.invoke(channel, packet);
            return true;
        } catch (Throwable e) {
            LOGGER.warn("[ztweaks] 发 Liberate 装件包失败，退回 TACZ 原型路径", e);
            return false;
        }
    }
}
