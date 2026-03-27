package com.stablebridge.txrecovery.application.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiKeyAuthFilterConfig {

    private final ApiKeyProperties apiKeyProperties;

    @Bean
    FilterRegistrationBean<ApiKeyAuthFilter> apiKeyAuthFilterRegistration() {
        var registration = new FilterRegistrationBean<>(new ApiKeyAuthFilter(apiKeyProperties.key()));
        registration.addUrlPatterns("/api/v1/*");
        registration.setOrder(1);
        return registration;
    }
}
