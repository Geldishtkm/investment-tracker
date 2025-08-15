package com.portfolio.tracker.repository;

import com.portfolio.tracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

/**
 * Repository interface for User entity operations
 * Provides data access methods for user management
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find a user by their username
     * @param username the username to search for
     * @return optional containing the user if found
     */
    Optional<User> findByUsername(String username);
}