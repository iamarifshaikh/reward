package com.intiq.reward.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Services take a Clock rather than calling Instant.now(), so expiry and cooldown logic can be
 * tested by moving time instead of sleeping.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
