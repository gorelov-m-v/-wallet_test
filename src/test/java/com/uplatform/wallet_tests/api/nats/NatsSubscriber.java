package com.uplatform.wallet_tests.api.nats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uplatform.wallet_tests.api.nats.dto.NatsMessage;
import io.nats.client.api.AckPolicy;
import io.nats.client.Dispatcher;
import io.nats.client.JetStream;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.Subscription;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.api.ReplayPolicy;
import io.nats.client.impl.NatsJetStreamMetaData;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiPredicate;

@Slf4j
class NatsSubscriber {

    private final io.nats.client.Connection nc;
    private final JetStream js;
    private final ObjectMapper objectMapper;
    private final NatsAttachmentHelper attachmentHelper;
    private final Duration searchTimeout;
    private final Duration ackWaitTimeout;
    private final Duration inactiveThreshold;
    private final String streamName;
    private final int subscriptionBufferSize;
    private final int subscriptionRetryCount;
    private final long subscriptionRetryDelayMs;

    NatsSubscriber(io.nats.client.Connection nc,
                   JetStream js,
                   ObjectMapper objectMapper,
                   NatsAttachmentHelper attachmentHelper,
                   Duration searchTimeout,
                   Duration ackWaitTimeout,
                   Duration inactiveThreshold,
                   String streamName,
                   int subscriptionBufferSize,
                   int subscriptionRetryCount,
                   long subscriptionRetryDelayMs) {
        this.nc = nc;
        this.js = js;
        this.objectMapper = objectMapper;
        this.attachmentHelper = attachmentHelper;
        this.searchTimeout = searchTimeout;
        this.ackWaitTimeout = ackWaitTimeout;
        this.inactiveThreshold = inactiveThreshold;
        this.streamName = streamName;
        this.subscriptionBufferSize = subscriptionBufferSize;
        this.subscriptionRetryCount = subscriptionRetryCount;
        this.subscriptionRetryDelayMs = subscriptionRetryDelayMs;
    }

    <T> CompletableFuture<NatsMessage<T>> findMessageAsync(String subject,
                                                           Class<T> messageType,
                                                           BiPredicate<T, String> filter) {
        CompletableFuture<NatsMessage<T>> future = new CompletableFuture<>();
        String logPrefix = String.format("NATS SEARCH ASYNC [%s -> %s]", this.streamName, subject);

        retryUntilSubscribed(subject, messageType, filter, future, logPrefix);

        return future;
    }

    private <T> Dispatcher startSubscription(String subject,
                                             Class<T> messageType,
                                             BiPredicate<T, String> filter,
                                             CompletableFuture<NatsMessage<T>> future,
                                             String logPrefix) throws IOException, JetStreamApiException {
        Dispatcher dispatcher = nc.createDispatcher();
        JavaType javaType = objectMapper.getTypeFactory().constructType(messageType);
        final Dispatcher dispatcherRef = dispatcher;
        final Subscription[] subHolder = new Subscription[1];

        MessageHandler handler = msg ->
                handleIncomingMessage(msg, javaType, filter, future, dispatcherRef, subHolder, logPrefix);

        subHolder[0] = createSubscription(subject, dispatcherRef, handler);

        awaitMessageFuture(future, dispatcherRef, subHolder[0], logPrefix);
        return dispatcher;
    }

