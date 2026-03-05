package com.containerize.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private AppConfig appConfig;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (appConfig.getRateLimit().isEnabled()) {
            registry.addInterceptor(new RateLimitInterceptor(
                            appConfig.getRateLimit().getGeneralRequestsPerMinute(),
                            appConfig.getRateLimit().getHeavyRequestsPerMinute()
                    ))
                    .addPathPatterns("/api/**")
                    .excludePathPatterns("/api/docs/**", "/api/openapi.json");
        }
    }
}
