package com.intiq.reward.auth.enums;

/**
 * UNVERIFIED until the first successful OTP; BLOCKED denies login without deleting history.
 */
public enum UserStatus {
    UNVERIFIED,
    ACTIVE,
    BLOCKED
}
