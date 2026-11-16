package com.nisatsama.shelfsense.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock applicationClock() {
        return Clock.systemDefaultZone();
    }
}
