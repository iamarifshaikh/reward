package com.intiq.reward.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param name     cookie carrying the refresh token
 * @param secure   false only on plain-http localhost; true everywhere else
 * @param sameSite Strict, because no third-party site ever needs to trigger a refresh
 * @param path     scoped to the auth endpoints, so the cookie is not sent with ordinary API calls
 */
@ConfigurationProperties(prefix = "intiq.auth.cookie")
public record AuthCookieProperties(String name, boolean secure, String sameSite, String path) {}