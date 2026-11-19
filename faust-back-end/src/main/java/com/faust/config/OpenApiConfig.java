package com.faust.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Agent Center API")
                        .version("2.0.0")
                        .description("Agent 中心后端接口文档，提供 Agent 的创建、发布、查询、管理等功能")
                        .contact(new Contact()
                                .name("Agent Center Team")));
    }
}
