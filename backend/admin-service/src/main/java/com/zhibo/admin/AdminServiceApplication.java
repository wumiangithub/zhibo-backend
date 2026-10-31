package com.zhibo.admin;

import com.zhibo.admin.auth.AuthProperties;
import com.zhibo.admin.volc.VolcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 管理端后端启动类。
 * <p>
 * {@code @SpringBootApplication} ≈ Express 里 {@code app.listen()} 的入口，
 * 同时打开组件扫描（默认只扫 {@code com.zhibo.admin}）和自动配置。
 * 共享库 Bean 不再靠扫 {@code com.zhibo}，而由 {@code vhall-common} 的 AutoConfiguration 注册。
 * 售卖密码 AES 由 live-common {@code ActivityJpaConfig} 注册。
 */
@SpringBootApplication
@EnableConfigurationProperties({VolcProperties.class, AuthProperties.class})
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }
}
