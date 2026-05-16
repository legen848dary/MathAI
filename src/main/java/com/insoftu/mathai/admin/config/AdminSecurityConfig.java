package com.insoftu.mathai.admin.config;

import com.insoftu.mathai.admin.security.AdminJwtFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class AdminSecurityConfig {

    @Bean
    public FilterRegistrationBean<AdminJwtFilter> adminJwtFilterRegistration(AdminJwtFilter filter) {
        FilterRegistrationBean<AdminJwtFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/admin/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        return registration;
    }
}
