package com.uplatform.wallet_tests.api.http.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import feign.Logger;
import feign.Request;
import feign.Response;
import feign.Util;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Feign logger that attaches request and response data to Allure reports.
 */
@Slf4j
public class AllureFeignLogger extends Logger {

    private final AllureAttachmentService attachmentService;

    public AllureFeignLogger(AllureAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    private static final Set<String> IGNORED_REQUEST_HEADERS = new HashSet<>(Arrays.asList(
            "accept", "content-type", "content-length", "host", "connection",
            "accept-encoding", "user-agent", "transfer-encoding"
    ));

    @Override
    protected void logRequest(String configKey, Level logLevel, Request request) {
        try {
            if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
                StringBuilder requestDetails = formatRequest(request, logLevel, true);
                attachmentService.attachText("Http Request", requestDetails.toString());
            }
        } catch (Exception e) {
            attachmentService.attachText("Http Request Attachment Error", e.toString());
        }
    }

    private StringBuilder formatRequest(Request request, Level logLevel, boolean filterHeaders) {
        StringBuilder requestDetails = new StringBuilder();
        requestDetails.append("Method: ").append(request.httpMethod().name()).append("\n");
        requestDetails.append("Url: ").append(request.url()).append("\n");

        if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            boolean headerSectionAdded = false;
            for (Map.Entry<String, Collection<String>> entry : request.headers().entrySet()) {
                String headerName = entry.getKey();
                String headerNameLower = headerName.toLowerCase(Locale.ROOT);
                if (!filterHeaders || !IGNORED_REQUEST_HEADERS.contains(headerNameLower)) {
                    if (!headerSectionAdded) {
                        requestDetails.append(filterHeaders ? "Custom Headers:\n" : "Headers:\n");
                        headerSectionAdded = true;
                    }
                    requestDetails.append("  ").append(headerName).append(": ")
                            .append(String.join("; ", entry.getValue())).append("\n");
                }
            }
            if (filterHeaders && !headerSectionAdded && !request.headers().isEmpty()) {
                requestDetails.append("Custom Headers: (none found or all filtered)\n");
            } else if (!filterHeaders && !headerSectionAdded && !request.headers().isEmpty()) {
                requestDetails.append("Headers: (present but listing failed?)\n");
            } else {
                request.headers();
            }
        }

        if (request.body() != null && logLevel.ordinal() >= Level.FULL.ordinal()) {
            requestDetails.append("Body:\n");
            String bodyText = "[Binary Body or Unknown Charset]";
            if (request.charset() != null) {
                try {
                    bodyText = new String(request.body(), request.charset());
                } catch (Exception e) {
                    bodyText = "[Binary Body - Decoding Failed]";
                }
            } else {
                bodyText = new String(request.body(), StandardCharsets.UTF_8);
            }
            requestDetails.append(tryFormatJson(bodyText));
        } else if (logLevel.ordinal() >= Level.FULL.ordinal()) {
            requestDetails.append("Body: (empty)");
        }
        return requestDetails;
    }

    @Override
    protected Response logAndRebufferResponse(String configKey, Level logLevel, Response response, long elapsedTime) throws IOException {
        byte[] bodyData = {};
        Response processedResponse = response;
        Integer reportedLength = null;

        try {
            if (logLevel.ordinal() >= Level.HEADERS.ordinal()) {
                Response.Body responseBody = response.body();
                if (responseBody != null) {
                    reportedLength = responseBody.length();
                    if (reportedLength == null || reportedLength != 0) {
                        try {
                            bodyData = Util.toByteArray(responseBody.asInputStream());
                            if (reportedLength != null && reportedLength > 0 && bodyData.length == 0)
                                log.warn("[AllureFeignLogger][{}] !!! Body length reported as {}, but read 0 bytes!", configKey, reportedLength);
                            else if (reportedLength != null && bodyData.length != reportedLength)
                                log.warn("[AllureFeignLogger][{}] Body length reported as {}, but read {} bytes.", configKey, reportedLength, bodyData.length);

                        } catch (IOException e) {
                            throw e;
                        } catch (Exception ignored) {
                        }
                    }
                    processedResponse = response.toBuilder().body(bodyData).build();

                } else {
                    processedResponse = response.toBuilder().body(null, (Integer) null).build();
                }

                StringBuilder responseDetails = formatResponse(processedResponse, elapsedTime, bodyData, logLevel, false);
                attachmentService.attachText("Http Response", responseDetails.toString());
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            attachmentService.attachText("Http Response Processing Error", e.toString());
            if (response.body() != null) try { response.body().close(); } catch (IOException ignored) {}
            throw new IOException("Unhandled exception during response processing: " + e.getMessage(), e);
        }
        return processedResponse;
    }

    private StringBuilder formatResponse(Response response, long elapsedTime, byte[] bodyData, Level logLevel, boolean includeHeaders) {
        StringBuilder responseDetails = new StringBuilder();
        int status = response.status();
        String reason = response.reason() != null ? (" " + response.reason()) : "";
        responseDetails.append("Status: ").append(status).append(reason).append("\n");
        responseDetails.append("Time(ms): ").append(elapsedTime).append("\n");

        if (includeHeaders && logLevel.ordinal() >= Level.HEADERS.ordinal()) {
            responseDetails.append("Headers:\n");
            for (Map.Entry<String, Collection<String>> entry : response.headers().entrySet()) {
                responseDetails.append("  ").append(entry.getKey()).append(": ")
                        .append(String.join("; ", entry.getValue())).append("\n");
            }
        }

        if (logLevel.ordinal() >= Level.FULL.ordinal()) {
            responseDetails.append("Body:\n");
            if (bodyData.length > 0) {
                String bodyText = new String(bodyData, StandardCharsets.UTF_8);
                String formattedBody = tryFormatJson(bodyText);
                responseDetails.append(formattedBody);
            } else {
                responseDetails.append("(empty)");
            }
        }
        return responseDetails;
    }

    @Override
    protected void log(String configKey, String format, Object... args) {
        if (log.isDebugEnabled()) {
            log.debug(String.format(methodTag(configKey) + format, args));
        }
    }

    private String tryFormatJson(String text) {
        if (text == null || text.isEmpty() || (!text.startsWith("{") && !text.startsWith("["))) {
            return text;
        }
        try {
            ObjectMapper mapper = LocalObjectMapperHolder.MAPPER;
            Object jsonObject = mapper.readValue(text, Object.class);
            return mapper.writeValueAsString(jsonObject);
        } catch (Exception e) {
            return text;
        }
    }

    private static class LocalObjectMapperHolder {
        static final ObjectMapper MAPPER = createObjectMapper();

        private static ObjectMapper createObjectMapper() {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            return mapper;
        }
    }
}
