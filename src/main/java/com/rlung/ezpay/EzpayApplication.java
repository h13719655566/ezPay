package com.rlung.ezpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EzpayApplication {

	public static void main(String[] args) {
		SpringApplication.run(EzpayApplication.class, args);
	}

}
