package com.intiq.reward.messaging;

import java.util.Map;

/**
 * One message, in the two forms a provider might need.
 *
 * <p>MSG91 and WhatsApp are template based: the text is pre-approved and identified by a template
 * id, and we supply only {@code variables}. SMTP and the log adapter have no templates, so they
 * render {@code subject} and {@code fallbackText}. Callers fill in both and never learn which
 * provider is configured.
 *
 * @param templateCode our own name for the message, e.g. OTP_LOGIN, mapped to each provider's id in config
 * @param variables    values substituted into the template
 */
public record MessageRequest(Channel channel,
                             String destination,
                             String recipientName,
                             String templateCode,
                             Map<String, String> variables,
                             String subject,
                             String fallbackText) {

    public static MessageRequest of(Channel channel,
                                    String destination,
                                    String recipientName,
                                    String templateCode,
                                    Map<String, String> variables,
                                    String subject,
                                    String fallbackText) {
        return new MessageRequest(channel, destination, recipientName, templateCode,
                variables == null ? Map.of() : variables, subject, fallbackText);
    }

    /**
     * Same message on another channel, used for channel fallback such as WhatsApp to SMS.
     * Only valid where the destination suits both channels, which is why it is not used for email.
     */
    public MessageRequest withChannel(Channel other) {
        return new MessageRequest(other, destination, recipientName, templateCode, variables, subject, fallbackText);
    }
}
