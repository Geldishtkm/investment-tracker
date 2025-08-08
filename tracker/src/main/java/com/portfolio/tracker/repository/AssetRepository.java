package com.portfolio.tracker.repository;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    
    // Find all assets for a specific user
    List<Asset> findByUser(User user);
    
    // Find asset by ID and user (for security)
    Optional<Asset> findByIdAndUser(Long id, User user);
}