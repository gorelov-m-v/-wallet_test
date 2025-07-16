package com.uplatform.wallet_tests.tests.util.utils.dto;

import feign.FeignException;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

import java.util.List;

@Getter
@Builder
public class ExecutionResult<S> {
    private final ResponseEntity<S> successfulResponse;
    private final FeignException feignException;
    private final List<Object> allRawResults;
    private final boolean expectedPatternMatched;
    private final boolean allTasksCompletedInTime;
}