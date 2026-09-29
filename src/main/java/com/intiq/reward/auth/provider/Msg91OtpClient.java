package com.intiq.reward.auth.provider;

import com.intiq.reward.messaging.Channel;
import com.intiq.reward.messaging.config.Msg91Properties;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * MSG91's dedicated OTP product: {@code SendOTP} and {@code Verify OTP}. This is a different
 * product from the plain SMS/Flow API used elsewhere in {@code messaging} — separate template
 * registry, separate endpoints, and here MSG91 itself generates and holds the code, not us.
 *
 * <p>Deliberately outside the generic {@code MessageDispatcher}/{@code MessageSender} abstraction:
 * that abstraction only knows how to send a message, and has no concept of "verify what I sent",
 * which is exactly what this product adds.
 *
 * <p>Reuses {@link Msg91Properties} for the shared auth key and the SMS template id, since it is
 * the same MSG91 account either way.
 */
@Component
public class Msg91OtpClient {

    private static final String SEND_PATH = "/api/v5/otp";
    private static final String VERIFY_PATH = "/api/v5/otp/verify";
    static final String TEMPLATE_CODE = "OTP_LOGIN";

    private final RestClient restClient;
    private final Msg91Properties properties;

    public Msg91OtpClient(Msg91Properties properties) {
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

    /**
     * Asks MSG91 to generate and send a code. Deliberately does not set {@code otp_expiry} or
     * {@code otp_length}: those are left to whatever the template is configured with in the MSG91
     * panel, so our config never drifts out of sync with what MSG91 actually enforces.
     *
     * @throws Msg91OtpException if MSG91 could not be reached at all (network failure, non-2xx)
     */
    public Msg91OtpResponse send(String mobileE164) {
        try {
            Msg91RawResponse response = restClient.post()
                    .uri(uriBuilder -> uriBuilder.path(SEND_PATH)
                            .queryParam("template_id", properties.templateId(Channel.SMS, TEMPLATE_CODE))
                            .queryParam("mobile", digitsOnly(mobileE164))
                            .queryParam("realTimeResponse", "1")
                            .build())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of())
                    .retrieve()
                    .body(Msg91RawResponse.class);
            return Msg91OtpResponse.from(response);
        } catch (RestClientException e) {
            throw new Msg91OtpException("MSG91 SendOTP request failed", e);
        }
    }

    /**
     * Asks MSG91 whether the code the user typed is the one it holds for this number.
     * MSG91's own wording ("OTP Expired", "Invalid OTP") is preserved in the response, so the
     * caller can classify it and the raw text can still be logged either way.
     */
    public Msg91OtpResponse verify(String mobileE164, String code) {
        try {
            Msg91RawResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path(VERIFY_PATH)
                            .queryParam("otp", code)
                            .queryParam("mobile", digitsOnly(mobileE164))
                            .build())
                    .retrieve()
                    .body(Msg91RawResponse.class);
            return Msg91OtpResponse.from(response);
        } catch (RestClientException e) {
            throw new Msg91OtpException("MSG91 Verify OTP request failed", e);
        }
    }

    /** MSG91 wants country code and digits only: 919876543210, not +919876543210. */
    private static String digitsOnly(String e164) {
        return e164 == null ? null : e164.replaceFirst("^\\+", "");
    }

    /** MSG91's actual envelope: {"type": "success" | "error", "message": "..."}. */
    private record Msg91RawResponse(String type, String message) {
    }

    /**
     * @param success true only when MSG91's type was exactly "success"
     * @param message MSG91's own text — "OTP Expired", "Invalid OTP", or its request id on success
     */
    public record Msg91OtpResponse(boolean success, String message) {
        static Msg91OtpResponse from(Msg91RawResponse raw) {
            if (raw == null) {
                return new Msg91OtpResponse(false, "empty response");
            }
            return new Msg91OtpResponse("success".equalsIgnoreCase(raw.type()), raw.message());
        }
    }

    /** MSG91 was unreachable, or answered with something that was not even a valid response. */
    public static class Msg91OtpException extends RuntimeException {
        public Msg91OtpException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
