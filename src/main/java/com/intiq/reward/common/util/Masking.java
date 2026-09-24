package com.intiq.reward.common.util;

/**
 * Masking for anything that leaves the service layer towards a log line, an audit diff or a
 * screen. The audit trail must never become a second copy of the sensitive data.
 */
public final class Masking {

    private Masking() {
    }

    /** +919876543210 becomes +91*******210 */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone == null ? null : "***";
        }
        String head = phone.substring(0, 3);
        String tail = phone.substring(phone.length() - 3);
        return head + "*".repeat(phone.length() - 6) + tail;
    }

    /** ravi.sharma@shop.com becomes ra***@shop.com */
    public static String email(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    /** AAPFU0939F becomes XXXXX939F */
    public static String pan(String pan) {
        if (pan == null || pan.length() != 10) {
            return pan == null ? null : "***";
        }
        return "XXXXX" + pan.substring(5);
    }
}
