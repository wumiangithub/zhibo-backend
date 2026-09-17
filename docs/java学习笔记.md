只记概念。任务勾选在 `进度.md`。架构以 `../zhibo-shared/docs/` 为准。

| 日期 | 概念 | 前端类比 | 位置 |
|------|------|----------|------|
| 2026-09-17 | 静态工具类 + `MessageDigest` | 纯函数 `crypto.createHash('md5')`，无 Spring Bean | `VhallSignUtil` |
| 2026-09-17 | JUnit 5 `@Test` | Vitest / Jest 的 `test()` | `VhallSignUtilTest` |
| 2026-09-17 | Maven Wrapper（`mvnw`） | 项目自带包管理器 / `npx`：不用全局装 Maven | `backend/mvnw.cmd` |
| 2026-09-17 | Maven `pom.xml` | `package.json` | `backend/pom.xml` |
| 2026-09-17 | Maven 多模块 | workspace：根管版本，子包才真正依赖 | 三个子模块 |
| 2026-09-17 | `dependencyManagement` | pnpm catalog，只锁版本 | 父 POM |
| 2026-09-17 | `starter-web` vs `spring-web` | Express 全家桶 vs 只引入 HTTP 客户端 | vhall-common 用后者 |
| 2026-09-17 | `@SpringBootApplication` | 入口，默认只扫本包 | 两个 Application |
| 2026-09-17 | `@ConfigurationProperties` | 把 env/yml 绑到 typed 对象 | `VhallProperties` |
| 2026-09-17 | AutoConfiguration | 装了依赖就生效的插件 | `VhallAutoConfiguration` |
| 2026-09-17 | `application-local.yml` | `.env.development` | 两个 service |
