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
}
