package com.ztweaks.client;

import com.mojang.blaze3d.vertex.PoseStack;
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
    public static final float DEFAULT_PITCH = 0f;
    public static final float DEFAULT_ZOOM = 1f;

    private static float yaw = DEFAULT_YAW;
    private static float pitch = DEFAULT_PITCH;
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

    private OrbitCamera() {
    }

    public static boolean active() {
        return enabled && Minecraft.getInstance().screen instanceof ZtRefitScreen;
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

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
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

    /** 绕武器原点的 X 轴旋转（左键上下拖）：观感是"翻转武器"。 */
    public static void rotateX(float degrees) {
        pitch = Math.max(-179f, Math.min(179f, pitch + degrees));
    }

    /** 绕武器原点的 Y 轴旋转（左键左右拖）。 */
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
        pitch = DEFAULT_PITCH;
        zoom = DEFAULT_ZOOM;
        offsetX = 0f;
        offsetY = 0f;
    }

    /**
     * 在 TACZ 应用完改装取景变换之后，追加"旋转 + 缩放"、前置"平移"。
     *
     * <p><b>旋转/缩放</b>：以 {@code mulPoseMatrix} 追加，展开后是
     * {@code P·Ry·Rx·S}，作用在**模型空间**，枢轴取模型自身原点（枪就在原点附近），
     * 观感就是"绕着武器转/翻转武器"。这里不能照抄 TACZ 的
     * {@code T(0,1.5,0)·M·T(0,-1.5,0)} 共轭写法：1.5 格在模型空间里等于
     * "模型上方 1.5 格"，大角度会把枪甩出画面。</p>
     *
     * <p><b>平移</b>：不能同样追加在模型空间里 —— 注入点之前 TACZ 已经叠了
     * {@code Rz(180°)}（基岩模型上下颠倒）和改装取景矩阵，
     * 模型自身的 X/Y 轴并不等于屏幕的左右/上下，追加会导致"鼠标左右拖，枪却往别处跑"。
     * 所以平移改为**前置**到当前 pose 上（{@code T·P}），它落在**视图/手持空间**：
     * X = 屏幕左右、Y = 屏幕上下（见 {@code GunItemRendererWrapper#cacheMuzzlePosition}
     * 把 {@code pose.m30/m31} 直接当作相对摄像机中心的横纵偏移）。
     * 这样平移也不受上面旋转的影响：转过枪之后再平移，依然是纯屏幕上的挪动。</p>
     */
    public static void applyTo(PoseStack poseStack) {
        if (!active()) {
            return;
        }
        applyCount++;
        float scale = 1f / Math.max(zoom, 0.05f);
        // ① 绕武器原点的环绕（Y 轴）与翻转（X 轴），外加缩放 —— 模型空间
        poseStack.mulPoseMatrix(new Matrix4f()
                .identity()
                .rotateY((float) Math.toRadians(yaw))
                .rotateX((float) Math.toRadians(pitch))
                .scale(scale, scale, scale));
        // ② 平移 —— 前置到当前 pose，等价于在屏幕上整体挪动武器
        if (offsetX != 0f || offsetY != 0f) {
            Matrix4f pose = poseStack.last().pose();
            pose.set(new Matrix4f().identity().translate(offsetX, offsetY, 0f).mul(pose));
        }
    }
}
