package com.portfolio.tracker.service;

import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /**
     * Registers a new user with encoded password.
     *
     * @param username     The username (unique)
     * @param rawPassword  The plain text password
     * @return saved User entity
     */
    public User registerUser(String username, String rawPassword) {
        // Check if user already exists
        if (userRepository.findByUsername(username).isPresent()) {
            throw new RuntimeException("User with username '" + username + "' already exists");
        }
        
        // Encode the raw password
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Create user object
        User user = User.builder()
                .username(username)
                .password(encodedPassword)
                .role("USER")  // default role
                .build();

        // Save user to database
        return userRepository.save(user);
    }

    /**
     * Finds a user by username.
     *
     * @param username The username to search for
     * @return User entity if found
     * @throws RuntimeException if user not found
     */
    public User findByUsername(String username) {
        try {
            System.out.println("Looking for user: " + username);
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found: " + username));
            System.out.println("Found user: " + user.getUsername() + " with ID: " + user.getId());
            return user;
        } catch (Exception e) {
            System.err.println("Error finding user: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Checks if a user exists by username.
     *
     * @param username The username to check
     * @return true if user exists, false otherwise
     */
    public boolean existsByUsername(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    /**
     * Gets a user by ID.
     *
     * @param id The user ID
     * @return User entity if found
     * @throws RuntimeException if user not found
     */
    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + id));
    }

    /**
     * Updates a user's password.
     *
     * @param username    The username
     * @param newPassword The new plain text password
     * @return updated User entity
     */
    public User updatePassword(String username, String newPassword) {
        User user = findByUsername(username);
        user.setPassword(passwordEncoder.encode(newPassword));
        return userRepository.save(user);
    }

    /**
     * Deletes a user by username.
     *
     * @param username The username to delete
     */
    public void deleteByUsername(String username) {
        User user = findByUsername(username);
        userRepository.delete(user);
    }
}