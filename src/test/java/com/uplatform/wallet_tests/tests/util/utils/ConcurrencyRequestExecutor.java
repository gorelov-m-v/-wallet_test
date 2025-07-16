package com.uplatform.wallet_tests.tests.util.utils;

import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import feign.FeignException;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ConcurrencyRequestExecutor {

    private final long requestTimeoutMs;
    private final int numberOfConcurrentRequests;

    @Autowired
    public ConcurrencyRequestExecutor(EnvironmentConfigurationProvider configProvider) {
        this.requestTimeoutMs = configProvider.getEnvironmentConfig().getApi().getConcurrency().getRequestTimeoutMs();
        this.numberOfConcurrentRequests = configProvider.getEnvironmentConfig().getApi().getConcurrency().getDefaultRequestCount();
    }

    @Getter
    @Builder
    public static class ExecutionResult<S> {
        private final ResponseEntity<S> successfulResponse;
        private final FeignException feignException;
        private final List<Object> allRawResults;
        private final boolean expectedPatternMatched;
        private final boolean allTasksCompletedInTime;
    }

    public <S> ExecutionResult<S> executeIdenticalRequests(
            Callable<ResponseEntity<S>> requestCallable
    ) {
        if (this.numberOfConcurrentRequests < 1) {
            throw new IllegalStateException("Number of concurrent requests configured is invalid: " + this.numberOfConcurrentRequests);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(this.numberOfConcurrentRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completeLatch = new CountDownLatch(this.numberOfConcurrentRequests);

        List<CompletableFuture<Object>> futures = new ArrayList<>(this.numberOfConcurrentRequests);

        for (int i = 0; i < this.numberOfConcurrentRequests; i++) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    if (!startLatch.await(this.requestTimeoutMs, TimeUnit.MILLISECONDS)) {
                        log.warn("Task did not start due to startLatch timeout ({} ms).", this.requestTimeoutMs);
                        return new TimeoutException("Start latch timed out for task.");
                    }
                    return requestCallable.call();
                } catch (FeignException fe) {
                    return fe;
                } catch (Exception e) {
                    log.error("Unexpected exception during concurrent request execution for task", e);
                    return e;
                } finally {
                    completeLatch.countDown();
                }
            }, executorService));
        }

        startLatch.countDown();

        boolean allTasksCompletedInTime = false;
        try {
            allTasksCompletedInTime = completeLatch.await(this.requestTimeoutMs, TimeUnit.MILLISECONDS);
            if (!allTasksCompletedInTime) {
                log.warn("Not all concurrent tasks completed within the configured timeout ({} ms). Cancelling pending tasks. Tasks to run: {}", this.requestTimeoutMs, this.numberOfConcurrentRequests);
                futures.forEach(f -> f.cancel(true));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Main thread interrupted while waiting for concurrent tasks to complete (timeout {} ms, tasks {}).", this.requestTimeoutMs, this.numberOfConcurrentRequests, e);
            futures.forEach(f -> f.cancel(true));
            return ExecutionResult.<S>builder()
                    .allRawResults(Collections.emptyList())
                    .expectedPatternMatched(false)
                    .allTasksCompletedInTime(false)
                    .build();
        }

        List<Object> rawResults = new ArrayList<>();
        for (CompletableFuture<Object> future : futures) {
            try {
                if (future.isDone() && !future.isCancelled()) {
                    rawResults.add(future.get(100, TimeUnit.MILLISECONDS));
                } else if (future.isCancelled()) {
                    rawResults.add(new CancellationException("Task was cancelled, likely due to overall timeout."));
                } else {
                    rawResults.add(new TimeoutException("Task did not complete and was not retrieved in time."));
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); rawResults.add(e);
            } catch (ExecutionException e) { rawResults.add(e.getCause() != null ? e.getCause() : e);
            } catch (TimeoutException e) { rawResults.add(e); }
        }

        ResponseEntity<S> successfulResponse = null;
        FeignException feignException = null;
        boolean patternMatched = false;

        if (this.numberOfConcurrentRequests == 2) {
            List<ResponseEntity> successes = rawResults.stream()
                    .filter(ResponseEntity.class::isInstance)
                    .map(r -> (ResponseEntity<S>) r)
                    .collect(Collectors.toList());

            List<FeignException> feignErrors = rawResults.stream()
                    .filter(FeignException.class::isInstance)
                    .map(FeignException.class::cast)
                    .collect(Collectors.toList());

            if (successes.size() == 1 && feignErrors.size() == 1) {
                successfulResponse = successes.get(0);
                feignException = feignErrors.get(0);
                patternMatched = true;
            } else {
                log.warn("Expected 1 successful response and 1 FeignException (for {} concurrent requests), but found {} successes and {} FeignExceptions. Raw results: {}",
                        this.numberOfConcurrentRequests, successes.size(), feignErrors.size(), rawResults.stream().map(r -> r == null ? "null" : r.getClass().getSimpleName()).collect(Collectors.toList()));
            }
        } else {
            log.info("Running with {} concurrent requests. Specific '1 success & 1 FeignException' pattern matching is designed for 2 requests and will be skipped.", this.numberOfConcurrentRequests);
        }

        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }

        return ExecutionResult.<S>builder()
                .successfulResponse(successfulResponse)
                .feignException(feignException)
                .allRawResults(rawResults)
                .expectedPatternMatched(patternMatched)
                .allTasksCompletedInTime(allTasksCompletedInTime)
                .build();
    }
}