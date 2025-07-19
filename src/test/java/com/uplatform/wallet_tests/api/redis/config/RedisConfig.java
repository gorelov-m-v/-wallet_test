package com.uplatform.wallet_tests.api.redis.config;

import com.uplatform.wallet_tests.api.redis.client.PlayerRedisClient;
import com.uplatform.wallet_tests.api.redis.client.RedisRetryHelper;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Duration;

@Configuration
public class RedisConfig {

    @Bean("playerRedisProperties")
    @ConfigurationProperties(prefix = "spring.data.redis.player")
    public RedisProperties playerRedisProperties() {
        return new RedisProperties();
    }

    @Bean("playerRedisTemplate")
    public RedisTemplate<String, String> playerRedisTemplate(
            @Qualifier("playerRedisProperties") RedisProperties properties) {
        return createRedisInfrastructure("player", properties);
    }

    @Bean("walletRedisProperties")
    @Primary
    @ConfigurationProperties(prefix = "spring.data.redis.wallet")
    public RedisProperties walletRedisProperties() {
        return new RedisProperties();
    }

    @Bean("walletRedisTemplate")
    @Primary
    public RedisTemplate<String, String> walletRedisTemplate(
            @Qualifier("walletRedisProperties") RedisProperties properties) {
        return createRedisInfrastructure("wallet", properties);
    }

    @Bean
    public PlayerRedisClient playerRedisClient(
            @Qualifier("playerRedisTemplate") RedisTemplate<String, String> template,
            RedisRetryHelper retryHelper,
            AllureAttachmentService attachmentService) {
        return new PlayerRedisClient(template, retryHelper, attachmentService);
    }

    @Bean
    public WalletRedisClient walletRedisClient(
            @Qualifier("walletRedisTemplate") RedisTemplate<String, String> template,
            RedisRetryHelper retryHelper,
            AllureAttachmentService attachmentService) {
        return new WalletRedisClient(template, retryHelper, attachmentService);
    }

    private LettucePoolingClientConfiguration createPoolingConfig(RedisProperties properties) {
        GenericObjectPoolConfig<?> poolConfig = new GenericObjectPoolConfig<>();
        if (properties.getLettuce().getPool() != null) {
            RedisProperties.Pool poolProps = properties.getLettuce().getPool();
            poolConfig.setMaxTotal(poolProps.getMaxActive());
            poolConfig.setMaxIdle(poolProps.getMaxIdle());
            poolConfig.setMinIdle(poolProps.getMinIdle());
            if (poolProps.getMaxWait() != null && !poolProps.getMaxWait().isNegative()) {
                poolConfig.setMaxWait(poolProps.getMaxWait());
            }
        } else {
            poolConfig.setMaxTotal(8);
            poolConfig.setMaxIdle(8);
            poolConfig.setMinIdle(0);
        }

        return LettucePoolingClientConfiguration.builder()
                .poolConfig(poolConfig)
                .commandTimeout(properties.getTimeout() != null ? properties.getTimeout() : Duration.ofSeconds(60))
                .shutdownTimeout(properties.getLettuce().getShutdownTimeout() != null ? properties.getLettuce().getShutdownTimeout() : Duration.ofMillis(100))
                .build();
    }

    private LettuceConnectionFactory createConnectionFactory(RedisProperties properties,
                                                             LettucePoolingClientConfiguration clientConfiguration) {
        RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
        standaloneConfig.setHostName(properties.getHost());
        standaloneConfig.setPort(properties.getPort());
        standaloneConfig.setDatabase(properties.getDatabase());
        if (properties.getPassword() != null && !properties.getPassword().isEmpty()) {
            standaloneConfig.setPassword(properties.getPassword());
        }

        return new LettuceConnectionFactory(standaloneConfig, clientConfiguration);
    }

    private RedisTemplate<String, String> createStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);
        template.afterPropertiesSet();
        return template;
    }

    private RedisTemplate<String, String> createRedisInfrastructure(String instanceName, RedisProperties properties) {
        LettucePoolingClientConfiguration poolingConfig = createPoolingConfig(properties);
        LettuceConnectionFactory connectionFactory = createConnectionFactory(properties, poolingConfig);
        return createStringRedisTemplate(connectionFactory);
    }
}
