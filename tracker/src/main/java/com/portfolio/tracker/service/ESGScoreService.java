package com.portfolio.tracker.service;

import com.portfolio.tracker.model.ESGScore;
import com.portfolio.tracker.repository.ESGScoreRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class ESGScoreService {

    private final ESGScoreRepository repository;
    private final RestTemplate restTemplate;

    public ESGScoreService(ESGScoreRepository repository) {
        this.repository = repository;
        this.restTemplate = new RestTemplate(); // You can also inject this as a bean
    }

    // ✅ Get all companies with good ESG
    public List<ESGScore> getCompaniesWithHighESG(double minScore) {
        return repository.findByTotalScoreGreaterThanEqual(minScore);
    }

    // ✅ Get ESG score from database, or fetch from API if not found
    public ESGScore getESGScore(String ticker) {
        return repository.findById(ticker.toUpperCase())
                .orElseGet(() -> fetchAndSaveESGFromAPI(ticker));
    }

    // ✅ Save or update manually (optional admin feature)
    public ESGScore saveOrUpdateESGScore(ESGScore esgScore) {
        return repository.save(esgScore);
    }

    // ✅ Delete (optional)
    public void deleteESGScore(String ticker) {
        repository.deleteById(ticker);
    }

    // ✅ Fetch from external ESG API and save to DB
    private ESGScore fetchAndSaveESGFromAPI(String ticker) {
        String url = "https://api.example.com/esg/" + ticker.toUpperCase();  // Replace with real API

        try {
            // Make the request and parse response as a Map
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);

            if (response == null || !response.containsKey("environmental")) {
                throw new RuntimeException("Invalid ESG data from API for: " + ticker);
            }

            double environmental = ((Number) response.get("environmental")).doubleValue();
            double social = ((Number) response.get("social")).doubleValue();
            double governance = ((Number) response.get("governance")).doubleValue();

            double total = (environmental + social + governance) / 3;

            ESGScore score = new ESGScore();
            score.setTicker(ticker.toUpperCase());
            score.setEnvironmentalScore(environmental);
            score.setSocialScore(social);
            score.setGovernanceScore(governance);
            score.setTotalScore(total);

            return repository.save(score);

        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch ESG data from external API for: " + ticker);
        }
    }
}


