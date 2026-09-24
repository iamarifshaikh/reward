package com.intiq.reward.auth.dto.response;

import java.util.List;

/**
 * The access token and who the caller now is. The refresh token is deliberately absent: it goes
 * back as an httpOnly cookie, so no script on the page can read it.
 */
public record LoginResponse(String accessToken,
                            long expiresInSeconds,
                            ContextResponse activeContext,
                            List<ContextResponse> availableContexts) {
}
