package com.uplatform.wallet_tests.api.db;

import jakarta.annotation.PostConstruct;
import org.awaitility.core.ConditionFactory;
import org.awaitility.core.ConditionTimeoutException;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import static org.awaitility.Awaitility.await;

public abstract class AbstractDatabaseClient {

    protected final AllureAttachmentService attachmentService;

    protected AbstractDatabaseClient(AllureAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @Value("${app.db.retry-timeout-seconds}")
    private long retryTimeoutSeconds;
    @Value("${app.db.retry-poll-interval-ms}")
    private long retryPollIntervalMillis;
    @Value("${app.db.retry-poll-delay-ms}")
    private long retryPollDelayMillis;

    protected Duration retryTimeoutDuration;
    protected Duration retryPollIntervalDuration;
    protected Duration retryPollDelayDuration;

    @PostConstruct
    public void initializeAwaitilityConfig() {
        this.retryTimeoutDuration = Duration.ofSeconds(retryTimeoutSeconds);
        this.retryPollIntervalDuration = Duration.ofMillis(retryPollIntervalMillis);
        this.retryPollDelayDuration = Duration.ofMillis(retryPollDelayMillis);
    }

    @SafeVarargs
    protected final <T> T awaitAndGetOrFail(String description,
                                           String attachmentNamePrefix,
                                           Supplier<Optional<T>> querySupplier,
                                           Class<? extends Throwable>... ignoredExceptionsDuringAwait) {
        Callable<Optional<T>> queryCallable = querySupplier::get;

        try {
            ConditionFactory condition = await(description)
                    .atMost(retryTimeoutDuration)
                    .pollInterval(retryPollIntervalDuration)
                    .pollDelay(retryPollDelayDuration)
                    .ignoreExceptionsInstanceOf(org.springframework.dao.TransientDataAccessException.class);

            if (ignoredExceptionsDuringAwait != null) {
                for (Class<? extends Throwable> ignored : ignoredExceptionsDuringAwait) {
                    if (ignored != null) {
                        condition = condition.ignoreExceptionsInstanceOf(ignored);
                    }
                }
            }

            Optional<T> optionalResult = condition.until(queryCallable, Optional::isPresent);

            T result = optionalResult.get();
            attachmentService.attachText(attachmentNamePrefix + " - Found", createJsonAttachment(result));
            return result;

        } catch (ConditionTimeoutException e) {
            attachmentService.attachText(attachmentNamePrefix + " - NOT Found (Timeout)",
                    "Timeout after " + retryTimeoutDuration + ": " + e.getMessage());
            throw e;
        } catch (Exception e) {
            attachmentService.attachText(attachmentNamePrefix + " - Error",
                    "Error type: " + e.getClass().getName() + "\nMessage: " + e.getMessage());
            throw new RuntimeException("Unexpected error during DB await for '" + description + "'", e);
        }
    }

    protected abstract String createJsonAttachment(Object object);
}

