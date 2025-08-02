package com.portfolio.tracker.service;

import com.portfolio.tracker.model.ESGScore;
import com.portfolio.tracker.repository.ESGScoreRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ESGScoreService {

    private final ESGScoreRepository repository;

    // Constructor-based injection
    public ESGScoreService(ESGScoreRepository repository) {
        this.repository = repository;
    }

    // ✅ Get a list of companies with total ESG score above a minimum value
    public List<ESGScore> getCompaniesWithHighESG(double minScore) {
        return repository.findByTotalScoreGreaterThanEqual(minScore);
    }

    // ✅ Get ESG score for a specific company (by ticker symbol)
    public ESGScore getESGScore(String ticker) {
        return repository.findById(ticker)
                .orElseThrow(() -> new RuntimeException("ESG data not found for ticker: " + ticker));
    }

    // ✅ Optional: Save or update an ESG score (if you want to support admin adding data)
    public ESGScore saveOrUpdateESGScore(ESGScore esgScore) {
        return repository.save(esgScore);
    }

    // ✅ Optional: Delete ESG score for a company
    public void deleteESGScore(String ticker) {
        repository.deleteById(ticker);
    }
}

