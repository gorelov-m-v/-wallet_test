package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

@Getter
public class EnvironmentConfigurationProvider {

    private static final Logger log = LoggerFactory.getLogger(EnvironmentConfigurationProvider.class);

    private EnvironmentConfig environmentConfig;

    public void loadConfig() throws IOException {
        try {
            URL resourceRoot = EnvironmentConfigurationProvider.class.getClassLoader().getResource("");
            if (resourceRoot != null) {
                log.info("DEBUG: Classpath root seems to be at: {}", resourceRoot.getPath());
            } else {
                log.warn("DEBUG: Could not determine classpath root.");
            }
        } catch (Exception e) {
            log.warn("DEBUG: Error while trying to get classpath root", e);
        }

        String envName = System.getProperty("env");
        if (envName == null || envName.trim().isEmpty()) {
            throw new IllegalStateException(
                    "System property 'env' is not set! Please run tests with -Denv=<environment_name>"
            );
        }

        log.info("Loading configuration for environment: {}", envName);
        String configFileName = "configs/" + envName + ".json";
        log.info("DEBUG: Attempting to load resource from classpath: {}", configFileName);


        try (InputStream configFileStream = EnvironmentConfigurationProvider.class.getClassLoader().getResourceAsStream(configFileName)) {
            if (configFileStream == null) {
                log.error("FATAL: Resource '{}' not found in classpath. getResourceAsStream returned null.", configFileName);
                throw new IOException("Configuration file not found in classpath: " + configFileName);
            }

            log.info("DEBUG: Resource '{}' found successfully! Reading content...", configFileName);
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.registerModule(new JavaTimeModule());

            this.environmentConfig = objectMapper.readValue(configFileStream, EnvironmentConfig.class);
        }
        log.info("Successfully loaded configuration for environment '{}'", environmentConfig.getName());
    }

    public KafkaConfig getKafkaConfig() {
        return environmentConfig.getKafka();
    }

    public NatsConfig getNatsConfig() {
        return environmentConfig.getNats();
    }
}