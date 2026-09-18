# TACZ: Z-Tweaks（TACZ：改装UI调整）

面向 **Minecraft Forge 1.20.1** 的 [TACZ](https://github.com/MCModderAnchor/TACZ)（Timeless & Classics Guns）附属模组。

## 定位

接管 TACZ 自带的改装界面（默认 Z 键），以 GUI 形式提供：

- **更好的武器预览** —— 可拖拽旋转、滚轮缩放的轨道相机
- **更简单的改装** —— 左键安装 / 右键卸下；列表默认只显示可安装的配件
- **更易读的简介和参数** —— 由配件属性修改器自动推导的 Pros / Cons 双栏（相对裸枪基准）

设计参考 Garry's Mod 的 ARC-9 配件系统。

## 状态

M0 骨架阶段（仓库与构建打通）。术语表见 `CONTEXT.md`，决策记录见 `docs/adr/`。

## 构建

1. 取得 TACZ 本体 jar（二选一）：
   - 在相邻仓库构建：`cd ../TACZ && ./gradlew build`，取 `build/libs/` 产物；
   - 或从 CurseForge / Modrinth 下载对应版本。
2. 将 jar 放入 `libs/`，命名为 `tacz-1.20.1-1.1.8-hotfix.jar`（与 `gradle.properties` 中的 `tacz_version` 一致）。
3. 构建本模组：

```bash
./gradlew build          # 产物在 build/libs/z_tweaks-1.20.1-0.1.0.jar
./gradlew runClient      # 启动开发客户端（需要 libs/ 中存在 TACZ jar）
```

> 注：改动依赖版本后同步修改 `gradle.properties` 与 `libs/` 中的文件名。

## 许可

**GPL-3.0**，与 TACZ 本体许可一致。引用 TACZ 源码之处均注明来源。
