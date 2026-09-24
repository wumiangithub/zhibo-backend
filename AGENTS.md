# 接手必读（后端 agent）

本仓库只写 **Java**。每次开工先看：

`../zhibo-shared/docs/工单.md`

有派给「后端」的待办就先做。架构和需求在 `../zhibo-shared/docs/`（`项目背景.md`、`项目规划.md`、`API约定.md`），经常读，日常不要改。

总控可以改你的代码。你不要改前端，不要打开父目录 `zhibo` 当工作区。

## 本仓文档

- [docs/进度.md](docs/进度.md) — 后端勾选和下一步
- [docs/java学习笔记.md](docs/java学习笔记.md) — 教过的 Java 概念

规则：用户不会 Java（前端类比）；提交前缀 `[backend]`；密钥走仓库根 `.env`（勿提交）；包名 `com.zhibo.vhall`。

## 代码评审

另有一个**技术总监** agent 只做 code review，结论写在 `../zhibo-shared/docs/代码评审.md`，他不改你的代码。有派给「后端」的条目就修，修完把那条状态改成 `已修` 并留一句怎么改的；不认同可以填 `不修` + 原因，别默默跳过。
