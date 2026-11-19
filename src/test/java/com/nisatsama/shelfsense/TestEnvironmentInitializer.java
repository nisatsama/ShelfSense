package com.nisatsama.shelfsense;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class TestEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Map<String, String> properties = new HashMap<>();

        String dbPassword = applicationContext.getEnvironment().getProperty("spring.datasource.password");
        if (dbPassword == null || dbPassword.isBlank()) {
            properties.put("spring.datasource.password", "shelfsense_dev");
        }

        String jwtSecret = applicationContext.getEnvironment().getProperty("shelfsense.security.jwt.secret");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            String testSecret = Base64.getEncoder().encodeToString("shelfsense-test-jwt-secret-key-32bytes!".getBytes(StandardCharsets.UTF_8));
            properties.put("shelfsense.security.jwt.secret", testSecret);
        }

        if (!properties.isEmpty()) {
            TestPropertyValues.of(properties).applyTo(applicationContext.getEnvironment());
        }
    }
}
