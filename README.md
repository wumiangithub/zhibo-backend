# zhibo-backend

Java 后端。公用架构文档：`../zhibo-shared`。前端：`../zhibo-frontend`。

## 运行（需 JDK 17 + MySQL）

凭证与库：仓库根 `.env`（可进 git；参考 `.env.example`），需 `VHALL_*`、`MYSQL_*`、TOS 非密钥项 `VOLC_*`。火山 AK/SK 放 `.env.local`（不进 git）。从 `backend/` 启动会向上加载 `.env` 再叠 `.env.local`。


首次启动时 **Flyway** 在库 `zhibo` 建 `activity` 表（迁移在 `live-common`）。

```bat
cd D:\ai-project\zhibo\zhibo-backend\backend
mvnw.cmd -pl admin-service -am install -DskipTests
java -jar admin-service\target\admin-service-1.0.0.jar
```

另开终端：

```bat
cd D:\ai-project\zhibo\zhibo-backend\backend
mvnw.cmd -pl live-service -am install -DskipTests
java -jar live-service\target\live-service-1.0.0.jar
```

`admin-service` → 8081，`live-service` → 8082。

若提示找不到 `java`：

```bat
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
```
