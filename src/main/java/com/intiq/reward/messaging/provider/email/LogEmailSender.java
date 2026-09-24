package com.intiq.reward.messaging.provider.email;

import com.intiq.reward.messaging.provider.EmailRequest;
import com.intiq.reward.messaging.provider.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Writes the email to the log instead of sending it. Used in tests and when no mail server is
 * available; selected with {@code intiq.messaging.email.provider=log}.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "intiq.messaging.email.provider", havingValue = "log")
public class LogEmailSender implements EmailSender {

    @Override
    public void send(EmailRequest request) {
        log.info("[EMAIL:log] to={} subject={} body={}", request.to(), request.subject(), request.body());
    }
}
