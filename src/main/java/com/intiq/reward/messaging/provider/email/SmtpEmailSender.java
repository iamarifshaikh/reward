package com.intiq.reward.messaging.provider.email;

import com.intiq.reward.messaging.MessageDeliveryException;
import com.intiq.reward.messaging.provider.EmailRequest;
import com.intiq.reward.messaging.provider.EmailSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Plain SMTP, so any mailbox or a local Mailpit container works during development.
 * Swapping to Amazon SES later means adding an adapter and changing one property.
 */
@Component
@ConditionalOnProperty(name = "intiq.messaging.email.provider", havingValue = "smtp", matchIfMissing = true)
public class SmtpEmailSender implements EmailSender {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void send(EmailRequest request) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.from());
        message.setTo(request.to());
        message.setSubject(request.subject());
        message.setText(request.body());

        try {
            mailSender.send(message);
        } catch (MailException e) {
            throw new MessageDeliveryException("SMTP failed to accept the message", e);
        }
    }
}
