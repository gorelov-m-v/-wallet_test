package com.uplatform.wallet_tests.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.util.ArrayList;
import java.util.List;

public class DynamicPropertiesConfigurator implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final EnvironmentConfigurationProvider provider = new EnvironmentConfigurationProvider();
    static {
        try {
            provider.loadConfig();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load environment configuration during class initialization", e);
        }
    }

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        EnvironmentConfig config = provider.getEnvironmentConfig();

        List<String> properties = new ArrayList<>();

        if (config.getApi() != null) {
            properties.add("app.api.fapi.base-url=" + config.getApi().getBaseUrl());
            properties.add("app.api.cap.base-url=https://cap." + config.getApi().getBaseUrl().replace("https://", ""));
            properties.add("app.api.manager.base-url=" + config.getApi().getBaseUrl());
            if (config.getApi().getCapCredentials() != null) {
                properties.add("app.api.cap.credentials.username=" + config.getApi().getCapCredentials().getUsername());
                properties.add("app.api.cap.credentials.password=" + config.getApi().getCapCredentials().getPassword());
            }
            if (config.getApi().getManager() != null) {
                properties.add("app.api.manager.secret=" + config.getApi().getManager().getSecret());
                properties.add("app.api.manager.casino-id=" + config.getApi().getManager().getCasinoId());
            }
            if (config.getApi().getConcurrency() != null) {
                properties.add("app.api.concurrency.request-timeout-ms=" + config.getApi().getConcurrency().getRequestTimeoutMs());
                properties.add("app.api.concurrency.default-request-count=" + config.getApi().getConcurrency().getDefaultRequestCount());
            }
        }

        if (config.getPlatform() != null) {
            properties.add("app.settings.default.platform-node-id=" + config.getPlatform().getNodeId());
            properties.add("app.settings.default.platform-group-id=" + config.getPlatform().getGroupId());
            properties.add("app.settings.default.currency=" + config.getPlatform().getCurrency());
            properties.add("app.settings.default.country=" + config.getPlatform().getCountry());
        }

        if (config.getDatabases() != null) {
            config.getDatabases().forEach((name, dbConfig) -> {
                String dbNameForUrl = config.getName() + "_" + name;
                String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                        dbConfig.getHost(), dbConfig.getPort(), dbNameForUrl);

                properties.add("spring.datasource." + name + ".url=" + url);
                properties.add("spring.datasource." + name + ".username=" + dbConfig.getUsername());
                properties.add("spring.datasource." + name + ".password=" + dbConfig.getPassword());
                properties.add("app.db.retry-timeout-seconds=" + dbConfig.getRetryTimeoutSeconds());
                properties.add("app.db.retry-poll-interval-ms=" + dbConfig.getRetryPollIntervalMs());
                properties.add("app.db.retry-poll-delay-ms=" + dbConfig.getRetryPollDelayMs());
            });
        }

        RedisSettings redisFullConfig = config.getRedis();

        if (redisFullConfig != null) {
            RedisAggregateConfig aggregateConfig = redisFullConfig.getAggregate();
            if (aggregateConfig != null) {
                properties.add("app.redis.aggregate.max-gambling.count=" + aggregateConfig.getMaxGamblingCount());
                properties.add("app.redis.aggregate.max-iframe.count=" + aggregateConfig.getMaxIframeCount());
                properties.add("app.redis.retry-attempts=" + aggregateConfig.getRetryAttempts());
                properties.add("app.redis.retry-delay-ms=" + aggregateConfig.getRetryDelayMs());
            }

            if (redisFullConfig.getInstances() != null) {
                redisFullConfig.getInstances().forEach((instanceName, instanceConfig) -> {
                    properties.add("spring.data.redis." + instanceName + ".host=" + instanceConfig.getHost());
                    properties.add("spring.data.redis." + instanceName + ".port=" + instanceConfig.getPort());
                    properties.add("spring.data.redis." + instanceName + ".database=" + instanceConfig.getDatabase());

                    if (instanceConfig.getTimeout() != null && !instanceConfig.getTimeout().isEmpty()) {
                        properties.add("spring.data.redis." + instanceName + ".timeout=" + instanceConfig.getTimeout());
                    }

                    LettucePoolConfig poolConfig = instanceConfig.getLettucePool();
                    if (poolConfig != null) {
                        properties.add("spring.data.redis." + instanceName + ".lettuce.pool.max-active=" + poolConfig.getMaxActive());
                        properties.add("spring.data.redis." + instanceName + ".lettuce.pool.max-idle=" + poolConfig.getMaxIdle());
                        properties.add("spring.data.redis." + instanceName + ".lettuce.pool.min-idle=" + poolConfig.getMinIdle());
                        if (poolConfig.getShutdownTimeout() != null && !poolConfig.getShutdownTimeout().isEmpty()) {
                            properties.add("spring.data.redis." + instanceName + ".lettuce.shutdown-timeout=" + poolConfig.getShutdownTimeout());
                        }
                    }
                });
            }
        }

        if (!properties.isEmpty()) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(applicationContext, properties.toArray(new String[0]));
        }
    }
}