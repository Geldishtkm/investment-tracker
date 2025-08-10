package com.portfolio.tracker.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Key;
import java.util.Date;
import java.util.function.Function;

/**
 * Utility class for JWT token operations including generation, validation, and extraction
 * of claims from JWT tokens
 */
@Component
public class JwtUtil {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtil.class);
    
    // JWT configuration from application.properties
    @Value("${jwt.secret}")
    private String secretKey;
    
    @Value("${jwt.expiration}")
    private long expirationTime;
    
    private static final SignatureAlgorithm ALGORITHM = SignatureAlgorithm.HS256;

    private Key signingKey;

    public JwtUtil() {
        // Initialize signing key after properties are injected
    }

    /**
     * Initialize the signing key after properties are injected
     */
    private void initializeSigningKey() {
        if (signingKey == null && secretKey != null) {
            try {
                byte[] keyBytes = secretKey.getBytes();
                this.signingKey = Keys.hmacShaKeyFor(keyBytes);
                logger.info("JWT signing key initialized with secret length: {} and expiration: {} ms", 
                           secretKey.length(), expirationTime);
            } catch (Exception e) {
                logger.error("Failed to create signing key: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to initialize JWT utility", e);
            }
        } else if (secretKey == null) {
            logger.error("JWT secret key is null - check application.properties configuration");
        }
    }

    /**
     * Get the signing key, initializing if necessary
     */
    private Key getSigningKey() {
        if (signingKey == null) {
            initializeSigningKey();
        }
        return signingKey;
    }

    /**
     * Extract username from JWT token
     * @param token JWT token string
     * @return username from token subject
     */
    public String extractUsername(String token) {
        try {
            String username = extractClaim(token, Claims::getSubject);
            logger.debug("Extracted username '{}' from JWT token", username);
            return username;
        } catch (Exception e) {
            logger.warn("Failed to extract username from JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract expiration date from JWT token
     * @param token JWT token string
     * @return expiration date
     */
    public Date extractExpiration(String token) {
        try {
            Date expiration = extractClaim(token, Claims::getExpiration);
            logger.debug("Extracted expiration date '{}' from JWT token", expiration);
            return expiration;
        } catch (Exception e) {
            logger.warn("Failed to extract expiration from JWT token: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract a specific claim from JWT token
     * @param token JWT token string
     * @param claimsResolver function to resolve the claim
     * @return the resolved claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        try {
            final Claims claims = extractAllClaims(token);
            T result = claimsResolver.apply(claims);
            logger.debug("Successfully extracted claim from JWT token");
            return result;
        } catch (Exception e) {
            logger.warn("Failed to extract claim from token: {}", e.getMessage());
            throw new RuntimeException("Token claim extraction failed", e);
        }
    }

    /**
     * Extract all claims from JWT token
     * @param token JWT token string
     * @return all claims from the token
     */
    private Claims extractAllClaims(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                       .setSigningKey(getSigningKey())
                       .build()
                       .parseClaimsJws(token)
                       .getBody();
            logger.debug("Successfully parsed JWT token and extracted claims");
            return claims;
        } catch (Exception e) {
            logger.error("Failed to parse JWT token: {}", e.getMessage());
            throw new RuntimeException("Invalid JWT token", e);
        }
    }

    /**
     * Check if the JWT token has expired
     * @param token JWT token string
     * @return true if token is expired, false otherwise
     */
    private boolean isTokenExpired(String token) {
        try {
            Date expiration = extractExpiration(token);
            boolean expired = expiration.before(new Date());
            if (expired) {
                logger.warn("JWT token has expired. Expiration: {}, Current: {}", expiration, new Date());
            } else {
                logger.debug("JWT token is valid. Expiration: {}, Current: {}", expiration, new Date());
            }
            return expired;
        } catch (Exception e) {
            logger.warn("Failed to check token expiration: {}", e.getMessage());
            return true; // Consider expired if we can't determine
        }
    }

    /**
     * Generate JWT token for a user
     * @param userDetails user details for token generation
     * @return JWT token string
     */
    public String generateToken(UserDetails userDetails) {
        try {
            String token = createToken(userDetails.getUsername());
            logger.info("Generated JWT token for user: {}", userDetails.getUsername());
            return token;
        } catch (Exception e) {
            logger.error("Failed to generate token for user {}: {}", 
                        userDetails.getUsername(), e.getMessage());
            throw new RuntimeException("Token generation failed", e);
        }
    }

    /**
     * Create JWT token with the given subject
     * @param subject subject (usually username) for the token
     * @return JWT token string
     */
    private String createToken(String subject) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationTime);

        String token = Jwts.builder()
                   .setSubject(subject)
                   .setIssuedAt(now)
                   .setExpiration(expiryDate)
                   .signWith(getSigningKey(), ALGORITHM)
                   .compact();
        
        logger.debug("Created JWT token for subject '{}' with expiration '{}'", subject, expiryDate);
        return token;
    }

    /**
     * Validate JWT token against user details
     * @param token JWT token string
     * @param userDetails user details to validate against
     * @return true if token is valid, false otherwise
     */
    public boolean validateToken(String token, UserDetails userDetails) {
        try {
            final String username = extractUsername(token);
            boolean isValid = username.equals(userDetails.getUsername()) && !isTokenExpired(token);
            
            if (isValid) {
                logger.debug("JWT token validation successful for user: {}", username);
            } else {
                logger.warn("JWT token validation failed for user: {}. Username match: {}, Expired: {}", 
                           username, username.equals(userDetails.getUsername()), isTokenExpired(token));
            }
            
            return isValid;
        } catch (Exception e) {
            logger.warn("Token validation failed: {}", e.getMessage());
            return false;
        }
    }
}