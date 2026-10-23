package com.zhibo.live.viewer;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 观众鉴权只拦观众接口。观看 {@code /api/v1/watch/**} 和回调保持不登录。 */
@Configuration
public class ViewerAuthWebConfig implements WebMvcConfigurer {

    private final ViewerAuthInterceptor viewerAuthInterceptor;

    public ViewerAuthWebConfig(ViewerAuthInterceptor viewerAuthInterceptor) {
        this.viewerAuthInterceptor = viewerAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(viewerAuthInterceptor).addPathPatterns("/api/v1/viewer/**");
    }
}
