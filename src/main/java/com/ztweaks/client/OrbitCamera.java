package com.ztweaks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.ztweaks.config.ZtConfig;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;

/**
 * 原型（throwaway）：模型级轨道相机的唯一状态源。
 *
 * <p>被 mixin 调用；仅当当前屏幕是 {@link ZtRefitScreen} 时生效，
 * 因此不会影响原生改装界面或任何其它界面。</p>
 */
public final class OrbitCamera {

    /** 默认值刻意取单位变换：这样"默认视角"= 原生取景，可直接当对照基线。 */
    public static final float DEFAULT_YAW = 0f;
    public static final float DEFAULT_ROLL = 0f;
    public static final float DEFAULT_ZOOM = 1f;

    private static float yaw = DEFAULT_YAW;
    private static float roll = DEFAULT_ROLL;
    private static float zoom = DEFAULT_ZOOM;

    /** 平移（模型空间的横向/纵向偏移，单位=格）。右键拖拽改这两个值。 */
    private static float offsetX = 0f;
    private static float offsetY = 0f;

    /** 平移上限（格）：防止把枪拖出画面找不回来。 */
    private static final float PAN_LIMIT = 3f;

    /** V 键开关：关掉后就是纯原生取景，用来做"开/关"对照。 */
    private static boolean enabled = true;

    /** mixin 命中计数：界面上显示它，用一张截图就能证明注入成功与否。 */
    private static int applyCount = 0;

    /** 上一帧实际使用的枢轴（模型空间，格）。诊断 HUD 读数用，确认配置真的生效了。 */
    private static float lastPivotX = 0f;
    private static float lastPivotY = 0f;

    private OrbitCamera() {
    }

    /**
     * 三个条件缺一不可：配置没硬关、V 键没临时关、当前是自家界面。
     *
     * <p>{@link ZtConfig#ORBIT_CAMERA} 是<b>硬关</b>——关掉后连 V 键都开不回来，
     * 与 V 键"临时对照一下"是两件事，混在一起会让 V 的行为不可预测。</p>
     */
    public static boolean active() {
        return ZtConfig.ORBIT_CAMERA.get() && enabled
                && Minecraft.getInstance().screen instanceof ZtRefitScreen;
    }

    public static boolean enabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static int applyCount() {
        return applyCount;
    }

    /** 上一帧实际使用的枢轴 X（模型空间，格）。 */
    public static float lastPivotX() {
        return lastPivotX;
    }

    /** 上一帧实际使用的枢轴 Y（模型空间，格）。 */
    public static float lastPivotY() {
        return lastPivotY;
    }

    public static float yaw() {
        return yaw;
    }

    public static float roll() {
        return roll;
    }

    public static float zoom() {
        return zoom;
    }

    public static float offsetX() {
        return offsetX;
    }

    public static float offsetY() {
        return offsetY;
    }

    /**
     * 绕武器自身长轴（模型空间 Z 轴）滚转（左键上下拖）。
     *
     * <p>取模而非 clamp：绕长轴是 SO(2) 自由度，clamp 到 ±179° 会砍掉一半可达姿态，
     * 而且"转到头拖不动"看着像 bug。当前实现没有任何角度插值，两种写法代价相同；
     * 将来若要加平滑，必须先做最短角差处理（{@code Mth.wrapDegrees(to - from)}）。</p>
     */
    public static void rotateRoll(float degrees) {
        roll = (roll + degrees) % 360f;
    }

    /** 绕武器自身竖直轴（模型空间 Y 轴）环绕（左键左右拖）。 */
    public static void rotateY(float degrees) {
        yaw = (yaw + degrees) % 360f;
    }

    /**
     * 纯平移（右键拖）：只挪位置，不改朝向。
     *
     * <p>第一个参数 = 左右（对应鼠标左右移动），第二个参数 = 上下；
     * 单位=格，作用在视图空间，所以"参数变大"就是武器往右（/上）走。</p>
     */
    public static void pan(float dx, float dy) {
        offsetX = clamp(offsetX + dx);
        offsetY = clamp(offsetY + dy);
    }

