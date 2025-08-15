package com.portfolio.tracker.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI portfolioTrackerOpenAPI() {
        Server devServer = new Server();
        devServer.setUrl("http://localhost:8080");
        devServer.setDescription("Development server");

        Server prodServer = new Server();
        prodServer.setUrl("https://portfolio-tracker-production.com");
        prodServer.setDescription("Production server");

        Contact contact = new Contact();
        contact.setName("Portfolio Tracker Team");
        contact.setEmail("dev@portfoliotracker.com");
        contact.setUrl("https://github.com/Geldishtkm/investment-tracker");

        License mitLicense = new License()
                .name("MIT License")
                .url("https://opensource.org/licenses/MIT");

        Info info = new Info()
                .title("Portfolio Tracker API")
                .version("1.0.0")
                .description("""
                    🚀 **Portfolio Tracker API** - Professional Investment Portfolio Management System
                    
                    ## Features
                    - **Portfolio Management**: Create, read, update, and delete investment portfolios
                    - **Asset Tracking**: Monitor individual assets with real-time price updates
                    - **Risk Analysis**: Calculate VaR (Value at Risk) and other risk metrics
                    - **Portfolio Optimization**: Rebalancing algorithms and optimization strategies
                    - **Real-Time Data**: WebSocket integration for live market data
                    - **Security**: JWT authentication with Multi-Factor Authentication (MFA)
                    - **Analytics**: Comprehensive portfolio performance analytics
                    
                    ## Authentication
                    Most endpoints require JWT authentication. Include the token in the Authorization header:
                    ```
                    Authorization: Bearer <your-jwt-token>
                    ```
                    
                    ## Rate Limiting
                    API calls are limited to 100 requests per minute per user.
                    
                    ## Support
                    For technical support, please contact our development team.
                    """)
                .contact(contact)
                .license(mitLicense);

        // Define security scheme for JWT
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Enter JWT token");

        Components components = new Components()
                .addSecuritySchemes("bearerAuth", securityScheme);

        return new OpenAPI()
                .info(info)
                .servers(List.of(devServer, prodServer))
                .components(components);
    }
}
