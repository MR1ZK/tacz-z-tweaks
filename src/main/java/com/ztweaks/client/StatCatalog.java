package com.ztweaks.client;

import com.tacz.guns.api.modifier.IAttachmentModifier;
import com.tacz.guns.resource.modifier.AttachmentCacheProperty;
import com.tacz.guns.resource.modifier.AttachmentPropertyManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 参数目录：可以作为"按参数排序 / 筛选"依据的配件参数，以及它们的取值口。
 *
 * <p><b>为什么要有这一层。</b>同一个参数在 TACZ 里有三种读法，直接拿错会排错队：
 * <ul>
 *   <li>配件 JSON 里的原始 {@code Modifier}（{@code addend} / {@code percent} / {@code multiplier}）——
 *       最便宜，但三个字段语义不同（{@code addend} 是绝对量、{@code percent} 是加性比例且基线为 1），
 *       跨配件直接比大小会串味；</li>
 *   <li>{@code AttachmentCacheProperty.getCache(id)} —— 是"当前值"，但类型随 modifier 变
 *       （{@code Float} / {@code Integer} / {@code ParameterizedCachePair}），而 {@code getCache}
 *       的泛型是指向推断的：写错类型能编译、运行期才 {@code ClassCastException}；</li>
 *   <li>{@link IAttachmentModifier#getPropertyDiagramsData} —— TACZ 自己画属性条用的数据，
 *       它的 {@code modifier} 字段就是"相对枪械基值的增减量"，类型统一是 {@link Number}，
 *       不需要任何强转，而且枪械基值（含开火模式、{@code SyncConfig} 倍率）由 TACZ 自己算。</li>
 * </ul>
 * 这里统一走第三种：**一次调用直接拿到"这个配件把该参数改了多少"**，不需要自己复刻基值，
 * 也就不存在"基值算错"这类静默错误。</p>
 *
 * <p><b>为什么只有十个。</b>{@code damage}（距离衰减曲线）、{@code inaccuracy}（四个子类型，
 * 且开镜那条被 {@code 1 - value} 反向）、{@code silence} / {@code explosion} / {@code ignite}
 * （布尔或开关）、{@code movement_speed}（三个倍率打包）这六项塌缩不出一个可比较的标量，
 * 一律不进排序项。取舍的完整理由见 issue #17。</p>
 *
 * <p>本类只读不写，也不持有任何状态 —— 每个会话/每把枪算出来的值都从传入的 cache 现取，
 * 不存在"缓存漂移"。</p>
 */
public final class StatCatalog {

    private StatCatalog() {
    }

    /**
     * 一个排序项：modifier 的 id（即 TACZ 属性注册表里的键）与它的显示名 lang key。
     *
     * <p>方向（越大越好 / 越小越好）不写在这里——它随 {@code DiagramsData.positivelyBetter}
     * 在运行时取，TACZ 改了方向我们自动跟着改。</p>
     */
    public record StatDef(String modifierId, String langKey) {
    }

    /** 十个可排序项。顺序 = TACZ 属性注册表的顺序 = 弹出层里的顺序。 */
    public static final List<StatDef> SORTABLE = List.of(
            new StatDef("ads", "gui.z_tweaks.refit.stat.ads"),
            new StatDef("recoil", "gui.z_tweaks.refit.stat.recoil"),
            new StatDef("rpm", "gui.z_tweaks.refit.stat.rpm"),
            new StatDef("weight_modifier", "gui.z_tweaks.refit.stat.weight"),
            new StatDef("effective_range", "gui.z_tweaks.refit.stat.effective_range"),
            new StatDef("ammo_speed", "gui.z_tweaks.refit.stat.ammo_speed"),
            new StatDef("armor_ignore", "gui.z_tweaks.refit.stat.armor_ignore"),
            new StatDef("pierce", "gui.z_tweaks.refit.stat.pierce"),
            new StatDef("knockback", "gui.z_tweaks.refit.stat.knockback"),
            new StatDef("head_shot", "gui.z_tweaks.refit.stat.head_shot"));

    /**
     * 读一个**已经跑过 {@code eval} 的枪栈**在某个参数上的**带方向的增减量**：
     * 正数 = 比枪械裸值更好，负数 = 更差，0 = 无改动。
     *
     * <p>这里量的是"相对枪械裸值"的总量——所以单独看它只能用来**排序**（同一槽位的候选件
     * 共享同一个基准，比大小是等价的）。要判断"这个件比现在装着的那个好还是差"，
     * 得用 {@link #marginal} 拿两份 cache 相减。</p>
     *
     * @param stat     要量的参数
     * @param gunItem  与 {@code cache} 配套的那把枪的栈（TACZ 靠它取开火模式等 NBT 信息）
     * @param gunData  枪械数据
     * @param cache    对上面那把枪跑过 {@code eval} 的属性缓存
     * @return 带方向的增减量；该枪没有任何配件带这个参数时返回 0
     */
    public static double read(StatDef stat, ItemStack gunItem, GunData gunData, AttachmentCacheProperty cache) {
        IAttachmentModifier<?, ?> modifier = AttachmentPropertyManager.getModifiers().get(stat.modifierId());
        if (modifier == null) {
            return 0;
        }
        List<IAttachmentModifier.DiagramsData> data = modifier.getPropertyDiagramsData(gunItem, gunData, cache);
        if (data.isEmpty()) {
            return 0;
        }
        double delta;
        boolean positivelyBetter = data.get(0).positivelyBetter();
        if (data.size() == 1) {
            delta = data.get(0).modifier().doubleValue();
        } else {
            // 多条的目前只有后坐力（pitch / yaw 两条，`RecoilModifier.getDiagramsDataSize() == 2`）。
            // 取绝对值最大的那一条 = 最坏的那个轴：一个只改好 pitch 却把 yaw 改坏的配件不该被排到前面。
            // 两条的 positivelyBetter 相同（同一个 record 构造），取第一条即可。
            delta = 0;
            for (IAttachmentModifier.DiagramsData entry : data) {
                double value = entry.modifier().doubleValue();
                if (Math.abs(value) > Math.abs(delta)) {
                    delta = value;
                }
            }
        }
        return positivelyBetter ? delta : -delta;
    }

    /**
     * 量一个配件对某个参数的**边际改善量**：正数 = 比现在装着的强，负数 = 不如现在，0 = 无差别。
     *
     * <p>两份 cache 必须是同一把枪的"装之前"与"装之后"——这是"只看改善的"筛选的判据。
     * 注意不能拿 {@link #read} 的绝对值当改善量：一个比裸枪好、但不如当前已装件的候选件，
     * 那样会被算成"改善"，与玩家的直觉相反。</p>
     */
    public static double marginal(StatDef stat, ItemStack gunItem, GunData gunData,
                                  AttachmentCacheProperty before, AttachmentCacheProperty after) {
        return read(stat, gunItem, gunData, after) - read(stat, gunItem, gunData, before);
    }
}
