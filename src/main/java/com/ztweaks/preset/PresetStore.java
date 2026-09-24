package com.ztweaks.preset;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 预设的落盘与分享码（issue #10 的结论，位置与隔离见 ADR-0005）。
 *
 * <p>三条口径写在这里，UI 只管调：</p>
 *
 * <ul>
 *   <li><b>只记"槽位 → 配件 id"</b>，一个 NBT 状态都不带。瞄具档位（{@code ZoomNumber}）与
 *       镭射色（{@code LaserColor}）长在实物自己的 ItemStack 上，装枪时整栈进枪 NBT，
 *       而我们没有任何包能"设置"它们 —— 记了也送不进去。</li>
 *   <li><b>路径是枪 id 直接落成目录层级</b>：{@code presets/<namespace>/<path>/<名>.json}。
 *       枪 id 里的 {@code :} 在 Windows 文件名里非法、{@code /} 本来就要分层，写不出
 *       {@code presets/<枪id>/}。文件名只负责"能存能列"，显示名以 json 里的字段为准。</li>
 *   <li><b>状态根与服务器隔离</b>：单人 {@code <游戏目录>/ztweaks/}，多人切到
 *       {@code <游戏目录>/ztweaks/servers/<host>/}（用主机名不用 {@code host:port}），
 *       两侧内部结构一致。</li>
 * </ul>
 *
 * <p>写盘是同步的：存/删都是一次明确动作，低频且体积小，没必要走"标脏 + 合批"。失败只记日志
 * ——丢的是一份预设，不该把界面带崩。</p>
 */
public final class PresetStore {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CODE_PREFIX = "ZT-PRESET-1:";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private PresetStore() {
    }

    /**
     * 一份预设。字段故意是 public 且不用 record：Gson 对 record 的支持要吃版本，
     * 而游戏自带的 Gson 版本不由我们定。
     */
    public static final class Preset {
        /** 结构版本，导入码里也带着它，将来变形状时好认。 */
        public int v = 1;
        /** 枪 id（{@code tacz:ak47}）。预设是按枪存的，换把枪就不适用。 */
        public String gun;
        /** 显示名。文件名的唯一职责是"能存能列"，显示名以这里为准。 */
        public String name;
        /** 槽位名（{@link com.tacz.guns.api.item.attachment.AttachmentType} 的枚举名）→ 配件 id。 */
        public Map<String, String> attachments = new LinkedHashMap<>();
    }

    // ------------------------------------------------------------------ 读 / 写

    /** 某把枪的全部预设，按名字排序（列表顺序稳定，不随文件系统的返回顺序抖）。 */
    public static List<Preset> list(ResourceLocation gunId) {
        List<Preset> presets = new ArrayList<>();
        Path dir = gunDir(gunId);
        if (!Files.isDirectory(dir)) {
            return presets;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(path -> path.getFileName().toString().endsWith(".json")).forEach(path -> {
                try {
                    Preset preset = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Preset.class);
                    if (preset != null && preset.name != null && preset.attachments != null) {
                        presets.add(preset);
                    } else {
                        LOGGER.warn("[ztweaks] 预设文件读出来是空的，跳过：{}", path);
                    }
                } catch (Exception e) {
                    LOGGER.warn("[ztweaks] 预设文件读不动，跳过：{}", path, e);
                }
            });
        } catch (Exception e) {
            LOGGER.warn("[ztweaks] 预设目录列不动：{}", dir, e);
        }
        presets.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return presets;
    }

    /** 存一份预设；同名覆盖。返回是否真的写下去了。 */
    public static boolean save(Preset preset) {
        try {
            Path dir = gunDir(ResourceLocation.tryParse(preset.gun));
            Files.createDirectories(dir);
            Files.writeString(fileFor(dir, preset.name), GSON.toJson(preset), StandardCharsets.UTF_8);
            return true;
        } catch (Exception e) {
            LOGGER.warn("[ztweaks] 预设写不下去：{}", preset.name, e);
            return false;
        }
    }

    /** 删一份预设；文件不在也算成功（幂等）。 */
    public static boolean delete(ResourceLocation gunId, String name) {
        try {
            return Files.deleteIfExists(fileFor(gunDir(gunId), name));
        } catch (Exception e) {
            LOGGER.warn("[ztweaks] 预设删不掉：{}", name, e);
            return false;
        }
    }

    /** 预设是否已存在（保存时用来问"要不要覆盖"）。 */
    public static boolean exists(ResourceLocation gunId, String name) {
        return Files.exists(fileFor(gunDir(gunId), name));
    }

    // ------------------------------------------------------------------ 分享码

    /** 导出：前缀 + Base64Url(GZip(json))。前缀既是"这是我们的码"的标记，也带了结构版本。 */
    public static String encode(Preset preset) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
                gzip.write(GSON.toJson(preset).getBytes(StandardCharsets.UTF_8));
            }
            return CODE_PREFIX + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(bytes.toByteArray());
        } catch (Exception e) {
            LOGGER.warn("[ztweaks] 预设码生成失败：{}", preset.name, e);
            return null;
        }
    }

    /** 导入：解不开、前缀不对、结构缺东西，一律抛 {@link IllegalArgumentException}（调用方给玩家报错）。 */
    public static Preset decode(String code) {
        String trimmed = code == null ? "" : code.trim();
        if (!trimmed.startsWith(CODE_PREFIX)) {
            throw new IllegalArgumentException("not a z-tweaks preset code");
        }
        try {
            byte[] raw = Base64.getUrlDecoder().decode(trimmed.substring(CODE_PREFIX.length()));
            ByteArrayOutputStream json = new ByteArrayOutputStream();
            try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                gzip.transferTo(json);
            }
            Preset preset = GSON.fromJson(json.toString(StandardCharsets.UTF_8), Preset.class);
            if (preset == null || preset.gun == null || preset.name == null || preset.attachments == null) {
                throw new IllegalArgumentException("preset code is incomplete");
            }
            return preset;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("preset code is corrupt", e);
        }
    }

    // ------------------------------------------------------------------ 路径

    /**
     * 状态根：单人 {@code <游戏目录>/ztweaks/}，多人 {@code <游戏目录>/ztweaks/servers/<host>/}。
     * 用主机名而不是 {@code host:port} —— 服务端换个端口不该让预设"不见了"。
     */
    public static Path root() {
        Path base = FMLPaths.GAMEDIR.get().resolve("ztweaks");
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null || server.ip == null || server.ip.isBlank()) {
            return base;
        }
        return base.resolve("servers").resolve(sanitize(hostOf(server.ip)));
    }

    private static Path gunDir(ResourceLocation gunId) {
        return root().resolve("presets").resolve(gunId.getNamespace()).resolve(gunId.getPath());
    }

    private static Path fileFor(Path dir, String name) {
        return dir.resolve(sanitize(name) + ".json");
    }

    private static String hostOf(String ip) {
        int colon = ip.lastIndexOf(':');
        return colon > 0 && ip.indexOf(':') == colon ? ip.substring(0, colon) : ip;
    }

    /**
     * 文件名安全化：Windows 的保留字符、路径分隔符与控制字符一律换成下划线，空格与点收干净。
     * 显示名不经过这里 —— 它存在 json 字段里，可以是任何玩家想写的字。
     */
    private static String sanitize(String name) {
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|\\x00-\\x1f]", "_").trim();
        while (cleaned.endsWith(".")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? "preset" : cleaned;
    }
}
