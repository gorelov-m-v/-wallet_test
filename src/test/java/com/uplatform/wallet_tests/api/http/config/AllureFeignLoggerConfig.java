package com.uplatform.wallet_tests.api.http.config;

import feign.Client;
import feign.Logger;
import feign.okhttp.OkHttpClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import java.util.concurrent.TimeUnit;

@Configuration
@Slf4j
public class AllureFeignLoggerConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    @Bean
    public Client feignClient() {
        okhttp3.OkHttpClient okHttpClient = new okhttp3.OkHttpClient.Builder()
                .readTimeout(60, TimeUnit.SECONDS)
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES))
                .build();

        return new OkHttpClient(okHttpClient);
    }

    @Bean
    public FeignBuilderCustomizer allureFeignLoggerCustomizer(Logger.Level feignLoggerLevel,
                                                              AllureAttachmentService attachmentService) {
        return builder -> {
            builder.logger(new AllureFeignLogger(attachmentService))
                    .logLevel(feignLoggerLevel);
        };
    }

}