package com.uplatform.wallet_tests.api.db.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(
        basePackages = "com.uplatform.wallet_tests.api.db.repository.core",
        entityManagerFactoryRef = "coreEntityManagerFactory",
        transactionManagerRef = "coreTransactionManager"
)
public class CoreDbConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.core")
    public DataSourceProperties coreDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    public DataSource coreDataSource(@Qualifier("coreDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @Primary
    public LocalContainerEntityManagerFactoryBean coreEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("coreDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.uplatform.wallet_tests.api.db.entity.core")
                .persistenceUnit("core")
                .build();
    }

    @Bean
    @Primary
    public PlatformTransactionManager coreTransactionManager(
            @Qualifier("coreEntityManagerFactory") LocalContainerEntityManagerFactoryBean entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory.getObject());
    }
}