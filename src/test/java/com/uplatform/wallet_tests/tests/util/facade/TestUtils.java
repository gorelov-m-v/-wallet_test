package com.uplatform.wallet_tests.tests.util.facade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import com.uplatform.wallet_tests.api.kafka.dto.WalletProjectionMessage;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import com.uplatform.wallet_tests.tests.util.utils.CapAdminTokenStorage;
import com.uplatform.wallet_tests.tests.util.utils.ConcurrencyRequestExecutor;
import com.uplatform.wallet_tests.tests.util.utils.HttpSignatureUtil;
import com.uplatform.wallet_tests.tests.util.utils.KafkaNatsComparator;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;

@Component
@RequiredArgsConstructor
@Slf4j
public class TestUtils {

    private final HttpSignatureUtil httpSignatureUtil;
    private final KafkaNatsComparator kafkaNatsComparator;
    private final CapAdminTokenStorage capAdminTokenStorage;
    private final ObjectMapper objectMapper;
    private final ConcurrencyRequestExecutor concurrencyRequestExecutor;

    public <S> ConcurrencyRequestExecutor.ExecutionResult<S> executeConcurrentIdenticalRequests(
            Callable<ResponseEntity<S>> requestCallable
    ) {
        return concurrencyRequestExecutor.executeIdenticalRequests(requestCallable);
    }

    public String createSignature(ApiEndpoints endpoint, Object body) {
        return httpSignatureUtil.createSignature(endpoint, body);
    }

    public String createSignature(ApiEndpoints endpoint, String queryParams, Object body) {
        return httpSignatureUtil.createSignature(endpoint, queryParams, body);
    }

    public boolean areEquivalent(WalletProjectionMessage kafkaMessage, NatsMessage<?> natsMessage) {
        return kafkaNatsComparator.areEquivalent(kafkaMessage, natsMessage);
    }

    public String getAuthorizationHeader() {
        return capAdminTokenStorage.getAuthorizationHeader();
    }

    public <T> T parseFeignExceptionContent(FeignException feignException, Class<T> errorClass) {
        if (feignException == null) {
            log.error("Cannot parse content from a null FeignException for type {}", errorClass.getSimpleName());
            throw new IllegalArgumentException("FeignException cannot be null for parsing.");
        }
        String content = feignException.contentUTF8();
        if (content == null || content.trim().isEmpty()) {
            log.warn("FeignException content is null or empty. Cannot parse to type {}", errorClass.getSimpleName());
            throw new RuntimeException("FeignException content is null or empty, cannot parse to " + errorClass.getSimpleName());
        }

        try {
            return objectMapper.readValue(content, errorClass);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse FeignException content to {}. Content: '{}'",
                    errorClass.getSimpleName(), content, e);
            throw new RuntimeException("Failed to parse error response from FeignException: " + e.getMessage(), e);
        }
    }
}