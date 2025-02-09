package net.casim.jbd.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI loanAppOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Loan API")
                        .version("1.0")
                        .description("API for creating, listing, and paying loans."));
    }
}
