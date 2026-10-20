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

## 工作区安全（D64，红线）

- **禁止**用 `git checkout HEAD --` / `git restore` / `git reset --hard` / `git clean -fd` 覆盖或删除未提交 WIP（含「修乱码」「修错误替换」）。
- 危险操作前先 commit（用户授权）或 `git stash push -u`。
- 细则：`../zhibo-shared/docs/工作区与Git安全.md`。

## 主键 / 外键类型（D56，强制）

类型跟库走，**禁止**把数字主键改成 `String` 再在接口里传来传去：

- 库列 `INT` → Java `Integer`，JSON 出数字。
- 库列 `BIGINT`（含 `rs_mainkey_id`、员工/部门/角色 id、审核人、上传人）→ Java `Long`，JSON 出数字。
- 库列 UUID / `VARCHAR` → Java `String`，JSON 出字符串。
- 路径参数虽是 URL 文本，语义是数字时按 `Long` 解析。
- 例外：确知可能超过 JS 安全整数的外部大整数才用 `String`，并在契约标明原因（当前仅订单中心 `spuId`）。
- 观看端进出场 `userId` 是第三方观众标识，不是 `rs_mainkey_id`，保持 `String`。

细则见 `../zhibo-shared/docs/API约定.md`「通则 · 主键 / 外键类型」。

## 路径前缀（D63，强制）

- 本站 API 统一前缀 **`/api/v1`**（`@RequestMapping("/api/v1/...")` 写全路径；不用 `servlet.context-path`）。
- 细则见 `../zhibo-shared/docs/API约定.md`「通则 · 路径前缀」。

## 路径命名（D62，强制）

- 本站管理端 API **用路径段区分动作**（`/create`、`/list`、`/detail/{id}`、`/update/{id}`、`/delete/{id}` 等），对齐 `/api/v1/roles/list|detail|save-menus`。
- **单 id 放末尾**：正确 `/api/v1/activities/audits/{id}`，错误 `/api/v1/activities/{id}/audits`。多 id 一律 Query/Body，不进路径。
- **禁止**同一 URL 仅靠 `GET`/`POST`/`PUT`/`DELETE` 区分不同业务（如禁止 `GET`+`PUT` 都挂 `/api/v1/xxx/members/{id}`；应拆 `members/list|save/{id}`）。
- 存量（报备/活动/素材/课程/org/watch）已按本条返工；新增接口一律按本条。细则见 `../zhibo-shared/docs/API约定.md`「通则 · 路径命名」。

## 图片 / 文件上传（D60，强制）

- 管理端上传入口是 `GET /api/v1/uploads/tos-sts`（STS → 浏览器直传 TOS），**不是** `POST /api/v1/uploads/image`。
- `POST /api/v1/uploads/image`（微吼代理）已**废弃**：可暂留 Controller，**禁止**新业务接入；勿在文档/示例里再推荐。
- 对象 key 前缀见 `../zhibo-shared/docs/API约定.md` tos-sts「对象 key 目录」权威表；新功能须先登记前缀。

## 业务枚举（强制）

新的业务状态、类型这类枚举，不要先用英文字符串再返工：

- 语义写在 Java 常量里（例如 `MaterialAudit`：`0` 待审核、`1` 已通过、`2` 已驳回）。业务代码引用常量。
- 数据库列用 `INT`，注释写清每个数字。
- 接口 JSON 和 Query 传整数，不传 `pending` / `passed` 这类英文。
- 已经落地的字符串枚举（直播/课程上架与风控、报备状态等）本次不改。以后新增按本条。

## OpenAPI（D58 / D61 / D65，强制）

维护 `docs/openapi/*.yaml`（生成脚本 `docs/openapi/generate-openapi.mjs`，及同步到 shared 的对外片段）时：

- **每一个**路径参数 / Query / 请求与响应 schema 属性都必须有**中文** `description`（D58）。
- 枚举必须写 `enum` 列表，并在 `description`（或独立枚举 schema）里用中文列清每个取值（如 `0 待审核 / 1 已通过 / 2 已驳回`）。禁止只列 code 不写中文。
- **落库类型与长度（D61）**：能表达的约束写进 `maxLength`/`format`/`minimum` 等；`description` 建议附带 `库：VARCHAR(64)` / `BIGINT` / `DECIMAL(10,2)` 等，与 Flyway 一致。导出件供 Apifox，也是正式后端按文档反向实现的输入之一。
- 与 `../zhibo-shared/docs/API约定.md`「通则 · OpenAPI」及决策 D58/D61/D65 一致。
- **D65**：改接口时以契约与代码为准；生成脚本可顺带改。**禁止**每次改接口都跑 `generate-openapi.mjs` 重导 yaml/json；**仅用户明确要求「重新生成 / 导出 OpenAPI」时再跑。**

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
