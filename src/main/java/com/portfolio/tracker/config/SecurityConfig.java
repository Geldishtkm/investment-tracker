package com.portfolio.tracker.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.portfolio.tracker.security.JwtFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);
    
    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HandlerMappingIntrospector introspector) throws Exception {
        logger.info("Configuring Spring Security filter chain...");
        
        // Create MvcRequestMatchers with proper servlet paths
        MvcRequestMatcher.Builder mvcMatcherBuilder = new MvcRequestMatcher.Builder(introspector);

        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterBefore(jwtFilter, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
            // Without a login form Spring answers unauthenticated requests with 403, which looks like a
            // permissions problem. Answer 401 instead, so the frontend knows to send the user to login
            .exceptionHandling(ex -> ex.authenticationEntryPoint(this::rejectUnauthenticated))
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny()) // Prevent clickjacking
                .contentTypeOptions(contentType -> {}) // Prevent MIME type sniffing
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000) // 1 year
                )
            )
            .authorizeHttpRequests(auth -> auth
                // Public endpoints (no authentication required). Market data is read-only for
                // anonymous users; forced cache refreshes need a login so they can't exhaust the
                // CoinGecko rate limit
                .requestMatchers(mvcMatcherBuilder.pattern("/")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/health")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/ping")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/error")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/auth/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/api/auth/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern(HttpMethod.GET, "/api/crypto/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern(HttpMethod.GET, "/api/price-history/**")).permitAll()

                // MFA steps of the login flow run before the user has a token
                .requestMatchers(mvcMatcherBuilder.pattern("/api/security/mfa/setup")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/api/security/mfa/verify")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/api/security/mfa/status/**")).permitAll()

                // Documentation endpoints (public for development, can be restricted in production)
                .requestMatchers(mvcMatcherBuilder.pattern("/swagger-ui/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/swagger-ui.html")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/api-docs/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/v3/api-docs/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/webjars/**")).permitAll()

                // Static resources
                .requestMatchers(mvcMatcherBuilder.pattern("/css/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/js/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/images/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/assets/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/dist/**")).permitAll()
                .requestMatchers(mvcMatcherBuilder.pattern("/favicon.ico")).permitAll()

                // All other endpoints require authentication
                .anyRequest().authenticated()
            );

        logger.info("Spring Security configured with proper authentication requirements and security headers");
        return http.build();
    }

    private void rejectUnauthenticated(HttpServletRequest request, HttpServletResponse response,
                                       AuthenticationException authException) throws IOException {
        Object tokenError = request.getAttribute(JwtFilter.TOKEN_ERROR_ATTRIBUTE);
        String code;
        String message;
        if ("expired".equals(tokenError)) {
            code = "TOKEN_EXPIRED";
            message = "Your session has expired. Please log in again.";
        } else if (tokenError != null) {
            code = "TOKEN_INVALID";
            message = "Your session is no longer valid. Please log in again.";
        } else {
            code = "AUTHENTICATION_REQUIRED";
            message = "Please log in to continue.";
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\",\"code\":\"" + code + "\"}");
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        
        logger.info("CORS configuration set up with allowed origins: {}, methods: {}, headers: {}", 
                   configuration.getAllowedOriginPatterns(), 
                   configuration.getAllowedMethods(), 
                   configuration.getAllowedHeaders());
        
        return source;
    }
}