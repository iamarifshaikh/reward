package com.intiq.reward.messaging;

/**
 * The port every provider implements. Callers depend on {@link MessageDispatcher}, never on this
 * directly, so swapping or adding a vendor is a configuration change.
 */
public interface MessageSender {

    /** Provider name used in configuration: msg91, smtp, log. */
    String provider();

    /** SMTP supports email only; MSG91 and the log adapter support everything. */
    boolean supports(Channel channel);

    /**
     * @throws MessageDeliveryException when the provider refused or failed to accept the message,
     *                                  which is what triggers the configured fallback
     */
    void send(MessageRequest request);
}
