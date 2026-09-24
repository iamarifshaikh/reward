package com.intiq.reward.common.enums;

/**
 * What the caller is acting as. Derived from the organisation type of their login, or CONSUMER
 * when the login has no organisation. Lives in common because both the security principal and the
 * audit trail need it.
 */
public enum ActorType {
    ADMIN,
    BRAND,
    DISTRIBUTOR,
    RETAILER,
    CONSUMER
}
