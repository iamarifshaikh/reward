package com.intiq.reward.messaging.provider;

/**
 * Port for outbound SMS. Callers depend on this, never on a vendor class, so the provider is
 * chosen by the {@code intiq.messaging.sms.provider} property alone.
 */
public interface SmsSender {

    void send(SmsRequest request);
}
