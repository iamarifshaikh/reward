package com.intiq.reward.messaging;

/**
 * How a message reaches someone. Every channel goes through the same port, so adding one does not
 * change any calling code.
 */
public enum Channel {
    SMS,
    EMAIL,
    WHATSAPP
}