    private <T> void retryUntilSubscribed(String subject,
                                          Class<T> messageType,
                                          BiPredicate<T, String> filter,
                                          CompletableFuture<NatsMessage<T>> future,
                                          String logPrefix) {
        for (int attempt = 1; attempt <= this.subscriptionRetryCount; attempt++) {
            Dispatcher dispatcher = null;
            try {
                dispatcher = startSubscription(subject, messageType, filter, future, logPrefix);
                return;
            } catch (JetStreamApiException | IOException e) {
                if (dispatcher != null) {
                    try {
                        nc.closeDispatcher(dispatcher);
                    } catch (Exception closeEx) {
                        log.warn("{} | Failed to close dispatcher after error: {}", logPrefix, closeEx.getMessage());
                    }
                }

                log.warn("{} | Attempt {}/{} to create NATS subscription failed: {}",
                        logPrefix, attempt, this.subscriptionRetryCount, e.getMessage());

                if (attempt == this.subscriptionRetryCount) {
                    log.error("{} | All {} subscription attempts failed for subject '{}'. Giving up.",
                            logPrefix, this.subscriptionRetryCount, subject, e);
                    future.completeExceptionally(
                            new RuntimeException(
                                    "NATS Subscription failed for " + subject +
                                            " after " + this.subscriptionRetryCount + " attempts", e));
                    return;
                }

                try {
                    TimeUnit.MILLISECONDS.sleep(this.subscriptionRetryDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("{} | Subscription retry delay was interrupted.", logPrefix, ie);
                    future.completeExceptionally(
                            new RuntimeException("Subscription retry interrupted for " + subject, ie));
                    return;
                }
            }
        }

        future.completeExceptionally(
                new IllegalStateException("Exited subscription retry loop unexpectedly for " + subject));
    }

    private Subscription createSubscription(String subject, Dispatcher dispatcher, MessageHandler handler)
            throws IOException, JetStreamApiException {
        PushSubscribeOptions pso = PushSubscribeOptions.builder()
                .stream(this.streamName)
                .configuration(
                        ConsumerConfiguration.builder()
                                .ackPolicy(AckPolicy.Explicit)
                                .ackWait(ackWaitTimeout)
                                .maxAckPending(subscriptionBufferSize)
                                .inactiveThreshold(inactiveThreshold)
                                .deliverPolicy(DeliverPolicy.All)
                                .replayPolicy(ReplayPolicy.Instant)
                                .build()
                ).build();
        return js.subscribe(subject, dispatcher, handler, false, pso);
    }

    private <T> void handleIncomingMessage(Message msg,
                                           JavaType javaType,
                                           BiPredicate<T, String> filter,
                                           CompletableFuture<NatsMessage<T>> future,
                                           Dispatcher dispatcher,
                                           Subscription[] subHolder,
                                           String logPrefix) {
        long msgSeq = -1L;
        String msgType = null;
        OffsetDateTime timestamp = null;

        try {
            if (msg.isJetStream()) {
                NatsJetStreamMetaData meta = msg.metaData();
                if (meta != null) {
                    msgSeq = meta.streamSequence();
                    ZonedDateTime ts = meta.timestamp();
                    if (ts != null) {
                        timestamp = ts.toOffsetDateTime();
                    }
                } else {
                    log.warn("{} | Received JetStream message without metadata object!", logPrefix);
                }
            } else {
                log.warn("{} | Received non-JetStream message", logPrefix); return;
            }

            msgType = msg.getHeaders() != null ? msg.getHeaders().getFirst("type") : null;

            T payload;
            try {
                payload = objectMapper.readValue(msg.getData(), javaType);
            } catch (JsonProcessingException e) {
                log.warn("{} | Failed JSON unmarshal seq={}: {}. Nacking msg.", logPrefix, msgSeq, e.getMessage());
                safeNack(msg);
                return;
            }

            if (filter.test(payload, msgType)) {
                safeAck(msg);
                NatsMessage<T> result = NatsMessage.<T>builder()
                        .payload(payload).subject(msg.getSubject()).type(msgType)
                        .sequence(msgSeq).timestamp(timestamp).build();
                attachmentHelper.addNatsAttachment("NATS Message Found", result);
                future.complete(result);
                unsubscribeSafely(dispatcher, subHolder[0], logPrefix + " after match");
            }
        } catch (Exception e) {
            log.error("{} | Error processing NATS msg (seq≈{}, type≈{}): {}", logPrefix, msgSeq, msgType, e.getMessage(), e);
            safeNack(msg);
        }
    }

    private <T> void awaitMessageFuture(CompletableFuture<NatsMessage<T>> future,
                                        Dispatcher dispatcher,
                                        Subscription subscription,
                                        String logPrefix) {
        future.orTimeout(searchTimeout.toMillis(), TimeUnit.MILLISECONDS).whenComplete((result, ex) -> {
            String completionLogPrefix = logPrefix + " | Future completed";
            if (ex instanceof TimeoutException) {
                log.warn("{} with Timeout after {}", completionLogPrefix, searchTimeout);
            }
            else if (ex != null) {
                log.error("{} with Exception: {}", completionLogPrefix, ex.getMessage(), ex);
            }
            unsubscribeSafely(dispatcher, subscription, logPrefix + " on completion");
        });
    }

    private void safeAck(Message msg) {
        try {
            if (msg.isJetStream()) msg.ack();
        } catch (Exception e) {
            logOnError("ACK", msg, e);
        }
    }
    private void safeNack(Message msg) {
        try {
            if (msg.isJetStream()) msg.nak();
        } catch (Exception e) {
            logOnError("NACK", msg, e);
        }
    }
    private void logOnError(String action, Message msg, Exception e) {
        if (!(e instanceof IllegalStateException && e.getMessage()!=null && e.getMessage().contains("Connection closed"))) {
            log.warn("NATS Failed to {} message sid={} subj={}: {}", action, msg.getSID(), msg.getSubject(), e.getMessage());
        }
    }

    private void unsubscribeSafely(Dispatcher d, Subscription sub, String context) {
        if (sub != null && sub.isActive()) {
            try {
                if (d != null && d.isActive()) {
                    d.unsubscribe(sub);
                } else {
                    sub.unsubscribe();
                }

                if (sub.isActive()) {
                    log.warn("{} | Subscription still active after first attempt, trying direct unsubscribe again.", context);
                    sub.unsubscribe();
                }
            } catch (IllegalStateException ise) {
                String msgText = ise.getMessage();
                if (msgText != null && (msgText.contains("Connection closed") || msgText.contains("Dispatcher inactive") || msgText.contains("Subscription closed"))) {
                    log.trace("{} | Ignored IllegalStateException during unsubscribe (likely closed/inactive): {}", context, msgText);
                } else {
                    log.warn("{} | Unexpected IllegalStateException during unsubscribe: {}", context, msgText);
                }
            } catch (Exception e) {
                log.warn("{} | Error during unsubscribe: {}", context, e.getMessage());
            }
        }
    }
}
