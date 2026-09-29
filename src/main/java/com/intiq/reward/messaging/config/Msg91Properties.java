package com.intiq.reward.messaging.config;

import com.intiq.reward.messaging.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Everything MSG91 needs, for SMS and WhatsApp. One account and one auth key cover both, so the
 * credentials sit at the top and each channel adds only its own specifics.
 *
 * <p>Email is not MSG91: see {@link SesProperties}, since we send email through AWS SES instead.
 *
 * <p>{@code templates} maps our template code to the provider's id per channel, which keeps ids
 * out of the code: a new message type in MVP 4 is a config entry, not a deployment.
 */
@ConfigurationProperties(prefix = "intiq.messaging.msg91")
public record Msg91Properties(String baseUrl,
                              String authKey,
                              Sms sms,
                              Whatsapp whatsapp,
                              Map<Channel, Map<String, String>> templates) {

    public String templateId(Channel channel, String templateCode) {
        Map<String, String> perChannel = templates == null ? null : templates.get(channel);
        String id = perChannel == null ? null : perChannel.get(templateCode);
        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                    "No MSG91 template configured for " + channel + "/" + templateCode);
        }
        return id;
    }

    /** @param senderId the DLT-approved six character header, e.g. INTIQR */
    public record Sms(String senderId) {
    }

    /**
     * @param integratedNumber the WhatsApp Business number registered on MSG91
     * @param languageCode     template language, e.g. en or en_US, as approved with the template
     */
    public record Whatsapp(String integratedNumber, String languageCode) {
    }
}
