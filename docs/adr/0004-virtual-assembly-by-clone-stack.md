# 悬停虚拟装配：在渲染入口替换为克隆枪栈（全项目第二处 mixin）

计划 §3.2-3 要求"鼠标悬停候选配件即在预览中看到装上效果，移开即还原"。难点在于那把 3D 枪**不在** `GunRefitScreen` 里绘制：它是第一人称手部渲染 pass 被 `RefitTransform` 掰到取景视角的结果，且只有 `Minecraft.screen instanceof GunRefitScreen` 时取景进度才增长（见 ADR-0002）。所以没有"给 Screen 传一个额外 ItemStack 让它渲染"的余地 —— `GunRefitScreen.render()` 只画控件与属性条。

实测 TACZ 1.1.8-hotfix 的渲染链后确认只有一个咽喉：`GunItemRendererWrapper.renderFirstPerson(player, stack, ...)`。同一个 `stack` 既被交给 `FirstPersonRenderGunEvent.applyFirstPersonGunTransform`（取景变换），又被交给 `BedrockGunModel.render`（真正画配件：该方法开头即用 `iGun.getAttachment(gunItem, type)` 把配件灌进渲染缓存，`BedrockGunModel:246-263`）。于是在方法入口用 `@ModifyVariable(argsOnly = true)` 把 `stack` 换成"已装上悬停配件"的克隆件，**模型、配件、枪口、取景变换会一起跟着变**，无需改 TACZ 任何代码，也不必侵入 §8 预警的模型内部类。

代价是全项目 mixin 从 1 处变为 2 处 —— 修订 ADR-0003 中"全项目唯一一处 mixin"的表述。换来的是零侵入，外加完整复用 TACZ 渲染管线（瞄具 Mount、转接口 adapter、弹匣扩容等级、镭射/握把互斥可见性等全部逻辑）。

## Considered Options

- **写进手持 ItemStack 的 NBT 做"本地预览"**：M1 试用过、已删除。服务端只认自己那份背包（`ClientMessageRefitGun.handle` 从服务端 inventory 取件），客户端写本地 NBT 只会让界面显示装了、服务端仍是空槽，两边分叉，还会被随后的服务端刷新打脸。
- **克隆件交给自定义渲染**：需自行复刻 `BedrockGunModel` 的配件装配、adapter、瞄具 Mount、弹匣扩容等级等逻辑，等于重写一半渲染层，且 TACZ 一改就会静默偏离。
- **mixin `BedrockGunModel.render` 的配件循环**（计划 §5 附录 A 备选）：注入点更深，要逐类型替换，还要多处理 `renderAccelerated` 分支；在入口换 `stack` 已能达成同样效果，故不取。

## Consequences

- 该 mixin 与第一处同样设 `require = 0`：注入失败时静默降级为"照常渲染真枪，只是没有悬停预览"，界面其余功能不受影响。是否生效看诊断 HUD 的"虚拟装配：命中"读数（为 0 即未生效）。
- 克隆件只活在渲染调用里，**永不写入手持物品**，因此不存在与服务端状态分叉的风险。
- 生效护栏（缺一即可能把预览画到别的枪上）：当前界面为 `ZtRefitScreen`、悬停对象仍在、手上仍是预览针对的那把枪（比对 gun id）、该槽位确实 `allowAttachmentType`。换枪、切槽、关屏都会自动失效。
- 克隆按"基准枪 NBT + 悬停对象"缓存，只在悬停对象或基准枪 NBT 变化时重建，避免每帧 clone + 写 NBT。
- 悬停**不改动 `selected`**：否则鼠标从候选列表划向安装按钮的途中会把待安装目标一并改掉，容易装错件。但**详情条的内容跟着悬停走**（`selected` 只决定鼠标移出后回到哪里）—— 3D 既然是"装上这个件之后"的样子，旁边那半参数就不能还说"当前实际"，否则屏幕上是一把不存在的枪配一组真实的数。
- 详情条必须显式标出**预览：\<件名\>**。3D 能自证（枪上真多了个瞄具），数字不能——而游戏里"数值变了"的默认解释是"已经生效"，最坏的误解是玩家以为装上了其实没装。
- 参数侧与 3D 侧**共用同一条护栏**（见上一条）：护栏一旦分叉，就会出现"3D 已失效、参数还停在假设态"的错配，那种错比两边都失效更难查。
- 悬停触发的重算**只在悬停行变化时发生**，按 枪id + NBT + 候选件 id 缓存，不按帧算。
- 若未来 `renderFirstPerson` 消失或签名变更，降级路径是退回"选中即预览"（`selected` 语义仍在），界面层无需改动。
