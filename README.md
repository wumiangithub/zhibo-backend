# zhibo-backend

Java 后端。公用架构文档：`../zhibo-shared`。前端：`../zhibo-frontend`。

## 运行（需 JDK 17）

```bat
cd backend
mvnw.cmd spring-boot:run -pl admin-service
mvnw.cmd spring-boot:run -pl live-service
```

`mvnw.cmd` 尚未补齐（进度 T0.2）。凭证：`VHALL_APP_KEY` / `VHALL_APP_SECRET`。
