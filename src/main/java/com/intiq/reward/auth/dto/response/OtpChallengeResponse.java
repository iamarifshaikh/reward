package com.intiq.reward.auth.dto.response;

/**
 * Deliberately says nothing about whether the contact exists.
 * retryAfterSeconds drives the resend countdown in the UI.
 */
public record OtpChallengeResponse(String message, int expiresInSeconds, int retryAfterSeconds) {
}
