package com.uplatform.wallet_tests;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.uplatform.wallet_tests.api.http")
public class WalletTestsApplication {

	public static void main(String[] args) {
		SpringApplication.run(WalletTestsApplication.class, args);
	}
}