    private static float clamp(float value) {
        return Math.max(-PAN_LIMIT, Math.min(PAN_LIMIT, value));
    }

    public static void addZoom(float delta) {
        zoom = Math.max(0.4f, Math.min(3.0f, zoom + delta));
    }

    public static void reset() {
        yaw = DEFAULT_YAW;
        roll = DEFAULT_ROLL;
        zoom = DEFAULT_ZOOM;
        offsetX = 0f;
        offsetY = 0f;
    }

    /**
     * 在 TACZ 应用完改装取景变换之后，追加"环绕 + 滚转 + 缩放"、前置"平移"。
     *
     * <p><b>轴向</b>：注入点处模型空间三轴在屏幕上的指向是 X≈视线方向、Y≈屏幕竖直（+Y 向下）、
     * Z≈屏幕水平（+Z 向右）。枪械的长轴（枪管）就是模型 Z 轴 —— 枪口在 −Z，屏幕上朝左。
     * 所以左右拖绕 Y 轴（环绕）、上下拖绕 Z 轴（绕枪管滚转）。</p>
     *
     * <p><b>枢轴</b>：追加的矩阵作用在链尾局部坐标上，枢轴默认是模型空间原点 (0,0,0)，
     * 而原点换算回基岩坐标是 (0,24,0) —— <b>悬在枪身上方约 1 格</b>，直接绕它转就成了
     * "绕一个外部的点翻筋斗"。所以这里再套一层共轭 {@code T(p)·R·T(−p)}，把枢轴搬到
     * 根骨骼所在的高度，也就是枪身长轴上。{@code pivotX/pivotY} 由调用方从
     * {@code BedrockGunModel.getRootNode()} 取（单位为基岩像素，需 /16）。</p>
     *
     * <p><b>平移</b>：不能同样追加在模型空间里 —— 注入点之前 TACZ 已经叠了
     * {@code Rz(180°)}（基岩模型上下颠倒）和改装取景矩阵，
     * 模型自身的 X/Y 轴并不等于屏幕的左右/上下，追加会导致"鼠标左右拖，枪却往别处跑"。
     * 所以平移改为**前置**到当前 pose 上（{@code T·P}），它落在**视图/手持空间**：
     * X = 屏幕左右、Y = 屏幕上下（见 {@code GunItemRendererWrapper#cacheMuzzlePosition}
     * 把 {@code pose.m30/m31} 直接当作相对摄像机中心的横纵偏移）。
     * 这样平移也不受上面旋转的影响：转过枪之后再平移，依然是纯屏幕上的挪动。</p>
     */
    public static void applyTo(PoseStack poseStack, float pivotX, float pivotY) {
        if (!active()) {
            return;
        }
        applyCount++;
        lastPivotX = pivotX;
        lastPivotY = pivotY;
        float scale = 1f / Math.max(zoom, 0.05f);
        // ① 环绕（Y 轴）+ 绕长轴滚转（Z 轴）+ 缩放，全部绕枪身长轴 —— 模型空间
        Matrix4f cam = new Matrix4f()
                .identity()
                .rotateY((float) Math.toRadians(yaw))
                .rotateZ((float) Math.toRadians(roll))
                .scale(scale, scale, scale);
        // ② 把枢轴从模型原点搬到枪身长轴上：T(p)·cam·T(−p)
        if (pivotX != 0f || pivotY != 0f) {
            cam = new Matrix4f().identity().translate(pivotX, pivotY, 0f)
                    .mul(cam)
                    .translate(-pivotX, -pivotY, 0f);
        }
        poseStack.mulPoseMatrix(cam);
        // ③ 平移 —— 前置到当前 pose，等价于在屏幕上整体挪动武器
        if (offsetX != 0f || offsetY != 0f) {
            Matrix4f pose = poseStack.last().pose();
            pose.set(new Matrix4f().identity().translate(offsetX, offsetY, 0f).mul(pose));
        }
    }
}
