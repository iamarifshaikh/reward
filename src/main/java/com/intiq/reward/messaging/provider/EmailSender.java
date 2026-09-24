package com.intiq.reward.messaging.provider;

/**
 * Port for outbound email. Chosen by the {@code intiq.messaging.email.provider} property:
 * SMTP now, Amazon SES or any other vendor later, with no change to calling code.
 */
public interface EmailSender {

    void send(EmailRequest request);
}
