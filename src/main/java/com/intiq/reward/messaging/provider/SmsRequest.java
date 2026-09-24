package com.intiq.reward.messaging.provider;

import java.util.Map;

/**
 * One SMS to send.
 *
 * @param destination phone number in E.164
 * @param templateCode our own template name, mapped to the provider's DLT template id in config
 * @param variables    values substituted into the template
 * @param fallbackText readable text used by providers and adapters that do not use templates
 */
public record SmsRequest(String destination,
                         String templateCode,
                         Map<String, String> variables,
                         String fallbackText) {

    public static SmsRequest of(String destination, String templateCode, Map<String, String> variables, String fallbackText) {
        return new SmsRequest(destination, templateCode, variables, fallbackText);
    }
}
