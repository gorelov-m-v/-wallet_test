package com.uplatform.wallet_tests.api.nats;

import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.config.NatsConfig;
import io.nats.client.*;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.api.StreamInfo;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@Slf4j
@Getter
public class NatsConnectionManager {

    private final Connection connection;
    private final JetStream jetStream;
    private final String streamName;
    private final String streamPrefix;
    private final String natsBaseName;

    @Autowired
    public NatsConnectionManager(EnvironmentConfigurationProvider configProvider) {
        NatsConfig natsConfig = configProvider.getNatsConfig();
        this.streamPrefix = configProvider.getEnvironmentConfig().getNatsStreamPrefix();
        this.natsBaseName = natsConfig.getStreamName();
        this.streamName = this.streamPrefix + this.natsBaseName;

        Options options = buildOptions(natsConfig);
        try {
            this.connection = Nats.connect(options);
        } catch (Exception e) {
            log.error("Failed to connect to NATS servers: {}", String.join(",", natsConfig.getHosts()), e);
            throw new IllegalStateException("NATS connection failed", e);
        }

        try {
            this.jetStream = initJetStream();
        } catch (Exception e) {
            log.error("Failed to create JetStream context or validate stream '{}'", this.streamName, e);
            gracefulClose();
            throw new IllegalStateException("Failed to initialize JetStream for stream " + this.streamName, e);
        }
    }

    private Options buildOptions(NatsConfig cfg) {
        return new Options.Builder()
                .servers(cfg.getHosts().toArray(new String[0]))
                .reconnectWait(Duration.ofSeconds(cfg.getConnectReconnectWaitSeconds()))
                .maxReconnects(cfg.getConnectMaxReconnects())
                .connectionListener(this::connectionListener)
                .errorListener(new SimpleErrorListener())
                .build();
    }

    private JetStream initJetStream() throws IOException, JetStreamApiException {
        JetStream jetStream = connection.jetStream();
        validateStreamExists();
        return jetStream;
    }

    private void validateStreamExists() throws IOException, JetStreamApiException {
        try {
            JetStreamManagement jsm = connection.jetStreamManagement();
            StreamInfo streamInfo = jsm.getStreamInfo(this.streamName);
            log.info("Successfully validated NATS stream: {}", streamInfo.getConfiguration().getName());
        } catch (JetStreamApiException e) {
            if (e.getMessage() != null && e.getMessage().contains("stream not found")) {
                log.error("NATS Stream '{}' was not found!", this.streamName);
                throw e;
            }
            if (e.getCause() instanceof TimeoutException || (e.getMessage() != null && e.getMessage().toLowerCase().contains("timeout"))) {
                log.error("Timeout occurred while validating NATS stream '{}'. Check connectivity and server status. Error: {}", this.streamName, e.getMessage());
                throw e;
            }
            log.warn("Could not get full info for stream '{}', but no 'not found' error. Assuming it exists. Error: {}", this.streamName, e.getMessage());
        } catch (IOException ioe) {
            if (ioe.getMessage() != null && ioe.getMessage().toLowerCase().contains("timeout")) {
                log.error("IOException (likely timeout) occurred while validating NATS stream '{}'. Check connectivity. Error: {}", this.streamName, ioe.getMessage());
            } else {
                log.error("IOException occurred while validating NATS stream '{}'. Error: {}", this.streamName, ioe.getMessage());
            }
            throw ioe;
        }
    }

    private void connectionListener(Connection conn, ConnectionListener.Events type) {
        log.info("NATS connection event: {} - Connection: {}", type, conn);
    }

    static class SimpleErrorListener implements ErrorListener {
        @Override public void errorOccurred(Connection conn, String error) {
            log.error("NATS Error: {} (Connection URL: {})", error, conn != null ? conn.getConnectedUrl() : "N/A");
        }
        @Override public void exceptionOccurred(Connection conn, Exception exp) {
            log.error("NATS Exception: {} (Connection URL: {})", exp.getMessage(), conn != null ? conn.getConnectedUrl() : "N/A", exp);
        }
        @Override public void slowConsumerDetected(Connection conn, Consumer consumer) {
            log.warn("NATS Slow Consumer detected: Consumer='{}' (Connection URL: {})", consumer != null ? consumer.toString() : "N/A", conn != null ? conn.getConnectedUrl() : "N/A");
        }
    }

    @PreDestroy
    public void gracefulClose() {
        if (connection != null && connection.getStatus() == Connection.Status.CONNECTED) {
            try {
                CompletableFuture<Boolean> drained = connection.drain(Duration.ofSeconds(5));
                if (drained.get(6, TimeUnit.SECONDS)) {
                    log.info("NATS connection drained successfully.");
                } else {
                    log.warn("NATS connection drain timed out.");
                }
            } catch (Exception e) {
                log.warn("Exception during NATS connection drain: {}", e.getMessage(), e);
            } finally {
                try {
                    connection.close();
                    log.info("NATS connection closed.");
                } catch (Exception closeEx) {
                    log.error("Exception during NATS connection close: {}", closeEx.getMessage(), closeEx);
                }
            }
        } else if (connection != null) {
            log.info("NATS connection already closed or not connected (Status: {}). Closing anyway.", connection.getStatus());
            try {
                connection.close();
            } catch (Exception e) {
                log.warn("Exception during close of non-connected NATS connection: {}", e.getMessage());
            }
        } else {
            log.warn("NATS connection instance was null, cannot close.");
        }
    }
}
