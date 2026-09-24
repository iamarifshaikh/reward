package com.intiq.reward.auth.enums;

/**
 * Why a code was issued. Verification matches on destination and purpose together, so a code sent
 * to confirm a new phone number cannot be spent on the login screen.
 *
 * <p>Adding a value here also needs a migration, because ck_otp_purpose constrains the column.
 */
public enum OtpPurpose {

    /** Signing in. */
    LOGIN,

    /** Confirming a new phone or email on an already active login. */
    CONTACT_CHANGE
}
