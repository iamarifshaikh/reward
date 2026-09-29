package com.intiq.reward.auth.service;

/**
 * What the client needs to render the "resend in 30s" countdown, and nothing more. Shared by
 * {@link EmailOtpService} and {@link SmsOtpService} so {@link AuthService} does not care which one
 * answered.
 */
public record OtpIssueResult(int expiresInSeconds, int retryAfterSeconds) {
}
