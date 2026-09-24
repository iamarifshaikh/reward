package com.intiq.reward.common.util;

import com.intiq.reward.common.exception.DomainException;
import com.intiq.reward.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.Locale;

/**
 * Normalises contacts before they are stored or looked up. Without this, the same person typed as
 * "9876543210", "+91 98765 43210" and "Ravi@Shop.com" becomes three accounts.
 */
public final class ContactUtils {

    /** India. When the platform crosses borders this becomes configuration. */
    private static final String DEFAULT_DIAL_CODE = "+91";

    private ContactUtils() {
    }

    /** Returns E.164, e.g. +919876543210. */
    public static String normalizePhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return null;
        }
        String digits = phone.replaceAll("[\\s\\-()]", "");
        if (digits.startsWith("+")) {
            return digits;
        }
        if (digits.startsWith("00")) {
            return "+" + digits.substring(2);
        }
        if (digits.length() == 10) {
            return DEFAULT_DIAL_CODE + digits;
        }
        if (digits.length() == 12 && digits.startsWith("91")) {
            return "+" + digits;
        }
        throw new DomainException(ErrorCode.COMMON_VALIDATION, "Not a valid phone number");
    }

    public static String normalizeEmail(String email) {
        return StringUtils.hasText(email) ? email.trim().toLowerCase(Locale.ROOT) : null;
    }
}
