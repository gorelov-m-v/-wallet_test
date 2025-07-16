package com.uplatform.wallet_tests.tests.util.comparator;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@RequiredArgsConstructor
@Slf4j
public class ComparatorStrategyManager {

    private final List<PayloadComparatorStrategy> strategies;
    private Map<String, PayloadComparatorStrategy> strategyMap;
    @PostConstruct
    void initializeStrategies() {
        strategyMap = new HashMap<>();
        int registeredCount = 0;

        for (PayloadComparatorStrategy strategy : strategies) {
            Set<String> supportedTypes = strategy.getSupportedEventTypes();
            if (supportedTypes == null || supportedTypes.isEmpty()) {
                continue;
            }

            for (String eventType : supportedTypes) {
                if (eventType == null || eventType.trim().isEmpty()) {
                    continue;
                }
                PayloadComparatorStrategy existingStrategy = strategyMap.put(eventType, strategy);

               if (existingStrategy == null) {
                    registeredCount++;
                }
            }
        }
    }

    public Optional<PayloadComparatorStrategy> findStrategy(String eventType) {
        if (eventType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategyMap.get(eventType));
    }
}