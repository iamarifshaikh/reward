package com.intiq.reward.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AWS SES settings. Only what SES cannot infer on its own: region and the verified from-address.
 *
 * <p>There is deliberately no access key or secret here. The AWS SDK resolves credentials through
 * its own default chain — environment variables, {@code ~/.aws/credentials} locally, or the
 * task/instance role in AWS — which is the standard practice for AWS services and avoids us
 * inventing a second, weaker place to store the same secrets.
 */
@ConfigurationProperties(prefix = "intiq.messaging.ses")
public record SesProperties(String region, String fromEmail, String fromName) {
}
