package com.intiq.reward.messaging;

import com.intiq.reward.messaging.config.MessagingProperties;
import com.intiq.reward.messaging.config.MessagingProperties.ChannelConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * The single entry point for sending anything. Services call this and know nothing about vendors.
 *
 * <p>It resolves the configured provider for the channel, and on failure makes exactly one further
 * attempt using the configured fallback. One attempt, never a chain, so a circular configuration
 * cannot loop.
 */
@Component
@Slf4j
public class MessageDispatcher {

    private final List<MessageSender> senders;
    private final MessagingProperties properties;

    public MessageDispatcher(List<MessageSender> senders, MessagingProperties properties) {
        this.senders = senders;
        this.properties = properties;
    }

    public void send(MessageRequest request) {
        ChannelConfig config = properties.configFor(request.channel());
        MessageSender primary = resolve(config.provider(), request.channel());

        try {
            primary.send(request);
            log.debug("Sent {} via {} template={}", request.channel(), primary.provider(), request.templateCode());
            return;
        } catch (MessageDeliveryException primaryFailure) {
            log.warn("{} failed to send {} template={}: {}",
                    primary.provider(), request.channel(), request.templateCode(), primaryFailure.getMessage());
            attemptFallback(request, config, primaryFailure);
        }
    }

    /**
     * Retrying the same message is safe for an OTP: the code is already stored, so this is a second
     * delivery attempt of one code, not a second code.
     */
    private void attemptFallback(MessageRequest request, ChannelConfig config, MessageDeliveryException cause) {
        if (StringUtils.hasText(config.fallbackProvider())) {
            MessageSender fallback = resolve(config.fallbackProvider(), request.channel());
            sendFallback(fallback, request, cause);
            return;
        }

        if (config.fallbackChannel() != null) {
            MessageRequest rerouted = request.withChannel(config.fallbackChannel());
            ChannelConfig fallbackConfig = properties.configFor(config.fallbackChannel());
            MessageSender fallback = resolve(fallbackConfig.provider(), rerouted.channel());
            sendFallback(fallback, rerouted, cause);
            return;
        }

        throw cause;
    }

    private void sendFallback(MessageSender fallback, MessageRequest request, MessageDeliveryException cause) {
        try {
            fallback.send(request);
            log.info("Delivered {} via fallback {} template={}",
                    request.channel(), fallback.provider(), request.templateCode());
        } catch (MessageDeliveryException fallbackFailure) {
            fallbackFailure.addSuppressed(cause);
            throw fallbackFailure;
        }
    }

    private MessageSender resolve(String provider, Channel channel) {
        return senders.stream()
                .filter(sender -> sender.provider().equalsIgnoreCase(provider))
                .filter(sender -> sender.supports(channel))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No message sender named '" + provider + "' supports channel " + channel));
    }
}
