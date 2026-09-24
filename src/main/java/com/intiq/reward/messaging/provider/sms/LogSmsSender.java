package com.intiq.reward.messaging.provider.sms;

import com.intiq.reward.messaging.provider.SmsRequest;
import com.intiq.reward.messaging.provider.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default adapter: writes the message to the log instead of sending it. Lets the whole OTP flow be
 * developed and tested before an SMS account or DLT registration exists.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "intiq.messaging.sms.provider", havingValue = "log", matchIfMissing = true)
public class LogSmsSender implements SmsSender {

    @Override
    public void send(SmsRequest request) {
        log.info("[SMS:log] to={} template={} text={}",
                request.destination(), request.templateCode(), request.fallbackText());
    }
}
