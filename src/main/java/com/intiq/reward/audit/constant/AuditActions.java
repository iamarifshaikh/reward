package com.intiq.reward.audit.constant;

/**
 * Stable action codes. Constants rather than free text, so the admin filter has a fixed list and
 * a rename cannot silently split one action into two.
 */
public final class AuditActions {

    // auth
    public static final String OTP_REQUESTED = "OTP_REQUESTED";
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";

    // users
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_CONTACT_CORRECTED = "USER_CONTACT_CORRECTED";
    public static final String USER_PROFILE_UPDATED = "USER_PROFILE_UPDATED";
    public static final String USER_BLOCKED = "USER_BLOCKED";
    public static final String USER_UNBLOCKED = "USER_UNBLOCKED";
    public static final String CONSUMER_ENROLLED = "CONSUMER_ENROLLED";

    // organizations
    public static final String ORG_CREATED = "ORG_CREATED";
    public static final String ORG_UPDATED = "ORG_UPDATED";
    public static final String ORG_APPROVED = "ORG_APPROVED";
    public static final String ORG_SUSPENDED = "ORG_SUSPENDED";
    public static final String ORG_REACTIVATED = "ORG_REACTIVATED";
    public static final String ORG_CLOSED = "ORG_CLOSED";

    // channel
    public static final String PARTNER_CREATED = "PARTNER_CREATED";
    public static final String PARTNER_LINK_UPDATED = "PARTNER_LINK_UPDATED";
    public static final String PARTNER_SUSPENDED = "PARTNER_SUSPENDED";
    public static final String PARTNER_ENDED = "PARTNER_ENDED";

    // kyc
    public static final String KYC_UPLOADED = "KYC_UPLOADED";
    public static final String KYC_SUBMITTED = "KYC_SUBMITTED";
    public static final String KYC_APPROVED = "KYC_APPROVED";
    public static final String KYC_REJECTED = "KYC_REJECTED";

    // entity types
    public static final String ENTITY_USER = "USER";
    public static final String ENTITY_ORGANIZATION = "ORGANIZATION";
    public static final String ENTITY_CHANNEL = "CHANNEL_RELATIONSHIP";
    public static final String ENTITY_KYC_DOCUMENT = "KYC_DOCUMENT";

    private AuditActions() {
    }
}
