package com.uplatform.wallet_tests.tests.util.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.cap.dto.check.CapTokenCheckRequest;
import com.uplatform.wallet_tests.api.http.cap.dto.check.CapTokenCheckResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
@RequiredArgsConstructor
@Slf4j
public class CapAdminTokenStorage {

    private final CapAdminClient capAdminClient;
    private final ObjectMapper objectMapper;
    @Value("${app.api.cap.credentials.username}") private String capAdminUsername;
    @Value("${app.api.cap.credentials.password}") private String capAdminPassword;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Lock readLock = lock.readLock();
    private final Lock writeLock = lock.writeLock();
    private String accessToken;
    private Instant expiresAt;
    private static final Duration REFRESH_THRESHOLD = Duration.ofMinutes(3);

    @PostConstruct
    public void initialize() {
        log.info("Initializing CAP Admin Token Storage...");
        try {
            fetchAndSetNewToken("Initial Token Fetch");
            log.info("Initial CAP Admin Token fetched successfully.");
        } catch (Exception e) {
            log.error("FATAL: Failed to fetch initial CAP Admin token! Subsequent requests might fail.", e);
        }
    }

    public String getValidAccessToken() {
        readLock.lock();
        try {
            if (isTokenValid()) {
                log.debug("Using cached valid CAP admin token.");
                return this.accessToken;
            }
        } finally {
            readLock.unlock();
        }

        writeLock.lock();
        try {
            if (isTokenValid()) {
                log.debug("Token was refreshed by another thread, using updated token.");
                return this.accessToken;
            }

            log.info("CAP Admin token needs refresh or is missing. Fetching new token...");
            fetchAndSetNewToken("Token Refresh");
            log.info("CAP Admin token refreshed successfully.");
            return this.accessToken;

        } catch (Exception e) {
            log.error("Failed to refresh CAP admin token!", e);
            throw new RuntimeException("Failed to obtain valid CAP Admin token", e);
        } finally {
            writeLock.unlock();
        }
    }

    public String getAuthorizationHeader() {
        return "Bearer " + getValidAccessToken();
    }

    private boolean isTokenValid() {
        if (this.accessToken == null || this.accessToken.trim().isEmpty() || this.expiresAt == null) {
            return false;
        }
        return Instant.now().isBefore(this.expiresAt.minus(REFRESH_THRESHOLD));
    }

    private void fetchAndSetNewToken(String context) {
        log.debug("Executing CAP admin token request...");
        CapTokenCheckRequest tokenRequest = CapTokenCheckRequest.builder()
                .username(capAdminUsername)
                .password(capAdminPassword)
                .build();

        ResponseEntity<CapTokenCheckResponse> responseEntity;
        try {
            responseEntity = capAdminClient.getToken(tokenRequest);
        } catch (Exception e) {
            log.error("Error during CAP token request execution: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to execute CAP token request", e);
        }

        if (responseEntity.getStatusCode() != HttpStatus.OK || responseEntity.getBody() == null) {
            log.error("Failed to get CAP admin token. Status: {}, Body: {}", responseEntity.getStatusCode(), responseEntity.getBody());
            throw new RuntimeException("Failed to get CAP admin token, status: " + responseEntity.getStatusCode());
        }

        CapTokenCheckResponse tokenResponse = responseEntity.getBody();

        if (tokenResponse.getToken() == null || tokenResponse.getToken().trim().isEmpty()) {
            log.error("Received empty token from CAP API.");
            throw new RuntimeException("Received empty token from CAP API.");
        }

        this.accessToken = tokenResponse.getToken();

        try {
            this.expiresAt = parseExpiryFromJwt(this.accessToken);
            log.debug("Parsed token expiry: {}", this.expiresAt);
        } catch (IOException e) {
            log.error("Failed to parse JWT expiry from token. Using default expiry (now + 15 min). Error: {}", e.getMessage(), e);
            this.expiresAt = Instant.now().plus(Duration.ofMinutes(15));
        }

        if (this.expiresAt == null || this.expiresAt.isBefore(Instant.now())) {
            log.error("Parsed token expiry is invalid or in the past: {}", this.expiresAt);
            throw new RuntimeException("Parsed token expiry is invalid or in the past: " + this.expiresAt);
        }

        log.info("New CAP admin token set. Expires at: {}", this.expiresAt);
    }

    private Instant parseExpiryFromJwt(String token) throws IOException {
        String[] parts = token.split("\\.");
        if (parts.length < 2) {
            throw new IOException("Invalid JWT token format: not enough parts");
        }
        String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

        Map<String, Object> claims = objectMapper.readValue(payloadJson, new TypeReference<>() {});

        Object expClaim = claims.get("exp");
        if (expClaim == null) {
            throw new IOException("JWT token missing 'exp' claim");
        }

        long expiresSeconds;
        if (expClaim instanceof Number number) {
            expiresSeconds = number.longValue();
        } else {
            throw new IOException("'exp' claim is not a number: " + expClaim.getClass().getName());
        }

        if (expiresSeconds <= 0) {
            throw new IOException("Invalid 'exp' claim value: " + expiresSeconds);
        }

        return Instant.ofEpochSecond(expiresSeconds);
    }
}