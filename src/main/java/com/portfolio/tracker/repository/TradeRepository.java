package com.portfolio.tracker.repository;

import com.portfolio.tracker.model.Trade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TradeRepository extends JpaRepository<Trade, Long> {

    List<Trade> findByUserIdOrderByTradeDateAscIdAsc(Long userId);

    List<Trade> findByAssetId(Long assetId);

    @Modifying
    @Query("delete from Trade t where t.assetId = :assetId")
    int deleteByAssetId(@Param("assetId") Long assetId);
}
