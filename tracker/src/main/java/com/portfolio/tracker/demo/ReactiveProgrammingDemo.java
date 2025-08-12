package com.portfolio.tracker.demo;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reactive Programming Demo
 * 
 * This class demonstrates the difference between:
 * - Traditional (blocking) programming
 * - Reactive (non-blocking) programming
 * 
 * Perfect for showing BlackRock interviewers how you understand
 * high-performance, scalable systems!
 */
@Component
public class ReactiveProgrammingDemo {

    private final ExecutorService executorService = Executors.newFixedThreadPool(5);

    /**
     * TRADITIONAL APPROACH (Blocking)
     * 
     * Problem: Each calculation blocks the thread until complete
     * Result: Slow performance, poor resource utilization
     */
    public List<String> traditionalApproach(List<Integer> numbers) {
        return numbers.stream()
                .map(this::slowCalculation)  // This blocks each time!
                .toList();
    }

    /**
     * REACTIVE APPROACH (Non-blocking)
     * 
     * Solution: Process all calculations concurrently
     * Result: Much faster, better resource utilization
     */
    public Flux<String> reactiveApproach(List<Integer> numbers) {
        return Flux.fromIterable(numbers)
                .flatMap(number -> Mono.fromCallable(() -> slowCalculation(number))
                        .subscribeOn(Schedulers.boundedElastic()))
                .doOnNext(result -> System.out.println("Processed: " + result));
    }

    /**
     * Simulates a slow operation (like API call or database query)
     */
    private String slowCalculation(Integer number) {
        try {
            Thread.sleep(100); // Simulate 100ms of work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "Result for " + number + " (processed in " + System.currentTimeMillis() + ")";
    }

    /**
     * Shows real-time data streaming (like BlackRock's market data)
     */
    public Flux<String> realTimeDataStream() {
        return Flux.interval(Duration.ofSeconds(1))
                .map(tick -> "Market Update " + tick + " at " + System.currentTimeMillis())
                .take(5) // Show 5 updates
                .doOnNext(update -> System.out.println("📊 " + update));
    }

    /**
     * Demonstrates concurrent processing of multiple tasks
     */
    public Mono<String> concurrentProcessing() {
        Mono<String> task1 = Mono.fromCallable(() -> "Task 1 completed")
                .subscribeOn(Schedulers.boundedElastic());
        
        Mono<String> task2 = Mono.fromCallable(() -> "Task 2 completed")
                .subscribeOn(Schedulers.boundedElastic());
        
        Mono<String> task3 = Mono.fromCallable(() -> "Task 3 completed")
                .subscribeOn(Schedulers.boundedElastic());

        // All tasks run concurrently and complete when all are done
        return Mono.zip(task1, task2, task3)
                .map(results -> String.join(" | ", results.getT1(), results.getT2(), results.getT3()));
    }

    /**
     * Shows error handling in reactive streams
     */
    public Flux<String> errorHandlingDemo() {
        return Flux.just("Success 1", "Success 2")
                .concatWith(Mono.error(new RuntimeException("Simulated error")))
                .concatWith(Flux.just("Success 3", "Success 4"))
                .onErrorResume(error -> {
                    System.out.println("🔄 Recovered from error: " + error.getMessage());
                    return Flux.just("Recovery 1", "Recovery 2");
                })
                .doOnNext(item -> System.out.println("✅ " + item));
    }

    /**
     * Performance comparison between traditional and reactive approaches
     */
    public void performanceComparison() {
        List<Integer> numbers = List.of(1, 2, 3, 4, 5);
        
        System.out.println("🚀 PERFORMANCE COMPARISON 🚀");
        System.out.println("Numbers to process: " + numbers.size());
        
        // Traditional approach timing
        long startTime = System.currentTimeMillis();
        List<String> traditionalResults = traditionalApproach(numbers);
        long traditionalTime = System.currentTimeMillis() - startTime;
        
        System.out.println("⏱️  Traditional approach: " + traditionalTime + "ms");
        System.out.println("📊 Traditional results: " + traditionalResults.size());
        
        // Reactive approach timing
        final long reactiveStartTime = System.currentTimeMillis();
        reactiveApproach(numbers)
                .collectList()
                .subscribe(results -> {
                    long reactiveTime = System.currentTimeMillis() - reactiveStartTime;
                    System.out.println("⚡ Reactive approach: " + reactiveTime + "ms");
                    System.out.println("📊 Reactive results: " + results.size());
                    
                    // Show performance improvement
                    double improvement = ((double) traditionalTime / reactiveTime - 1) * 100;
                    System.out.println("🎯 Performance improvement: " + String.format("%.1f", improvement) + "%");
                });
    }

    /**
     * Clean up resources
     */
    public void cleanup() {
        executorService.shutdown();
    }
}
