# TACZ: Z-Tweaks

TACZ 附属模组（Minecraft Forge 1.20.1）：接管改装界面，以 GUI 形式提供更好的武器预览、更简单的改装流程与更易阅读的配件简介和参数。

- 目标平台：Forge `1.20.1-47.3.19` / Java 17
- 必需依赖：TACZ `>= 1.1.8`（编译期与开发运行均需 `libs/tacz-1.20.1-<version>.jar`）
- 许可：GPL-3.0
- 构建：`./gradlew build` / 开发客户端：`./gradlew runClient`

## Agent skills

### Issue tracker

Issues live as GitHub issues, driven by the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default vocabulary: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` + `docs/adr/` at the repo root. See `docs/agents/domain.md`.
