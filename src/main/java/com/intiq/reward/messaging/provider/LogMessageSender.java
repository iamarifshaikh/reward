package com.intiq.reward.messaging.provider;

import com.intiq.reward.messaging.Channel;
import com.intiq.reward.messaging.MessageRequest;
import com.intiq.reward.messaging.MessageSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Writes the message to the log instead of sending it, for every channel.
 *
 * <p>Always registered, so development and tests need no accounts, no credits and no network. It is
 * only used where configuration names it, which is why it can exist alongside the real providers.
 */
@Component
@Slf4j
public class LogMessageSender implements MessageSender {

    @Override
    public String provider() {
        return "log";
    }

    @Override
    public boolean supports(Channel channel) {
        return true;
    }

    @Override
    public void send(MessageRequest request) {
        log.info("[{}:log] to={} template={} vars={} text={}",
                request.channel(), request.destination(), request.templateCode(),
                request.variables(), request.fallbackText());
    }
}
