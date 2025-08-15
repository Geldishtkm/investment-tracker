package com.portfolio.tracker.service;

import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Register a new user with the given username and password
     * @param username The username for the new user
     * @param rawPassword The plain text password to be encoded
     * @return The newly created user
     * @throws RuntimeException if username already exists or validation fails
     */
    public User registerUser(String username, String rawPassword) {
        // Validate input parameters
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        
        // Check if username already exists
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("User with username '" + username + "' already exists");
        }
        
        // Encode the password for security
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Create and save the new user
        User user = new User(username, encodedPassword, "USER");
        return userRepository.save(user);
    }

    /**
     * Find a user by their username
     * @param username The username to search for
     * @return The user if found
     * @throws RuntimeException if user is not found
     */
    public User findByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
    }

    /**
     * Check if a user exists by username
     * @param username The username to check
     * @return true if user exists, false otherwise
     */
    public boolean existsByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        return userRepository.findByUsername(username).isPresent();
    }

    /**
     * Find a user by their ID
     * @param id The user ID to search for
     * @return The user if found
     * @throws RuntimeException if user is not found
     */
    public User findById(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("User ID cannot be null");
        }
        
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    /**
     * Update a user's password
     * @param username The username of the user
     * @param newPassword The new password to set
     * @return The updated user
     * @throws RuntimeException if user is not found or validation fails
     */
    public User updatePassword(String username, String newPassword) {
        // Validate new password
        if (newPassword == null || newPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("New password cannot be null or empty");
        }
        if (newPassword.length() < 6) {
            throw new IllegalArgumentException("New password must be at least 6 characters long");
        }
        
        User user = findByUsername(username);
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    /**
     * Delete a user by username
     * @param username The username of the user to delete
     * @throws RuntimeException if user is not found
     */
    public void deleteByUsername(String username) {
        User user = findByUsername(username);
        userRepository.delete(user);
    }

    /**
     * Get all users (admin only functionality)
     * @return List of all users
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * Check if a user has admin privileges
     * @param username The username to check
     * @return true if user is admin, false otherwise
     */
    public boolean isAdmin(String username) {
        try {
            User user = findByUsername(username);
            return user.isAdmin();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get user count
     * @return Total number of users in the system
     */
    public long getUserCount() {
        return userRepository.count();
    }

    /**
     * Authenticate a user with username and password
     * @param username The username to authenticate
     * @param password The plain text password to verify
     * @return The authenticated user if credentials are valid
     * @throws RuntimeException if authentication fails
     */
    public User authenticateUser(String username, String password) {
        User user = findByUsername(username);
        if (passwordEncoder.matches(password, user.getPassword())) {
            return user;
        }
        throw new RuntimeException("Invalid credentials for user: " + username);
    }

    /**
     * Update a user entity
     * @param user The user to update
     * @return The updated user
     */
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    /**
     * Get a user by username (alias for findByUsername)
     * @param username The username to search for
     * @return The user if found
     * @throws RuntimeException if user is not found
     */
    public User getUserByUsername(String username) {
        return findByUsername(username);
    }
}