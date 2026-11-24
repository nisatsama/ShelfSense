package com.nisatsama.shelfsense;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class TestEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        Map<String, String> properties = new HashMap<>();

        String dbPassword = applicationContext.getEnvironment().getProperty("spring.datasource.password");
        if (dbPassword == null || dbPassword.isBlank()) {
            String postgresPassword = applicationContext.getEnvironment().getProperty("POSTGRES_PASSWORD");
            if (postgresPassword != null && !postgresPassword.isBlank()) {
                properties.put("spring.datasource.password", postgresPassword);
            } else {
                loadFromDotEnv(properties);
            }
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

    private void loadFromDotEnv(Map<String, String> properties) {
        Path envPath = Path.of(".env");
        if (!Files.exists(envPath)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(envPath, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && !line.startsWith("#") && line.contains("=")) {
                    int eqIdx = line.indexOf('=');
                    String key = line.substring(0, eqIdx).trim();
                    String val = line.substring(eqIdx + 1).trim();
                    if ("SPRING_DATASOURCE_PASSWORD".equals(key) && !val.isBlank()) {
                        properties.put("spring.datasource.password", val);
                        return;
                    } else if ("POSTGRES_PASSWORD".equals(key) && !val.isBlank() && !properties.containsKey("spring.datasource.password")) {
                        properties.put("spring.datasource.password", val);
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }
}
