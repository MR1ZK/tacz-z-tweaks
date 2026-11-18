package com.ztweaks.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 主题注册表：从客户端资源管理器读取 {@code assets/<命名空间>/themes/*.json}（见 ADR-0008）。
 *
 * <p>资源包可以覆盖同名文件，也可以在自己的命名空间下新增；解析顺序按 {@code order} 再按 id。
 * 认不出的 id、坏文件、缺字段一律回落到内置的默认主题并记日志 —— 主题坏了不该让界面打不开。</p>
 *
 * <p>加载是**懒**的：第一次需要主题时（开改装界面 / 配置屏）才扫资源；{@link #invalidate()}
 * 由两个 Screen 的 {@code init} 调用，所以资源包在重载后重新开屏就能看到新主题。不在每帧重扫。</p>
 */
public final class ThemeRegistry {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DEFAULT_ID = "default";
    /** 这些字段属于"这套主题是谁"，不允许从父主题继承（否则子主题会顶着父主题的名字）。 */
    private static final String[] IDENTITY_KEYS = {
            "id", "name_key", "short_name_key", "order", "accent_editable"
    };

    private static final Map<String, ThemeSpec> SPECS = new LinkedHashMap<>();
    private static final List<ThemeSpec> ORDERED = new ArrayList<>();
    private static final Set<String> WARNED = new LinkedHashSet<>();

    private static boolean loaded;
    private static ThemeSpec fallback;

    private ThemeRegistry() {
    }

    /** 让下一次访问重新扫描资源（资源包重载后由 Screen 的 init 调用）。 */
    public static void invalidate() {
        loaded = false;
    }

    /** 解析配置里的主题 id；未知 / 空值回落默认主题。 */
    public static ThemeSpec resolve(@Nullable String rawId) {
        ensureLoaded();
        String id = normalize(rawId);
        ThemeSpec spec = SPECS.get(id);
        if (spec != null) {
            return spec;
        }
        if (WARNED.add(id)) {
            LOGGER.warn("Unknown theme '{}', falling back to '{}'", rawId, DEFAULT_ID);
        }
        return fallback();
    }

    /** 注册表里所有主题的 id，按 order / id 排序。 */
    public static List<String> ids() {
        ensureLoaded();
        List<String> ids = new ArrayList<>(ORDERED.size());
        for (ThemeSpec spec : ORDERED) {
            ids.add(spec.theme().id());
        }
        return ids;
    }

    /** 第 {@code index} 套主题（越界给 null）。 */
    @Nullable
    public static ThemeSpec specAt(int index) {
        ensureLoaded();
        return index >= 0 && index < ORDERED.size() ? ORDERED.get(index) : null;
    }

    /** 第 {@code index} 套主题的调色板（越界回落到默认）。 */
    public static ZtTheme themeAt(int index) {
        ThemeSpec spec = specAt(index);
        return spec != null ? spec.theme() : fallback().theme();
    }

    /** 第 {@code index} 套主题显示名（越界给空串）。 */
    public static Component displayNameAt(int index) {
        ThemeSpec spec = specAt(index);
        return spec != null ? spec.theme().displayName() : Component.empty();
    }

    /** 某个 id 的显示名；未知 id 直接显示 id 本身（资源包没带 lang 时的兜底）。 */
    public static Component displayName(String rawId) {
        ensureLoaded();
        ThemeSpec spec = SPECS.get(normalize(rawId));
        return spec != null ? spec.theme().displayName() : Component.literal(String.valueOf(rawId));
    }

    /** 某个 id 的短名（配置屏按钮用）；未知 id 回落到 id 本身。 */
    public static Component shortName(String rawId) {
        ensureLoaded();
        ThemeSpec spec = SPECS.get(normalize(rawId));
        return spec != null ? spec.theme().shortName() : Component.literal(String.valueOf(rawId));
    }

    /** 默认主题（强调色复位、无主题可用时的兜底）。 */
    public static ZtTheme defaultTheme() {
        ensureLoaded();
        ThemeSpec spec = SPECS.get(DEFAULT_ID);
        return spec != null ? spec.theme() : fallback().theme();
    }

    /** 内置兜底调色板：不触发资源扫描，供静态初始化阶段使用。 */
    public static ZtTheme fallbackTheme() {
        return fallback().theme();
    }

    // ------------------------------------------------------------------ 加载

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        load();
    }

    private static void load() {
        SPECS.clear();
        ORDERED.clear();
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.getResourceManager() == null) {
                installFallback("resource manager not ready");
                return;
            }
            Map<ResourceLocation, Resource> files = minecraft.getResourceManager()
                    .listResources("themes", path -> path.getPath().endsWith(".json"));
            if (files.isEmpty()) {
                installFallback("no themes/*.json found");
                return;
            }
            Map<String, JsonObject> raws = readAll(files);
            if (raws.isEmpty()) {
                installFallback("no readable theme files");
                return;
            }

            List<ThemeSpec> parsed = new ArrayList<>();
            for (Map.Entry<String, JsonObject> entry : raws.entrySet()) {
                try {
                    JsonObject resolved = resolveExtends(raws, entry.getKey(), new LinkedHashSet<>());
                    parsed.add(parse(entry.getKey(), resolved));
                } catch (RuntimeException e) {
                    LOGGER.warn("Theme '{}' is invalid and was skipped", entry.getKey(), e);
                }
            }
            if (parsed.isEmpty()) {
                installFallback("all theme files were invalid");
                return;
            }
            parsed.sort(Comparator.comparingInt((ThemeSpec s) -> s.theme().order())
                    .thenComparing(s -> s.theme().id()));
            for (ThemeSpec spec : parsed) {
                if (SPECS.putIfAbsent(spec.theme().id(), spec) != null) {
                    LOGGER.warn("Duplicate theme id '{}' ignored", spec.theme().id());
                    continue;
                }
                ORDERED.add(spec);
            }
            if (!SPECS.containsKey(DEFAULT_ID)) {
                LOGGER.warn("No '{}' theme loaded; using built-in fallback for it", DEFAULT_ID);
            }
            LOGGER.info("Loaded {} theme(s): {}", SPECS.size(), SPECS.keySet());
        } catch (RuntimeException e) {
            LOGGER.warn("Theme registry failed to load; using built-in fallback", e);
        } finally {
            if (SPECS.isEmpty()) {
                installFallback("registry empty");
            }
        }
    }

    private static Map<String, JsonObject> readAll(Map<ResourceLocation, Resource> files) {
        Map<String, JsonObject> raws = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : files.entrySet()) {
            try (var stream = entry.getValue().open()) {
                JsonElement element = JsonParser.parseReader(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                if (!element.isJsonObject()) {
                    LOGGER.warn("Theme file {} is not a JSON object", entry.getKey());
                    continue;
                }
                JsonObject object = element.getAsJsonObject();
                String id = str(object, "id", fileId(entry.getKey()));
                if (raws.putIfAbsent(id, object) != null) {
                    LOGGER.warn("Duplicate theme id '{}' in {}", id, entry.getKey());
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to read theme file {}", entry.getKey(), e);
            }
        }
        return raws;
    }

    private static String fileId(ResourceLocation location) {
        String path = location.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1, path.length() - ".json".length()) : path;
    }

    /** 展开一层 {@code extends}：父对象深合并到子对象上，子对象优先；禁止环与二层以上继承。 */
    private static JsonObject resolveExtends(Map<String, JsonObject> raws, String id, Set<String> stack) {
        JsonObject own = raws.get(id).deepCopy();
        String parentId = str(own, "extends", null);
        own.remove("extends");
        if (parentId == null || parentId.isBlank()) {
            return own;
        }
        if (!stack.add(id)) {
            LOGGER.warn("Theme extends cycle at '{}', ignoring the parent", id);
            return own;
        }
        try {
            JsonObject parent = raws.get(parentId);
            if (parent == null) {
                LOGGER.warn("Theme '{}' extends unknown theme '{}'", id, parentId);
                return own;
            }
            JsonObject inherited = resolveExtends(raws, parentId, stack).deepCopy();
            // 身份字段不继承：子主题没写 name_key 时应该回落到自己的 id，而不是顶着父主题的名字
            for (String key : IDENTITY_KEYS) {
                inherited.remove(key);
            }
            return deepMerge(inherited, own);
        } finally {
            stack.remove(id);
        }
    }

    private static JsonObject deepMerge(JsonObject base, JsonObject over) {
        JsonObject out = base.deepCopy();
        for (Map.Entry<String, JsonElement> entry : over.entrySet()) {
            JsonElement value = entry.getValue();
            JsonElement existing = out.get(entry.getKey());
            if (value.isJsonObject() && existing != null && existing.isJsonObject()) {
                out.add(entry.getKey(), deepMerge(existing.getAsJsonObject(), value.getAsJsonObject()));
            } else {
                out.add(entry.getKey(), value.deepCopy());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 解析

    private static ThemeSpec parse(String fallbackId, JsonObject object) {
        String id = str(object, "id", fallbackId);
        String nameKey = str(object, "name_key", "gui.z_tweaks.theme.name." + id);
        String shortKey = str(object, "short_name_key", "gui.z_tweaks.theme.short." + id);
        int order = integer(object, "order", 100);
        boolean accentEditable = bool(object, "accent_editable", false);
        int corner = Math.max(0, Math.min(4, integer(object, "corner", 1)));
        ZtTheme.Surface surface = surface(str(object, "surface", "gradient"));

        ZtTheme base = fallback().theme();
        JsonObject palette = obj(object, "palette");
        int panelTop = color(palette, "panel_top", base.panelTop());
        int panelBottom = color(palette, "panel_bottom", base.panelBottom());
        int border = color(palette, "border", base.border());
        int accent = color(palette, "accent", base.accent());
        int text = color(palette, "text", base.text());
        int textDim = color(palette, "text_dim", base.textDim());
        int textMuted = color(palette, "text_muted", base.textMuted());
        int good = color(palette, "good", base.good());
        int bad = color(palette, "bad", base.bad());
        int blocked = color(palette, "blocked", base.blocked());
        int hover = color(palette, "hover_overlay", base.hoverOverlay());
        int control = color(palette, "control_bg", base.controlBg());
        int menuBg = color(palette, "menu_bg", base.menuBg());
        int menuBorder = color(palette, "menu_border", base.menuBorder());
        int menuHover = color(palette, "menu_hover", base.menuHover());

        ZtTheme theme = new ZtTheme(id, nameKey, shortKey, order, accentEditable,
                panelTop, panelBottom, surface, corner, border,
                accent, text, textDim, textMuted,
                good, bad, blocked, hover, control,
                menuBg, menuBorder, menuHover);

        JsonObject metrics = obj(object, "metrics");
        ThemeMetrics defaults = ThemeMetrics.DEFAULT;
        ThemeMetrics parsedMetrics = new ThemeMetrics(
                clamp(flt(metrics, "density", defaults.density()), 0.90f, 1.15f),
                clamp(flt(metrics, "detail_left_ratio", defaults.detailLeftRatio()), 0.36f, 0.44f),
                clamp(flt(metrics, "info_font_scale", defaults.infoFontScale()), 0.75f, 0.90f),
                Math.max(4, Math.min(8, integer(metrics, "panel_padding", defaults.panelPadding()))),
                Math.max(2, Math.min(6, integer(metrics, "slot_gap", defaults.slotGap()))));

        JsonObject effects = obj(object, "effects");
        return new ThemeSpec(theme, parsedMetrics,
                parseChrome(obj(object, "chrome")),
                parseEffect(effects, accent),
                parseBlur(obj(effects, "blur")));
    }

    private static ThemeChrome parseChrome(JsonObject object) {
        if (object == null || object.size() == 0) {
            return ThemeChrome.DEFAULT;
        }
        JsonObject frame = obj(object, "frame");
        JsonElement panelElement = frame.get("panel");
        JsonElement popupElement = frame.get("popup");
        ThemeChrome.Frame frameSpec = new ThemeChrome.Frame(
                frameStyle(panelElement),
                frameStyle(popupElement),
                frameSlice(panelElement),
                frameSlice(popupElement));

        JsonObject title = obj(object, "title_bar");
        ThemeChrome.TitleBar titleBar = new ThemeChrome.TitleBar(
                enumValue(ThemeChrome.TitleStyle.class, str(title, "style", "accent"),
                        ThemeChrome.TitleStyle.ACCENT),
                frameSlice(title));

        JsonObject corners = obj(object, "corners");
        ThemeChrome.Corners cornersSpec = new ThemeChrome.Corners(
                enumValue(ThemeChrome.CornerStyle.class, str(corners, "style", "none"),
                        ThemeChrome.CornerStyle.NONE));

        JsonObject pattern = obj(object, "pattern");
        ThemeChrome.Pattern patternSpec = new ThemeChrome.Pattern(
                enumValue(ThemeChrome.PatternStyle.class, str(pattern, "style", "none"),
                        ThemeChrome.PatternStyle.NONE),
                clamp(flt(pattern, "alpha", 0.08f), 0f, 0.30f));

        JsonObject selection = obj(object, "selection");
        ThemeChrome.Selection selectionSpec = new ThemeChrome.Selection(
                enumValue(ThemeChrome.SelectionStyle.class, str(selection, "style", "bar"),
                        ThemeChrome.SelectionStyle.BAR));

        JsonObject button = obj(object, "button");
        ThemeChrome.Button buttonSpec = new ThemeChrome.Button(
                enumValue(ThemeChrome.ButtonStyle.class, str(button, "style", "default"),
                        ThemeChrome.ButtonStyle.DEFAULT),
                frameSlice(button.get("normal")),
                frameSlice(button.get("hover")),
                frameSlice(button.get("disabled")));

        return new ThemeChrome(frameSpec, titleBar, cornersSpec, patternSpec, selectionSpec, buttonSpec);
    }

    /** 外框可以是 `"panel": "bevel"`，也可以是带贴图的对象；两种都解析成样式。 */
    private static ThemeChrome.FrameStyle frameStyle(JsonElement element) {
        if (element == null) {
            return ThemeChrome.FrameStyle.DEFAULT;
        }
        if (element.isJsonObject()) {
            return enumValue(ThemeChrome.FrameStyle.class,
                    str(element.getAsJsonObject(), "style", "default"),
                    ThemeChrome.FrameStyle.DEFAULT);
        }
        return enumValue(ThemeChrome.FrameStyle.class, element.getAsString(),
                ThemeChrome.FrameStyle.DEFAULT);
    }

    /** 外框贴图：对象里有 texture 才解析；文件不存在时返回 null，回落到程序化样式。 */
    private static ThemeChrome.NineSlice frameSlice(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String texture = str(object, "texture", null);
        if (texture == null || texture.isBlank()) {
            return null;
        }
        ResourceLocation location = ResourceLocation.tryParse(texture);
        if (location == null || !textureExists(location)) {
            LOGGER.warn("Theme frame texture '{}' is missing, falling back to programmatic style", texture);
            return null;
        }
        int width = integer(object, "width", 0);
        int height = integer(object, "height", 0);
        if (width <= 0 || height <= 0) {
            LOGGER.warn("Theme frame texture '{}' has no valid width/height", texture);
            return null;
        }
        int left = Math.max(0, Math.min(width / 2, integer(object, "left", 4)));
        int right = Math.max(0, Math.min(width / 2, integer(object, "right", 4)));
        int top = Math.max(0, Math.min(height / 2, integer(object, "top", 4)));
        int bottom = Math.max(0, Math.min(height / 2, integer(object, "bottom", 4)));
        return new ThemeChrome.NineSlice(location, integer(object, "u", 0), integer(object, "v", 0),
                width, height, left, top, right, bottom);
    }

    private static boolean textureExists(ResourceLocation location) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.getResourceManager() != null
                && minecraft.getResourceManager().getResource(location).isPresent();
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String raw, E fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Unknown {} value '{}', using {}", type.getSimpleName(), raw, fallback);
            return fallback;
        }
    }

    private static ThemeEffect parseEffect(JsonObject effects, int accent) {
        JsonObject ambient = obj(effects, "ambient");
        ThemeEffect.Type type = enumValue(ThemeEffect.Type.class,
                str(ambient, "type", "none"), ThemeEffect.Type.NONE);
        int color = color(ambient, "color", accent);
        float density = clamp(flt(ambient, "density", 0.45f), 0f, 1f);
        float speed = clamp(flt(ambient, "speed", 1f), 0.2f, 3f);
        float alpha = clamp(flt(ambient, "alpha", 0.35f), 0f, 1f);
        return new ThemeEffect(type, color, density, speed, alpha);
    }

    private static ThemeBlur parseBlur(JsonObject object) {
        if (!bool(object, "enabled", false)) {
            return ThemeBlur.DISABLED;
        }
        return new ThemeBlur(true, clamp(flt(object, "strength", 0.55f), 0.1f, 1f));
    }

    private static void installFallback(String reason) {
        LOGGER.info("Using built-in default theme ({})", reason);
        SPECS.clear();
        ORDERED.clear();
        ThemeSpec spec = fallback();
        SPECS.put(spec.theme().id(), spec);
        ORDERED.add(spec);
    }

    private static ThemeSpec fallback() {
        if (fallback == null) {
            ZtTheme theme = new ZtTheme(DEFAULT_ID,
                    "gui.z_tweaks.theme.name." + DEFAULT_ID,
                    "gui.z_tweaks.theme.short." + DEFAULT_ID,
                    0, true,
                    0xD2181C21, 0xD20C0E11, ZtTheme.Surface.GRADIENT, 1, 0x38FFFFFF,
                    0xFF55FFFF, 0xFFE8E8E8, 0xFFA8AEB5, 0xFF6E7479,
                    0xFF5FD96A, 0xFFFF6E6E, 0xFF8A5A5A, 0x22FFFFFF, 0x33FFFFFF,
                    0xF0101010, 0x88FFFFFF, 0x20FFFFFF);
            fallback = new ThemeSpec(theme, ThemeMetrics.DEFAULT, ThemeChrome.DEFAULT,
                    ThemeEffect.NONE, ThemeBlur.DISABLED);
        }
        return fallback;
    }

    // ------------------------------------------------------------------ 小工具

    private static String normalize(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_ID;
        }
        String value = raw.trim();
        // 旧配置存的是枚举名；读进来直接映射到新 id，玩家不用手改 TOML
        return switch (value) {
            case "DEFAULT" -> "default";
            case "MD3" -> "md3";
            case "AERO" -> "aero";
            case "WIN_CLASSIC" -> "win_classic";
            default -> value;
        };
    }

    private static ZtTheme.Surface surface(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "solid" -> ZtTheme.Surface.SOLID;
            case "glass" -> ZtTheme.Surface.GLASS;
            default -> ZtTheme.Surface.GRADIENT;
        };
    }

    private static int parseColor(String raw, int fallbackColor) {
        String value = raw.trim();
        if (value.startsWith("#")) {
            value = value.substring(1);
        }
        try {
            if (value.length() == 6) {
                return 0xFF000000 | Integer.parseInt(value, 16);
            }
            if (value.length() == 8) {
                int r = Integer.parseInt(value.substring(0, 2), 16);
                int g = Integer.parseInt(value.substring(2, 4), 16);
                int b = Integer.parseInt(value.substring(4, 6), 16);
                int a = Integer.parseInt(value.substring(6, 8), 16);
                return a << 24 | r << 16 | g << 8 | b;
            }
        } catch (NumberFormatException ignored) {
            // 落到下面的日志
        }
        LOGGER.warn("Bad theme color '{}', using fallback", raw);
        return fallbackColor;
    }

    private static String str(JsonObject object, String key, @Nullable String fallbackValue) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() ? element.getAsString() : fallbackValue;
    }

    private static int integer(JsonObject object, String key, int fallbackValue) {
        JsonElement element = object.get(key);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsInt() : fallbackValue;
        } catch (RuntimeException ignored) {
            return fallbackValue;
        }
    }

    private static float flt(JsonObject object, String key, float fallbackValue) {
        JsonElement element = object.get(key);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsFloat() : fallbackValue;
        } catch (RuntimeException ignored) {
            return fallbackValue;
        }
    }

    private static boolean bool(JsonObject object, String key, boolean fallbackValue) {
        JsonElement element = object.get(key);
        try {
            return element != null && element.isJsonPrimitive() ? element.getAsBoolean() : fallbackValue;
        } catch (RuntimeException ignored) {
            return fallbackValue;
        }
    }

    private static int color(JsonObject palette, String key, int fallbackColor) {
        String raw = str(palette, key, null);
        return raw == null ? fallbackColor : parseColor(raw, fallbackColor);
    }

    private static JsonObject obj(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : new JsonObject();
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
