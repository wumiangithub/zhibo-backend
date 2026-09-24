# zhibo-backend

Java 后端。公用架构文档：`../zhibo-shared`。前端：`../zhibo-frontend`。

## 运行（需 JDK 17）

两条命令各开一个终端，都在 `zhibo-backend` 下执行。第一条会一直占着窗口，不要在同一个窗口接着敲第二条。

```bat
cd D:\ai-project\zhibo\zhibo-backend\backend
mvnw.cmd spring-boot:run -pl admin-service -am
```

```bat
cd D:\ai-project\zhibo\zhibo-backend\backend
mvnw.cmd spring-boot:run -pl live-service -am
```

`-am` 会先带上共享模块 `vhall-common`。  
`admin-service` 起在 8081，`live-service` 起在 8082。

若提示找不到 `java`：

```bat
set JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot
set PATH=%JAVA_HOME%\bin;%PATH%
```

凭证：仓库根目录 `.env`（参考 `.env.example`）。本练习项目故意入库，clone 后一般已有。从 `backend/` 启动时会向上找到并加载。

已经 `package` 过、只想快速再起，也可以用 jar（效果和上面一样）：

```bat
cd D:\ai-project\zhibo\zhibo-backend
java -jar backend\admin-service\target\admin-service-1.0.0.jar
java -jar backend\live-service\target\live-service-1.0.0.jar
```
