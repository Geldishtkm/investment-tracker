package main.java.com.portfolio.tracker.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve static files from classpath:/dist/
        registry.addResourceHandler("/dist/**")
                .addResourceLocations("classpath:/dist/");
        
        // Serve favicon
        registry.addResourceHandler("/favicon.ico")
                .addResourceLocations("classpath:/dist/");
    }
}
