package com.intiq.reward.messaging.provider.sms;

import com.intiq.reward.messaging.MessageDeliveryException;
import com.intiq.reward.messaging.provider.SmsRequest;
import com.intiq.reward.messaging.provider.SmsSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;

/**
 * MSG91 flow API. Activated by {@code intiq.messaging.sms.provider=msg91}; when it is active the
 * log adapter is not created, so nothing else in the application changes.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "intiq.messaging.sms.provider", havingValue = "msg91")
public class Msg91SmsSender implements SmsSender {

    private final RestClient restClient;
    private final Msg91Properties properties;

    public Msg91SmsSender(RestClient.Builder restClientBuilder, Msg91Properties properties) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader("authkey", properties.authKey())
                .build();
    }

    @Override
    public void send(SmsRequest request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("template_id", properties.templateIdFor(request.templateCode()));
        payload.put("sender", properties.senderId());
        payload.put("mobiles", request.destination().replaceFirst("^\\+", ""));
        if (request.variables() != null) {
            payload.putAll(request.variables());
        }

        try {
            restClient.post()
                    .uri("/api/v5/flow")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e) {
            throw new MessageDeliveryException("MSG91 failed to accept the message", e);
        }
    }
}
