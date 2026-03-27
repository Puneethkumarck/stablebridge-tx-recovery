package com.stablebridge.txrecovery.application.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiKeyAuthFilterConfig {

    @Bean
    FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration(
            @Value("${str.api.key:}") String apiKey) {
        var registration = new FilterRegistrationBean<>(new ApiKeyAuthFilter(apiKey));
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1);
        return registration;
    }
}
