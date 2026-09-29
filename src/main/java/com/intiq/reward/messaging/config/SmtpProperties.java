package com.intiq.reward.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Host, port and credentials stay under {@code spring.mail.*} so Boot's own auto-configuration
 * keeps working. Only the from-address is ours.
 */
@ConfigurationProperties(prefix = "intiq.messaging.smtp")
public record SmtpProperties(String from) {
}
