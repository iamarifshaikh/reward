package com.intiq.reward.auth.enums;

/**
 * What MSG91 actually told us, for one {@code sms_otp_attempts} row.
 * ERROR means we never got a real answer at all — a timeout or a non-2xx from MSG91 itself,
 * as opposed to MSG91 answering and rejecting the request.
 */
public enum SmsOtpOutcome {
    ACCEPTED,
    REJECTED,
    VERIFIED,
    EXPIRED,
    INVALID,
    ERROR
}
