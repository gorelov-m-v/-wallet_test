package com.uplatform.wallet_tests.api.nats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class NatsAttachmentHelper {

    private final ObjectMapper objectMapper;
    private final AllureAttachmentService attachmentService;

    public <T> void addNatsAttachment(String name, NatsMessage<T> natsMsg) {
        if (natsMsg == null || natsMsg.getPayload() == null) {
            log.warn("Skipping NATS attachment '{}' because message or payload is null.", name);
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Subject: ").append(natsMsg.getSubject()).append("\n");
        sb.append("Sequence: ").append(natsMsg.getSequence()).append("\n");
        if (natsMsg.getType() != null) {
            sb.append("Type Header: ").append(natsMsg.getType()).append("\n");
        }
        if (natsMsg.getTimestamp() != null) {
            sb.append("Timestamp: ").append(natsMsg.getTimestamp().toInstant()).append(" (").append(natsMsg.getTimestamp()).append(")\n");
        }
        sb.append("Data Type: ").append(natsMsg.getPayload().getClass().getName()).append("\n\n");
        try {
            sb.append("Payload (JSON):\n");
            sb.append(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(natsMsg.getPayload()));
        } catch (JsonProcessingException e) {
            sb.append("Error marshalling payload: ").append(e.getMessage()).append("\n");
            sb.append("Payload (toString()):\n").append(natsMsg.getPayload().toString());
        }
        try {
            attachmentService.attachText(name, sb.toString());
        } catch (Exception e) {
            log.error("Failed to add Allure attachment '{}': {}", name, e.getMessage());
        }
    }
}
