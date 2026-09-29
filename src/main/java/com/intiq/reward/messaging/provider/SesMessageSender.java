package com.intiq.reward.messaging.provider;

import com.intiq.reward.messaging.Channel;
import com.intiq.reward.messaging.MessageDeliveryException;
import com.intiq.reward.messaging.MessageRequest;
import com.intiq.reward.messaging.MessageSender;
import com.intiq.reward.messaging.config.SesProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SesException;

/**
 * Email via AWS SES. Selected with {@code intiq.messaging.channels.email.provider=ses}.
 *
 * <p>Unlike MSG91 and WhatsApp, SES has no pre-approval requirement: we can send whatever subject
 * and body we like, so this adapter renders {@code subject} and {@code fallbackText} directly,
 * the same way {@code SmtpMessageSender} does, and ignores {@code templateCode}/{@code variables}.
 * That keeps SES and its SMTP fallback interchangeable in the dispatcher: both read from the same
 * two fields on {@link MessageRequest}.
 */
@Component
public class SesMessageSender implements MessageSender {

    private final SesClient sesClient;
    private final SesProperties properties;

    public SesMessageSender(SesClient sesClient, SesProperties properties) {
        this.sesClient = sesClient;
        this.properties = properties;
    }

    @Override
    public String provider() {
        return "ses";
    }

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.EMAIL;
    }

    @Override
    public void send(MessageRequest request) {
        String source = StringUtils.hasText(properties.fromName())
                ? "%s <%s>".formatted(properties.fromName(), properties.fromEmail())
                : properties.fromEmail();

        SendEmailRequest sesRequest = SendEmailRequest.builder()
                .source(source)
                .destination(Destination.builder().toAddresses(request.destination()).build())
                .message(Message.builder()
                        .subject(Content.builder().data(request.subject()).build())
                        .body(Body.builder()
                                .text(Content.builder().data(request.fallbackText()).build())
                                .build())
                        .build())
                .build();

        try {
            sesClient.sendEmail(sesRequest);
        } catch (SesException e) {
            // Covers an unverified sender/recipient (SES sandbox), a suppressed address, or throttling.
            throw new MessageDeliveryException("SES failed to accept the message: " + e.awsErrorDetails().errorMessage(), e);
        }
    }
}
