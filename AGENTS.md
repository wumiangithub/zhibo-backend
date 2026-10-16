# 接手必读（后端 agent）

本仓库只写 **Java**。每次开工先看：

`../zhibo-shared/docs/工单.md`

有派给「后端」的待办就先做。架构和需求在 `../zhibo-shared/docs/`（`项目背景.md`、`项目规划.md`、`API约定.md`），经常读，日常不要改。  
管理端登录（T21）：只读 `../zhibo-shared/docs/管理端登录.md` + API约定「管理端登录」，**不要**再打开墨刀原型；密钥已在 `.env.local`。

总控可以改你的代码。你不要改前端，也不要改 `zhibo-wxmini`，不要打开父目录 `zhibo` 当工作区。

## 本仓文档

- [docs/进度.md](docs/进度.md) — 后端勾选和下一步
- [docs/java学习笔记.md](docs/java学习笔记.md) — 教过的 Java 概念

规则：用户不会 Java（前端类比）；提交前缀 `[backend]`；共享配置走 `.env`（可提交）；`VOLC_ACCESS_KEY`/`SECRET` 等敏感项放 `.env.local`（不进 git；GitHub 会拦）；包名 `com.zhibo.vhall`。

## 本地进程（强制）

发现 `admin-service` / `live-service` 等进程退出、端口不通、或 shell 任务异常结束时：**只向用户汇报**，**禁止擅自重新拉起**。

原因：用户或总控 / 其他 agent 可能正在重启、换 jar、或故意关掉；你这边再自动起，容易和对方抢端口、互相杀进程，形成死循环。

需要再开服务时，等用户明确说「启动 / 重启」再动手。

## 依赖版本（D36，强制）

- `pom.xml`：**禁止**版本区间、`LATEST`、`RELEASE`；Spring Boot parent 与显式依赖都写死具体版本（含补丁号）。
- 由 Boot BOM 管理的 starter 可不写 `<version>`，但**升级 Boot parent 必须显式改版本并说明**，禁止顺手升。
- 新增第三方库必须在 pom 里钉死版本；升级单独提交，写进 `docs/进度.md`。

## 代码评审

另有一个**技术总监** agent 只做 code review，结论写在 `../zhibo-shared/docs/代码评审.md`，他不改你的代码。有派给「后端」的条目就修，修完把那条状态改成 `已修` 并留一句怎么改的；不认同可以填 `不修` + 原因，别默默跳过。
