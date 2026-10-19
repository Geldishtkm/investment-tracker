package com.portfolio.tracker.repository;

import com.portfolio.tracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
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

    Optional<User> findByEmailIgnoreCase(String email);

    /** Unverified accounts whose verification code expired before the given time. */
    List<User> findByEnabledFalseAndCodeExpirationBefore(LocalDateTime cutoff);

    /** Locks the user's row until the transaction ends, so concurrent buys can't spend the same cash twice. */
    @Query(value = "select id from users where id = :id for update", nativeQuery = true)
    Long lockById(@Param("id") Long id);

    @Modifying
    @Query("delete from User u where u.id = :id")
    int deleteUserById(@Param("id") Long id);
}
