package com.zhibo.admin.oms;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OMS 只读库连接（直播带货「成功退款金额」）。
 * 绑定 {@code oms.datasource.*}，值来自 {@code OMS_DB_*} 环境变量 / {@code .env}。
 * 缺主机或密码时不建连接、不拒启动。密码禁止写入日志或 {@code toString}。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "oms.datasource")
public class OmsDataSourceProperties {

    /** 默认生产只读地址；可用 {@code OMS_DB_HOST} 覆盖。 */
    private String host = "mysql-8c1c7f5fabcf-public.rds.volces.com";

    private int port = 35846;

    private String database = "oms_db";

    private String username = "pro_oms_zb_user";

    private String password = "";

    public boolean isConfigured() {
        return notBlank(host) && notBlank(username) && notBlank(password) && notBlank(database) && port > 0;
    }

    /** JDBC URL：utf8mb4、超时、只读；密码不放在 URL 里。 */
    public String jdbcUrl() {
        return "jdbc:mysql://"
                + host
                + ":"
                + port
                + "/"
                + database
                + "?useUnicode=true&characterEncoding=utf8"
                + "&connectionCollation=utf8mb4_0900_ai_ci"
                + "&serverTimezone=Asia/Shanghai"
                + "&connectTimeout=5000&socketTimeout=10000"
                + "&defaultReadOnly=true&allowMultiQueries=false";
    }

    @Override
    public String toString() {
        return "OmsDataSourceProperties[host="
                + host
                + ", port="
                + port
                + ", database="
                + database
                + ", username="
                + username
                + ", password=***]";
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
