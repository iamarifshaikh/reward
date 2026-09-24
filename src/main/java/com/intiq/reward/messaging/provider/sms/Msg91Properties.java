package com.intiq.reward.messaging.provider.sms;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * MSG91 settings. {@code templateIds} maps our own template code to the DLT template id approved
 * for the client's sender, so template ids live in config rather than in code.
 */
@ConfigurationProperties(prefix = "intiq.messaging.sms.msg91")
public record Msg91Properties(String baseUrl,
                              String authKey,
                              String senderId,
                              Map<String, String> templateIds) {

    public String templateIdFor(String templateCode) {
        String id = templateIds == null ? null : templateIds.get(templateCode);
        if (id == null) {
            throw new IllegalStateException("No MSG91 template id configured for template code: " + templateCode);
        }
        return id;
    }
}
