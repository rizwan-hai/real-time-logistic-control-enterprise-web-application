package com.example.logistics.shared.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {
    @Bean OpenAPI logisticsOpenApi() {
        return new OpenAPI().info(new Info().title("Logistics Control API").version("v1")
                .description("Versioned logistics control platform API"));
    }
}
