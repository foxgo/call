package com.callcenter.iam.interfaces.rest.support;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI iamOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Call IAM API")
                .version("v1")
                .description("IAM user center APIs"));
    }

    @Bean
    public GroupedOpenApi iamApiGroup() {
        return GroupedOpenApi.builder()
                .group("iam")
                .pathsToMatch("/api/iam/**")
                .build();
    }
}
