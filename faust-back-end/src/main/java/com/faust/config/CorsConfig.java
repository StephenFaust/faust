package com.faust.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration(proxyBeanMethods = false)
public class CorsConfig {

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 允许携带凭证（cookie / Authorization header）
        config.setAllowCredentials(true);
        // 允许所有来源（开发阶段）
        config.addAllowedOriginPattern("*");
        // 允许所有 header
        config.addAllowedHeader("*");
        // 允许所有 HTTP 方法
        config.addAllowedMethod("*");
        // 暴露的响应头
        config.addExposedHeader("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        // 对所有路径生效
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }
}
