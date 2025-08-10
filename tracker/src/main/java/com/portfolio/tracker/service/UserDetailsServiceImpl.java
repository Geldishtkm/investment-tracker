package com.portfolio.tracker.service;

import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;

/**
 * Implementation of Spring Security's UserDetailsService
 * Loads user details for authentication and authorization
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger logger = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    @Autowired
    private UserRepository userRepository;

    /**
     * Load user details by username for Spring Security authentication
     * @param username the username to search for
     * @return UserDetails object containing user information
     * @throws UsernameNotFoundException if user is not found
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        if (username == null || username.trim().isEmpty()) {
            logger.warn("Attempted to load user with null or empty username");
            throw new UsernameNotFoundException("Username cannot be null or empty");
        }

        try {
            logger.debug("Loading user details for username: {}", username);
            
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> {
                        logger.warn("User not found: {}", username);
                        return new UsernameNotFoundException("User not found: " + username);
                    });

            // Create Spring Security UserDetails object
            org.springframework.security.core.userdetails.User userDetails = 
                new org.springframework.security.core.userdetails.User(
                    user.getUsername(),
                    user.getPassword(),
                    Collections.emptyList() // No authorities for now - can be extended later
                );

            logger.debug("Successfully loaded user details for: {}", username);
            return userDetails;
            
        } catch (UsernameNotFoundException e) {
            // Re-throw UsernameNotFoundException as-is
            throw e;
        } catch (Exception e) {
            logger.error("Error loading user details for username {}: {}", username, e.getMessage(), e);
            throw new UsernameNotFoundException("Error loading user: " + username, e);
        }
    }
}
