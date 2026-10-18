package com.portfolio.tracker.service;

import com.portfolio.tracker.model.User;
import com.portfolio.tracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenService refreshTokenService;

    /**
     * Outcome of a signup: either a new account, or an unverified account the same person
     * already started (same username, email and password) whose code is still valid.
     */
    public record Registration(User user, boolean existingPendingSignup) {
    }

    /**
     * Register a new user, or resume their pending signup.
     *
     * Unverified accounts whose code has expired no longer reserve their username or email: they
     * are deleted and the signup proceeds. An unverified account whose code is still valid is only
     * resumed when the username, email and password all match, so nobody can attach themselves to
     * someone else's pending signup.
     *
     * @throws IllegalArgumentException if the input is invalid
     * @throws IllegalStateException if the username or email belongs to another account
     */
    @Transactional
    public Registration registerUser(String username, String email, String rawPassword) {
        // Validate input parameters
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalArgumentException("Username cannot be null or empty");
        }
        if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("Password cannot be null or empty");
        }
        if (rawPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long");
        }
        String normalizedEmail = email.trim();
        LocalDateTime now = LocalDateTime.now();

        Optional<User> byUsername = userRepository.findByUsername(username);
        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(normalizedEmail);

        // Same person signing up again before their code expired: resume instead of failing
        if (byUsername.isPresent() && byEmail.isPresent()
                && byUsername.get().getId().equals(byEmail.get().getId())) {
            User pending = byUsername.get();
            if (!pending.isEnabled() && !isExpired(pending, now)
                    && passwordEncoder.matches(rawPassword, pending.getPassword())) {
                return new Registration(pending, true);
            }
        }

        releaseIfExpiredOrFail(byUsername, now,
                "Username '" + username + "' is already taken",
                "Username '" + username + "' is reserved by a signup waiting for email verification");
        releaseIfExpiredOrFail(byEmail, now,
                "An account with this email already exists",
                "This email has a signup waiting for verification");
        userRepository.flush(); // make deletions visible to the unique constraints before inserting
        
        // Encode the password for security
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // Create the account disabled; it is enabled once the emailed code is verified
        User user = new User(username, encodedPassword, "USER");
        user.setEmail(normalizedEmail);
        user.setEnabled(false);
        return new Registration(userRepository.save(user), false);
    }

    /**
     * Delete every unverified account whose verification code has expired.
     * @return the number of accounts removed
     */
    @Transactional
    public int deleteExpiredUnverifiedAccounts() {
        List<User> expired = userRepository.findByEnabledFalseAndCodeExpirationBefore(LocalDateTime.now());
        userRepository.deleteAll(expired);
        return expired.size();
    }

    private void releaseIfExpiredOrFail(Optional<User> existing, LocalDateTime now,
                                        String takenMessage, String pendingMessage) {
        if (existing.isEmpty()) return;
        User user = existing.get();
        if (user.isEnabled()) {
            throw new IllegalStateException(takenMessage);
        }
        if (isExpired(user, now)) {
            // An abandoned signup: free the username/email for the new registration
            userRepository.delete(user);
            return;
        }
        long minutes = Math.max(1, Duration.between(now, user.getCodeExpiration()).toMinutes() + 1);
        throw new IllegalStateException(pendingMessage + ". If this is your signup, use the same username, email and password to get back to "
                + "verification, or try again in " + minutes + (minutes == 1 ? " minute." : " minutes."));
    }

    private static boolean isExpired(User user, LocalDateTime now) {
        return user.getCodeExpiration() == null || now.isAfter(user.getCodeExpiration());
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
        User saved = userRepository.save(user);
        // Log out every device that signed in with the old password
        refreshTokenService.revokeAll(saved.getId());
        return saved;
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