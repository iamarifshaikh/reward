package com.intiq.reward.common.constant;

/**
 * One definition per format, so the same rule is not retyped across DTOs and drifts.
 */
public final class ValidationPatterns {

    /** E.164, e.g. +919876543210. */
    public static final String PHONE = "^\\+[1-9]\\d{7,14}$";

    /** 27AAPFU0939F1ZV */
    public static final String GSTIN = "^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";

    /** AAPFU0939F */
    public static final String PAN = "^[A-Z]{5}[0-9]{4}[A-Z]$";

    public static final String PINCODE = "^[1-9][0-9]{5}$";

    private ValidationPatterns() {
    }
}
