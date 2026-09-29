package com.intiq.reward.messaging.provider;

import com.intiq.reward.messaging.Channel;
import com.intiq.reward.messaging.MessageDeliveryException;
import com.intiq.reward.messaging.MessageRequest;
import com.intiq.reward.messaging.MessageSender;
import com.intiq.reward.messaging.config.Msg91Properties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * MSG91 for SMS and WhatsApp. One client, one auth key, one response check; only the endpoint and
 * the payload differ, which is why this is a single adapter rather than two.
 *
 * <p>Email is not handled here: see {@code SesMessageSender}, since email goes through AWS SES.
 *
 * <p>Timeouts are deliberate: an OTP send happens inside a login request, so a slow provider must
 * fail fast and let the dispatcher fall back rather than hold the user on a spinner.
 */
@Component
@Slf4j
public class Msg91MessageSender implements MessageSender {

    private static final String SMS_PATH = "/api/v5/flow";
    private static final String WHATSAPP_PATH = "/api/v5/whatsapp/whatsapp-outbound-message/bulk/";

    private final RestClient restClient;
    private final Msg91Properties properties;

    public Msg91MessageSender(Msg91Properties properties) {
        this.properties = properties;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(properties.baseUrl())
                .defaultHeader("authkey", properties.authKey())
                .defaultHeader("accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public String provider() {
        return "msg91";
    }

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.SMS || channel == Channel.WHATSAPP;
    }

    @Override
    public void send(MessageRequest request) {
        String path = switch (request.channel()) {
            case SMS -> SMS_PATH;
            case WHATSAPP -> WHATSAPP_PATH;
            case EMAIL -> throw new IllegalArgumentException("Msg91MessageSender does not handle email; use SES");
        };
        Map<String, Object> payload = switch (request.channel()) {
            case SMS -> smsPayload(request);
            case WHATSAPP -> whatsappPayload(request);
            case EMAIL -> throw new IllegalArgumentException("Msg91MessageSender does not handle email; use SES");
        };

        Msg91Response response;
        try {
            response = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Msg91Response.class);
        } catch (RestClientException e) {
            throw new MessageDeliveryException("MSG91 request failed for " + request.channel(), e);
        }

        // MSG91 answers 200 even when it rejects a message, so the body decides, not the status.
        if (response != null && response.type() != null && !"success".equalsIgnoreCase(response.type())) {
            throw new MessageDeliveryException("MSG91 rejected the message: " + response.message());
        }
        log.debug("MSG91 accepted {} template={} ref={}",
                request.channel(), request.templateCode(), response == null ? null : response.message());
    }

    /** Flow API: variables live inside each recipient, alongside the number. */
    private Map<String, Object> smsPayload(MessageRequest request) {
        Map<String, Object> recipient = new HashMap<>();
        recipient.put("mobiles", digitsOnly(request.destination()));
        recipient.putAll(request.variables());

        Map<String, Object> payload = new HashMap<>();
        payload.put("template_id", properties.templateId(Channel.SMS, request.templateCode()));
        payload.put("recipients", List.of(recipient));
        payload.put("realTimeResponse", "1");
        if (properties.sms() != null && StringUtils.hasText(properties.sms().senderId())) {
            payload.put("sender", properties.sms().senderId());
        }
        return payload;
    }

    /**
     * WhatsApp templates take positional components, not named variables, so the variable map is
     * ordered by key and mapped to body_1, body_2 and so on. Name variables v1, v2 … when a
     * template has more than one.
     */
    private Map<String, Object> whatsappPayload(MessageRequest request) {
        Msg91Properties.Whatsapp whatsapp = properties.whatsapp();

        Map<String, Object> components = new HashMap<>();
        int position = 1;
        for (Map.Entry<String, String> variable : new TreeMap<>(request.variables()).entrySet()) {
            components.put("body_" + position++, Map.of("type", "text", "value", variable.getValue()));
        }

        List<Map<String, Object>> toAndComponents = new ArrayList<>();
        toAndComponents.add(Map.of(
                "to", List.of(digitsOnly(request.destination())),
                "components", components));

        Map<String, Object> template = new HashMap<>();
        template.put("name", properties.templateId(Channel.WHATSAPP, request.templateCode()));
        template.put("language", Map.of(
                "code", whatsapp.languageCode(),
                "policy", "deterministic"));
        template.put("to_and_components", toAndComponents);

        Map<String, Object> payload = new HashMap<>();
        payload.put("integrated_number", digitsOnly(whatsapp.integratedNumber()));
        payload.put("content_type", "template");
        payload.put("payload", Map.of("type", "template", "template", template));
        return payload;
    }

    /** MSG91 wants country code and digits only: 919876543210, not +919876543210. */
    private static String digitsOnly(String phone) {
        return phone == null ? null : phone.replaceFirst("^\\+", "");
    }

    /** On success {@code message} carries MSG91's request id, on failure the reason. */
    private record Msg91Response(String message, String type) {
    }
}
