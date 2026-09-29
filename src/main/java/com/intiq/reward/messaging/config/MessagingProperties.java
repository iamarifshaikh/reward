package com.intiq.reward.messaging.config;

import com.intiq.reward.messaging.Channel;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

/**
 * Which provider serves each channel, and what to try when it fails.
 *
 * <p>Two kinds of fallback, because they solve different problems:
 * {@code fallbackProvider} covers a vendor being down or misconfigured, e.g. MSG91 email failing
 * over to SMTP. {@code fallbackChannel} covers a channel being unavailable for that person, e.g.
 * WhatsApp not reachable so the message goes by SMS.
 */
@ConfigurationProperties(prefix = "intiq.messaging")
public record MessagingProperties(Map<Channel, ChannelConfig> channels) {

    public ChannelConfig configFor(Channel channel) {
        ChannelConfig config = channels == null ? null : channels.get(channel);
        if (config == null) {
            throw new IllegalStateException("No messaging provider configured for channel " + channel);
        }
        return config;
    }

    public record ChannelConfig(String provider, String fallbackProvider, Channel fallbackChannel) {
    }
}
