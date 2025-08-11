package com.portfolio.tracker;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Comprehensive Test Suite Runner for Portfolio Tracker
 * 
 * This test suite organizes and executes all tests in a structured manner:
 * - Unit Tests: Fast, isolated tests for individual components
 * - Integration Tests: Tests that verify component interactions
 * - Performance Tests: Tests that validate system performance
 * - End-to-End Tests: Tests that verify complete workflows
 * 
 * Benefits:
 * - Organized test execution
 * - Parallel test execution support
 * - Test categorization for CI/CD pipelines
 * - Comprehensive coverage reporting
 */
@Suite
@SuiteDisplayName("Portfolio Tracker Complete Test Suite")
@SelectPackages({
    "com.portfolio.tracker.service",      // Service layer unit tests
    "com.portfolio.tracker.controller",   // Controller layer unit tests
    "com.portfolio.tracker.integration", // Integration tests
    "com.portfolio.tracker.performance"  // Performance tests
})
public class TestSuiteRunner {
    
    /**
     * Test Suite Configuration:
     * 
     * 1. Service Layer Tests:
     *    - AssetServiceTest: Tests business logic and calculations
     *    - UserServiceTest: Tests user management operations
     *    - PortfolioRebalancingServiceTest: Tests portfolio logic
     *    - VaRCalculationServiceTest: Tests risk calculations
     * 
     * 2. Controller Layer Tests:
     *    - AssetControllerTest: Tests HTTP endpoints and responses
     *    - AuthControllerTest: Tests authentication flows
     *    - PortfolioControllerTest: Tests portfolio operations
     * 
     * 3. Integration Tests:
     *    - Database integration with H2 in-memory database
     *    - Service layer interactions
     *    - End-to-end workflows
     * 
     * 4. Performance Tests:
     *    - Load testing with concurrent requests
     *    - Memory usage monitoring
     *    - Response time validation
     *    - Scalability testing
     * 
     * Test Execution Strategy:
     * - Unit tests run first (fastest)
     * - Integration tests run second (medium speed)
     * - Performance tests run last (slowest)
     * - All tests can run in parallel for faster execution
     * 
     * CI/CD Integration:
     * - Unit tests: Run on every commit
     * - Integration tests: Run on pull requests
     * - Performance tests: Run nightly or on releases
     * - Test results are reported to CI/CD pipeline
     * 
     * Test Categories:
     * - FAST: Unit tests (< 100ms each)
     * - MEDIUM: Integration tests (< 1s each)
     * - SLOW: Performance tests (< 30s each)
     * 
     * Coverage Goals:
     * - Line coverage: > 90%
     * - Branch coverage: > 85%
     * - Method coverage: > 95%
     * - Class coverage: > 90%
     */
}
