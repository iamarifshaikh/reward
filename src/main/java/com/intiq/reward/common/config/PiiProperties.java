package com.intiq.reward.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param key base64 encoded 32 byte AES key. Comes from the environment, and in production from
 *            Secrets Manager. Rotating it means re-encrypting the stored values, so treat it as
 *            long lived and guard it accordingly.
 */
@ConfigurationProperties(prefix = "intiq.security.pii")
public record PiiProperties(String key) {}