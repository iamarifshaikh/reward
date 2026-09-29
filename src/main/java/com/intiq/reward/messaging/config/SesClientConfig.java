package com.intiq.reward.messaging.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * The SES client is created eagerly at startup, deliberately: a missing region or bad credentials
 * should fail the app on boot, not on the first login attempt someone makes.
 */
@Configuration
public class SesClientConfig {

    @Bean
    public SesClient sesClient(SesProperties properties) {
        return SesClient.builder()
                .region(Region.of(properties.region()))
                .build();
    }
}
