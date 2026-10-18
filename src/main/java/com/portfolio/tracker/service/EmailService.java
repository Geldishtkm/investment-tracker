package com.portfolio.tracker.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends account emails over SMTP. When no mail server is configured (MAIL_HOST unset, the usual
 * case in local development) the message is written to the log instead so signup still works.
 */
@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String mailHost;
    private final String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${spring.mail.host:}") String mailHost,
                        @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.mailHost = mailHost;
        this.from = from;
    }

    public boolean isConfigured() {
        return mailHost != null && !mailHost.isBlank() && mailSender.getIfAvailable() != null;
    }

    /**
     * Send the signup verification code.
     * @throws org.springframework.mail.MailException if the SMTP server rejects or cannot be reached
     */
    public void sendVerificationCode(String to, String username, String code, long ttlMinutes) {
        String subject = "Your Portfolio Tracker verification code: " + code;
        String body = "Hi " + username + ",\n\n"
                + "Your verification code is:\n\n"
                + "    " + code + "\n\n"
                + "Enter it on the sign-up screen to activate your account. "
                + "The code expires in " + ttlMinutes + " minutes.\n\n"
                + "If you didn't create a Portfolio Tracker account, you can ignore this email.\n";

        if (!isConfigured()) {
            logger.warn("MAIL_HOST is not set, so the email was not sent. Verification code for {} <{}>: {}",
                    username, to, code);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        mailSender.getObject().send(message);
        logger.info("Verification email sent to {}", to);
    }
}
