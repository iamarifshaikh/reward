package com.intiq.reward.messaging.provider;

import com.intiq.reward.messaging.Channel;
import com.intiq.reward.messaging.MessageDeliveryException;
import com.intiq.reward.messaging.MessageRequest;
import com.intiq.reward.messaging.MessageSender;
import com.intiq.reward.messaging.config.SmtpProperties;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Plain SMTP, email only. Its job is to be the fallback when MSG91 email is unavailable or its
 * domain verification is still pending, and to work against a local mail catcher in development.
 *
 * <p>There are no templates here, so it renders the subject and body the caller supplied.
 */
@Component
public class SmtpMessageSender implements MessageSender {

    private final JavaMailSender mailSender;
    private final SmtpProperties properties;

    public SmtpMessageSender(JavaMailSender mailSender, SmtpProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public String provider() {
        return "smtp";
    }

    @Override
    public boolean supports(Channel channel) {
        return channel == Channel.EMAIL;
    }

    @Override
    public void send(MessageRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(request.destination());
        message.setSubject(request.subject());
        message.setText(request.fallbackText());

        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new MessageDeliveryException("SMTP failed to accept the message", e);
        }
    }
}
