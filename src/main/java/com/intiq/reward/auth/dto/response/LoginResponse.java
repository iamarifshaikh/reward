package com.intiq.reward.auth.dto.response;

/**
 * The access token and who the caller is. The refresh token is deliberately absent: it goes
 * back as an httpOnly cookie, so no script on the page can read it.
 */
public record LoginResponse(String accessToken,
                            long expiresInSeconds,
                            ContextResponse context) {
}
