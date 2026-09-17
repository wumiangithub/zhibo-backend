只记概念。任务勾选在 `进度.md`。架构以 `../zhibo-shared/docs/` 为准。

| 日期 | 概念 | 前端类比 | 位置 |
|------|------|----------|------|
| 2026-09-17 | `UriComponentsBuilder` 拼 query | `URLSearchParams` / `new URL().searchParams` | `WatchService` |
| 2026-09-17 | `RestClient` GET + query 签名 | axios `params`；GET 与 POST form 共用一套签名 | `VhallClient#get` |
| 2026-09-17 | `@RestController` + `@RequestMapping` | Express Router / 一组路由 | `ActivityController` |
| 2026-09-17 | `@Service` + 构造注入 | 前端 service 层；依赖由框架注入 | `ActivityService` |
| 2026-09-17 | `@Valid` + Bean Validation | zod / yup 校验请求体 | `CreateActivityRequest` |
| 2026-09-17 | Mockito `@Mock` | vi.mock / jest.mock | `ActivityServiceTest` |
| 2026-09-17 | `@RestControllerAdvice` | Express 统一 error middleware / axios 拦截器 | 两服务 `GlobalExceptionHandler` |
| 2026-09-17 | `ApiResponse<code=0>` | 前端约定的 `{code,msg,data}`，成功认 0 | 两服务 `web/ApiResponse` |
| 2026-09-17 | `MockMvc` standalone | 不启全服，只测 Controller + Advice | `GlobalExceptionHandlerTest` |
| 2026-09-17 | `RestClient` | 封装好的 `axios`/`fetch` 客户端 | `VhallClient` |
| 2026-09-17 | `MockRestServiceServer` | MSW / nock：假 HTTP 服务端 | `VhallClientTest` |
| 2026-09-17 | `@Bean` + `@ConditionalOnMissingBean` | 插件默认导出，可被用户覆盖 | `VhallAutoConfiguration` |
| 2026-09-17 | 泛型 `VhallResponse<T>` | TypeScript `ApiResult<T>`，`data` 类型由调用方定 | `dto/VhallResponse` |
| 2026-09-17 | `RuntimeException` | Promise reject：可不在方法签名强制声明 | `VhallException` |
| 2026-09-17 | `@JsonProperty` / `ignoreUnknown` | 字段改名映射；多出来的 JSON 字段别炸 | Jackson 反序列化 |
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
