package com.intiq.reward.messaging;

/**
 * A provider refused or failed to accept a message. Thrown by every adapter so callers handle one
 * exception type regardless of which vendor is configured.
 */
public class MessageDeliveryException extends RuntimeException {

    public MessageDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }

    public MessageDeliveryException(String message) {
        super(message);
    }
}
