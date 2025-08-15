package com.portfolio.tracker.repository;

import com.portfolio.tracker.model.Asset;
import com.portfolio.tracker.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Asset entity operations
 * Provides data access methods for portfolio assets
 */
@Repository
public interface AssetRepository extends JpaRepository<Asset, Long> {
    
    /**
     * Find all assets belonging to a specific user
     * @param user the user whose assets to retrieve
     * @return list of assets owned by the user
     */
    List<Asset> findByUser(User user);
    
    /**
     * Find a specific asset by ID that belongs to a specific user
     * This ensures users can only access their own assets
     * @param id the asset ID
     * @param user the user who should own the asset
     * @return optional containing the asset if found and owned by the user
     */
    Optional<Asset> findByIdAndUser(Long id, User user);
}