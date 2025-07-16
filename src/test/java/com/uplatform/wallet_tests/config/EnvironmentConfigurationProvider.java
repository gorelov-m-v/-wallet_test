package com.uplatform.wallet_tests.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

@Service
@Slf4j
@Getter
public class EnvironmentConfigurationProvider {

    private EnvironmentConfig environmentConfig;

    @PostConstruct
    public void loadConfig() throws IOException {
        String envName = System.getProperty("env");
        if (envName == null || envName.trim().isEmpty()) {
            throw new IllegalStateException(
                    "System property 'env' is not set! Please run tests with -Denv=<environment_name>"
            );
        }

        log.info("Loading configuration for environment: {}", envName);
        String configFileName = "configs/" + envName + ".json";

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        try (InputStream configFileStream = getClass().getClassLoader().getResourceAsStream(configFileName)) {
            if (configFileStream == null) {
                throw new IOException("Configuration file not found in classpath: " + configFileName);
            }
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