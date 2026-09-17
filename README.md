# zhibo-backend

Java 后端。公用架构文档：`../zhibo-shared`。前端：`../zhibo-frontend`。

## 运行（需 JDK 17）

```bat
cd backend
mvnw.cmd spring-boot:run -pl admin-service
mvnw.cmd spring-boot:run -pl live-service
```

凭证：仓库根目录 `.env`（参考 `.env.example`，已 gitignore）。启动时自动向上查找并加载。
