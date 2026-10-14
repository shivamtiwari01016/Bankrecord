package com.bankrecon.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI bankReconOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BankRecon API")
                        .description("Banking Transaction & Reconciliation System — Educational API. "
                                + "This application uses synthetic data only. It does NOT implement "
                                + "production-grade banking security, regulatory compliance, or real payment processing.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("BankRecon Project")));
    }
}
