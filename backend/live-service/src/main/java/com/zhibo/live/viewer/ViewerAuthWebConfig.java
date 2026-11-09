package com.zhibo.live.viewer;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 观众鉴权拦观众接口、回放上报、邀请归因、评论上报。
 * summary/records 等仍不进本拦截器，由 {@code WatchAccessService} 统一强制登录+售卖门禁（D115）。
 */
@Configuration
public class ViewerAuthWebConfig implements WebMvcConfigurer {

    private final ViewerAuthInterceptor viewerAuthInterceptor;

    public ViewerAuthWebConfig(ViewerAuthInterceptor viewerAuthInterceptor) {
        this.viewerAuthInterceptor = viewerAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(viewerAuthInterceptor)
                .addPathPatterns(
                        "/api/v1/viewer/**",
                        "/api/v1/watch/replay/**",
                        "/api/v1/watch/audience/**",
                        "/api/v1/watch/course/**",
                        "/api/v1/watch/comments/**");
    }
}
