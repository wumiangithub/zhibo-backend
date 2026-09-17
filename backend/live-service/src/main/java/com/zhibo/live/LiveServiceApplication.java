package com.zhibo.live;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 观看端后端启动类。
 * <p>
 * {@code @SpringBootApplication} 默认只扫 {@code com.zhibo.live}。
 * 共享库 Bean 由 {@code vhall-common} 的 AutoConfiguration 注册，不靠扫 {@code com.zhibo}。
 */
@SpringBootApplication
public class LiveServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiveServiceApplication.class, args);
    }
}
