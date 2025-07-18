package com.uplatform.wallet_tests.tests.base;

import com.uplatform.wallet_tests.allure.CustomSuiteExtension;
import com.uplatform.wallet_tests.config.DynamicPropertiesConfigurator;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import com.uplatform.wallet_tests.tests.default_steps.facade.DefaultTestSteps;
import com.uplatform.wallet_tests.tests.util.facade.TestUtils;
import com.uplatform.wallet_tests.api.http.manager.client.ManagerClient;
import com.uplatform.wallet_tests.api.http.cap.client.CapAdminClient;
import com.uplatform.wallet_tests.api.http.fapi.client.FapiClient;
import com.uplatform.wallet_tests.api.redis.client.WalletRedisClient;
import com.uplatform.wallet_tests.api.nats.NatsClient;
import com.uplatform.wallet_tests.api.kafka.client.WalletProjectionKafkaClient;
import com.uplatform.wallet_tests.api.kafka.client.GameSessionKafkaClient;
import com.uplatform.wallet_tests.api.kafka.client.LimitKafkaClient;
import com.uplatform.wallet_tests.api.db.WalletDatabaseClient;
import com.uplatform.wallet_tests.api.db.CoreDatabaseClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

@ExtendWith(CustomSuiteExtension.class)
@SpringBootTest
@ContextConfiguration(initializers = DynamicPropertiesConfigurator.class)
@Execution(ExecutionMode.CONCURRENT)
public abstract class BaseTest {

    @Autowired
    protected EnvironmentConfigurationProvider configProvider;

    @Autowired
    protected DefaultTestSteps defaultTestSteps;

    @Autowired
    protected TestUtils utils;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ManagerClient managerClient;

    @Autowired
    protected WalletRedisClient redisClient;

    @Autowired
    protected NatsClient natsClient;

    @Autowired
    protected WalletProjectionKafkaClient walletProjectionKafkaClient;

    @Autowired
    protected WalletDatabaseClient walletDatabaseClient;

    @Autowired
    protected CapAdminClient capAdminClient;

    @Autowired
    protected FapiClient publicClient;

    @Autowired
    protected LimitKafkaClient limitKafkaClient;

    @Autowired
    protected GameSessionKafkaClient gameSessionKafkaClient;

    @Autowired
    protected CoreDatabaseClient coreDatabaseClient;
}
