package com.ppiyaki.notification;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class NotificationWebMvcConfig implements WebMvcConfigurer {

    private final SeniorActivityInterceptor seniorActivityInterceptor;

    public NotificationWebMvcConfig(final SeniorActivityInterceptor seniorActivityInterceptor) {
        this.seniorActivityInterceptor = seniorActivityInterceptor;
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(seniorActivityInterceptor)
                .addPathPatterns("/api/**");
    }
}
