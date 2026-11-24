package com.nisatsama.shelfsense;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class TestEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Map<String, Object> properties = new HashMap<>();

        String dbPassword = environment.getProperty("spring.datasource.password");
        if (dbPassword == null || dbPassword.isBlank()) {
            String postgresPassword = environment.getProperty("POSTGRES_PASSWORD");
            if (postgresPassword != null && !postgresPassword.isBlank()) {
                properties.put("spring.datasource.password", postgresPassword);
            } else {
                loadFromDotEnv(properties);
            }
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

    private void loadFromDotEnv(Map<String, Object> properties) {
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
