package com.intiq.reward.common.security;

import com.intiq.reward.common.enums.ActorType;

import java.util.UUID;

/**
 * The caller, as resolved from the access token. Injected into controllers with {@code @CurrentUser}.
 *
 * @param userId    the login
 * @param orgId     the business it acts for, null when acting as a consumer
 * @param actorType what it is acting as, which is what authorisation is based on
 */
public record AuthPrincipal(UUID userId, UUID orgId, ActorType actorType) {

    public boolean isAdmin() {
        return actorType == ActorType.ADMIN;
    }

    public boolean isConsumer() {
        return actorType == ActorType.CONSUMER;
    }

    public boolean isBrand() {
        return actorType == ActorType.BRAND;
    }

    public boolean isRetailer() {
        return actorType == ActorType.RETAILER;
    }

    public boolean belongsTo(UUID targetOrgId) {
        return orgId != null && orgId.equals(targetOrgId);
    }
}
