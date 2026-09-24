package com.intiq.reward.messaging.provider;

/**
 * One email to send. Plain text for now; an html body can be added without breaking callers.
 */
public record EmailRequest(String to, String subject, String body) {

    public static EmailRequest of(String to, String subject, String body) {
        return new EmailRequest(to, subject, body);
    }
}
