package com.portfolio.tracker.repository;

import com.portfolio.tracker.model.PriceHistoryCacheEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PriceHistoryCacheRepository
        extends JpaRepository<PriceHistoryCacheEntry, PriceHistoryCacheEntry.Key> {
}
