package com.uplatform.wallet_tests.api.attachment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.qameta.allure.Allure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AllureAttachmentService {
    private final ObjectMapper objectMapper;

    public void attachJson(String name, Object data) {
        if (data == null) {
            Allure.addAttachment(name, "application/json", "null", ".json");
            return;
        }
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
            Allure.addAttachment(name, "application/json", json, ".json");
        } catch (JsonProcessingException e) {
            String content = "Failed to serialize to JSON: " + e.getMessage() + "\n\n" + data;
            Allure.addAttachment(name, "text/plain", content, ".txt");
        }
    }

    public void attachText(String name, String content) {
        Allure.addAttachment(name, "text/plain", content, ".txt");
    }
}
