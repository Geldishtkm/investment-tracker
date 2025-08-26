package com.portfolio.tracker.security;

import com.portfolio.tracker.service.UserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * JWT Authentication Filter that processes JWT tokens from incoming requests
 * and sets up Spring Security context for authenticated users
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtFilter.class);
    private static final String AUTH_HEADER_PREFIX = "Bearer ";
    private static final String AUTH_ENDPOINT_PREFIX = "/auth/";

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();
        String method = request.getMethod();
        
        logger.debug("Processing request: {} {}", method, requestUri);

        // Log all headers for debugging
        if (logger.isDebugEnabled()) {
            java.util.Enumeration<String> headerNames = request.getHeaderNames();
            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                logger.debug("Header: {} = {}", headerName, headerValue);
            }
        }

        // Skip JWT processing for authentication endpoints
        if (isAuthEndpoint(requestUri)) {
            logger.debug("Skipping JWT processing for auth endpoint: {}", requestUri);
            logger.info("ALLOWING auth request: {} {} (no JWT processing)", method, requestUri);
            filterChain.doFilter(request, response);
            return;
        }

        try {
            processJwtToken(request);
        } catch (Exception e) {
            logger.error("Unexpected error in JWT filter: {}", e.getMessage(), e);
            // Continue with the filter chain even if JWT processing fails
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Check if the request URI is for authentication endpoints
     */
    private boolean isAuthEndpoint(String requestUri) {
        return requestUri != null && requestUri.startsWith(AUTH_ENDPOINT_PREFIX);
    }

    /**
     * Process JWT token from the request and set up authentication if valid
     */
    private void processJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null) {
            logger.debug("No Authorization header found for request: {}", request.getRequestURI());
            return; // No authorization header
        }
        
        if (!authHeader.startsWith(AUTH_HEADER_PREFIX)) {
            logger.debug("Invalid Authorization header format: {}", authHeader);
            return; // Invalid authorization header format
        }

        String token = extractToken(authHeader);
        if (token == null) {
            logger.debug("Could not extract token from Authorization header");
            return; // Invalid token format
        }

        logger.debug("Processing JWT token for request: {}", request.getRequestURI());

        String username = extractUsernameFromToken(token);
        if (username == null) {
            logger.debug("Could not extract username from JWT token");
            return; // Could not extract username
        }

        // Set up authentication if not already set
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            logger.debug("Setting up authentication for user: {}", username);
            setupAuthentication(token, username, request);
        } else {
            logger.debug("Authentication already set for request: {}", request.getRequestURI());
        }
    }

    /**
     * Extract the JWT token from the Authorization header
     */
    private String extractToken(String authHeader) {
        try {
            return authHeader.substring(AUTH_HEADER_PREFIX.length());
        } catch (StringIndexOutOfBoundsException e) {
            logger.warn("Invalid Authorization header format: {}", authHeader);
            return null;
        }
    }

    /**
     * Extract username from JWT token
     */
    private String extractUsernameFromToken(String token) {
        try {
            return jwtUtil.extractUsername(token);
        } catch (Exception e) {
            logger.warn("JWT token extraction failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Set up Spring Security authentication context
     */
    private void setupAuthentication(String token, String username, HttpServletRequest request) {
        try {
            // Check if we already have authentication for this user in this request
            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                String currentUser = SecurityContextHolder.getContext().getAuthentication().getName();
                if (currentUser.equals(username)) {
                    logger.debug("Authentication already set for user: {} on request: {}", 
                               username, request.getRequestURI());
                    return;
                }
            }
            
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtUtil.validateToken(token, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
                logger.info("Authentication successfully set for user: {} on request: {}", 
                           username, request.getRequestURI());
            } else {
                logger.warn("Invalid JWT token for user: {} on request: {}", 
                           username, request.getRequestURI());
            }
        } catch (Exception e) {
            logger.error("Error setting up authentication for user {} on request {}: {}", 
                        username, request.getRequestURI(), e.getMessage());
        }
    }
}