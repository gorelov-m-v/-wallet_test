package com.uplatform.wallet_tests.tests.util.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.http.manager.dto.gambling.enums.ApiEndpoints;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Formatter;
import java.util.Objects;

@Slf4j
@Component
public class HttpSignatureUtil {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String apiSecret;

    @Autowired
    public HttpSignatureUtil(ObjectMapper objectMapper,
                             @Value("${app.api.manager.secret}") String apiSecret) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper cannot be null");
        this.apiSecret = Objects.requireNonNull(apiSecret,
                "API secret cannot be null (check property 'api.signature.secret')");
    }

    public String createSignature(ApiEndpoints endpoint, Object body) {
        return createSignature(endpoint, "", body);
    }

    public String createSignature(ApiEndpoints endpoint, String queryParams, Object body) {
        long timestamp = Instant.now().getEpochSecond();
        String bodyStr  = serializeBody(body);

        String queryStr = (queryParams != null && !queryParams.trim().isEmpty())
                ? "?" + queryParams
                : "";

        String signStr = String.format("%d.%s%s.%s",
                timestamp,
                endpoint.getPath(),
                queryStr,
                bodyStr
        );

        String signature = calculateHmac(signStr, this.apiSecret);
        return String.format("t=%d,v1=%s", timestamp, signature);
    }

    private String serializeBody(Object body) {
        if (body == null) {
            return "";
        }
        try {
            return this.objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize request body for signature", e);
        }
    }

    private String calculateHmac(String data, String secret) {
        try {
            Mac sha256_HMAC = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec =
                    new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            sha256_HMAC.init(secretKeySpec);
            byte[] hmacBytes = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hmacBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("HMAC SHA256 Algorithm Not Available", e);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(
                    "Invalid secret key for HMAC SHA256 (check property 'api.signature.secret')", e);
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        try (Formatter formatter = new Formatter(hexString)) {
            for (byte b : bytes) {
                formatter.format("%02x", b);
            }
        }
        return hexString.toString();
    }
}
