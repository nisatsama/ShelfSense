package com.nisatsama.shelfsense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShelfsenseApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShelfsenseApplication.class, args);
	}

}
