
package com.banking.notificationservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * JavaMailSender connects our Spring Boot application to Gmail SMTP.
 * SimpleMailMessage prepares the email.
 * setFrom() specifies the sender.
 * setTo() specifies the recipient.
 * setSubject() specifies the email subject.
 * setText() specifies the email body.
 * send() actually sends the email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendEmail(
            String to,
            String subject,
            String body) {

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        // Let exceptions propagate so Kafka retry
        // and DLT handling can work later.
        mailSender.send(message);

        log.info("Email successfully submitted to recipient");
    }
}
