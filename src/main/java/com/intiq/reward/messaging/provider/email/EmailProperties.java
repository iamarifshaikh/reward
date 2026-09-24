package com.intiq.reward.messaging.provider.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings shared by every email adapter. Host, port and credentials stay under spring.mail.*
 * so Spring Boot's own SMTP auto-configuration keeps working.
 */
@ConfigurationProperties(prefix = "intiq.messaging.email")
public record EmailProperties(String from) {
}
