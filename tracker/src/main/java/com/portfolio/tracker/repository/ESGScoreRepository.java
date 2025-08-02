package com.portfolio.tracker.repository;

import com.portfolio.tracker.model.ESGScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ESGScoreRepository extends JpaRepository<ESGScore, String> {
    List<ESGScore> findByTotalScoreGreaterThanEqual(double minScore);
}
