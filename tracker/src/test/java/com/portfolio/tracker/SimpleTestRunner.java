package com.portfolio.tracker;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple test runner to verify our testing infrastructure works
 * This demonstrates that our testing setup is properly configured
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Simple Test Runner")
public class SimpleTestRunner {

    @Test
    @DisplayName("Should demonstrate basic testing infrastructure")
    void shouldDemonstrateBasicTestingInfrastructure() {
        // Given
        String expected = "Hello Testing World!";
        
        // When
        String actual = "Hello Testing World!";
        
        // Then
        assertEquals(expected, actual, "Basic assertion should work");
        assertTrue(actual.contains("Testing"), "String should contain 'Testing'");
        assertNotNull(actual, "String should not be null");
    }

    @Test
    @DisplayName("Should demonstrate mathematical operations")
    void shouldDemonstrateMathematicalOperations() {
        // Given
        double a = 10.0;
        double b = 5.0;
        
        // When
        double sum = a + b;
        double product = a * b;
        double quotient = a / b;
        
        // Then
        assertEquals(15.0, sum, 0.01, "Addition should work correctly");
        assertEquals(50.0, product, 0.01, "Multiplication should work correctly");
        assertEquals(2.0, quotient, 0.01, "Division should work correctly");
    }

    @Test
    @DisplayName("Should demonstrate collection operations")
    void shouldDemonstrateCollectionOperations() {
        // Given
        java.util.List<String> fruits = java.util.Arrays.asList("Apple", "Banana", "Cherry");
        
        // When
        int size = fruits.size();
        boolean containsApple = fruits.contains("Apple");
        boolean containsOrange = fruits.contains("Orange");
        
        // Then
        assertEquals(3, size, "List should have 3 elements");
        assertTrue(containsApple, "List should contain Apple");
        assertFalse(containsOrange, "List should not contain Orange");
    }

    @Test
    @DisplayName("Should demonstrate exception handling")
    void shouldDemonstrateExceptionHandling() {
        // Given
        String nullString = null;
        
        // When & Then
        assertThrows(NullPointerException.class, () -> {
            nullString.length();
        }, "Accessing length of null string should throw NullPointerException");
    }

    @Test
    @DisplayName("Should demonstrate timeout handling")
    void shouldDemonstrateTimeoutHandling() {
        // Given
        long startTime = System.currentTimeMillis();
        
        // When
        // Simulate some work
        try {
            Thread.sleep(10); // 10ms delay
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        // Then
        assertTrue(executionTime < 100, "Execution should complete in under 100ms");
    }
}
