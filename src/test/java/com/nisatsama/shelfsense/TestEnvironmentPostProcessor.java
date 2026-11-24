package com.nisatsama.shelfsense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class TestEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();

        String dbPassword = environment.getProperty("spring.datasource.password");
        if (dbPassword == null || dbPassword.isBlank()) {
            properties.put("spring.datasource.password", "shelfsense_dev");
        }

        String jwtSecret = environment.getProperty("shelfsense.security.jwt.secret");
        if (jwtSecret == null || jwtSecret.isBlank()) {
            String testSecret = Base64.getEncoder().encodeToString("shelfsense-test-jwt-secret-key-32bytes!".getBytes(StandardCharsets.UTF_8));
            properties.put("shelfsense.security.jwt.secret", testSecret);
        }

        if (!properties.isEmpty()) {
            environment.getPropertySources().addFirst(new MapPropertySource("shelfsenseTestDefaults", properties));
        }
    }
}
