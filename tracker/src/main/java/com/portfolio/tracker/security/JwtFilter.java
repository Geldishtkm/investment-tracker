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

import java.io.IOException;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        try {
            String authHeader = request.getHeader("Authorization");
            logger.info("Processing request: " + request.getRequestURI() + " with auth header: " + (authHeader != null ? "present" : "null"));

            String token = null;
            String username = null;

            // Check if the Authorization header contains a Bearer token
            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                token = authHeader.substring(7); // Extract token after "Bearer "
                try {
                    username = jwtUtil.extractUsername(token);
                    logger.info("Extracted username: " + username);
                } catch (Exception e) {
                    logger.error("JWT token extraction failed: " + e.getMessage(), e);
                }
            }

            // If username extracted and authentication not already set
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    logger.info("Loaded user details for: " + username);

                    // Validate token against userDetails
                    if (jwtUtil.validateToken(token, userDetails)) {
                        UsernamePasswordAuthenticationToken authToken =
                                new UsernamePasswordAuthenticationToken(
                                        userDetails, null, userDetails.getAuthorities());

                        authToken.setDetails(
                                new WebAuthenticationDetailsSource().buildDetails(request));

                        // Set the authentication in the security context
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                        logger.info("Authentication set for user: " + username);
                    } else {
                        logger.warn("Token validation failed for user: " + username);
                    }
                } catch (Exception e) {
                    logger.error("Error loading user details for: " + username + " - " + e.getMessage(), e);
                }
            }

            // Proceed with the next filter in the chain
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            logger.error("Unexpected error in JWT filter: " + e.getMessage(), e);
            filterChain.doFilter(request, response);
        }
    }
}